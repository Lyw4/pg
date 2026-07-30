package com.feedflow.common.exception;

/**
 * 조회 대상 데이터가 존재하지 않을 때 발생.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException ofProduct(Long productId) {
        return new ResourceNotFoundException("존재하지 않는 품목입니다. id=" + productId);
    }

    public static ResourceNotFoundException ofWarehouseBin(Long binId) {
        return new ResourceNotFoundException("존재하지 않는 창고 구역입니다. id=" + binId);
    }

    public static ResourceNotFoundException ofCenter(Long centerId) {
        return new ResourceNotFoundException("존재하지 않는 물류센터입니다. id=" + centerId);
    }

    public static ResourceNotFoundException ofOrder(Long orderId) {
        return new ResourceNotFoundException("존재하지 않는 주문입니다. id=" + orderId);
    }

    public static ResourceNotFoundException ofProductLot(Long lotId) {
        return new ResourceNotFoundException("존재하지 않는 로트입니다. id=" + lotId);
    }
}
