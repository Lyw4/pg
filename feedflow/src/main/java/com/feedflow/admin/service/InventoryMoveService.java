package com.feedflow.admin.service;

import com.feedflow.admin.dto.InventoryDto;
import com.feedflow.admin.dto.StockMoveForm;
import com.feedflow.admin.dto.StockMoveResultDto;
import com.feedflow.common.exception.BusinessRuleException;
import com.feedflow.common.exception.ResourceNotFoundException;
import com.feedflow.common.util.Numbers;
import com.feedflow.domain.Inventory;
import com.feedflow.domain.Product;
import com.feedflow.domain.ProductLot;
import com.feedflow.domain.StockMovement;
import com.feedflow.domain.WarehouseBin;
import com.feedflow.repository.InventoryRepository;
import com.feedflow.repository.StockMovementRepository;
import com.feedflow.repository.WarehouseBinRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 구역 간 재고 이동(MOVE) 서비스.
 *
 * <h3>다른 재고 변동과 결정적으로 다른 점</h3>
 * 입고 · 출고 · 폐기는 창고 <b>전체 재고량</b>이 바뀌지만, 이동은 <b>위치만</b> 바뀐다.
 * 따라서 {@code ProductLot.lotQuantity} 와 {@code Product.totalStock} 을
 * <b>건드리지 않는다.</b> 이 두 값을 함께 조정하면 재고가 이중 계상된다.
 * ({@link com.feedflow.domain.MovementType#MOVE} 의 sign 이 0 인 이유가 이것이다)
 * <p>
 * 바뀌는 것은 {@code Inventory.quantity} 두 행뿐이다.
 * <pre>
 *   출발 구역 재고  -수량
 *   도착 구역 재고  +수량   (행이 없으면 새로 만든다)
 *   합계는 언제나 그대로
 * </pre>
 *
 * <h3>검증 규칙</h3>
 * <ol>
 *     <li>출발지와 도착지가 같을 수 없다.</li>
 *     <li>보관 중인 수량보다 많이 옮길 수 없다.</li>
 *     <li>사용 중지된 구역으로는 옮길 수 없다.
 *         단 <b>사용 중지된 구역에서 빼내는 것은 허용</b>한다. 구역을 비우는 작업이
 *         바로 사용 중지의 목적이므로 이를 막으면 재고가 갇힌다.</li>
 *     <li>도착 구역의 적재 한도를 넘을 수 없다.
 *         판정은 {@link WarehouseBin#canAccept(int, int)} 를 재사용해
 *         입고 · 출고 취소 복구와 같은 규칙을 쓴다.</li>
 * </ol>
 *
 * <h3>동시성</h3>
 * 전 과정을 하나의 트랜잭션으로 묶는다. {@code Inventory} 는 {@code @Version} 대상이라
 * 같은 재고 행을 두 명이 동시에 옮기면 나중 트랜잭션이
 * {@code OptimisticLockingFailureException} 으로 실패하고 전체가 롤백된다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryMoveService {

    private final InventoryRepository inventoryRepository;
    private final WarehouseBinRepository warehouseBinRepository;
    private final StockMovementRepository stockMovementRepository;

    /* ==================================================================
     * 이동 처리
     * ================================================================== */

    /**
     * 재고를 다른 구역으로 옮긴다.
     *
     * @param form     이동 요청 (출발 재고 행, 도착 구역, 수량)
     * @param userId   처리자 ID (이력 스냅샷용, null 허용)
     * @param userName 처리자 이름 (이력 스냅샷용, null 허용)
     * @throws ResourceNotFoundException 출발 재고나 도착 구역이 없는 경우
     * @throws BusinessRuleException     같은 구역 / 수량 초과 / 사용 중지 구역 /
     *                                   적재 한도 초과인 경우
     */
    @Transactional
    public StockMoveResultDto move(StockMoveForm form, Long userId, String userName) {

        int quantity = Numbers.orZero(form.getQuantity());
        if (quantity <= 0) {
            throw new BusinessRuleException("이동 수량은 1 이상이어야 합니다.");
        }

        // 1) 출발 재고 (로트 · 품목 · 구역을 한 번에 읽는다)
        Inventory source = inventoryRepository.findWithDetailById(form.getInventoryId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "이동할 재고를 찾을 수 없습니다. id=" + form.getInventoryId()));

        ProductLot lot = source.getLot();
        Product product = lot.getProduct();
        WarehouseBin fromBin = source.getBin();

        // 2) 도착 구역
        WarehouseBin toBin = warehouseBinRepository.findById(form.getTargetBinId())
                .orElseThrow(() -> ResourceNotFoundException.ofWarehouseBin(form.getTargetBinId()));

        validateMovable(fromBin, toBin, source, quantity);

        int fromQuantityBefore = Numbers.orZero(source.getQuantity());

        // 3) 도착 구역의 기존 재고 행 (같은 로트가 이미 있으면 합산한다)
        Inventory target = inventoryRepository
                .findByLot_LotIdAndBin_BinId(lot.getLotId(), toBin.getBinId())
                .orElse(null);

        boolean targetCreated = (target == null);
        int toQuantityBefore = targetCreated ? 0 : Numbers.orZero(target.getQuantity());

        // 4) 출발 차감 → 도착 증가
        //    로트 잔여와 품목 총 재고는 의도적으로 건드리지 않는다 (위치만 바뀐다)
        source.subtractQuantity(quantity);

        if (targetCreated) {
            target = inventoryRepository.save(Inventory.createForInbound(lot, toBin, quantity));
        } else {
            target.addQuantity(quantity);
        }

        // 5) 이동 이력 (출발지와 도착지를 함께 남긴다)
        stockMovementRepository.save(StockMovement.move(
                lot, fromBin, toBin, quantity, form.getMemo(), userId, userName));

        return StockMoveResultDto.builder()
                .productId(product.getProductId())
                .productCode(product.getProductCode())
                .productName(product.getName())
                .lotId(lot.getLotId())
                .lotNo(lot.getLotNo())
                .fromBinId(fromBin.getBinId())
                .fromBinCode(fromBin.getBinCode())
                .fromBinLocation(fromBin.locationLabel())
                .fromQuantityBefore(fromQuantityBefore)
                .fromQuantityAfter(Numbers.orZero(source.getQuantity()))
                .toBinId(toBin.getBinId())
                .toBinCode(toBin.getBinCode())
                .toBinLocation(toBin.locationLabel())
                .toQuantityBefore(toQuantityBefore)
                .toQuantityAfter(Numbers.orZero(target.getQuantity()))
                .toCapacityLimit(toBin.capacityLimit())
                .movedQuantity(quantity)
                .lotQuantity(Numbers.orZero(lot.getLotQuantity()))
                .productTotalStock(Numbers.orZero(product.getTotalStock()))
                .targetCreated(targetCreated)
                .sourceDepleted(source.isEmpty())
                .build();
    }

    /* ==================================================================
     * 조회 (화면 구성용)
     * ================================================================== */

    /**
     * 이동 가능한 재고 목록.
     * <p>
     * {@code search} 쿼리가 이미 {@code quantity > 0} 조건을 갖고 있어
     * 옮길 것이 없는 행(출고 · 폐기로 0 이 된 재고 행은 삭제하지 않고 남겨둔다)은
     * 자연히 빠진다. 자바에서 다시 걸러내지 않는다.
     *
     * @param binId 특정 구역의 재고만 볼 때 지정, 전체는 null
     */
    public List<InventoryDto> getMovableInventories(Long binId) {
        LocalDate today = LocalDate.now();

        return inventoryRepository.search(null, binId, null).stream()
                .map(inventory -> InventoryDto.of(inventory, today))
                .toList();
    }

    /* ==================================================================
     * 검증
     * ================================================================== */

    private void validateMovable(WarehouseBin fromBin,
                                 WarehouseBin toBin,
                                 Inventory source,
                                 int quantity) {

        if (fromBin.getBinId().equals(toBin.getBinId())) {
            throw new BusinessRuleException(
                    "출발 구역과 도착 구역이 같습니다. (" + fromBin.getBinCode() + ")");
        }

        // 사용 중지된 구역에서 빼내는 것은 허용한다. 막으면 재고를 옮길 수 없어 갇힌다.
        if (!toBin.isActive()) {
            throw new BusinessRuleException(
                    "사용 중지된 구역으로는 이동할 수 없습니다. (" + toBin.getBinCode() + ")");
        }

        int stored = Numbers.orZero(source.getQuantity());
        if (quantity > stored) {
            throw new BusinessRuleException(
                    "보관 수량보다 많이 이동할 수 없습니다. 구역 [" + fromBin.getBinCode() + "] 보관 "
                            + stored + "개 / 요청 " + quantity + "개");
        }

        validateBinCapacity(toBin, quantity);
    }

    /**
     * 도착 구역에 자리가 있는지 확인한다.
     * <p>
     * 판정 규칙은 {@code WarehouseBin} 이 갖고 있다. 입고 · 출고 취소 복구와 같은 규칙을
     * 써야 하므로 여기서 다시 구현하지 않는다.
     */
    private void validateBinCapacity(WarehouseBin toBin, int quantity) {
        // 재고가 한 건도 없는 구역은 sum() 이 null 을 반환하므로 0 으로 보정한다
        int currentLoad = (int) Numbers.orZero(inventoryRepository.sumQuantityByBinId(toBin.getBinId()));

        if (!toBin.canAccept(currentLoad, quantity)) {
            throw new BusinessRuleException(
                    "구역 [" + toBin.getBinCode() + "] 의 적재 한도를 초과합니다."
                            + " (현재 " + currentLoad + " + 이동 " + quantity
                            + " > 한도 " + toBin.capacityLimit() + ")");
        }
    }
}
