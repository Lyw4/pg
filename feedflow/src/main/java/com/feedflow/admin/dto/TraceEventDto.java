package com.feedflow.admin.dto;

import com.feedflow.domain.MovementType;
import com.feedflow.domain.StockMovement;
import com.feedflow.domain.WarehouseBin;
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

    /**
     * 이 이벤트가 영향을 준 구역.
     * <p>
     * 구역 간 이동에서는 <b>도착지</b>다. 출발지는 {@link #fromBinCode} 를 쓴다.
     */
    private final String binCode;

    /** 구역 위치 (센터명 제외 — 센터는 {@link #centerName} 으로 따로 표시한다) */
    private final String binLocation;

    /**
     * 이 행위가 일어난 물류센터.
     * <p>
     * 전국 단위에서는 "언제 · 얼마나" 만으로는 이력을 읽을 수 없다.
     * <b>어느 센터에서 일어난 일인지</b> 가 함께 있어야 물류 흐름을 복원할 수 있다.
     * 구역이 없는 이력(로트 단위 조정 등)에서는 null 이다.
     */
    private final Long centerId;
    private final String centerName;

    /** 구역 간 이동의 출발 구역 (이동 이벤트에만 값이 있다) */
    private final String fromBinCode;
    private final String fromBinLocation;

    /** 출발 구역의 센터 (이동 이벤트에만 값이 있다) */
    private final Long fromCenterId;
    private final String fromCenterName;

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
        WarehouseBin bin = movement.getBin();
        WarehouseBin fromBin = movement.getFromBin();

        return TraceEventDto.builder()
                .sequence(sequence)
                .movementId(movement.getMovementId())
                .movementType(movement.getMovementType())
                .occurredAt(movement.getCreatedAt())
                .quantity(movement.getQuantity() == null ? 0 : movement.getQuantity())
                .balanceAfter(balanceAfter)
                .binCode(bin == null ? null : bin.getBinCode())
                // 센터를 별도로 표시하므로 위치 라벨에서는 센터명을 뺀다
                .binLocation(bin == null ? null : bin.zoneLabel())
                .centerId(bin == null ? null : bin.centerId())
                .centerName(bin == null ? null : bin.centerName())
                .fromBinCode(fromBin == null ? null : fromBin.getBinCode())
                .fromBinLocation(fromBin == null ? null : fromBin.zoneLabel())
                .fromCenterId(fromBin == null ? null : fromBin.centerId())
                .fromCenterName(fromBin == null ? null : fromBin.centerName())
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
            case MOVE -> "ff-trace-dot-move";
            default -> "ff-trace-dot-etc";
        };
    }

    /**
     * 구역 간 이동 이벤트인지.
     * <p>
     * 이동은 총 재고가 변하지 않고 위치만 바뀌므로 화면에서 "A-01 → B-02" 형태로
     * 다른 이벤트와 구분해 표시한다.
     */
    public boolean isRelocation() {
        return movementType == MovementType.MOVE && fromBinCode != null;
    }

    /**
     * <b>센터를 넘는 이동</b>인지.
     * <p>
     * 현재 이동 기능({@code MOVE})은 총 재고 불변을 전제로 하지만, 센터가 다르면
     * <b>한쪽 센터의 재고가 실제로 줄어든다.</b> 즉 {@code MOVE} 로 처리해서는 안 되는
     * 이동이다. (센터 간 이동은 {@code TRANSFER_OUT}/{@code TRANSFER_IN} 과
     * 운송 중 상태가 필요하다 — Epic Phase 3)
     * <p>
     * 지금은 이동 화면이 센터를 넘는 선택을 막지 않으므로, 그런 이력이 생기면
     * 타임라인에서 <b>눈에 띄게 경고로 표시</b>해 조용히 묻히지 않게 한다.
     */
    public boolean isCenterTransfer() {
        return isRelocation()
                && fromCenterId != null
                && centerId != null
                && !fromCenterId.equals(centerId);
    }

    /**
     * 센터 안에서의 이동인지 (출발지와 도착지가 같은 센터).
     * <p>
     * 이 경우 화면은 센터명을 한 번만 쓰고 구역만 {@code A-01 → B-02} 로 보여준다.
     * 양쪽에 같은 센터명을 반복하면 정보가 늘지 않고 줄만 길어진다.
     */
    public boolean isWithinCenterMove() {
        return isRelocation() && !isCenterTransfer();
    }

    /** 센터 정보가 있는 이벤트인지 (구역이 없는 이력은 센터도 알 수 없다) */
    public boolean isCenterKnown() {
        return centerName != null;
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
