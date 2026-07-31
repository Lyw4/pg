package com.feedflow.common.exception;

/**
 * 출고 가능 재고가 요청 수량보다 부족할 때 발생.
 * <p>
 * <b>{@code Product.totalStock} 이 충분해도 이 예외가 날 수 있다.</b> 전체 재고와
 * 출고 가능 재고는 다르기 때문이다. 담당자가 "재고가 720인데 왜 부족하다는 거냐" 고
 * 되묻지 않도록, 메시지에 제외 사유를 함께 적는다.
 * <ul>
 *     <li>유통기한이 지난 로트 — 출고 대상이 아니다</li>
 *     <li>사용 중지된 구역 — 물건을 꺼낼 수 없다</li>
 *     <li>입고 대기 · 검수 구역 — 검수를 통과하지 않은 물건이다.
 *         '구역 간 이동' 으로 보관 구역에 넣어야 가용 재고가 된다</li>
 *     <li>운송 중(가상) 구역 — 트럭 위에 있어 집어올 수 없다</li>
 * </ul>
 */
public class InsufficientStockException extends BusinessRuleException {

    private final String productCode;
    private final int requestedQuantity;
    private final int availableQuantity;

    public InsufficientStockException(String productCode,
                                     int requestedQuantity,
                                     int availableQuantity) {
        super("출고 가능 재고가 부족합니다. 품목=" + productCode
                + ", 요청=" + requestedQuantity
                + ", 출고 가능=" + availableQuantity
                + " (유통기한 경과 로트, 사용 중지 구역, 입고 대기 · 검수 구역,"
                + " 운송 중 재고는 출고 대상에서 제외됩니다)");
        this.productCode = productCode;
        this.requestedQuantity = requestedQuantity;
        this.availableQuantity = availableQuantity;
    }

    public String getProductCode() {
        return productCode;
    }

    public int getRequestedQuantity() {
        return requestedQuantity;
    }

    public int getAvailableQuantity() {
        return availableQuantity;
    }

    public int getShortage() {
        return Math.max(requestedQuantity - availableQuantity, 0);
    }
}
