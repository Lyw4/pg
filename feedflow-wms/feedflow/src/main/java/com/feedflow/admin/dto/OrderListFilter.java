package com.feedflow.admin.dto;

import com.feedflow.domain.OrderStatus;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

/**
 * 주문 목록 화면의 상태 필터.
 *
 * <h3>왜 필요한가</h3>
 * 목록이 출고 대기(결제완료 · 출고대기)만 조회하고 있어서 <b>취소 · 출고완료 · 배송완료 주문은
 * 화면에서 찾을 방법이 없었다.</b> 특히 취소된 주문은 취소하는 순간 목록에서 사라져
 * 취소 사유를 다시 확인하려면 URL 을 직접 입력해야 했다.
 *
 * <h3>정렬을 필터가 갖는 이유</h3>
 * 출고 대기는 <b>오래 기다린 주문을 먼저</b> 처리해야 하므로 주문이 빠른 순이다.
 * 반면 완료 · 취소 이력을 볼 때는 최근 건이 먼저 보이는 것이 자연스럽다.
 * 화면이나 서비스가 필터별로 분기하지 않도록 정렬 방향을 필터에 함께 담는다.
 */
@Getter
@RequiredArgsConstructor
public enum OrderListFilter {

    WAITING("출고 대기", EnumSet.of(OrderStatus.PAID, OrderStatus.READY), true),
    SHIPPED("출고 완료", EnumSet.of(OrderStatus.SHIPPED), false),
    DELIVERED("배송 완료", EnumSet.of(OrderStatus.DELIVERED), false),
    CANCELED("주문 취소", EnumSet.of(OrderStatus.CANCELED), false),
    ALL("전체", EnumSet.allOf(OrderStatus.class), false);

    private final String label;

    /** 이 필터가 조회할 주문 상태 */
    private final Set<OrderStatus> statuses;

    /** true 면 주문이 빠른 순(오래된 순), false 면 최신순 */
    private final boolean oldestFirst;

    /**
     * 요청 파라미터를 필터로 변환한다.
     * <p>
     * 값이 없거나 알 수 없는 값이면 기본값({@link #WAITING})으로 돌린다.
     * 잘못된 쿼리 스트링으로 예외 화면을 띄우는 것보다 기본 목록을 보여주는 편이 낫다.
     */
    public static OrderListFilter of(String name) {
        if (name == null || name.isBlank()) {
            return WAITING;
        }
        return Arrays.stream(values())
                .filter(filter -> filter.name().equalsIgnoreCase(name.trim()))
                .findFirst()
                .orElse(WAITING);
    }

    /** 출고 처리가 가능한 주문만 모인 목록인지 (버튼 라벨을 바꾸는 데 쓴다) */
    public boolean isDispatchTargetList() {
        return this == WAITING;
    }
}
