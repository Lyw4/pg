package com.feedflow.admin.dto;

import com.feedflow.domain.DefectRecord;
import com.feedflow.domain.DefectResolution;
import com.feedflow.domain.DefectStage;
import com.feedflow.domain.DefectStatus;
import com.feedflow.domain.DefectType;
import com.feedflow.domain.Manufacturer;
import com.feedflow.domain.Product;
import lombok.Builder;
import lombok.Getter;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 불량 기록 목록 행.
 * <p>
 * 엔티티를 화면으로 넘기지 않는다. {@code DefectRecord.lot} · {@code bin} 은 지연 로딩이라
 * 템플릿에서 건드리면 렌더링 중에 쿼리가 나가고, 영속성 컨텍스트가 닫혀 있으면 예외가 된다.
 */
@Getter
@Builder
public class DefectRecordDto {

    private final Long defectId;
    private final String defectNo;

    private final Long lotId;
    private final String lotNo;
    private final String productCode;
    private final String productName;

    /** 제조사명. 품목에 제조사가 등록되지 않았으면 null */
    private final String manufacturerName;

    /** 제조사 연락처. 반품 처리 시 필요 */
    private final String manufacturerPhone;

    private final String binCode;
    private final String centerName;

    private final int quantity;
    private final DefectType defectType;
    private final DefectStage stage;
    private final DefectStatus status;
    private final DefectResolution resolution;

    private final String memo;
    private final String resolutionMemo;
    private final String reportedByName;
    private final String resolvedByName;

    private final LocalDateTime createdAt;
    private final LocalDateTime resolvedAt;

    public static DefectRecordDto of(DefectRecord record) {
        Product product = record.product();
        Manufacturer manufacturer = record.manufacturer();

        return DefectRecordDto.builder()
                .defectId(record.getDefectId())
                .defectNo(record.getDefectNo())
                .lotId(record.lotId())
                .lotNo(record.lotNo())
                .productCode(product == null ? null : product.getProductCode())
                .productName(product == null ? null : product.getName())
                .manufacturerName(manufacturer == null ? null : manufacturer.getName())
                .manufacturerPhone(manufacturer == null ? null : manufacturer.getPhone())
                .binCode(record.getBin() == null ? null : record.getBin().getBinCode())
                .centerName(record.centerName())
                .quantity(record.getQuantity())
                .defectType(record.getDefectType())
                .stage(record.getStage())
                .status(record.getStatus())
                .resolution(record.getResolution())
                .memo(record.getMemo())
                .resolutionMemo(record.getResolutionMemo())
                .reportedByName(record.getReportedByName())
                .resolvedByName(record.getResolvedByName())
                .createdAt(record.getCreatedAt())
                .resolvedAt(record.getResolvedAt())
                .build();
    }

    /* ------------------------------------------------------------------
     * 화면 표기용
     * ------------------------------------------------------------------ */

    public String getDefectTypeDescription() {
        return defectType == null ? "-" : defectType.getDescription();
    }

    public String getDefectTypeBadgeClass() {
        return defectType == null ? "bg-light text-dark" : defectType.getBadgeClass();
    }

    public String getStageDescription() {
        return stage == null ? "-" : stage.getDescription();
    }

    public String getStageBadgeClass() {
        return stage == null ? "bg-light text-dark" : stage.getBadgeClass();
    }

    public String getStatusDescription() {
        return status == null ? "-" : status.getDescription();
    }

    public String getStatusBadgeClass() {
        return status == null ? "bg-light text-dark" : status.getBadgeClass();
    }

    public String getResolutionDescription() {
        return resolution == null ? null : resolution.getDescription();
    }

    public String getResolutionBadgeClass() {
        return resolution == null ? "bg-light text-dark" : resolution.getBadgeClass();
    }

    /** 처리 후 담당자가 해야 할 일 (재고 차감이 남았는지 등) */
    public String getFollowUp() {
        return resolution == null ? null : resolution.getFollowUp();
    }

    public boolean isOpen() {
        return status != null && status.isOpen();
    }

    /** 격리 상태인지 (검사 착수 버튼을 보일지 결정) */
    public boolean isQuarantined() {
        return status == DefectStatus.QUARANTINED;
    }

    /** 재고 차감이 아직 남은 건인지 (반품 · 폐기로 처리했으면 폐기 화면을 거쳐야 한다) */
    public boolean isStockRemovalPending() {
        return resolution != null && resolution.isStockRemoved();
    }

    /** 제조사가 등록되지 않은 품목인지 — 반품을 검토해야 하는데 대상을 모르는 상태 */
    public boolean isManufacturerUnknown() {
        return manufacturerName == null || manufacturerName.isBlank();
    }

    /**
     * 공급업체 반품을 검토할 만한 건인지.
     * <p>
     * 유형이 제조 · 운송 문제로 보이고 <b>입고 검사에서 발견</b>되었을 때 가장 강한
     * 근거가 된다. 우리가 보관해 본 적 없는 재고이기 때문이다.
     */
    public boolean isSupplierReturnCandidate() {
        return defectType != null && defectType.isSupplierReturnCandidate()
                && stage != null && stage.isAtReceiving();
    }

    /** 등록 후 경과 일수 — 격리 재고가 며칠째 방치되어 있는지 */
    public long getAgeDays() {
        if (createdAt == null) {
            return 0;
        }
        LocalDateTime end = resolvedAt == null ? LocalDateTime.now() : resolvedAt;
        return Math.max(0, Duration.between(createdAt, end).toDays());
    }

    /** 미처리인데 오래된 건인지 (화면에서 강조) */
    public boolean isStale() {
        return isOpen() && getAgeDays() >= 7;
    }
}
