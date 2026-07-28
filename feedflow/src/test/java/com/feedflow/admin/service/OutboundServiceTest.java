package com.feedflow.admin.service;

import com.feedflow.domain.AnimalType;
import com.feedflow.admin.dto.AllocationLineDto;
import com.feedflow.admin.dto.AllocationPlanDto;
import com.feedflow.admin.dto.OrderDispatchResultDto;
import com.feedflow.admin.dto.OutboundForm;
import com.feedflow.admin.dto.OutboundResultDto;
import com.feedflow.common.exception.BusinessRuleException;
import com.feedflow.common.exception.InsufficientStockException;
import com.feedflow.domain.Inventory;
import com.feedflow.domain.MovementType;
import com.feedflow.domain.Order;
import com.feedflow.domain.OrderItem;
import com.feedflow.domain.OrderStatus;
import com.feedflow.domain.Product;
import com.feedflow.domain.ProductLot;
import com.feedflow.domain.Role;
import com.feedflow.domain.StockMovement;
import com.feedflow.domain.User;
import com.feedflow.domain.WarehouseBin;
import com.feedflow.repository.InventoryRepository;
import com.feedflow.repository.OrderRepository;
import com.feedflow.repository.ProductRepository;
import com.feedflow.repository.StockMovementRepository;
import org.junit.jupiter.api.DisplayName;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 선입선출(FEFO) 출고 서비스 단위 테스트.
 *
 * <p>핵심 시나리오
 * <pre>
 *   재고 : [로트A 10개 / 유통기한 D-1]  [로트B 20개 / 유통기한 D-10]
 *   요청 : 15개 출고
 *   기대 : 로트A 10개 전량 소진 + 로트B 5개 차감 (로트B 15개 잔여)
 * </pre>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OutboundService FEFO 출고 단위 테스트")
class OutboundServiceTest {

    private static final Long PRODUCT_ID = 1L;
    private static final Long LOT_A_ID = 100L;
    private static final Long LOT_B_ID = 200L;
    private static final Long BIN_A_ID = 10L;
    private static final Long BIN_B_ID = 20L;

    private static final Long USER_ID = 2L;
    private static final String USER_NAME = "이사원";

    private final LocalDate today = LocalDate.now();

    @Mock
    private ProductRepository productRepository;
    @Mock
    private InventoryRepository inventoryRepository;
    @Mock
    private StockMovementRepository stockMovementRepository;
    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OutboundService outboundService;

    /* ==================================================================
     * 핵심 시나리오 : 여러 로트에 걸친 FEFO 분할 차감
     * ================================================================== */

    @Test
    @DisplayName("[핵심] 로트A(10개, D-1) + 로트B(20개, D-10) 상태에서 15개 출고하면 A 전량 + B 5개가 차감된다")
    void dispatch_fefo_splitsAcrossLots() {
        // given : 품목 전체 재고 30개 (= 10 + 20)
        Product product = product(30);

        ProductLot lotA = lot(LOT_A_ID, product, "LOT-A", today.plusDays(1), 10);
        ProductLot lotB = lot(LOT_B_ID, product, "LOT-B", today.plusDays(10), 20);

        Inventory inventoryA = inventory(1L, lotA, bin(BIN_A_ID, "A-01-01"), 10);
        Inventory inventoryB = inventory(2L, lotB, bin(BIN_B_ID, "B-01-01"), 20);

        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
        // 일부러 유통기한이 늦은 로트B를 먼저 넣어서, 서비스가 FEFO 로 재정렬하는지까지 검증한다
        given(inventoryRepository.findAllocatableByProductId(eq(PRODUCT_ID), any(LocalDate.class)))
                .willReturn(List.of(inventoryB, inventoryA));

        // when : 15개 출고
        OutboundResultDto result = outboundService.dispatch(outboundForm(15), USER_ID, USER_NAME);

        // then 1) 로트A 가 전량 소진되고, 로트B 에서 5개만 차감된다
        assertThat(inventoryA.getQuantity())
                .as("유통기한이 임박한 로트A 는 전량 소진되어야 한다")
                .isZero();
        assertThat(lotA.getLotQuantity()).isZero();

        assertThat(inventoryB.getQuantity())
                .as("로트B 는 20 - 5 = 15 가 남아야 한다")
                .isEqualTo(15);
        assertThat(lotB.getLotQuantity()).isEqualTo(15);

        // then 2) 품목 전체 재고도 30 - 15 = 15
        assertThat(product.getTotalStock()).isEqualTo(15);
        assertThat(result.getProductTotalStock()).isEqualTo(15);

        // then 3) 차감 내역은 FEFO 순서(로트A → 로트B)로 2줄이 만들어진다
        assertThat(result.getLines()).hasSize(2);
        assertThat(result.getUsedLotCount()).isEqualTo(2);
        assertThat(result.getDepletedLotCount()).isEqualTo(1);

        AllocationLineDto first = result.getLines().get(0);
        assertThat(first.getSequence()).isEqualTo(1);
        assertThat(first.getLotNo()).isEqualTo("LOT-A");
        assertThat(first.getAllocatedQuantity()).isEqualTo(10);
        assertThat(first.getBinQuantityBefore()).isEqualTo(10);
        assertThat(first.getBinQuantityAfter()).isZero();
        assertThat(first.getLotQuantityAfter()).isZero();
        assertThat(first.isDepleted()).isTrue();
        assertThat(first.getRemainingDays()).isEqualTo(1L);

        AllocationLineDto second = result.getLines().get(1);
        assertThat(second.getSequence()).isEqualTo(2);
        assertThat(second.getLotNo()).isEqualTo("LOT-B");
        assertThat(second.getAllocatedQuantity()).isEqualTo(5);
        assertThat(second.getBinQuantityBefore()).isEqualTo(20);
        assertThat(second.getBinQuantityAfter()).isEqualTo(15);
        assertThat(second.getLotQuantityAfter()).isEqualTo(15);
        assertThat(second.isDepleted()).isFalse();
        assertThat(second.getRemainingDays()).isEqualTo(10L);

        // then 4) 출고 이력이 로트별로 2건 기록된다 (10개 + 5개)
        ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository, times(2)).save(captor.capture());

        List<StockMovement> movements = captor.getAllValues();
        assertThat(movements).allSatisfy(movement ->
                assertThat(movement.getMovementType()).isEqualTo(MovementType.OUTBOUND));
        assertThat(movements.get(0).getLot()).isSameAs(lotA);
        assertThat(movements.get(0).getQuantity()).isEqualTo(10);
        assertThat(movements.get(1).getLot()).isSameAs(lotB);
        assertThat(movements.get(1).getQuantity()).isEqualTo(5);
        assertThat(movements.get(0).getUserName()).isEqualTo(USER_NAME);
    }

    @Test
    @DisplayName("[FEFO] 첫 로트만으로 충족되면 다음 로트는 건드리지 않는다")
    void dispatch_singleLotIsEnough() {
        // given
        Product product = product(30);
        ProductLot lotA = lot(LOT_A_ID, product, "LOT-A", today.plusDays(1), 10);
        ProductLot lotB = lot(LOT_B_ID, product, "LOT-B", today.plusDays(10), 20);
        Inventory inventoryA = inventory(1L, lotA, bin(BIN_A_ID, "A-01-01"), 10);
        Inventory inventoryB = inventory(2L, lotB, bin(BIN_B_ID, "B-01-01"), 20);

        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
        given(inventoryRepository.findAllocatableByProductId(eq(PRODUCT_ID), any(LocalDate.class)))
                .willReturn(List.of(inventoryA, inventoryB));

        // when : 7개만 출고
        OutboundResultDto result = outboundService.dispatch(outboundForm(7), USER_ID, USER_NAME);

        // then
        assertThat(result.getLines()).hasSize(1);
        assertThat(inventoryA.getQuantity()).isEqualTo(3);
        assertThat(lotA.getLotQuantity()).isEqualTo(3);
        assertThat(inventoryB.getQuantity())
                .as("두 번째 로트는 전혀 차감되지 않아야 한다")
                .isEqualTo(20);
        assertThat(lotB.getLotQuantity()).isEqualTo(20);
        assertThat(product.getTotalStock()).isEqualTo(23);

        verify(stockMovementRepository, times(1)).save(any(StockMovement.class));
    }

    @Test
    @DisplayName("[FEFO] 요청 수량이 전체 재고와 정확히 같으면 모든 로트가 전량 소진된다")
    void dispatch_exactMatch_depletesAllLots() {
        // given
        Product product = product(30);
        ProductLot lotA = lot(LOT_A_ID, product, "LOT-A", today.plusDays(1), 10);
        ProductLot lotB = lot(LOT_B_ID, product, "LOT-B", today.plusDays(10), 20);
        Inventory inventoryA = inventory(1L, lotA, bin(BIN_A_ID, "A-01-01"), 10);
        Inventory inventoryB = inventory(2L, lotB, bin(BIN_B_ID, "B-01-01"), 20);

        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
        given(inventoryRepository.findAllocatableByProductId(eq(PRODUCT_ID), any(LocalDate.class)))
                .willReturn(List.of(inventoryA, inventoryB));

        // when
        OutboundResultDto result = outboundService.dispatch(outboundForm(30), USER_ID, USER_NAME);

        // then
        assertThat(inventoryA.getQuantity()).isZero();
        assertThat(inventoryB.getQuantity()).isZero();
        assertThat(lotA.getLotQuantity()).isZero();
        assertThat(lotB.getLotQuantity()).isZero();
        assertThat(product.getTotalStock()).isZero();
        assertThat(result.getDepletedLotCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("[FEFO] 유통기한이 같으면 구역 코드 순으로 차감한다")
    void dispatch_sameExpiration_ordersByBinCode() {
        // given : 유통기한이 동일한 두 구역 (B-01-01, A-01-01)
        Product product = product(30);
        ProductLot lotA = lot(LOT_A_ID, product, "LOT-A", today.plusDays(5), 30);

        Inventory inB = inventory(1L, lotA, bin(BIN_B_ID, "B-01-01"), 20);
        Inventory inA = inventory(2L, lotA, bin(BIN_A_ID, "A-01-01"), 10);

        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
        given(inventoryRepository.findAllocatableByProductId(eq(PRODUCT_ID), any(LocalDate.class)))
                .willReturn(List.of(inB, inA));

        // when
        OutboundResultDto result = outboundService.dispatch(outboundForm(15), USER_ID, USER_NAME);

        // then : A-01-01 이 먼저 차감된다
        assertThat(result.getLines().get(0).getBinCode()).isEqualTo("A-01-01");
        assertThat(inA.getQuantity()).isZero();
        assertThat(inB.getQuantity()).isEqualTo(15);
    }

    /* ==================================================================
     * 재고 부족 / 만료 로트 제외
     * ================================================================== */

    @Test
    @DisplayName("[부족] 출고 가능 재고가 부족하면 예외가 발생하고 아무 재고도 차감되지 않는다")
    void dispatch_insufficientStock_throwsAndChangesNothing() {
        // given : 가용 재고 30개
        Product product = product(30);
        ProductLot lotA = lot(LOT_A_ID, product, "LOT-A", today.plusDays(1), 10);
        ProductLot lotB = lot(LOT_B_ID, product, "LOT-B", today.plusDays(10), 20);
        Inventory inventoryA = inventory(1L, lotA, bin(BIN_A_ID, "A-01-01"), 10);
        Inventory inventoryB = inventory(2L, lotB, bin(BIN_B_ID, "B-01-01"), 20);

        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
        given(inventoryRepository.findAllocatableByProductId(eq(PRODUCT_ID), any(LocalDate.class)))
                .willReturn(List.of(inventoryA, inventoryB));

        // when & then : 31개 요청 → 부족
        assertThatThrownBy(() -> outboundService.dispatch(outboundForm(31), USER_ID, USER_NAME))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("출고 가능 재고가 부족합니다")
                .hasMessageContaining("요청=31")
                .hasMessageContaining("출고 가능=30");

        // 부분 출고 없이 원본 그대로 유지되어야 한다
        assertThat(inventoryA.getQuantity()).isEqualTo(10);
        assertThat(inventoryB.getQuantity()).isEqualTo(20);
        assertThat(lotA.getLotQuantity()).isEqualTo(10);
        assertThat(lotB.getLotQuantity()).isEqualTo(20);
        assertThat(product.getTotalStock()).isEqualTo(30);

        verify(stockMovementRepository, never()).save(any(StockMovement.class));
    }

    @Test
    @DisplayName("[부족] 부족 수량과 가용 수량이 예외에 담긴다")
    void dispatch_insufficientStock_exceptionCarriesDetails() {
        Product product = product(10);
        ProductLot lotA = lot(LOT_A_ID, product, "LOT-A", today.plusDays(1), 10);
        Inventory inventoryA = inventory(1L, lotA, bin(BIN_A_ID, "A-01-01"), 10);

        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
        given(inventoryRepository.findAllocatableByProductId(eq(PRODUCT_ID), any(LocalDate.class)))
                .willReturn(List.of(inventoryA));

        assertThatThrownBy(() -> outboundService.dispatch(outboundForm(25), USER_ID, USER_NAME))
                .isInstanceOf(InsufficientStockException.class)
                .satisfies(e -> {
                    InsufficientStockException ex = (InsufficientStockException) e;
                    assertThat(ex.getProductCode()).isEqualTo("FD-CT-001");
                    assertThat(ex.getRequestedQuantity()).isEqualTo(25);
                    assertThat(ex.getAvailableQuantity()).isEqualTo(10);
                    assertThat(ex.getShortage()).isEqualTo(15);
                });
    }

    @Test
    @DisplayName("[만료] 출고 후보 조회 시 오늘 날짜를 기준으로 만료 로트를 제외한다")
    void dispatch_excludesExpiredLots() {
        // given
        Product product = product(10);
        ProductLot lotA = lot(LOT_A_ID, product, "LOT-A", today.plusDays(1), 10);
        Inventory inventoryA = inventory(1L, lotA, bin(BIN_A_ID, "A-01-01"), 10);

        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
        given(inventoryRepository.findAllocatableByProductId(eq(PRODUCT_ID), any(LocalDate.class)))
                .willReturn(List.of(inventoryA));

        // when
        outboundService.dispatch(outboundForm(5), USER_ID, USER_NAME);

        // then : Repository 에 오늘 날짜가 전달되어 만료 로트가 걸러진다
        ArgumentCaptor<LocalDate> dateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(inventoryRepository).findAllocatableByProductId(eq(PRODUCT_ID), dateCaptor.capture());
        assertThat(dateCaptor.getValue()).isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName("[검증] 출고 수량이 0 이하면 예외가 발생한다")
    void dispatch_invalidQuantity_throwsException() {
        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product(30)));

        assertThatThrownBy(() -> outboundService.dispatch(outboundForm(0), USER_ID, USER_NAME))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("1 이상");

        verify(inventoryRepository, never()).findAllocatableByProductId(anyLong(), any(LocalDate.class));
    }

    /* ==================================================================
     * 주문 기반 출고
     * ================================================================== */

    @Test
    @DisplayName("[주문출고] 주문의 모든 항목을 FEFO 로 차감하고 주문 상태를 출고완료로 변경한다")
    void dispatchOrder_success() {
        // given : 주문 1건 (품목 1종 15개)
        Product product = product(30);
        ProductLot lotA = lot(LOT_A_ID, product, "LOT-A", today.plusDays(1), 10);
        ProductLot lotB = lot(LOT_B_ID, product, "LOT-B", today.plusDays(10), 20);
        Inventory inventoryA = inventory(1L, lotA, bin(BIN_A_ID, "A-01-01"), 10);
        Inventory inventoryB = inventory(2L, lotB, bin(BIN_B_ID, "B-01-01"), 20);

        OrderItem orderItem = OrderItem.builder()
                .orderItemId(1L)
                .product(product)
                .quantity(15)
                .orderPrice(32000L)
                .build();
        Order order = order(OrderStatus.PAID, orderItem);

        given(orderRepository.findWithItemsById(1L)).willReturn(Optional.of(order));
        given(inventoryRepository.findAllocatableByProductId(eq(PRODUCT_ID), any(LocalDate.class)))
                .willReturn(List.of(inventoryA, inventoryB));

        // when
        OrderDispatchResultDto result = outboundService.dispatchOrder(1L, USER_ID, USER_NAME);

        // then : 재고가 FEFO 로 차감된다
        assertThat(inventoryA.getQuantity()).isZero();
        assertThat(inventoryB.getQuantity()).isEqualTo(15);
        assertThat(product.getTotalStock()).isEqualTo(15);

        // 주문 상태가 출고완료로 변경된다
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(result.getStatus()).isEqualTo(OrderStatus.SHIPPED);

        // 주문 항목에는 가장 먼저 만료되는(먼저 출고된) 로트가 대표로 기록된다
        assertThat(orderItem.getLot()).isSameAs(lotA);

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getTotalQuantity()).isEqualTo(15);
        assertThat(result.getTotalLineCount()).isEqualTo(2);
        assertThat(result.getCustomerName()).isEqualTo("정한우목장");
    }

    @Test
    @DisplayName("[주문출고] 이미 출고된 주문은 다시 출고할 수 없다")
    void dispatchOrder_notDispatchableStatus_throwsException() {
        Order order = order(OrderStatus.SHIPPED,
                OrderItem.builder().orderItemId(1L).product(product(30)).quantity(5).orderPrice(32000L).build());

        given(orderRepository.findWithItemsById(1L)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> outboundService.dispatchOrder(1L, USER_ID, USER_NAME))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("출고할 수 없는 주문 상태");

        verify(inventoryRepository, never()).findAllocatableByProductId(anyLong(), any(LocalDate.class));
        verify(stockMovementRepository, never()).save(any(StockMovement.class));
    }

    @Test
    @DisplayName("[주문출고] 항목 중 하나라도 재고가 부족하면 예외가 발생한다 (전체 롤백)")
    void dispatchOrder_insufficientStock_throwsException() {
        Product product = product(10);
        ProductLot lotA = lot(LOT_A_ID, product, "LOT-A", today.plusDays(1), 10);
        Inventory inventoryA = inventory(1L, lotA, bin(BIN_A_ID, "A-01-01"), 10);

        OrderItem orderItem = OrderItem.builder()
                .orderItemId(1L).product(product).quantity(50).orderPrice(32000L).build();
        Order order = order(OrderStatus.PAID, orderItem);

        given(orderRepository.findWithItemsById(1L)).willReturn(Optional.of(order));
        given(inventoryRepository.findAllocatableByProductId(eq(PRODUCT_ID), any(LocalDate.class)))
                .willReturn(List.of(inventoryA));

        assertThatThrownBy(() -> outboundService.dispatchOrder(1L, USER_ID, USER_NAME))
                .isInstanceOf(InsufficientStockException.class);

        // 주문 상태와 재고가 그대로여야 한다 (트랜잭션 롤백 대상)
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(inventoryA.getQuantity()).isEqualTo(10);
        assertThat(orderItem.getLot()).isNull();
    }

    /* ==================================================================
     * 미리보기 (재고 변경 없음)
     * ================================================================== */

    @Test
    @DisplayName("[미리보기] 할당 계획만 계산하고 실제 재고는 변경하지 않는다")
    void previewAllocation_doesNotMutateStock() {
        // given
        Product product = product(30);
        ProductLot lotA = lot(LOT_A_ID, product, "LOT-A", today.plusDays(1), 10);
        ProductLot lotB = lot(LOT_B_ID, product, "LOT-B", today.plusDays(10), 20);
        Inventory inventoryA = inventory(1L, lotA, bin(BIN_A_ID, "A-01-01"), 10);
        Inventory inventoryB = inventory(2L, lotB, bin(BIN_B_ID, "B-01-01"), 20);

        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
        given(inventoryRepository.findAllocatableByProductId(eq(PRODUCT_ID), any(LocalDate.class)))
                .willReturn(List.of(inventoryA, inventoryB));

        // when
        AllocationPlanDto plan = outboundService.previewAllocation(PRODUCT_ID, 15);

        // then : 계획은 로트A 10 + 로트B 5
        assertThat(plan.isFulfillable()).isTrue();
        assertThat(plan.getAvailableQuantity()).isEqualTo(30);
        assertThat(plan.getAllocatedQuantity()).isEqualTo(15);
        assertThat(plan.getShortage()).isZero();
        assertThat(plan.getLines()).hasSize(2);
        assertThat(plan.getLines().get(0).getAllocatedQuantity()).isEqualTo(10);
        assertThat(plan.getLines().get(0).getBinQuantityAfter()).isZero();
        assertThat(plan.getLines().get(1).getAllocatedQuantity()).isEqualTo(5);
        assertThat(plan.getLines().get(1).getBinQuantityAfter()).isEqualTo(15);

        // 실제 재고는 그대로
        assertThat(inventoryA.getQuantity()).isEqualTo(10);
        assertThat(inventoryB.getQuantity()).isEqualTo(20);
        assertThat(product.getTotalStock()).isEqualTo(30);
        verify(stockMovementRepository, never()).save(any(StockMovement.class));
    }

    @Test
    @DisplayName("[미리보기] 재고가 부족하면 예외 없이 부족 수량을 알려준다")
    void previewAllocation_reportsShortage() {
        Product product = product(10);
        ProductLot lotA = lot(LOT_A_ID, product, "LOT-A", today.plusDays(1), 10);
        Inventory inventoryA = inventory(1L, lotA, bin(BIN_A_ID, "A-01-01"), 10);

        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
        given(inventoryRepository.findAllocatableByProductId(eq(PRODUCT_ID), any(LocalDate.class)))
                .willReturn(List.of(inventoryA));

        AllocationPlanDto plan = outboundService.previewAllocation(PRODUCT_ID, 25);

        assertThat(plan.isFulfillable()).isFalse();
        assertThat(plan.getAllocatedQuantity()).isEqualTo(10);
        assertThat(plan.getShortage()).isEqualTo(15);
    }

    /* ==================================================================
     * 픽스처
     * ================================================================== */

    private OutboundForm outboundForm(int quantity) {
        OutboundForm form = new OutboundForm();
        form.setProductId(PRODUCT_ID);
        form.setQuantity(quantity);
        form.setMemo("테스트 출고");
        return form;
    }

    private Product product(int totalStock) {
        return Product.builder()
                .productId(PRODUCT_ID)
                .productCode("FD-CT-001")
                .name("프리미엄 육성우 배합사료")
                .animalType(AnimalType.CATTLE)
                .weightKg(25)
                .price(32000L)
                .totalStock(totalStock)
                .safetyStock(50)
                .shelfLifeDays(180)
                .active(true)
                .build();
    }

    private ProductLot lot(Long lotId, Product product, String lotNo,
                           LocalDate expirationDate, int lotQuantity) {
        return ProductLot.builder()
                .lotId(lotId)
                .product(product)
                .lotNo(lotNo)
                .manufacturedDate(expirationDate.minusDays(180))
                .expirationDate(expirationDate)
                .lotQuantity(lotQuantity)
                .build();
    }

    private WarehouseBin bin(Long binId, String binCode) {
        return WarehouseBin.builder()
                .binId(binId)
                .binCode(binCode)
                .zone(binCode.substring(0, 1))
                .rack("01")
                .binLevel(1)
                .maxCapacity(500)
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

    private Order order(OrderStatus status, OrderItem... items) {
        User customer = User.builder()
                .userId(3L)
                .email("farm1@example.com")
                .password("{noop}user123")
                .name("정한우목장")
                .phone("010-3333-3003")
                .role(Role.USER)
                .createdAt(LocalDateTime.now())
                .build();

        return Order.builder()
                .orderId(1L)
                .user(customer)
                .totalPrice(480000L)
                .discountPrice(0L)
                .finalPrice(480000L)
                .shippingAddress("경북 상주시 낙동면 목장길 12")
                .status(status)
                .createdAt(LocalDateTime.now())
                .orderItems(List.of(items))
                .build();
    }
}
