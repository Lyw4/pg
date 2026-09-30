package com.feedflow.domain;

import com.feedflow.common.exception.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 불량 기록 - 검수에서 문제를 발견한 뒤 처리까지의 이력.
 *
 * <h3>왜 이 엔티티가 필요했나</h3>
 * 이 프로젝트는 <b>검수를 통과하지 않은 재고가 출고되는 것을 막았다.</b>
 * 입고 대기 · 검수 구역의 재고는 출고 후보에서 제외된다.
 * <p>
 * 그런데 <b>검수에서 불량이 나오면 그 다음에 무엇을 하는가</b> 에 대한 답이 없었다.
 * 재고는 검수 구역에 남아 출고되지 않을 뿐, 언제 무엇 때문에 문제가 있었고
 * 어떻게 처리했는지 기록할 곳이 없었다. 그래서 다음을 알 수 없었다.
 * <ul>
 *     <li>어느 제조사에서 불량이 반복되는가 (공급업체 평가의 근거)</li>
 *     <li>어느 단계에서 불량이 주로 발견되는가 (입고 검사 vs 보관 중)</li>
 *     <li>격리해 둔 재고가 며칠째 방치되어 있는가</li>
 * </ul>
 * B2C 담당 팀원의 {@code com.ex.entity.DefectRecord} 를 이 프로젝트의 구역 체계와
 * 폐기 기능에 맞춰 옮겼다.
 *
 * <h3>재고를 직접 바꾸지 않는다</h3>
 * 이 엔티티는 <b>기록만</b> 한다. 재고 차감은 기존 폐기 기능이, 정상 복귀는
 * 구역 간 이동이 담당한다. 같은 일을 하는 코드가 두 곳에 생기면 한쪽만 고쳤을 때
 * 재고는 줄었는데 이력이 없는 상태가 만들어진다.
 * {@link DefectResolution#getFollowUp()} 이 담당자에게 다음 할 일을 안내한다.
 *
 * <h3>수량은 로트 잔여를 넘을 수 있다</h3>
 * 이상하게 보이지만 의도한 것이다. 불량 100포대를 격리하고 폐기까지 마친 뒤에도
 * <b>그 기록은 남아야 한다.</b> 폐기하면 로트 잔여는 줄어들므로, 기록 수량과 현재
 * 재고를 비교해 검증하면 과거 기록이 모두 오류로 잡힌다.
 * 여기서 검증하는 것은 <b>등록 시점에 양수인지</b>뿐이다.
 */
@Entity
@Table(
        name = "defectRecords",
        uniqueConstraints = @UniqueConstraint(name = "ukDefectNo", columnNames = "defectNo")
)
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DefectRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "defectId")
    private Long defectId;

    /**
     * 불량 관리 번호 (업무 식별자).
     * <p>
     * 담당자끼리 "DF-2607-003 건" 으로 부를 수 있어야 한다. PK 로 부르면
     * 데이터를 다시 심었을 때 번호가 달라진다.
     */
    @Column(name = "defectNo", nullable = false, unique = true, length = 30)
    private String defectNo;

    /**
     * 대상 로트.
     * <p>
     * 품목이 아니라 로트를 참조한다. 같은 품목이어도 <b>제조 단위가 다르면 별개 문제</b>다.
     * 특정 로트에서만 불량이 나오면 그 로트의 제조 시점을 의심할 수 있다.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lotId", nullable = false)
    private ProductLot lot;

    /**
     * 발견된 구역.
     * <p>
     * nullable 이다. 이관 중 파손처럼 <b>구역을 특정할 수 없는 경우</b>가 있고,
     * 그때는 {@link #stage} 만으로도 어디서 생긴 문제인지 알 수 있다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "binId")
    private WarehouseBin bin;

    /** 불량 수량 (포대) */
    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "defectType", nullable = false, length = 30)
    private DefectType defectType;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false, length = 30)
    private DefectStage stage;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private DefectStatus status;

    /** 처리 방법. 처리 완료 전에는 null */
    @Enumerated(EnumType.STRING)
    @Column(name = "resolution", length = 30)
    private DefectResolution resolution;

    /** 발견 당시 상황 · 검사 소견 */
    @Column(name = "memo", length = 300)
    private String memo;

    /** 처리 결과 설명 (반품 접수 번호, 재작업 내용 등) */
    @Column(name = "resolutionMemo", length = 300)
    private String resolutionMemo;

    /**
     * 발견자 이름 스냅샷.
     * <p>
     * FK 가 아니다. 담당자가 퇴사해도 "누가 발견했는가" 는 남아야 한다.
     * 이 프로젝트의 재고 이력이 처리자를 같은 방식으로 보관한다.
     */
    @Column(name = "reportedByName", length = 50)
    private String reportedByName;

    /** 처리자 이름 스냅샷 */
    @Column(name = "resolvedByName", length = 50)
    private String resolvedByName;

    @Column(name = "createdAt", nullable = false)
    private LocalDateTime createdAt;

    /** 처리 완료 일시. 완료 전에는 null */
    @Column(name = "resolvedAt")
    private LocalDateTime resolvedAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = DefectStatus.QUARANTINED;
        }
    }

    /* ------------------------------------------------------------------
     * 생성
     * ------------------------------------------------------------------ */

    /**
     * 불량 발견 등록.
     *
     * <h3>여기서 검증하는 것과 하지 않는 것</h3>
     * 검증하는 것은 <b>이 기록 자체가 성립하는가</b> 뿐이다. 로트가 있어야 하고,
     * 유형이 있어야 하고, 수량이 양수여야 한다.
     * <p>
     * 검증하지 <b>않는</b> 것은 <b>수량이 로트 잔여 재고를 넘는지</b>다. 불량 기록은
     * 처리 후에도 남아야 하는 이력인데, 폐기로 처리하면 재고가 줄어든다.
     * 현재 재고와 비교하는 규칙을 두면 어제 등록한 정상적인 기록이 오늘 오류가 된다.
     * <p>
     * 발견 단계를 넘기지 않으면 구역 용도로 추정한다. 검수 구역에서 발견했다면
     * 입고 검사 단계인 것이 분명하므로 담당자에게 다시 묻지 않는다.
     *
     * @param defectNo       관리번호 (서비스가 발급)
     * @param lot            불량이 발생한 로트 (필수)
     * @param bin            발견 구역. 센터 간 이관 중 파손처럼 특정할 수 없으면 null
     * @param quantity       불량 수량 (포대, 1 이상)
     * @param defectType     불량 유형 (필수)
     * @param stage          발견 단계. null 이면 구역 용도로 추정
     * @param memo           발견 당시 상황
     * @param reportedByName 발견자 이름
     * @throws BusinessRuleException 로트 · 유형이 없거나 수량이 1 미만인 경우
     */
    public static DefectRecord report(String defectNo,
                                      ProductLot lot,
                                      WarehouseBin bin,
                                      int quantity,
                                      DefectType defectType,
                                      DefectStage stage,
                                      String memo,
                                      String reportedByName) {
        if (lot == null) {
            throw new BusinessRuleException("불량이 발생한 로트를 선택해 주세요.");
        }
        if (defectType == null) {
            throw new BusinessRuleException("불량 유형을 선택해 주세요.");
        }
        if (quantity <= 0) {
            throw new BusinessRuleException("불량 수량은 1 이상이어야 합니다.");
        }

        DefectStage resolvedStage = stage != null
                                    ? stage
                                    : DefectStage.from(bin == null ? null : bin.getBinPurpose());
        if (resolvedStage == null) {
            throw new BusinessRuleException(
                    "발견 단계를 선택해 주세요. 구역을 지정하지 않으면 단계를 추정할 수 없습니다.");
        }

        return DefectRecord.builder()
                .defectNo(defectNo)
                .lot(lot)
                .bin(bin)
                .quantity(quantity)
                .defectType(defectType)
                .stage(resolvedStage)
                .status(DefectStatus.QUARANTINED)
                .memo(memo)
                .reportedByName(reportedByName)
                .createdAt(LocalDateTime.now())
                .build();
    }

    /* ------------------------------------------------------------------
     * 도메인 로직
     * ------------------------------------------------------------------ */

    /**
     * 검사 착수 (격리 → 검사 중).
     *
     * @throws BusinessRuleException 이미 검사 중이거나 처리 완료된 경우
     */
    public void startInspection(String memo) {
        requireTransition(DefectStatus.INSPECTING);
        this.status = DefectStatus.INSPECTING;
        if (memo != null && !memo.isBlank()) {
            this.memo = memo;
        }
    }

    /**
     * 처리 완료.
     * <p>
     * 격리 상태에서 곧바로 완료할 수 있다. 유통기한 경과처럼 원인이 명백하면
     * 검사 단계를 거칠 이유가 없다.
     *
     * @param resolution     처리 방법
     * @param resolutionMemo 처리 결과 설명
     * @param resolvedByName 처리자 이름
     * @throws BusinessRuleException 이미 처리 완료된 경우 또는 처리 방법이 없는 경우
     */
    public void resolve(DefectResolution resolution, String resolutionMemo, String resolvedByName) {
        if (resolution == null) {
            throw new BusinessRuleException("처리 방법을 선택해 주세요.");
        }
        requireTransition(DefectStatus.RESOLVED);

        this.status = DefectStatus.RESOLVED;
        this.resolution = resolution;
        this.resolutionMemo = resolutionMemo;
        this.resolvedByName = resolvedByName;
        this.resolvedAt = LocalDateTime.now();
    }

    private void requireTransition(DefectStatus target) {
        if (!status.canMoveTo(target)) {
            throw new BusinessRuleException(
                    "현재 상태(" + status.getDescription() + ")에서 "
                            + target.getDescription() + " 으로 변경할 수 없습니다."
                            + (status == DefectStatus.RESOLVED
                               ? " 이미 처리된 건은 되돌릴 수 없습니다."
                                 + " 같은 로트에서 불량이 다시 나왔다면 새 건으로 등록하세요."
                               : ""));
        }
    }

    /* ------------------------------------------------------------------
     * 조회 편의
     * ------------------------------------------------------------------ */

    public boolean isOpen() {
        return status.isOpen();
    }

    /** 재고 차감이 아직 남은 건인지 (반품 · 폐기로 처리했는데 폐기 화면을 거치지 않았을 수 있다) */
    public boolean isStockRemovalPending() {
        return resolution != null && resolution.isStockRemoved();
    }

    public Long lotId() {
        return lot == null ? null : lot.getLotId();
    }

    public String lotNo() {
        return lot == null ? null : lot.getLotNo();
    }

    public Product product() {
        return lot == null ? null : lot.getProduct();
    }

    /** 제조사 (없을 수 있다 — 품목에 제조사가 등록되지 않은 경우) */
    public Manufacturer manufacturer() {
        Product product = product();
        return product == null ? null : product.getManufacturer();
    }

    public Long centerId() {
        return bin == null ? null : bin.centerId();
    }

    public String centerName() {
        return bin == null ? null : bin.centerName();
    }
}
