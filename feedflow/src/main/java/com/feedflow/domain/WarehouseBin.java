package com.feedflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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

    /** 소속 창고(동) — 2D 도면은 창고 단위로 그린다 */
    @Enumerated(EnumType.STRING)
    @Column(name = "warehouse", nullable = false, length = 20)
    private Warehouse warehouse;

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
        if (warehouse == null) {
            warehouse = Warehouse.WH1;
        }
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
                                 Warehouse warehouse,
                                 String zone,
                                 BinPurpose binPurpose,
                                 String rack,
                                 Integer binLevel,
                                 Integer maxCapacity,
                                 String memo) {
        this.binCode = binCode;
        this.warehouse = warehouse;
        this.zone = zone;
        this.binPurpose = binPurpose;
        this.rack = rack;
        this.binLevel = binLevel;
        this.maxCapacity = maxCapacity;
        this.memo = memo;
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

    /** 화면 표기용 위치 문자열 (예: 제1창고 · A구역 · 1랙 · 2단) */
    public String locationLabel() {
        StringBuilder sb = new StringBuilder();
        if (warehouse != null) {
            sb.append(warehouse.getDescription()).append(" · ");
        }
        sb.append(zone).append("구역");
        if (rack != null && !rack.isBlank()) {
            sb.append(" · ").append(rack).append("랙");
        }
        if (binLevel != null) {
            sb.append(" · ").append(binLevel).append("단");
        }
        return sb.toString();
    }
}
