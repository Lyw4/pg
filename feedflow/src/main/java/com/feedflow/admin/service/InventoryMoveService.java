package com.feedflow.admin.service;

import com.feedflow.admin.dto.InventoryDto;
import com.feedflow.admin.dto.StockMoveForm;
import com.feedflow.admin.dto.StockMoveResultDto;
import com.feedflow.common.exception.BusinessRuleException;
import com.feedflow.common.exception.ResourceNotFoundException;
import com.feedflow.common.util.Numbers;
import com.feedflow.common.util.Texts;
import com.feedflow.domain.Center;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 재고 위치 이동 서비스 — 구역 간 이동(MOVE)과 센터 간 이관(TRANSFER).
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
 * <h3>같은 센터인가 다른 센터인가</h3>
 * 사용자에게는 이동 화면이 하나뿐이다. 도착 구역이 다른 센터면 서비스가 <b>이관</b>으로
 * 처리한다. 재고를 옮기는 절차는 같지만 <b>남기는 이력이 다르다.</b>
 * <pre>
 *   같은 센터 : MOVE 1건                        (sign 0, 총량 불변)
 *   다른 센터 : TRANSFER_OUT + TRANSFER_IN 2건   (-1 / +1, 두 건의 합이 0)
 * </pre>
 * {@code MOVE} 하나로 센터를 넘으면 "제1창고에서 나갔다" 와 "제2창고에 들어왔다" 를
 * 구분할 수 없다. 센터별 입출고 실적을 집계할 수 없고, 운송 중 상태를 표현할 자리도 없다.
 *
 * <h3>3계층 불변식</h3>
 * 이관도 {@code Inventory} 두 행의 합을 바꾸지 않으므로
 * {@code totalStock} = Σ{@code lotQuantity} = Σ{@code Inventory.quantity} 가 유지된다.
 * 운송 중 재고를 {@code Inventory} 밖에 두는 방식(전표 테이블만 사용)을 택하지 않은
 * 이유가 이것이다. 그 방식은 운송 중에 이 불변식이 깨져 재고 정합성 점검이 오탐한다.
 *
 * <h3>검증 규칙</h3>
 * <ol>
 *     <li>출발지와 도착지가 같을 수 없다.</li>
 *     <li>보관 중인 수량보다 많이 옮길 수 없다.</li>
 *     <li>사용 중지된 구역으로는 옮길 수 없다.
 *         단 <b>사용 중지된 구역에서 빼내는 것은 허용</b>한다. 구역을 비우는 작업이
 *         바로 사용 중지의 목적이므로 이를 막으면 재고가 갇힌다.</li>
 *     <li>도착 구역의 적재 한도를 넘을 수 없다.
 *         판정은 {@link BinCapacityChecker} 에 위임해 입고 · 출고 취소 복구와
 *         <b>같은 규칙과 같은 예외 문구</b>를 쓴다.</li>
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
    private final BinCapacityChecker binCapacityChecker;

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

        // 2) 도착 구역 (결과 화면이 위치 라벨에 센터명을 쓰므로 센터까지 함께 읽는다)
        WarehouseBin toBin = warehouseBinRepository.findWithCenterById(form.getTargetBinId())
                .orElseThrow(() -> ResourceNotFoundException.ofWarehouseBin(form.getTargetBinId()));

        validateMovable(fromBin, toBin, source, quantity);

        // 도착 구역의 전체 적재량. 한도 검증과 결과 표기(남은 여유)에 같은 값을 쓴다.
        int toBinLoadBefore = binCapacityChecker.checkCanAccept(toBin, quantity, "이동");

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

        // 5) 이력 기록
        //    같은 센터면 MOVE 한 건, 센터가 다르면 운송 중 구역을 경유해 두 건을 남긴다.
        boolean centerTransfer = isAcrossCenters(fromBin, toBin);
        WarehouseBin inTransitBin = centerTransfer
                ? recordTransfer(lot, fromBin, toBin, quantity, form.getMemo(), userId, userName)
                : recordMove(lot, fromBin, toBin, quantity, form.getMemo(), userId, userName);

        return StockMoveResultDto.builder()
                .productId(product.getProductId())
                .productCode(product.getProductCode())
                .productName(product.getName())
                .lotId(lot.getLotId())
                .lotNo(lot.getLotNo())
                .fromBinId(fromBin.getBinId())
                .fromBinCode(fromBin.getBinCode())
                .fromBinLocation(fromBin.locationLabel())
                .fromCenterName(fromBin.centerName())
                .fromQuantityBefore(fromQuantityBefore)
                .fromQuantityAfter(Numbers.orZero(source.getQuantity()))
                .toBinId(toBin.getBinId())
                .toBinCode(toBin.getBinCode())
                .toBinLocation(toBin.locationLabel())
                .toCenterName(toBin.centerName())
                .toQuantityBefore(toQuantityBefore)
                .toQuantityAfter(Numbers.orZero(target.getQuantity()))
                .toBinLoadAfter(toBinLoadBefore + quantity)
                .toCapacityLimit(toBin.capacityLimit())
                .movedQuantity(quantity)
                .lotQuantity(Numbers.orZero(lot.getLotQuantity()))
                .productTotalStock(Numbers.orZero(product.getTotalStock()))
                .targetCreated(targetCreated)
                .sourceDepleted(source.isEmpty())
                .centerTransfer(centerTransfer)
                .inTransitBinCode(inTransitBin == null ? null : inTransitBin.getBinCode())
                .build();
    }

    /* ------------------------------------------------------------------
     * 이력 기록
     * ------------------------------------------------------------------ */

    /** 같은 센터 안의 이동 — {@code MOVE} 한 건 */
    private WarehouseBin recordMove(ProductLot lot,
                                    WarehouseBin fromBin,
                                    WarehouseBin toBin,
                                    int quantity,
                                    String memo,
                                    Long userId,
                                    String userName) {
        stockMovementRepository.save(StockMovement.move(
                lot, fromBin, toBin, quantity, memo, userId, userName));
        return null;
    }

    /**
     * 센터 간 이관 — {@code TRANSFER_OUT} + {@code TRANSFER_IN} 두 건.
     *
     * <h3>왜 운송 중 구역을 경유하는가</h3>
     * 두 구간을 한 트랜잭션에서 처리하므로 운송 중 구역의 잔량은 <b>평상시 0</b> 이다.
     * 그래도 경유시키는 이유는 두 가지다.
     * <ol>
     *     <li><b>이력이 두 건으로 남는다.</b> "제1창고에서 나갔다" 와 "제2창고에 들어왔다" 가
     *         별개 이벤트가 되어 이력 추적 타임라인이 센터별 입출고를 정확히 보여준다.
     *         {@code MOVE} 한 건으로는 표현할 수 없었다.</li>
     *     <li><b>P3b 확장 지점이 된다.</b> 두 구간이 이미 분리되어 있으므로 실제 운송 중
     *         상태가 필요해지면 두 번째 구간을 나중에 호출하는 것으로 충분하다.</li>
     * </ol>
     *
     * <h3>불변식</h3>
     * 재고 수량은 이미 호출부에서 <b>출발 구역 → 도착 구역</b>으로 옮겨진 상태다.
     * 운송 중 구역에 실제로 수량을 넣었다 빼지는 않는다. 한 트랜잭션 안에서
     * 넣고 곧바로 빼면 결과가 같은데 {@code Inventory} 행만 하나 더 생기고
     * 낙관적 락 충돌 지점이 늘어난다.
     * <p>
     * 3계층 불변식은 이 방식에서도 <b>매 순간 성립</b>한다. 이관은 {@code Inventory}
     * 두 행의 합을 바꾸지 않고, 커밋 전 중간 상태는 트랜잭션 밖에서 관찰되지 않는다.
     * 운송 중 구역은 <b>P3b 에서 두 구간이 분리될 때 재고가 실제로 머무는 자리</b>이며,
     * 지금은 그 자리를 이력상 경유지로 기록한다.
     *
     * @return 경유한 운송 중 가상 구역 (결과 화면 표기용)
     */
    private WarehouseBin recordTransfer(ProductLot lot,
                                        WarehouseBin fromBin,
                                        WarehouseBin toBin,
                                        int quantity,
                                        String memo,
                                        Long userId,
                                        String userName) {

        // 운송 중 구역은 출발 센터 소속이다. 운송 중 재고는 아직 출발 센터의
        // 책임 아래 있고, 분실·파손 시 책임 소재도 그쪽이다.
        WarehouseBin inTransitBin = resolveInTransitBin(fromBin.getCenter());

        String transferMemo = buildTransferMemo(fromBin, toBin, memo);

        stockMovementRepository.save(StockMovement.transferOut(
                lot, fromBin, inTransitBin, quantity, transferMemo, userId, userName));
        stockMovementRepository.save(StockMovement.transferIn(
                lot, inTransitBin, toBin, quantity, transferMemo, userId, userName));

        return inTransitBin;
    }

    /**
     * 이관 메모에 출발·도착 센터를 남긴다.
     * <p>
     * 이력 두 건은 각자 한쪽 센터만 알고 있다. {@code TRANSFER_OUT} 만 보면
     * "어디로 갔는지" 를, {@code TRANSFER_IN} 만 보면 "어디서 왔는지" 를 알 수 없다.
     * 두 건이 같은 이관임을 이어주는 전표 번호가 없으므로(P3a 범위) 메모로 잇는다.
     */
    private String buildTransferMemo(WarehouseBin fromBin, WarehouseBin toBin, String memo) {
        String route = "[센터 이관] " + fromBin.centerName() + " " + fromBin.getBinCode()
                + " → " + toBin.centerName() + " " + toBin.getBinCode();

        return Texts.isBlank(memo) ? route : route + " · " + memo;
    }

    /**
     * 센터의 운송 중 가상 구역을 가져온다. 없으면 만든다.
     * <p>
     * 센터는 운영 중에 늘어난다. 센터를 만들 때마다 사람이 가상 구역을 함께 만들게 하면
     * 반드시 빠뜨리고, 그러면 <b>첫 이관 시점에 실패</b>한다. 시스템이 규칙에 맞는
     * 코드로 자동 생성해 그 실패 가능성을 없앤다.
     */
    private WarehouseBin resolveInTransitBin(Center center) {
        return warehouseBinRepository.findInTransitBin(center.getCenterId())
                .orElseGet(() -> warehouseBinRepository.save(
                        WarehouseBin.createInTransit(center)));
    }

    /**
     * 두 구역이 서로 다른 센터에 속하는지.
     * <p>
     * 센터는 {@code optional = false} 라 null 이 될 수 없지만, 단위 테스트에서
     * 센터를 지정하지 않은 픽스처를 쓸 수 있어 방어한다. 센터를 알 수 없으면
     * 센터 간 이관으로 취급하지 않는다 — 알 수 없는 상태를 이관으로 단정하면
     * 실제로는 같은 센터인 이동이 두 건의 이력으로 부풀려진다.
     */
    private boolean isAcrossCenters(WarehouseBin fromBin, WarehouseBin toBin) {
        Long fromCenterId = fromBin.centerId();
        Long toCenterId = toBin.centerId();

        return fromCenterId != null && toCenterId != null && !fromCenterId.equals(toCenterId);
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
     * <h3>정렬이 재고 현황 화면과 다르다</h3>
     * 재고 현황 목록은 <b>유통기한 순</b>이다. FEFO 출고 순서를 확인하는 화면이기 때문이다.
     * 이동 화면은 목적이 다르다 — 사용자는 "이 구역의 저 재고를 옮기겠다" 는 의도를 갖고
     * 들어오므로 <b>구역을 빨리 찾는 것</b>이 중요하다.
     * 그래서 센터 → 구역 코드 → 유통기한 순으로 정렬한다.
     * (같은 구역에 여러 로트가 있으면 그 안에서는 급한 것부터)
     *
     * @param binId 특정 구역의 재고만 볼 때 지정, 전체는 null
     */
    public List<InventoryDto> getMovableInventories(Long binId) {
        LocalDate today = LocalDate.now();

        // 구역이 이미 센터를 결정하므로 센터 조건은 필요 없다 (구역 하나는 센터 하나에 속한다)
        return inventoryRepository.search(null, null, binId, null).stream()
                .map(inventory -> InventoryDto.of(inventory, today))
                .sorted(MOVABLE_ORDER)
                .toList();
    }

    /**
     * 이동 화면 선택 목록의 정렬 기준.
     * <p>
     * Repository 의 {@code search} 정렬(유통기한 우선)을 바꾸지 않고 여기서 다시 세운다.
     * {@code search} 는 재고 현황 목록도 함께 쓰는데 그 화면은 FEFO 순서가 핵심이라
     * 쿼리 정렬을 바꾸면 다른 화면이 망가진다.
     * <p>
     * 센터명이 null 인 경우(단위 테스트 픽스처)를 뒤로 보내 {@code NullPointerException} 을 막는다.
     */
    private static final Comparator<InventoryDto> MOVABLE_ORDER =
            Comparator.comparing(InventoryDto::getCenterName,
                            Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(InventoryDto::getBinCode,
                            Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(InventoryDto::getExpirationDate,
                            Comparator.nullsLast(Comparator.naturalOrder()));

    /**
     * 이동 가능한 재고를 <b>센터별로 묶은</b> 선택 목록.
     * <p>
     * 화면에서 {@code <optgroup>} 으로 렌더링해 센터 경계를 눈으로 구분할 수 있게 한다.
     * 묶지 않으면 제1창고의 {@code C-01} 과 제2창고의 {@code N-01} 이 섞여 나와
     * <b>어느 센터의 재고를 옮기는지 모른 채 이관을 일으킬 수 있다.</b>
     * 도착 구역 목록과 같은 형태로 맞춰 두 select 를 나란히 읽을 수 있게 한다.
     * <p>
     * 정렬 순서를 유지해야 하므로 {@link LinkedHashMap} 으로 모은다.
     */
    public Map<String, List<InventoryDto>> getMovableInventoriesByCenter(Long binId) {
        return getMovableInventories(binId).stream()
                .collect(Collectors.groupingBy(
                        dto -> Texts.defaultIfBlank(dto.getCenterName(), "센터 미지정"),
                        LinkedHashMap::new,
                        Collectors.toList()));
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

        // 운송 중 가상 구역은 이관 로직만 다룰 수 있다.
        // 화면 선택 목록에서 이미 제외했지만, 요청을 직접 조립하면 통과할 수 있으므로
        // 서비스에서도 막는다. 사용자가 여기에 재고를 넣으면 어느 센터에서도
        // 팔 수 없는 상태로 갇힌다.
        if (toBin.isInTransit()) {
            throw new BusinessRuleException(
                    "운송 중 구역으로는 직접 이동할 수 없습니다. (" + toBin.getBinCode() + ")"
                            + " 센터 간 이관은 도착 센터의 구역을 선택하면 자동으로 처리됩니다.");
        }

        int stored = Numbers.orZero(source.getQuantity());
        if (quantity > stored) {
            throw new BusinessRuleException(
                    "보관 수량보다 많이 이동할 수 없습니다. 구역 [" + fromBin.getBinCode() + "] 보관 "
                            + stored + "개 / 요청 " + quantity + "개");
        }
    }

}
