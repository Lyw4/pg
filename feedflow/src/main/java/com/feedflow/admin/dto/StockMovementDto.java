package com.feedflow.admin.dto;

import com.feedflow.domain.MovementType;
import com.feedflow.domain.StockMovement;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 입·출고 이력 목록 행.
 */
@Getter
@Builder
public class StockMovementDto {

    private final Long movementId;
    private final MovementType movementType;

    private final String productCode;
    private final String productName;
    private final String lotNo;
    private final String binCode;

    private final Integer quantity;
    private final String memo;

    private final String userName;
    private final LocalDateTime createdAt;

    public static StockMovementDto from(StockMovement movement) {
        return StockMovementDto.builder()
                .movementId(movement.getMovementId())
                .movementType(movement.getMovementType())
                .productCode(movement.getProduct().getProductCode())
                .productName(movement.getProduct().getName())
                .lotNo(movement.getLot().getLotNo())
                .binCode(movement.getBin() == null ? null : movement.getBin().getBinCode())
                .quantity(movement.getQuantity())
                .memo(movement.getMemo())
                .userName(movement.getUserName())
                .createdAt(movement.getCreatedAt())
                .build();
    }

    public String getTypeLabel() {
        return movementType.getDescription();
    }

    public String getTypeBadgeClass() {
        return movementType.getBadgeClass();
    }

    /** 수량 표기 색상 클래스 */
    public String getQuantityTextClass() {
        int sign = movementType.getSign();
        if (sign > 0) {
            return "text-success";
        }
        if (sign < 0) {
            return "text-danger";
        }
        return "";
    }

    /** 수량 표기 (입고 +, 출고 -) */
    public String getSignedQuantity() {
        int sign = movementType.getSign();
        if (sign > 0) {
            return "+" + quantity;
        }
        if (sign < 0) {
            return "-" + quantity;
        }
        return String.valueOf(quantity);
    }
}
