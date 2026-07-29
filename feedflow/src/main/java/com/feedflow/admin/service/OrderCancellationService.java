package com.feedflow.admin.service;

import com.feedflow.admin.dto.OrderCancelResultDto;
import com.feedflow.admin.dto.RestorationLineDto;
import com.feedflow.common.exception.BusinessRuleException;
import com.feedflow.common.exception.ResourceNotFoundException;
import com.feedflow.common.util.Numbers;
import com.feedflow.domain.Inventory;
import com.feedflow.domain.MovementType;
import com.feedflow.domain.Order;
import com.feedflow.domain.OrderStatus;
import com.feedflow.domain.Product;
import com.feedflow.domain.ProductLot;
import com.feedflow.domain.StockMovement;
import com.feedflow.domain.WarehouseBin;
import com.feedflow.repository.InventoryRepository;
import com.feedflow.repository.OrderRepository;
import com.feedflow.repository.StockMovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 출고(주문) 취소 서비스.
 *
 * <h3>취소가 단순한 상태 변경이 아닌 이유</h3>
 * 출고는 FEFO 규칙에 따라 <b>여러 로트에 걸쳐</b> 재고를 차감한다.
 * 따라서 취소할 때도 "어느 로트의 어느 구역에서 몇 개를 뺐는지" 를 그대로 알아야
 * 정확히 되돌릴 수 있다.
 * <p>
 * {@code orderItems.lotId} 는 대표 로트 하나만 기록하므로 근거로 쓸 수 없다.
 * 그래서 출고 시 남긴 {@link MovementType#OUTBOUND} 이력을 <b>주문 번호로 조회해
 * 역재생(replay)</b> 하는 방식을 쓴다.
 *
 * <h3>복구 순서</h3>
 * <ol>
 *     <li>구역 재고({@code Inventory.quantity}) 복구</li>
 *     <li>로트 잔여({@code ProductLot.lotQuantity}) 복구</li>
 *     <li>품목 총 재고({@code Product.totalStock}) 복구</li>
 *     <li>{@link MovementType#CANCEL} 이력 기록 (주문 번호 포함)</li>
 *     <li>주문 상태를 {@code CANCELED} 로 변경</li>
 * </ol>
 * 이 순서는 출고 차감의 정확한 역순이라 세 계층의 합계가 항상 일치한다.
 *
 * <h3>동시성</h3>
 * 전 과정을 하나의 트랜잭션으로 묶고, 수량을 바꾸는 세 엔티티
 * ({@code Inventory} · {@code ProductLot} · {@code Product}) 는 모두 {@code @Version}
 * 낙관적 락 대상이다. 같은 주문을 두 명이 동시에 취소하면 뒤늦은 트랜잭션이
 * {@code OptimisticLockingFailureException} 으로 실패하고 롤백되므로
 * 재고가 두 번 복구되는 일은 발생하지 않는다.
 * <p>
 * 순차 실행(먼저 취소가 커밋된 뒤 두 번째 시도)인 경우는 상태 검사에서 걸러진다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderCancellationService {

    private final OrderRepository orderRepository;
    private final InventoryRepository inventoryRepository;
    private final StockMovementRepository stockMovementRepository;

    /**
     * 주문을 취소하고, 이미 출고된 주문이라면 재고를 원상 복구한다.
     *
     * @param orderId  취소할 주문
     * @param reason   취소 사유 (이력 메모에 남는다)
     * @param userId   처리자
     * @param userName 처리자 이름 (이력 스냅샷)
     * @throws ResourceNotFoundException 주문이 없는 경우
     * @throws BusinessRuleException     이미 취소됐거나 배송 완료된 경우,
     *                                   또는 복구 시 구역 수용량을 초과하는 경우
     */
    @Transactional
    public OrderCancelResultDto cancel(Long orderId, String reason, Long userId, String userName) {
        Order order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> ResourceNotFoundException.ofOrder(orderId));

        validateCancelable(order);

        OrderStatus previousStatus = order.getStatus();
        boolean stockDeducted = order.isStockDeducted();

        List<RestorationLineDto> restoredLines = stockDeducted
                ? restoreStock(order, reason, userId, userName)
                : List.of();

        order.cancel();

        return OrderCancelResultDto.builder()
                .orderId(order.getOrderId())
                .customerName(order.getUser().getName())
                .previousStatus(previousStatus)
                .status(order.getStatus())
                .restoredLines(restoredLines)
                .stockRestored(stockDeducted)
                .build();
    }

    /* ------------------------------------------------------------------
     * 검증
     * ------------------------------------------------------------------ */

    private void validateCancelable(Order order) {
        if (order.getStatus() == OrderStatus.CANCELED) {
            throw new BusinessRuleException("이미 취소된 주문입니다. (주문 #" + order.getOrderId() + ")");
        }
        if (!order.isCancelable()) {
            throw new BusinessRuleException(
                    "배송이 완료된 주문은 취소할 수 없습니다. 반품 절차로 처리하세요."
                            + " (주문 #" + order.getOrderId()
                            + ", 현재 상태: " + order.getStatus().getDescription() + ")");
        }
    }

    /* ------------------------------------------------------------------
     * 재고 복구
     * ------------------------------------------------------------------ */

    /**
     * 출고 이력을 역재생해 재고를 되돌린다.
     */
    private List<RestorationLineDto> restoreStock(Order order,
                                                  String reason,
                                                  Long userId,
                                                  String userName) {

        List<StockMovement> outbounds =
                stockMovementRepository.findByOrderIdAndType(order.getOrderId(), MovementType.OUTBOUND);

        if (outbounds.isEmpty()) {
            // 출고 완료 상태인데 이력이 없다면 근거 없이 재고를 늘리게 되므로 중단한다
            throw new BusinessRuleException(
                    "출고 이력이 없어 재고를 복구할 수 없습니다."
                            + " 재고 정합성 점검 후 수동으로 조정하세요. (주문 #" + order.getOrderId() + ")");
        }

        String memo = buildMemo(order.getOrderId(), reason);

        List<RestorationLineDto> lines = new ArrayList<>();
        int sequence = 1;

        for (StockMovement outbound : outbounds) {
            lines.add(restoreOne(outbound, sequence++, order.getOrderId(), memo, userId, userName));
        }
        return lines;
    }

    /** 출고 이력 한 건을 되돌린다 */
    private RestorationLineDto restoreOne(StockMovement outbound,
                                          int sequence,
                                          Long orderId,
                                          String memo,
                                          Long userId,
                                          String userName) {

        ProductLot lot = outbound.getLot();
        Product product = lot.getProduct();
        WarehouseBin bin = outbound.getBin();
        int quantity = Numbers.orZero(outbound.getQuantity());

        if (bin == null) {
            // 출고 이력에는 구역이 반드시 기록되므로 정상 데이터라면 발생하지 않는다
            throw new BusinessRuleException(
                    "출고 이력에 구역 정보가 없어 재고를 복구할 수 없습니다."
                            + " (이력 #" + outbound.getMovementId() + ")");
        }

        // 1) 구역 재고 복구 (출고로 0이 된 뒤 행이 정리됐다면 새로 만든다)
        Inventory inventory = inventoryRepository
                .findByLot_LotIdAndBin_BinId(lot.getLotId(), bin.getBinId())
                .orElse(null);

        boolean binRecreated = inventory == null;
        int binQuantityBefore = binRecreated ? 0 : Numbers.orZero(inventory.getQuantity());

        validateBinCapacity(bin, quantity);

        if (binRecreated) {
            inventory = inventoryRepository.save(Inventory.builder()
                    .lot(lot)
                    .bin(bin)
                    .quantity(quantity)
                    .updatedAt(LocalDateTime.now())
                    .build());
        } else {
            inventory.addQuantity(quantity);
        }

        // 2) 로트 잔여 복구  3) 품목 총 재고 복구
        lot.addQuantity(quantity);
        product.increaseStock(quantity);

        // 4) 취소 이력 기록 (입고와 구분되는 CANCEL 유형 + 주문 번호)
        stockMovementRepository.save(
                StockMovement.cancelRestore(lot, bin, quantity, orderId, memo, userId, userName));

        return RestorationLineDto.builder()
                .sequence(sequence)
                .productId(product.getProductId())
                .productCode(product.getProductCode())
                .productName(product.getName())
                .lotId(lot.getLotId())
                .lotNo(lot.getLotNo())
                .binId(bin.getBinId())
                .binCode(bin.getBinCode())
                .restoredQuantity(quantity)
                .binQuantityBefore(binQuantityBefore)
                .binQuantityAfter(Numbers.orZero(inventory.getQuantity()))
                .lotQuantityAfter(Numbers.orZero(lot.getLotQuantity()))
                .totalStockAfter(Numbers.orZero(product.getTotalStock()))
                .binRecreated(binRecreated)
                .build();
    }

    /**
     * 되돌릴 구역에 자리가 있는지 확인한다.
     * <p>
     * 출고 후 그 자리에 다른 물건이 들어왔을 수 있다. 수용량을 넘겨 되돌리면
     * 창고 도면과 적재 한도 규칙이 깨지므로 취소 자체를 막고 사유를 알려준다.
     * (관리자가 자리를 비우거나 구역을 옮긴 뒤 다시 시도해야 한다)
     */
    private void validateBinCapacity(WarehouseBin bin, int quantity) {
        // 재고가 한 건도 없는 구역은 sum() 이 null 을 반환하므로 0 으로 보정한다
        int currentLoad = (int) Numbers.orZero(inventoryRepository.sumQuantityByBinId(bin.getBinId()));

        if (!bin.canAccept(currentLoad, quantity)) {
            throw new BusinessRuleException(
                    "구역 " + bin.getBinCode() + " 의 적재 한도를 초과해 재고를 되돌릴 수 없습니다."
                            + " (현재 " + currentLoad + " + 복구 " + quantity
                            + " > 한도 " + bin.capacityLimit() + ")");
        }
    }

    private String buildMemo(Long orderId, String reason) {
        String trimmed = reason == null ? "" : reason.trim();
        String base = "주문 #" + orderId + " 출고 취소";
        return trimmed.isEmpty() ? base : base + " - " + trimmed;
    }
}
