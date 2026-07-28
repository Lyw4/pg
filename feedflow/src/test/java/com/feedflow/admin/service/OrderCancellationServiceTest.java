package com.feedflow.admin.service;

import com.feedflow.admin.dto.OrderCancelResultDto;
import com.feedflow.admin.dto.RestorationLineDto;
import com.feedflow.common.exception.BusinessRuleException;
import com.feedflow.common.exception.ResourceNotFoundException;
import com.feedflow.domain.AnimalType;
import com.feedflow.domain.BinPurpose;
import com.feedflow.domain.Inventory;
import com.feedflow.domain.MovementType;
import com.feedflow.domain.Order;
import com.feedflow.domain.OrderItem;
import com.feedflow.domain.OrderStatus;
import com.feedflow.domain.Product;
import com.feedflow.domain.ProductLot;
import com.feedflow.domain.ProductType;
import com.feedflow.domain.Role;
import com.feedflow.domain.StockMovement;
import com.feedflow.domain.User;
import com.feedflow.domain.Warehouse;
import com.feedflow.domain.WarehouseBin;
import com.feedflow.repository.InventoryRepository;
import com.feedflow.repository.OrderRepository;
import com.feedflow.repository.StockMovementRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 출고(주문) 취소 서비스 테스트.
 *
 * <h3>핵심 검증</h3>
 * 출고 취소는 상태만 바꾸는 것이 아니라 <b>세 계층의 재고를 정확히 되돌려야</b> 한다.
 * <ul>
 *     <li>{@code Inventory.quantity} — 구역별 실물</li>
 *     <li>{@code ProductLot.lotQuantity} — 로트별 잔여</li>
 *     <li>{@code Product.totalStock} — 품목 총 재고</li>
 * </ul>
 * FEFO 출고는 여러 로트에 걸쳐 차감되므로 <b>로트별로 정확한 수량이</b> 돌아가야 한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderCancellationService 단위 테스트")
class OrderCancellationServiceTest {

    private static final Long ORDER_ID = 1L;
    private static final Long USER_ID = 1L;
    private static final String USER_NAME = "김책임";

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private StockMovementRepository stockMovementRepository;

    @InjectMocks
    private OrderCancellationService orderCancellationService;

    /* ==================================================================
     * 재고 복구 (핵심)
     * ================================================================== */

    @Nested
    @DisplayName("출고 완료 주문 취소 - 재고 복구")
    class RestoreStock {

        @Test
        @DisplayName("여러 로트에 걸쳐 차감된 재고를 로트별로 정확히 되돌린다")
        void restoresAcrossMultipleLots() {
            // given : 품목 총 재고 70. 출고 30 이 LOT-A 20 + LOT-B 10 으로 나뉘어 빠진 상태
            Product product = product(70);

            ProductLot lotA = lot(10L, product, "LOT-A", 5);    // 출고 후 잔여 5 (원래 25)
            ProductLot lotB = lot(11L, product, "LOT-B", 40);   // 출고 후 잔여 40 (원래 50)

            WarehouseBin binA = bin(1L, "A-01", 500);
            WarehouseBin binB = bin(2L, "B-01", 600);

            Inventory inventoryA = inventory(100L, lotA, binA, 5);
            Inventory inventoryB = inventory(101L, lotB, binB, 40);

            Order order = order(OrderStatus.SHIPPED, product, 30);

            given(orderRepository.findWithItemsById(ORDER_ID)).willReturn(Optional.of(order));
            given(stockMovementRepository.findByOrderIdAndType(ORDER_ID, MovementType.OUTBOUND))
                    .willReturn(List.of(
                            outbound(1L, lotA, binA, 20),
                            outbound(2L, lotB, binB, 10)));

            given(inventoryRepository.findByLot_LotIdAndBin_BinId(10L, 1L))
                    .willReturn(Optional.of(inventoryA));
            given(inventoryRepository.findByLot_LotIdAndBin_BinId(11L, 2L))
                    .willReturn(Optional.of(inventoryB));
            given(inventoryRepository.sumQuantityByBinId(1L)).willReturn(5L);
            given(inventoryRepository.sumQuantityByBinId(2L)).willReturn(40L);

            // when
            OrderCancelResultDto result =
                    orderCancellationService.cancel(ORDER_ID, "고객 요청", USER_ID, USER_NAME);

            // then : 로트 잔여가 각각 되돌아온다
            assertThat(lotA.getLotQuantity())
                    .as("LOT-A 는 5 + 20 = 25")
                    .isEqualTo(25);
            assertThat(lotB.getLotQuantity())
                    .as("LOT-B 는 40 + 10 = 50")
                    .isEqualTo(50);

            // 구역 재고도 각각 되돌아온다
            assertThat(inventoryA.getQuantity()).isEqualTo(25);
            assertThat(inventoryB.getQuantity()).isEqualTo(50);

            // 품목 총 재고는 두 로트 복구량의 합만큼 늘어난다
            assertThat(product.getTotalStock())
                    .as("70 + 30 = 100")
                    .isEqualTo(100);

            // 세 계층의 합이 어긋나지 않는다
            assertThat(product.getTotalStock())
                    .as("totalStock 은 로트 잔여 합계와 같아야 한다")
                    .isEqualTo(lotA.getLotQuantity() + lotB.getLotQuantity());

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
            assertThat(result.isStockRestored()).isTrue();
            assertThat(result.getRestoredQuantity()).isEqualTo(30);
            assertThat(result.getRestoredLineCount()).isEqualTo(2);
            assertThat(result.getPreviousStatus()).isEqualTo(OrderStatus.SHIPPED);
        }

        @Test
        @DisplayName("복구 내역에 로트 · 구역 · 전후 수량을 담아 반환한다")
        void returnsRestorationDetail() {
            Product product = product(70);
            ProductLot lotA = lot(10L, product, "LOT-A", 5);
            WarehouseBin binA = bin(1L, "A-01", 500);
            Inventory inventoryA = inventory(100L, lotA, binA, 5);

            given(orderRepository.findWithItemsById(ORDER_ID))
                    .willReturn(Optional.of(order(OrderStatus.SHIPPED, product, 20)));
            given(stockMovementRepository.findByOrderIdAndType(ORDER_ID, MovementType.OUTBOUND))
                    .willReturn(List.of(outbound(1L, lotA, binA, 20)));
            given(inventoryRepository.findByLot_LotIdAndBin_BinId(10L, 1L))
                    .willReturn(Optional.of(inventoryA));
            given(inventoryRepository.sumQuantityByBinId(1L)).willReturn(5L);

            OrderCancelResultDto result =
                    orderCancellationService.cancel(ORDER_ID, null, USER_ID, USER_NAME);

            assertThat(result.getRestoredLines())
                    .extracting(RestorationLineDto::getLotNo,
                            RestorationLineDto::getBinCode,
                            RestorationLineDto::getRestoredQuantity,
                            RestorationLineDto::getBinQuantityBefore,
                            RestorationLineDto::getBinQuantityAfter)
                    .containsExactly(tuple("LOT-A", "A-01", 20, 5, 25));

            RestorationLineDto line = result.getRestoredLines().get(0);
            assertThat(line.getLotQuantityAfter()).isEqualTo(25);
            assertThat(line.getTotalStockAfter()).isEqualTo(90);
            assertThat(line.isBinRecreated()).isFalse();
        }

        @Test
        @DisplayName("출고로 사라진 구역 재고 행은 새로 만들어 되돌린다")
        void recreatesMissingInventoryRow() {
            Product product = product(0);
            ProductLot lotA = lot(10L, product, "LOT-A", 0);
            WarehouseBin binA = bin(1L, "A-01", 500);

            given(orderRepository.findWithItemsById(ORDER_ID))
                    .willReturn(Optional.of(order(OrderStatus.SHIPPED, product, 20)));
            given(stockMovementRepository.findByOrderIdAndType(ORDER_ID, MovementType.OUTBOUND))
                    .willReturn(List.of(outbound(1L, lotA, binA, 20)));
            given(inventoryRepository.findByLot_LotIdAndBin_BinId(10L, 1L))
                    .willReturn(Optional.empty());
            given(inventoryRepository.sumQuantityByBinId(1L)).willReturn(null);
            given(inventoryRepository.save(any(Inventory.class)))
                    .willAnswer(call -> call.getArgument(0));

            OrderCancelResultDto result =
                    orderCancellationService.cancel(ORDER_ID, null, USER_ID, USER_NAME);

            ArgumentCaptor<Inventory> captor = ArgumentCaptor.forClass(Inventory.class);
            verify(inventoryRepository).save(captor.capture());

            assertThat(captor.getValue().getQuantity()).isEqualTo(20);
            assertThat(captor.getValue().getLot()).isSameAs(lotA);
            assertThat(captor.getValue().getBin()).isSameAs(binA);

            assertThat(result.getRestoredLines().get(0).isBinRecreated()).isTrue();
            assertThat(lotA.getLotQuantity()).isEqualTo(20);
            assertThat(product.getTotalStock()).isEqualTo(20);
        }

        @Test
        @DisplayName("복구 이력은 입고가 아니라 CANCEL 유형으로 주문 번호와 함께 남는다")
        void recordsCancelMovementWithOrderId() {
            Product product = product(70);
            ProductLot lotA = lot(10L, product, "LOT-A", 5);
            WarehouseBin binA = bin(1L, "A-01", 500);

            given(orderRepository.findWithItemsById(ORDER_ID))
                    .willReturn(Optional.of(order(OrderStatus.SHIPPED, product, 20)));
            given(stockMovementRepository.findByOrderIdAndType(ORDER_ID, MovementType.OUTBOUND))
                    .willReturn(List.of(outbound(1L, lotA, binA, 20)));
            given(inventoryRepository.findByLot_LotIdAndBin_BinId(10L, 1L))
                    .willReturn(Optional.of(inventory(100L, lotA, binA, 5)));
            given(inventoryRepository.sumQuantityByBinId(1L)).willReturn(5L);

            orderCancellationService.cancel(ORDER_ID, "오배송", USER_ID, USER_NAME);

            ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
            verify(stockMovementRepository).save(captor.capture());

            StockMovement saved = captor.getValue();
            assertThat(saved.getMovementType())
                    .as("입고로 남기면 실제 들어오지 않은 물량이 매입 실적에 섞인다")
                    .isEqualTo(MovementType.CANCEL);
            assertThat(saved.getOrderId())
                    .as("이력 추적에서 주문으로 되짚을 수 있어야 한다")
                    .isEqualTo(ORDER_ID);
            assertThat(saved.getQuantity()).isEqualTo(20);
            assertThat(saved.getLot()).isSameAs(lotA);
            assertThat(saved.getBin()).isSameAs(binA);
            assertThat(saved.getUserName()).isEqualTo(USER_NAME);
            assertThat(saved.getMemo())
                    .contains("주문 #1 출고 취소")
                    .contains("오배송");
        }

        @Test
        @DisplayName("같은 로트에서 두 번 차감된 이력도 각각 되돌린다")
        void restoresRepeatedDeductionsOnSameLot() {
            Product product = product(50);
            ProductLot lotA = lot(10L, product, "LOT-A", 10);
            WarehouseBin binA = bin(1L, "A-01", 500);
            Inventory inventoryA = inventory(100L, lotA, binA, 10);

            given(orderRepository.findWithItemsById(ORDER_ID))
                    .willReturn(Optional.of(order(OrderStatus.SHIPPED, product, 25)));
            given(stockMovementRepository.findByOrderIdAndType(ORDER_ID, MovementType.OUTBOUND))
                    .willReturn(List.of(
                            outbound(1L, lotA, binA, 15),
                            outbound(2L, lotA, binA, 10)));
            given(inventoryRepository.findByLot_LotIdAndBin_BinId(10L, 1L))
                    .willReturn(Optional.of(inventoryA));
            given(inventoryRepository.sumQuantityByBinId(1L)).willReturn(10L, 25L);

            OrderCancelResultDto result =
                    orderCancellationService.cancel(ORDER_ID, null, USER_ID, USER_NAME);

            assertThat(inventoryA.getQuantity()).isEqualTo(35);
            assertThat(lotA.getLotQuantity()).isEqualTo(35);
            assertThat(product.getTotalStock()).isEqualTo(75);
            assertThat(result.getRestoredQuantity()).isEqualTo(25);
        }
    }

    /* ==================================================================
     * 출고 전 주문 취소
     * ================================================================== */

    @Nested
    @DisplayName("출고 전 주문 취소")
    class CancelBeforeDispatch {

        @Test
        @DisplayName("결제완료 주문은 재고를 건드리지 않고 상태만 바꾼다")
        void paidOrder_onlyChangesStatus() {
            Product product = product(100);
            Order order = order(OrderStatus.PAID, product, 20);

            given(orderRepository.findWithItemsById(ORDER_ID)).willReturn(Optional.of(order));

            OrderCancelResultDto result =
                    orderCancellationService.cancel(ORDER_ID, null, USER_ID, USER_NAME);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
            assertThat(product.getTotalStock())
                    .as("출고 전이므로 차감된 적이 없어 복구할 것도 없다")
                    .isEqualTo(100);
            assertThat(result.isStockRestored()).isFalse();
            assertThat(result.getRestoredLines()).isEmpty();
            assertThat(result.getSummaryMessage()).contains("되돌릴 재고가 없습니다");

            verify(stockMovementRepository, never()).save(any(StockMovement.class));
            verify(stockMovementRepository, never()).findByOrderIdAndType(anyLong(), any());
        }

        @Test
        @DisplayName("출고대기 주문도 재고 복구 없이 취소된다")
        void readyOrder_onlyChangesStatus() {
            Product product = product(100);
            Order order = order(OrderStatus.READY, product, 20);

            given(orderRepository.findWithItemsById(ORDER_ID)).willReturn(Optional.of(order));

            OrderCancelResultDto result =
                    orderCancellationService.cancel(ORDER_ID, null, USER_ID, USER_NAME);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
            assertThat(product.getTotalStock()).isEqualTo(100);
            assertThat(result.isStockRestored()).isFalse();
        }
    }

    /* ==================================================================
     * 취소 거부 규칙
     * ================================================================== */

    @Nested
    @DisplayName("취소할 수 없는 경우")
    class RejectCancel {

        @Test
        @DisplayName("이미 취소된 주문은 다시 취소할 수 없다 (재고 이중 복구 방지)")
        void alreadyCanceled_throwsException() {
            Product product = product(100);
            given(orderRepository.findWithItemsById(ORDER_ID))
                    .willReturn(Optional.of(order(OrderStatus.CANCELED, product, 20)));

            assertThatThrownBy(() ->
                    orderCancellationService.cancel(ORDER_ID, null, USER_ID, USER_NAME))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("이미 취소된 주문");

            assertThat(product.getTotalStock())
                    .as("두 번 복구되면 없는 재고가 생긴다")
                    .isEqualTo(100);
            verify(stockMovementRepository, never()).save(any(StockMovement.class));
        }

        @Test
        @DisplayName("배송 완료된 주문은 취소할 수 없다")
        void delivered_throwsException() {
            Product product = product(100);
            given(orderRepository.findWithItemsById(ORDER_ID))
                    .willReturn(Optional.of(order(OrderStatus.DELIVERED, product, 20)));

            assertThatThrownBy(() ->
                    orderCancellationService.cancel(ORDER_ID, null, USER_ID, USER_NAME))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("배송이 완료된 주문");

            assertThat(product.getTotalStock()).isEqualTo(100);
        }

        @Test
        @DisplayName("존재하지 않는 주문이면 ResourceNotFoundException 이 발생한다")
        void notFound_throwsException() {
            given(orderRepository.findWithItemsById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() ->
                    orderCancellationService.cancel(999L, null, USER_ID, USER_NAME))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("존재하지 않는 주문");
        }

        @Test
        @DisplayName("출고 완료인데 출고 이력이 없으면 근거 없이 재고를 늘리지 않고 중단한다")
        void shippedWithoutHistory_throwsException() {
            Product product = product(70);
            given(orderRepository.findWithItemsById(ORDER_ID))
                    .willReturn(Optional.of(order(OrderStatus.SHIPPED, product, 20)));
            given(stockMovementRepository.findByOrderIdAndType(ORDER_ID, MovementType.OUTBOUND))
                    .willReturn(List.of());

            assertThatThrownBy(() ->
                    orderCancellationService.cancel(ORDER_ID, null, USER_ID, USER_NAME))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("출고 이력이 없어");

            assertThat(product.getTotalStock())
                    .as("근거가 없으면 재고를 늘려서는 안 된다")
                    .isEqualTo(70);
        }

        @Test
        @DisplayName("되돌릴 구역에 자리가 없으면 취소를 막고 사유를 알려준다")
        void binCapacityExceeded_throwsException() {
            // given : 출고 후 그 자리에 다른 물건이 들어와 한도가 490/500 인 상태
            Product product = product(70);
            ProductLot lotA = lot(10L, product, "LOT-A", 5);
            WarehouseBin binA = bin(1L, "A-01", 500);

            given(orderRepository.findWithItemsById(ORDER_ID))
                    .willReturn(Optional.of(order(OrderStatus.SHIPPED, product, 20)));
            given(stockMovementRepository.findByOrderIdAndType(ORDER_ID, MovementType.OUTBOUND))
                    .willReturn(List.of(outbound(1L, lotA, binA, 20)));
            given(inventoryRepository.findByLot_LotIdAndBin_BinId(10L, 1L))
                    .willReturn(Optional.of(inventory(100L, lotA, binA, 5)));
            given(inventoryRepository.sumQuantityByBinId(1L)).willReturn(490L);

            assertThatThrownBy(() ->
                    orderCancellationService.cancel(ORDER_ID, null, USER_ID, USER_NAME))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("적재 한도를 초과");

            assertThat(product.getTotalStock())
                    .as("예외로 롤백되므로 재고는 그대로여야 한다")
                    .isEqualTo(70);
            assertThat(lotA.getLotQuantity()).isEqualTo(5);
        }
    }

    /* ==================================================================
     * 픽스처
     * ================================================================== */

    private Product product(int totalStock) {
        return Product.builder()
                .productId(1L)
                .productCode("FD-CT-001")
                .name("프리미엄 육성우 배합사료")
                .animalType(AnimalType.CATTLE)
                .productType(ProductType.FEED)
                .weightKg(25)
                .price(32000L)
                .totalStock(totalStock)
                .safetyStock(10)
                .shelfLifeDays(180)
                .active(true)
                .build();
    }

    private ProductLot lot(Long lotId, Product product, String lotNo, int lotQuantity) {
        return ProductLot.builder()
                .lotId(lotId)
                .product(product)
                .lotNo(lotNo)
                .manufacturedDate(LocalDate.now().minusDays(30))
                .expirationDate(LocalDate.now().plusDays(150))
                .lotQuantity(lotQuantity)
                .build();
    }

    private WarehouseBin bin(Long binId, String binCode, int maxCapacity) {
        return WarehouseBin.builder()
                .binId(binId)
                .binCode(binCode)
                .warehouse(Warehouse.WH1)
                .zone(binCode.substring(0, 1))
                .binPurpose(BinPurpose.STORAGE)
                .rack("01")
                .binLevel(1)
                .maxCapacity(maxCapacity)
                .posX(1)
                .posY(1)
                .posWidth(2)
                .posHeight(2)
                .active(true)
                .build();
    }

    private Inventory inventory(Long inventoryId, ProductLot lot, WarehouseBin bin, int quantity) {
        return Inventory.builder()
                .inventoryId(inventoryId)
                .lot(lot)
                .bin(bin)
                .quantity(quantity)
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private StockMovement outbound(Long movementId, ProductLot lot, WarehouseBin bin, int quantity) {
        return StockMovement.builder()
                .movementId(movementId)
                .movementType(MovementType.OUTBOUND)
                .product(lot.getProduct())
                .lot(lot)
                .bin(bin)
                .quantity(quantity)
                .orderId(ORDER_ID)
                .userId(USER_ID)
                .userName(USER_NAME)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private Order order(OrderStatus status, Product product, int quantity) {
        User customer = User.builder()
                .userId(3L)
                .email("farm@example.com")
                .password("encoded")
                .name("김농장")
                .phone("010-0000-0000")
                .role(Role.USER)
                .createdAt(LocalDateTime.now())
                .build();

        Order order = Order.builder()
                .orderId(ORDER_ID)
                .user(customer)
                .totalPrice(320000L)
                .discountPrice(0L)
                .finalPrice(320000L)
                .shippingAddress("경북 상주시 낙동면 목장길 12")
                .status(status)
                .createdAt(LocalDateTime.now())
                .build();

        order.getOrderItems().add(OrderItem.builder()
                .orderItemId(1L)
                .order(order)
                .product(product)
                .quantity(quantity)
                .orderPrice(32000L)
                .build());

        return order;
    }
}
