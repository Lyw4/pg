package com.ex.entity;

import java.time.LocalDateTime;

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
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "warehouse_bin",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"warehouse_id", "bin_code"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WarehouseBin {

    /**
     * 창고 구역은 평면 면적뿐 아니라 랙의 수직 적재 단수까지 사용할 수 있습니다.
     * 현재 운영안은 층당 기준 용량을 10단으로 적재하는 것으로 계산합니다.
     */
    public static final int VERTICAL_STACKING_LEVELS = 10;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long binId;

    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Column(name = "bin_code", nullable = false, length = 30)
    private String binCode;

    @Column(nullable = false, length = 40)
    private String zone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BinPurpose purpose;

    @Column(nullable = false)
    private int posX;

    @Column(nullable = false)
    private int posY;

    @Column(nullable = false)
    private int posWidth;

    @Column(nullable = false)
    private int posHeight;

    @Column(length = 20)
    private String rack;

    @Column(name = "bin_level")
    private Integer binLevel;

    @Column(nullable = false)
    private int maxCapacity;

    @Column(nullable = false)
    private boolean active = true;

    @Column(length = 200)
    private String memo;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public WarehouseBin(
            Warehouse warehouse,
            String binCode,
            String zone,
            BinPurpose purpose,
            int posX,
            int posY,
            int posWidth,
            int posHeight,
            int maxCapacity,
            String memo) {
        this.warehouse = warehouse;
        this.binCode = normalizeCode(binCode);
        this.zone = required(zone, "구역명");
        this.purpose = purpose;
        updateLayout(posX, posY, posWidth, posHeight);
        changeCapacity(maxCapacity);
        this.memo = memo;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public void update(
            String binCode,
            String zone,
            BinPurpose purpose,
            int maxCapacity,
            String memo) {
        if (purpose != null && purpose.isSystemManaged()) {
            throw new IllegalArgumentException(
                    "시스템 관리 구역은 직접 지정할 수 없습니다.");
        }
        this.binCode = normalizeCode(binCode);
        this.zone = required(zone, "구역명");
        this.purpose = purpose;
        changeCapacity(maxCapacity);
        this.memo = memo;
    }

    public void updateLayout(
            int posX,
            int posY,
            int posWidth,
            int posHeight) {
        if (posX < 1 || posY < 1 || posWidth < 1 || posHeight < 1) {
            throw new IllegalArgumentException(
                    "도면 좌표와 크기는 1 이상이어야 합니다.");
        }
        this.posX = posX;
        this.posY = posY;
        this.posWidth = posWidth;
        this.posHeight = posHeight;
    }

    public void updateLocation(String rack, Integer binLevel) {
        this.rack = rack == null || rack.isBlank() ? null : rack.trim();
        this.binLevel = binLevel;
    }

    public String getLocationLabel() {
        StringBuilder label = new StringBuilder(zone).append("구역");
        if (rack != null) {
            label.append(" · ").append(rack).append("랙");
        }
        if (binLevel != null) {
            label.append(" · ").append(binLevel).append("단");
        }
        return label.toString();
    }

    public void changeCapacity(int maxCapacity) {
        if (maxCapacity < 0) {
            throw new IllegalArgumentException(
                    "최대 적재량은 0 이상이어야 합니다.");
        }
        this.maxCapacity = maxCapacity;
    }

    public void changeActive(boolean active) {
        if (purpose != null && purpose.isSystemManaged() && !active) {
            throw new IllegalStateException(
                    "이동 중 구역은 비활성화할 수 없습니다.");
        }
        this.active = active;
    }

    public boolean hasCapacityLimit() {
        return purpose == null || purpose.isPhysicalSpace();
    }

    /**
     * 화면/입고 검증에서 사용하는 실제 적재 가능량입니다.
     * maxCapacity는 층당 기준 용량으로 유지해 기존 데이터와 등록 폼의 의미를 보존합니다.
     */
    public int getEffectiveMaxCapacity() {
        if (!hasCapacityLimit()) {
            return Integer.MAX_VALUE;
        }
        long effective = (long) maxCapacity * VERTICAL_STACKING_LEVELS;
        return effective >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) effective;
    }

    public boolean canAccept(int currentQuantity, int addQuantity) {
        return !hasCapacityLimit()
                || currentQuantity + addQuantity <= getEffectiveMaxCapacity();
    }

    public String getDisplayName() {
        return warehouse.getName() + " · " + binCode;
    }

    private static String normalizeCode(String value) {
        return required(value, "구역 코드").toUpperCase();
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + "을(를) 입력해 주세요.");
        }
        return value.trim();
    }
}
