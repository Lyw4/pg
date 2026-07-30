package com.feedflow.domain;

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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 창고 구역(Bin) - 기준 정보(Master Data).
 * <p>
 * 재고를 보관하는 최소 단위 위치를 나타낸다. (구역 - 랙 - 단)
 * 예) A-01-02 = A구역 1번 랙 2단
 * <p>
 * 테이블/컬럼명은 카멜 표기법으로 선언한다.
 * (level 은 SQL 예약어와 혼동될 수 있어 컬럼명을 binLevel 로 지정)
 */
@Entity
@Table(name = "warehouseBins")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WarehouseBin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "binId")
    private Long binId;

    /** 구역 코드 (업무 식별자, 중복 불가) 예: A-01-02 */
    @Column(name = "binCode", nullable = false, unique = true, length = 30)
    private String binCode;

    /**
     * 소속 물류센터 — 2D 도면은 센터 단위로 그린다.
     * <p>
     * 원래 {@code Warehouse} enum 이었으나 전국 단위로 확장하면서 엔티티로 승격했다.
     * 센터는 운영 중에 늘고 줄어들어 enum 으로는 값을 추가할 수 없다.
     * <p>
     * {@code LAZY} 이므로 센터명을 표시하는 조회는 {@code join fetch} 로 함께 읽어야 한다.
     * ({@link #locationLabel()} 이 센터명을 쓴다)
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "centerId", nullable = false)
    private Center center;

    /** 구역(Zone) 예: A, B, COLD */
    @Column(name = "zone", nullable = false, length = 20)
    private String zone;

    /** 구역 용도 (보관 / 입고 대기 / 출고 대기 / 검수) */
    @Enumerated(EnumType.STRING)
    @Column(name = "binPurpose", nullable = false, length = 20)
    private BinPurpose binPurpose;

    /* ------------------------------------------------------------------
     * 2D 도면 배치 좌표
     *
     * 창고 평면을 격자(기본 24 x 18)로 보고 사각형의 좌상단 위치와 크기를 저장한다.
     * 랙/단만으로는 실제 배치를 표현할 수 없어(통로, 검수실, 대형 구역 등)
     * 자유 배치가 가능한 좌표를 별도로 둔다.
     * ------------------------------------------------------------------ */

    /** 좌상단 열 (1-based) */
    @Column(name = "posX", nullable = false)
    private Integer posX;

    /** 좌상단 행 (1-based) */
    @Column(name = "posY", nullable = false)
    private Integer posY;

    /** 가로 칸 수 */
    @Column(name = "posWidth", nullable = false)
    private Integer posWidth;

    /** 세로 칸 수 */
    @Column(name = "posHeight", nullable = false)
    private Integer posHeight;

    /** 랙 번호 */
    @Column(name = "rack", length = 20)
    private String rack;

    /** 단(층) 번호 */
    @Column(name = "binLevel")
    private Integer binLevel;

    /** 최대 적재 수량(포대 기준) */
    @Column(name = "maxCapacity", nullable = false)
    private Integer maxCapacity;

    /** 사용 여부 (false = 사용 중지) */
    @Column(name = "active", nullable = false)
    private boolean active;

    /** 비고 */
    @Column(name = "memo", length = 200)
    private String memo;

    @Column(name = "createdAt", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (maxCapacity == null) {
            maxCapacity = 0;
        }
        // center 는 optional=false 이므로 기본값을 넣지 않는다.
        // 어느 센터에 속하는지는 업무상 반드시 지정해야 하고, 임의의 센터로
        // 채우면 엉뚱한 도면에 구역이 나타난다. 비어 있으면 저장 단계에서 실패시킨다.
        if (binPurpose == null) {
            binPurpose = BinPurpose.STORAGE;
        }
        // 좌표가 없으면 도면 좌상단에 1x1 크기로 둔다 (등록 후 배치를 조정하면 된다)
        if (posX == null) {
            posX = 1;
        }
        if (posY == null) {
            posY = 1;
        }
        if (posWidth == null) {
            posWidth = 1;
        }
        if (posHeight == null) {
            posHeight = 1;
        }
    }

    /** 기준 정보 수정 */
    public void updateMasterData(String binCode,
                                 Center center,
                                 String zone,
                                 BinPurpose binPurpose,
                                 String rack,
                                 Integer binLevel,
                                 Integer maxCapacity,
                                 String memo) {
        this.binCode = binCode;
        this.center = center;
        this.zone = zone;
        this.binPurpose = binPurpose;
        this.rack = rack;
        this.binLevel = binLevel;
        this.maxCapacity = maxCapacity;
        this.memo = memo;
    }

    /**
     * 이 구역에 수량을 더 넣을 수 있는지 검사한다.
     * <p>
     * 입고 · 출고 취소 복구 등 <b>재고를 늘리는 모든 경로가 같은 규칙을 써야</b> 한다.
     * 서비스마다 따로 구현하면 한쪽만 고쳐져 한도가 새는 일이 생긴다.
     *
     * @param currentQuantity 현재 이 구역에 쌓여 있는 수량
     * @param addQuantity     추가하려는 수량
     * @return 한도를 넘지 않으면 true
     */
    public boolean canAccept(int currentQuantity, int addQuantity) {
        int limit = maxCapacity == null ? 0 : maxCapacity;
        return currentQuantity + addQuantity <= limit;
    }

    /** 적재 한도 (null 이면 0) */
    public int capacityLimit() {
        return maxCapacity == null ? 0 : maxCapacity;
    }

    /** 도면 배치 변경 */
    public void updateLayout(Integer posX, Integer posY, Integer posWidth, Integer posHeight) {
        this.posX = posX;
        this.posY = posY;
        this.posWidth = posWidth;
        this.posHeight = posHeight;
    }

    /** 사용 여부 변경 */
    public void changeActive(boolean active) {
        this.active = active;
    }

    /**
     * 센터 안에서의 위치 문자열 (예: A구역 · 1랙 · 2단)
     * <p>
     * <b>센터명을 포함하지 않는다.</b> 화면이 센터를 이미 별도 컬럼이나 탭으로
     * 보여주고 있을 때 센터명을 또 붙이면 같은 정보가 두 번 나온다.
     * 센터명까지 필요하면 {@link #locationLabel()} 을 쓴다.
     */
    public String zoneLabel() {
        StringBuilder sb = new StringBuilder();
        sb.append(zone).append("구역");
        if (rack != null && !rack.isBlank()) {
            sb.append(" · ").append(rack).append("랙");
        }
        if (binLevel != null) {
            sb.append(" · ").append(binLevel).append("단");
        }
        return sb.toString();
    }

    /**
     * 센터명까지 포함한 전체 위치 문자열 (예: 제1창고 · A구역 · 1랙 · 2단)
     * <p>
     * 센터명을 포함하므로 {@code center} 가 초기화된 상태에서 호출해야 한다.
     * 조회 쿼리에 {@code join fetch center} 가 없으면 구역 수만큼 추가 쿼리가 나간다.
     * <p>
     * 센터가 화면의 다른 곳에 이미 표시되는 경우에는 {@link #zoneLabel()} 을 쓴다.
     */
    public String locationLabel() {
        if (center == null) {
            return zoneLabel();
        }
        return center.displayName() + " · " + zoneLabel();
    }

    /** 소속 센터명 (센터가 없으면 null — 신규 객체를 조립하는 중일 때만 발생한다) */
    public String centerName() {
        return center == null ? null : center.displayName();
    }

    /** 소속 센터 식별자 (센터가 없으면 null) */
    public Long centerId() {
        return center == null ? null : center.getCenterId();
    }

    /* ------------------------------------------------------------------
     * 운송 중(IN_TRANSIT) 가상 구역
     * ------------------------------------------------------------------ */

    /** 운송 중 가상 구역의 코드 접두어 — 코드만 보고 성격을 알 수 있어야 한다 */
    public static final String IN_TRANSIT_CODE_PREFIX = "TRANSIT-";

    /** 운송 중 가상 구역의 구역(Zone) 이름 */
    private static final String IN_TRANSIT_ZONE = "TRANSIT";

    /**
     * 센터의 운송 중 가상 구역을 만든다.
     * <p>
     * 센터 간 이관 중인 재고가 머무는 자리다. 이 자리가 없으면 이관 도중 재고가
     * 어디에도 속하지 않아 <b>3계층 불변식</b>이 깨지고 재고 정합성 점검이 오탐한다.
     *
     * <h3>규격</h3>
     * <ul>
     *     <li>코드는 {@code TRANSIT-{센터코드}} — 센터당 하나임이 코드에서 드러난다</li>
     *     <li>{@code maxCapacity = 0} — 물리적 공간이 아니라 한도를 셀 수 없다.
     *         {@link BinPurpose#isPhysicalSpace()} 가 {@code false} 이므로
     *         적재 한도 검증 자체를 건너뛴다. 0 이라서 막히는 것이 아니다.</li>
     *     <li>좌표는 1,1 — 컬럼이 {@code NOT NULL} 이라 값은 필요하지만
     *         도면 조회에서 제외되므로 그려지지 않는다</li>
     * </ul>
     *
     * @param center 출발 센터 (운송 중 재고는 출발 센터의 책임 아래 있다)
     */
    public static WarehouseBin createInTransit(Center center) {
        return WarehouseBin.builder()
                .binCode(IN_TRANSIT_CODE_PREFIX + center.getCenterCode())
                .center(center)
                .zone(IN_TRANSIT_ZONE)
                .binPurpose(BinPurpose.IN_TRANSIT)
                .maxCapacity(0)
                .posX(1)
                .posY(1)
                .posWidth(1)
                .posHeight(1)
                .active(true)
                .memo("센터 간 이관 중인 재고가 머무는 가상 구역 (시스템 자동 생성)")
                .build();
    }

    /** 운송 중 가상 구역인지 */
    public boolean isInTransit() {
        return binPurpose == BinPurpose.IN_TRANSIT;
    }

    /**
     * 적재 한도를 검증해야 하는 구역인지.
     * <p>
     * 운송 중 가상 구역은 창고 바닥이 아니라 트럭 위라서 면적이 없다. 한도를 셀 수 없으므로
     * 검증 대상이 아니다. ({@link BinPurpose#isPhysicalSpace()})
     * <p>
     * <b>용도를 알 수 없으면 한도가 있는 것으로 본다.</b> {@code binPurpose} 는
     * {@code nullable = false} 이고 {@link #prePersist()} 가 기본값을 채우므로 저장된
     * 구역은 항상 값이 있지만, 아직 저장되지 않은 객체는 비어 있을 수 있다.
     * 그때 한도 검증을 <b>건너뛰는 쪽으로 기울면 안 된다</b> — 알 수 없는 상태를 이유로
     * 안전 장치를 끄면 적재 한도가 조용히 새어 나간다. 막는 쪽이 안전한 기본값이다.
     */
    public boolean hasCapacityLimit() {
        return binPurpose == null || binPurpose.isPhysicalSpace();
    }
}
