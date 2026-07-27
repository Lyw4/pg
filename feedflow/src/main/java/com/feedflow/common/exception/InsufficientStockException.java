package com.feedflow.common.exception;

/**
 * 출고 가능 재고가 요청 수량보다 부족할 때 발생.
 * <p>
 * 유통기한이 지난 로트는 출고 대상에서 제외되므로,
 * 전체 재고는 남아 있어도 이 예외가 발생할 수 있다.
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
                + " (유통기한이 지난 로트는 출고 대상에서 제외됩니다)");
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
