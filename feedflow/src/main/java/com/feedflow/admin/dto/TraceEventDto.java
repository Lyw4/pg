package com.feedflow.admin.dto;

import com.feedflow.domain.MovementType;
import com.feedflow.domain.StockMovement;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 이력 추적 타임라인의 이벤트 한 건.
 * <p>
 * 입고 · 출고 · 출고취소 · 폐기를 같은 형태로 표현해 화면이 유형별 분기 없이
 * 하나의 반복문으로 타임라인을 그릴 수 있게 한다.
 */
@Getter
@Builder
public class TraceEventDto {

    /** 타임라인 표시 순번 (1부터) */
    private final int sequence;

    private final Long movementId;
    private final MovementType movementType;

    private final LocalDateTime occurredAt;

    /** 이동 수량 (항상 양수) */
    private final int quantity;

    /**
     * 이 이벤트가 끝난 직후의 로트 잔여 수량.
     * <p>
     * 이력을 시간순으로 누적해 계산한다. "언제 몇 개가 남아 있었는지" 를 보여줘
     * CS 대응 시 특정 시점의 재고를 되짚을 수 있다.
     */
    private final int balanceAfter;

    private final String binCode;
    private final String binLocation;

    /** 주문 기반 출고 · 출고 취소면 주문 번호 */
    private final Long orderId;

    private final String memo;
    private final String userName;

    /**
     * 이력 한 건을 타임라인 이벤트로 변환한다.
     *
     * @param movement     fetch join 으로 product / lot / bin 이 초기화된 상태여야 한다
     * @param sequence     표시 순번
     * @param balanceAfter 이 이벤트 직후의 로트 잔여 수량
     */
    public static TraceEventDto of(StockMovement movement, int sequence, int balanceAfter) {
        return TraceEventDto.builder()
                .sequence(sequence)
                .movementId(movement.getMovementId())
                .movementType(movement.getMovementType())
                .occurredAt(movement.getCreatedAt())
                .quantity(movement.getQuantity() == null ? 0 : movement.getQuantity())
                .balanceAfter(balanceAfter)
                .binCode(movement.getBin() == null ? null : movement.getBin().getBinCode())
                .binLocation(movement.getBin() == null ? null : movement.getBin().locationLabel())
                .orderId(movement.getOrderId())
                .memo(movement.getMemo())
                .userName(movement.getUserName())
                .build();
    }

    /* ------------------------------------------------------------------
     * 화면 표기
     * ------------------------------------------------------------------ */

    public String getTypeLabel() {
        return movementType.getDescription();
    }

    public String getTypeBadgeClass() {
        return movementType.getBadgeClass();
    }

    /** 재고가 늘어난 이벤트인지 (입고 · 출고취소) */
    public boolean isIncrease() {
        return movementType.getSign() > 0;
    }

    /** 재고가 줄어든 이벤트인지 (출고 · 폐기) */
    public boolean isDecrease() {
        return movementType.getSign() < 0;
    }

    /** 수량 표기 (+20 / -10 / 20) */
    public String getSignedQuantity() {
        if (isIncrease()) {
            return "+" + quantity;
        }
        if (isDecrease()) {
            return "-" + quantity;
        }
        return String.valueOf(quantity);
    }

    public String getQuantityTextClass() {
        if (isIncrease()) {
            return "text-success";
        }
        if (isDecrease()) {
            return "text-danger";
        }
        return "text-secondary";
    }

    /** 타임라인 점 색상 (테두리) */
    public String getMarkerClass() {
        return switch (movementType) {
            case INBOUND -> "ff-trace-dot-inbound";
            case OUTBOUND -> "ff-trace-dot-outbound";
            case CANCEL -> "ff-trace-dot-cancel";
            case DISPOSAL -> "ff-trace-dot-disposal";
            default -> "ff-trace-dot-etc";
        };
    }

    /** 타임라인 점 아이콘 (Bootstrap Icons) */
    public String getIconClass() {
        return switch (movementType) {
            case INBOUND -> "bi-box-arrow-in-down";
            case OUTBOUND -> "bi-box-arrow-up";
            case CANCEL -> "bi-arrow-counterclockwise";
            case DISPOSAL -> "bi-trash3";
            case MOVE -> "bi-arrows-move";
            case ADJUST -> "bi-sliders";
        };
    }

    /** 주문과 연결된 이벤트인지 (주문 상세로 이동 링크 표시용) */
    public boolean isLinkedToOrder() {
        return orderId != null;
    }
}
