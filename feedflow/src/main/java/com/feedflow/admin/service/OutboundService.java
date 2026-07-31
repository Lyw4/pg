package com.feedflow.admin.service;

import com.feedflow.common.util.Numbers;
import com.feedflow.admin.dto.AllocationLineDto;
import com.feedflow.admin.dto.AllocationPlanDto;
import com.feedflow.admin.dto.OrderDispatchPreviewDto;
import com.feedflow.admin.dto.OrderDispatchResultDto;
import com.feedflow.admin.dto.OrderItemPreviewDto;
import com.feedflow.admin.dto.OrderListFilter;
import com.feedflow.admin.dto.OrderSummaryDto;
import com.feedflow.admin.dto.OutboundForm;
import com.feedflow.admin.dto.OutboundResultDto;
import com.feedflow.common.exception.BusinessRuleException;
import com.feedflow.common.exception.InsufficientStockException;
import com.feedflow.common.exception.ResourceNotFoundException;
import com.feedflow.domain.Inventory;
import com.feedflow.domain.MovementType;
import com.feedflow.domain.Order;
import com.feedflow.domain.OrderItem;
import com.feedflow.domain.Product;
import com.feedflow.domain.ProductLot;
import com.feedflow.domain.StockMovement;
import com.feedflow.domain.WarehouseBin;
import com.feedflow.repository.InventoryRepository;
import com.feedflow.repository.OrderRepository;
import com.feedflow.repository.ProductRepository;
import com.feedflow.repository.StockMovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 선입선출(FEFO) 출고 서비스.
 *
 * <h3>FIFO / FEFO 정책</h3>
 * 사료는 유통기한이 있는 상품이므로 단순 입고순(FIFO)이 아니라
 * <b>유통기한이 가장 먼저 도래하는 로트부터 출고</b>(FEFO, First Expired First Out)한다.
 * 유통기한이 같으면 구역 코드 순으로 출고한다.
 *
 * <h3>출고 처리 규칙</h3>
 * <ol>
 *     <li>출고 후보에서 제외하는 것 — 유통기한이 이미 지난 로트, 사용 중지된 구역,
 *         <b>입고 대기 · 검수 구역</b>(검수 전이라 내보낼 수 없다),
 *         <b>운송 중</b>(트럭 위라 집어올 수 없다).
 *         조건은 {@code InventoryRepository.findAllocatableByProductId} 의 JPQL 에 있다.</li>
 *     <li>요청 수량을 채울 때까지 <b>여러 로트/구역에 걸쳐 순차적으로 차감</b>한다.</li>
 *     <li>출고 가능 수량이 부족하면 {@link InsufficientStockException} 을 던져
 *         트랜잭션 전체를 롤백한다. (부분 출고 없음)</li>
 *     <li>차감 대상 : Inventory.quantity → ProductLot.lotQuantity → Product.totalStock</li>
 *     <li>차감 한 줄마다 OUTBOUND 이력을 남긴다.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OutboundService {

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final StockMovementRepository stockMovementRepository;
    private final OrderRepository orderRepository;

    /* ==================================================================
     * 직접 출고 (주문과 무관한 출고)
     * ================================================================== */

    /**
     * 품목 단위 출고 처리 (FEFO).
     *
     * @param form     출고 요청 (품목, 수량, 비고)
     * @param userId   처리자 ID (이력 스냅샷용)
     * @param userName 처리자 이름 (이력 스냅샷용)
     * @return 로트별 차감 내역이 담긴 출고 결과
     * @throws InsufficientStockException 출고 가능 재고가 부족한 경우
     */
    @Transactional
    public OutboundResultDto dispatch(OutboundForm form, Long userId, String userName) {
        Product product = findProduct(form.getProductId());
        int quantity = requirePositive(form.getQuantity());

        List<AllocationLineDto> lines =
                allocateAndDeduct(product, quantity, form.getMemo(), userId, userName, null, null);

        return OutboundResultDto.builder()
                .productId(product.getProductId())
                .productCode(product.getProductCode())
                .productName(product.getName())
                .quantity(quantity)
                .lines(lines)
                .productTotalStock(product.getTotalStock())
                .build();
    }

    /* ==================================================================
     * 주문 기반 출고
     * ================================================================== */

    /**
     * 주문 출고 처리.
     * 주문의 모든 항목을 FEFO 로 차감하고 주문 상태를 출고완료로 변경한다.
     * 항목 중 하나라도 재고가 부족하면 전체가 롤백된다.
     */
    @Transactional
    public OrderDispatchResultDto dispatchOrder(Long orderId, Long userId, String userName) {
        Order order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("존재하지 않는 주문입니다. id=" + orderId));

        if (!order.isDispatchable()) {
            throw new BusinessRuleException(
                    "출고할 수 없는 주문 상태입니다. 현재 상태=" + order.getStatus().getDescription());
        }
        if (order.getOrderItems().isEmpty()) {
            throw new BusinessRuleException("주문 상세 항목이 없어 출고할 수 없습니다. 주문번호=" + orderId);
        }

        String memo = "주문 #" + orderId + " 출고";
        List<OutboundResultDto> results = new ArrayList<>();

        for (OrderItem orderItem : order.getOrderItems()) {
            Product product = orderItem.getProduct();
            int quantity = requirePositive(orderItem.getQuantity());

            List<AllocationLineDto> lines =
                    allocateAndDeduct(product, quantity, memo, userId, userName,
                            orderItem, order.getOrderId());

            results.add(OutboundResultDto.builder()
                    .productId(product.getProductId())
                    .productCode(product.getProductCode())
                    .productName(product.getName())
                    .quantity(quantity)
                    .lines(lines)
                    .productTotalStock(product.getTotalStock())
                    .build());
        }

        order.markShipped();

        return OrderDispatchResultDto.builder()
                .orderId(order.getOrderId())
                .customerName(order.getUser().getName())
                .status(order.getStatus())
                .items(results)
                .build();
    }

    /* ==================================================================
     * 조회 / 미리보기
     * ================================================================== */

    /**
     * 상태 필터에 맞는 주문 목록.
     * <p>
     * 정렬 방향은 필터가 알고 있다. (출고 대기는 오래된 순, 이력 조회는 최신순)
     */
    public List<OrderSummaryDto> getOrders(OrderListFilter filter) {
        List<Order> orders = filter.isOldestFirst()
                ? orderRepository.findDispatchTargets(filter.getStatuses())
                : orderRepository.findByStatusesLatestFirst(filter.getStatuses());

        return orders.stream()
                .map(OrderSummaryDto::from)
                .toList();
    }

    /** 주문 출고 상세 + 항목별 FEFO 할당 미리보기 (재고 변경 없음) */
    public OrderDispatchPreviewDto getOrderPreview(Long orderId) {
        Order order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("존재하지 않는 주문입니다. id=" + orderId));

        // 주문 항목마다 후보 재고를 조회하면 항목 수만큼 쿼리가 반복된다.
        // 미리보기는 재고를 변경하지 않으므로 한 번에 모아 조회한 뒤 품목별로 나눠 쓴다.
        Map<Long, List<Inventory>> candidatesByProductId = findAllocatableCandidates(order);

        List<OrderItemPreviewDto> items = order.getOrderItems().stream()
                .map(item -> OrderItemPreviewDto.builder()
                        .orderItemId(item.getOrderItemId())
                        .productId(item.getProduct().getProductId())
                        .productCode(item.getProduct().getProductCode())
                        .productName(item.getProduct().getName())
                        .animalType(item.getProduct().getAnimalType().getDescription())
                        .quantity(item.getQuantity())
                        .orderPrice(item.getOrderPrice())
                        .plan(previewAllocation(item.getProduct(), item.getQuantity(),
                                candidatesByProductId.getOrDefault(
                                        item.getProduct().getProductId(), List.of())))
                        .build())
                .toList();

        // 취소된 주문만 복구 이력을 조회한다 (그 외에는 쿼리를 실행하지 않는다)
        List<StockMovement> restorations = order.isCanceled()
                ? stockMovementRepository.findByOrderIdAndType(order.getOrderId(), MovementType.CANCEL)
                : List.of();

        return OrderDispatchPreviewDto.builder()
                .orderId(order.getOrderId())
                .customerName(order.getUser().getName())
                .customerPhone(order.getUser().getPhone())
                .shippingAddress(order.getShippingAddress())
                .status(order.getStatus())
                .totalPrice(order.getTotalPrice())
                .discountPrice(order.getDiscountPrice())
                .finalPrice(order.getFinalPrice())
                .createdAt(order.getCreatedAt())
                .items(items)
                .dispatchable(order.isDispatchable())
                .cancelable(order.isCancelable())
                .stockDeducted(order.isStockDeducted())
                .canceled(order.isCanceled())
                .cancelReason(order.getCancelReason())
                .canceledAt(order.getCanceledAt())
                .canceledByName(order.getCanceledByName())
                .restoredQuantity(restorations.stream()
                        .mapToInt(movement -> Numbers.orZero(movement.getQuantity()))
                        .sum())
                .restoredLineCount(restorations.size())
                .build();
    }

    /** 품목 + 수량에 대한 FEFO 할당 미리보기 (재고 변경 없음) */
    public AllocationPlanDto previewAllocation(Long productId, int quantity) {
        Product product = findProduct(productId);
        return previewAllocation(product, quantity,
                inventoryRepository.findAllocatableByProductId(productId, LocalDate.now()));
    }

    /**
     * 주문의 모든 항목에 대한 FEFO 후보 재고를 <b>한 번의 쿼리</b>로 가져와 품목별로 묶는다.
     * <p>
     * 미리보기는 재고를 변경하지 않으므로 항목마다 다시 조회할 이유가 없다.
     */
    private Map<Long, List<Inventory>> findAllocatableCandidates(Order order) {
        Set<Long> productIds = order.getOrderItems().stream()
                .map(item -> item.getProduct().getProductId())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (productIds.isEmpty()) {
            // in () 조건은 DB 에 따라 문법 오류가 되므로 쿼리를 아예 실행하지 않는다
            return Map.of();
        }

        return inventoryRepository.findAllocatableByProductIds(productIds, LocalDate.now()).stream()
                .collect(Collectors.groupingBy(
                        inventory -> inventory.getLot().getProduct().getProductId(),
                        LinkedHashMap::new,
                        Collectors.toList()));
    }

    /**
     * FEFO 할당 미리보기.
     *
     * @param candidates 이미 조회해 둔 출고 후보 재고 (정렬은 {@link #planFefo} 가 보장한다)
     */
    private AllocationPlanDto previewAllocation(Product product, int quantity,
                                               List<Inventory> candidates) {
        LocalDate today = LocalDate.now();

        int available = totalQuantityOf(candidates);
        List<Allocation> allocations = planFefo(candidates, quantity);

        List<AllocationLineDto> lines = new ArrayList<>();
        int sequence = 1;
        int allocated = 0;

        for (Allocation allocation : allocations) {
            Inventory inventory = allocation.inventory();
            int take = allocation.quantity();
            int before = Numbers.orZero(inventory.getQuantity());
            allocated += take;

            // 미리보기는 재고를 변경하지 않으므로 "차감 후" 값을 직접 계산해서 넘긴다
            lines.add(toAllocationLine(sequence++, inventory, take, before,
                    before - take, Numbers.orZero(inventory.getLot().getLotQuantity()) - take, today));
        }

        return AllocationPlanDto.builder()
                .productId(product.getProductId())
                .productCode(product.getProductCode())
                .productName(product.getName())
                .requestedQuantity(quantity)
                .availableQuantity(available)
                .allocatedQuantity(allocated)
                .lines(lines)
                .build();
    }

    /* ==================================================================
     * 핵심 : FEFO 할당 및 차감
     * ================================================================== */

    /**
     * FEFO 순서로 재고를 찾아 <b>여러 로트 · 구역에 걸쳐 나눠서</b> 실제로 차감한다.
     *
     * <h3>왜 나눠서 차감하는가</h3>
     * 요청 수량이 로트 하나의 잔여를 넘는 일이 흔하다. 사료는 같은 품목이라도
     * 제조일이 다른 로트가 여러 구역에 쌓여 있기 때문이다. 한 로트로 부족하면
     * 거절하는 대신 <b>유통기한이 급한 로트부터 순서대로 이어서</b> 채운다.
     * 그래야 오래된 재고가 창고에 남아 폐기로 가는 것을 줄일 수 있다.
     *
     * <h3>처리 순서</h3>
     * <ol>
     *     <li>출고 후보 조회 — 유통기한 경과 · 사용 중지 구역 · <b>검수 전(입고 대기 ·
     *         검수) · 운송 중</b> 재고는 애초에 후보에서 빠진다
     *         ({@code InventoryRepository.findAllocatableByProductId} 의 JPQL 조건)</li>
     *     <li>{@link #planFefo} 로 "어느 재고에서 몇 개" 계획만 먼저 세운다</li>
     *     <li>계획 합계가 요청보다 적으면 <b>아무것도 건드리지 않고</b> 예외를 던진다.
     *         부분 출고를 만들지 않기 위해서다 — 절반만 나간 주문은 되돌리기가
     *         출고보다 어렵다</li>
     *     <li>계획대로 차감한다. 순서는 구역 재고 → 로트 수량 → 이력이고,
     *         마지막에 품목 전체 재고를 한 번 줄인다</li>
     * </ol>
     *
     * <h3>3계층 불변식</h3>
     * {@code Product.totalStock} = Σ{@code ProductLot.lotQuantity} =
     * Σ{@code Inventory.quantity} 를 유지한다. 차감을 세 계층에 각각 반영하되
     * 같은 트랜잭션 안에서 처리하므로, 중간에 예외가 나면 전부 롤백된다.
     *
     * @param product   출고할 품목 (전체 재고를 마지막에 차감한다)
     * @param quantity  요청 수량. 이 수량을 정확히 채우지 못하면 예외
     * @param memo      이력에 남길 메모
     * @param orderItem 주문 기반 출고면 <b>대표 로트</b>를 기록할 주문 항목, 직접 출고면 null.
     *                  FEFO 는 여러 로트에 걸치는데 {@code orderItems.lotId} 는 하나만
     *                  담을 수 있어 가장 먼저 만료되는 로트를 대표로 남긴다.
     *                  (되돌릴 근거로는 쓸 수 없다 — 그래서 아래 orderId 를 이력에 남긴다)
     * @param orderId   주문 기반 출고면 주문 번호, 직접 출고면 null.
     *                  {@code orderItem.getOrder()} 로 역참조하지 않고 <b>명시적으로 받는다.</b>
     *                  양방향 연관의 반대편이 채워져 있다는 보장에 기대면 호출 맥락에 따라
     *                  null 참조가 발생할 수 있다.
     *                  이 값이 있어야 출고 취소 시 어느 로트 · 구역으로 되돌릴지 알 수 있다.
     * @throws InsufficientStockException 출고 가능 재고가 요청 수량보다 적을 때.
     *                                    전체 재고가 충분해도 발생할 수 있다
     * @see #planFefo
     */
    private List<AllocationLineDto> allocateAndDeduct(Product product,
                                                      int quantity,
                                                      String memo,
                                                      Long userId,
                                                      String userName,
                                                      OrderItem orderItem,
                                                      Long orderId) {

        LocalDate today = LocalDate.now();

        // 1) 유통기한 임박 순으로 출고 후보 재고를 가져온다
        List<Inventory> candidates =
                inventoryRepository.findAllocatableByProductId(product.getProductId(), today);

        // 2) 어느 로트에서 몇 개를 뺄지 계획을 세운다
        List<Allocation> allocations = planFefo(candidates, quantity);
        int allocated = allocations.stream().mapToInt(Allocation::quantity).sum();

        // 3) 부족하면 아무것도 차감하지 않고 예외 → 트랜잭션 롤백
        if (allocated < quantity) {
            throw new InsufficientStockException(
                    product.getProductCode(), quantity, totalQuantityOf(candidates));
        }

        // 4) 계획대로 차감 (구역 재고 → 로트 수량 → 이력)
        List<AllocationLineDto> lines = new ArrayList<>();
        int sequence = 1;

        for (Allocation allocation : allocations) {
            Inventory inventory = allocation.inventory();
            ProductLot lot = inventory.getLot();
            WarehouseBin bin = inventory.getBin();

            int take = allocation.quantity();
            int before = Numbers.orZero(inventory.getQuantity());

            inventory.subtractQuantity(take);
            lot.subtractQuantity(take);

            // 주문 번호를 남겨야 나중에 출고 취소 시 어느 로트/구역으로 되돌릴지 알 수 있다
            stockMovementRepository.save(
                    StockMovement.outbound(lot, bin, take, orderId, memo, userId, userName));

            // 첫 번째(= 가장 먼저 만료되는) 로트를 주문 항목의 대표 로트로 기록
            if (orderItem != null && sequence == 1) {
                orderItem.assignLot(lot);
            }

            // 이미 차감이 반영된 상태이므로 현재 값을 그대로 넘긴다
            lines.add(toAllocationLine(sequence++, inventory, take, before,
                    Numbers.orZero(inventory.getQuantity()), Numbers.orZero(lot.getLotQuantity()), today));
        }

        // 5) 품목 전체 재고 차감
        product.decreaseStock(quantity);

        return lines;
    }

    /**
     * 차감 내역 한 줄을 만든다.
     * 미리보기(계산값)와 실제 차감(반영값) 두 경로가 같은 표기를 쓰도록 한 곳으로 모았다.
     */
    private AllocationLineDto toAllocationLine(int sequence,
                                               Inventory inventory,
                                               int allocatedQuantity,
                                               int binQuantityBefore,
                                               int binQuantityAfter,
                                               int lotQuantityAfter,
                                               LocalDate today) {
        ProductLot lot = inventory.getLot();
        WarehouseBin bin = inventory.getBin();

        return AllocationLineDto.builder()
                .sequence(sequence)
                .lotId(lot.getLotId())
                .lotNo(lot.getLotNo())
                .expirationDate(lot.getExpirationDate())
                .remainingDays(lot.daysUntilExpiration(today))
                .binId(bin.getBinId())
                .binCode(bin.getBinCode())
                .allocatedQuantity(allocatedQuantity)
                .binQuantityBefore(binQuantityBefore)
                .binQuantityAfter(binQuantityAfter)
                .lotQuantityAfter(lotQuantityAfter)
                .build();
    }

    /**
     * FEFO 할당 계획 수립 (부수효과 없음).
     * <p>
     * Repository 에서 이미 유통기한 오름차순으로 정렬해 주지만,
     * 선입선출 규칙은 이 로직의 핵심 계약이므로 서비스 계층에서 한 번 더 정렬한다.
     * (유통기한 → 구역코드 순)
     */
    /**
     * FEFO(First Expired First Out) 분할 할당 계획을 세운다. <b>재고를 변경하지 않는다.</b>
     *
     * <h3>계획과 실행을 분리한 이유</h3>
     * 차감하면서 부족을 발견하면 이미 줄여 놓은 재고를 되돌려야 한다. 먼저 계획을
     * 세워 합계를 확인하면 <b>부족한 경우 아무것도 건드리지 않고</b> 중단할 수 있다.
     * 덕분에 미리보기 화면과 실제 출고가 <b>같은 함수</b>를 쓴다 — 두 경로가 갈리면
     * "미리보기에서는 되던 출고가 실제로는 실패" 하는 상황이 생긴다.
     *
     * <h3>정렬 기준</h3>
     * <pre>
     *   1순위  유통기한 오름차순  — 먼저 만료되는 로트부터 내보낸다 (FEFO)
     *   2순위  구역 코드 오름차순 — 유통기한이 같을 때 순서를 고정한다
     * </pre>
     * 2순위가 없으면 같은 유통기한 로트들의 처리 순서가 조회마다 달라져,
     * 미리보기에 보인 구역과 실제 차감된 구역이 어긋날 수 있다.
     *
     * <h3>분할 방식</h3>
     * 앞에서부터 <b>가능한 만큼 다 담고 남은 수량을 다음 재고로 넘긴다.</b>
     * 마지막 재고에서는 남은 수량만 부분 차감된다.
     * <pre>
     *   요청 250  /  A-01 로트X 100(D-5) · B-02 로트Y 120(D-10) · C-03 로트Z 200(D-30)
     *     → A-01 에서 100  (남은 150)
     *     → B-02 에서 120  (남은  30)
     *     → C-03 에서  30  (남은   0)  ← 부분 차감
     * </pre>
     * 수량이 0 인 재고 행은 건너뛴다. 출고 · 폐기로 0 이 되어도 행을 남기는 정책이라
     * 후보 목록에 섞여 들어온다.
     *
     * @param candidates 출고 후보 재고. 호출부가 이미 걸러 온 것을 전제한다
     *                   (유통기한 경과 · 사용 중지 구역 · 검수 전 · 운송 중 제외)
     * @param quantity   채우려는 수량
     * @return 채운 만큼의 할당 계획. <b>요청 수량을 채우지 못했어도 부분 계획을 그대로
     *         돌려준다.</b> 부족 판정은 호출부가 합계를 보고 하고, 미리보기 화면은
     *         "어디까지 채워지는지" 를 보여주는 데 이 부분 계획을 쓴다
     */
    private List<Allocation> planFefo(List<Inventory> candidates, int quantity) {
        List<Inventory> sorted = candidates.stream()
                .sorted(Comparator
                        .comparing((Inventory i) -> i.getLot().getExpirationDate())
                        .thenComparing(i -> i.getBin().getBinCode()))
                .toList();

        List<Allocation> allocations = new ArrayList<>();
        int remaining = quantity;

        for (Inventory inventory : sorted) {
            if (remaining <= 0) {
                break;
            }
            int available = Numbers.orZero(inventory.getQuantity());
            if (available <= 0) {
                continue;
            }

            int take = Math.min(remaining, available);
            allocations.add(new Allocation(inventory, take));
            remaining -= take;
        }

        return allocations;
    }

    /** 로트 × 구역 재고 한 줄에서 빼낼 수량 */
    private record Allocation(Inventory inventory, int quantity) {
    }

    /* ==================================================================
     * 내부 헬퍼
     * ================================================================== */

    private Product findProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> ResourceNotFoundException.ofProduct(productId));
    }

    private int requirePositive(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new BusinessRuleException("출고 수량은 1 이상이어야 합니다.");
        }
        return quantity;
    }

    private int totalQuantityOf(List<Inventory> inventories) {
        return inventories.stream()
                .mapToInt(inventory -> Numbers.orZero(inventory.getQuantity()))
                .sum();
    }
}
