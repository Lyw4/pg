package com.feedflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    /** 구역(Zone) 예: A, B, COLD */
    @Column(name = "zone", nullable = false, length = 20)
    private String zone;

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
    }

    /** 기준 정보 수정 */
    public void updateMasterData(String binCode,
                                 String zone,
                                 String rack,
                                 Integer binLevel,
                                 Integer maxCapacity,
                                 String memo) {
        this.binCode = binCode;
        this.zone = zone;
        this.rack = rack;
        this.binLevel = binLevel;
        this.maxCapacity = maxCapacity;
        this.memo = memo;
    }

    /** 사용 여부 변경 */
    public void changeActive(boolean active) {
        this.active = active;
    }

    /** 화면 표기용 위치 문자열 (예: A구역 · 1랙 · 2단) */
    public String locationLabel() {
        StringBuilder sb = new StringBuilder(zone).append("구역");
        if (rack != null && !rack.isBlank()) {
            sb.append(" · ").append(rack).append("랙");
        }
        if (binLevel != null) {
            sb.append(" · ").append(binLevel).append("단");
        }
        return sb.toString();
    }
}
