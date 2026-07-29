package com.feedflow.admin.controller;

import com.feedflow.common.exception.BusinessRuleException;
import com.feedflow.common.exception.ResourceNotFoundException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 관리자 화면(HTML) 전용 예외 처리.
 * 존재하지 않는 ID 로 접근했을 때 500 대신 안내 화면을 보여준다.
 */
@ControllerAdvice(assignableTypes = {
        AdminController.class,
        AdminProductController.class,
        AdminWarehouseBinController.class,
        AdminInventoryController.class,
        AdminStockSyncController.class,
        AdminOutboundController.class,
        AdminTraceabilityController.class
})
public class AdminViewExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleResourceNotFound(ResourceNotFoundException e, Model model) {
        model.addAttribute(FlashAttr.ERROR, e.getMessage());
        return "error/not-found";
    }

    /**
     * 업무 규칙 위반.
     * 폼 화면에서 처리하지 못한 경우(직접 URL 호출 등)의 안전망이다.
     */
    @ExceptionHandler(BusinessRuleException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleBusinessRule(BusinessRuleException e, Model model) {
        model.addAttribute(FlashAttr.ERROR, e.getMessage());
        return "error/not-found";
    }

    /**
     * 낙관적 락 충돌 (동시 입고/출고/폐기/출고취소).
     * <p>
     * 같은 재고를 두 사람이 동시에 수정하면 나중 커밋이 실패하고 전체가 롤백된다.
     * 데이터가 깨진 것이 아니므로 "다시 시도" 안내만 하면 된다.
     * <p>
     * <b>{@code OptimisticLockingFailureException}(상위 타입)을 잡는 이유</b> —
     * JPA 경로에서는 하위 타입인 {@code ObjectOptimisticLockingFailureException} 이 오지만,
     * Spring Data 의 삭제·배치 경로 등에서는 상위 타입이 그대로 올라온다.
     * 하위 타입만 잡으면 그런 충돌이 500 에러로 새어 나가고,
     * JSON API 쪽({@link AdminApiExceptionHandler})과 처리 기준도 어긋난다.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String handleOptimisticLock(OptimisticLockingFailureException e, Model model) {
        model.addAttribute(FlashAttr.ERROR,
                "다른 사용자가 같은 재고를 동시에 처리했습니다. 요청은 취소되었으니 최신 재고를 확인한 뒤 다시 시도해 주세요.");
        return "error/conflict";
    }
}
