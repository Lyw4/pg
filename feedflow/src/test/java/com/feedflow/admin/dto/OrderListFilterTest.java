package com.feedflow.admin.dto;

import com.feedflow.domain.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrderListFilter 단위 테스트")
class OrderListFilterTest {

    @Nested
    @DisplayName("요청 파라미터 변환")
    class Of {

        @Test
        @DisplayName("필터 이름으로 변환한다 (대소문자 무시)")
        void convertsByName() {
            assertThat(OrderListFilter.of("CANCELED")).isEqualTo(OrderListFilter.CANCELED);
            assertThat(OrderListFilter.of("canceled")).isEqualTo(OrderListFilter.CANCELED);
            assertThat(OrderListFilter.of("  shipped  ")).isEqualTo(OrderListFilter.SHIPPED);
        }

        @ParameterizedTest
        @DisplayName("값이 없거나 알 수 없는 값이면 기본 필터(출고 대기)로 돌린다")
        @ValueSource(strings = {"", "   ", "UNKNOWN", "1", "PAID"})
        void fallsBackToWaiting(String input) {
            // 잘못된 쿼리 스트링으로 예외 화면을 띄우는 것보다 기본 목록을 보여주는 편이 낫다.
            // 'PAID' 는 주문 상태이지 필터 이름이 아니므로 폴백 대상이다.
            assertThat(OrderListFilter.of(input)).isEqualTo(OrderListFilter.WAITING);
        }

        @Test
        @DisplayName("null 이면 기본 필터로 돌린다")
        void nullFallsBackToWaiting() {
            assertThat(OrderListFilter.of(null)).isEqualTo(OrderListFilter.WAITING);
        }
    }

    @Nested
    @DisplayName("조회 대상 상태")
    class Statuses {

        @Test
        @DisplayName("출고 대기는 결제완료와 출고대기만 포함한다")
        void waiting() {
            assertThat(OrderListFilter.WAITING.getStatuses())
                    .containsExactlyInAnyOrder(OrderStatus.PAID, OrderStatus.READY);
        }

        @Test
        @DisplayName("전체는 모든 상태를 포함한다")
        void all() {
            // 상태가 추가되면 자동으로 따라와야 한다 (EnumSet.allOf)
            assertThat(OrderListFilter.ALL.getStatuses())
                    .containsExactlyInAnyOrder(OrderStatus.values());
        }

        @Test
        @DisplayName("취소 필터는 취소 상태만 포함한다")
        void canceled() {
            assertThat(OrderListFilter.CANCELED.getStatuses())
                    .containsExactly(OrderStatus.CANCELED);
        }
    }

    @Nested
    @DisplayName("정렬 방향")
    class Sorting {

        @Test
        @DisplayName("출고 대기만 오래된 순이고 나머지는 최신순이다")
        void oldestFirstOnlyForWaiting() {
            // 출고 대기는 오래 기다린 주문을 먼저 처리해야 한다.
            // 완료·취소 이력은 최근 건을 먼저 보는 것이 자연스럽다.
            assertThat(OrderListFilter.WAITING.isOldestFirst()).isTrue();

            assertThat(OrderListFilter.SHIPPED.isOldestFirst()).isFalse();
            assertThat(OrderListFilter.DELIVERED.isOldestFirst()).isFalse();
            assertThat(OrderListFilter.CANCELED.isOldestFirst()).isFalse();
            assertThat(OrderListFilter.ALL.isOldestFirst()).isFalse();
        }
    }

    @Nested
    @DisplayName("화면 표기")
    class Display {

        @Test
        @DisplayName("출고 처리 버튼은 출고 대기 목록에서만 노출된다")
        void dispatchTargetList() {
            assertThat(OrderListFilter.WAITING.isDispatchTargetList()).isTrue();
            assertThat(OrderListFilter.CANCELED.isDispatchTargetList()).isFalse();
            assertThat(OrderListFilter.ALL.isDispatchTargetList())
                    .as("전체 목록에는 취소된 주문도 섞이므로 출고 처리 버튼을 쓰지 않는다")
                    .isFalse();
        }

        @Test
        @DisplayName("모든 필터에 라벨이 있다")
        void everyFilterHasLabel() {
            for (OrderListFilter filter : OrderListFilter.values()) {
                assertThat(filter.getLabel())
                        .as("필터 %s 의 라벨", filter.name())
                        .isNotBlank();
            }
        }
    }
}
