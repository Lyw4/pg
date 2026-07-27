package com.feedflow.admin.service;

import com.feedflow.admin.dto.InboundForm;
import com.feedflow.admin.dto.InboundResultDto;
import com.feedflow.admin.dto.InventoryDto;
import com.feedflow.admin.dto.StockMovementDto;
import com.feedflow.common.exception.BusinessRuleException;
import com.feedflow.common.exception.ResourceNotFoundException;
import com.feedflow.domain.Inventory;
import com.feedflow.domain.MovementType;
import com.feedflow.domain.Product;
import com.feedflow.domain.ProductLot;
import com.feedflow.domain.StockMovement;
import com.feedflow.domain.WarehouseBin;
import com.feedflow.repository.InventoryRepository;
import com.feedflow.repository.ProductLotRepository;
import com.feedflow.repository.ProductRepository;
import com.feedflow.repository.StockMovementRepository;
import com.feedflow.repository.WarehouseBinRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 재고 입고(Inbound) 및 재고 현황 서비스.
 *
 * <h3>입고 처리 규칙</h3>
 * <ol>
 *     <li>사용 중지된 품목 / 구역으로는 입고할 수 없다.</li>
 *     <li>로트번호를 입력하지 않으면 자동으로 부여한다. (L{yyMMdd}-{품목코드}-{순번})</li>
 *     <li>유통기한은 입력받지 않고 <b>제조일자 + 품목의 유통기한 일수</b> 로 자동 계산한다.</li>
 *     <li>같은 품목에 동일한 로트번호가 이미 있으면 새 로트를 만들지 않고 수량을 합산한다.</li>
 *     <li>같은 로트가 같은 구역에 다시 들어오면 재고 행을 추가하지 않고 수량을 합산(update)하고,
 *         새로운 구역이면 재고 행을 새로 생성(insert)한다.</li>
 *     <li>구역의 최대 적재 수량을 초과할 수 없다.</li>
 *     <li>품목의 전체 재고(totalStock)를 증가시키고 입고 이력을 남긴다.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryService {

    private static final DateTimeFormatter LOT_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyMMdd");

    private final ProductRepository productRepository;
    private final ProductLotRepository productLotRepository;
    private final WarehouseBinRepository warehouseBinRepository;
    private final InventoryRepository inventoryRepository;
    private final StockMovementRepository stockMovementRepository;

    /* ==================================================================
     * 입고 처리
     * ================================================================== */

    /**
     * 사료 입고 처리.
     *
     * @param form     입고 요청 (품목, 구역, 로트번호(optional), 제조일자, 수량)
     * @param userId   처리자 ID (이력 스냅샷용, null 허용)
     * @param userName 처리자 이름 (이력 스냅샷용, null 허용)
     * @return 입고 결과 (부여된 로트번호, 자동 계산된 유통기한, 갱신된 수량)
     */
    @Transactional
    public InboundResultDto receive(InboundForm form, Long userId, String userName) {

        int quantity = form.getQuantity() == null ? 0 : form.getQuantity();
        if (quantity <= 0) {
            throw new BusinessRuleException("입고 수량은 1 이상이어야 합니다.");
        }

        // 1) 기준 정보 조회 및 사용 여부 검증
        Product product = productRepository.findById(form.getProductId())
                .orElseThrow(() -> ResourceNotFoundException.ofProduct(form.getProductId()));
        if (!product.isActive()) {
            throw new BusinessRuleException(
                    "사용 중지된 품목에는 입고할 수 없습니다. (" + product.getProductCode() + ")");
        }

        WarehouseBin bin = warehouseBinRepository.findById(form.getBinId())
                .orElseThrow(() -> ResourceNotFoundException.ofWarehouseBin(form.getBinId()));
        if (!bin.isActive()) {
            throw new BusinessRuleException(
                    "사용 중지된 구역에는 입고할 수 없습니다. (" + bin.getBinCode() + ")");
        }

        // 2) 구역 적재 용량 검증
        validateBinCapacity(bin, quantity);

        // 3) 로트 결정 : 기존 로트에 합산하거나 새 로트를 생성 (유통기한 자동 계산)
        String lotNo = resolveLotNo(form.getLotNo(), product, form.getManufacturedDate());

        ProductLot lot = productLotRepository
                .findByProduct_ProductIdAndLotNo(product.getProductId(), lotNo)
                .orElse(null);

        boolean newLot = (lot == null);
        if (newLot) {
            lot = productLotRepository.save(
                    ProductLot.createForInbound(product, lotNo, form.getManufacturedDate(), quantity));
        } else {
            lot.addQuantity(quantity);
        }

        // 4) 구역 재고 : 동일 로트 + 동일 구역이면 합산(update), 아니면 신규 생성(insert)
        Inventory inventory = inventoryRepository
                .findByLot_LotIdAndBin_BinId(lot.getLotId(), bin.getBinId())
                .orElse(null);

        boolean newInventory = (inventory == null);
        if (newInventory) {
            inventory = inventoryRepository.save(Inventory.createForInbound(lot, bin, quantity));
        } else {
            inventory.addQuantity(quantity);
        }

        // 5) 품목 전체 재고 증가
        product.increaseStock(quantity);

        // 6) 입고 이력 기록
        stockMovementRepository.save(
                StockMovement.inbound(lot, bin, quantity, form.getMemo(), userId, userName));

        return InboundResultDto.builder()
                .lotId(lot.getLotId())
                .lotNo(lot.getLotNo())
                .productCode(product.getProductCode())
                .productName(product.getName())
                .binCode(bin.getBinCode())
                .manufacturedDate(lot.getManufacturedDate())
                .expirationDate(lot.getExpirationDate())
                .quantity(quantity)
                .binQuantity(inventory.getQuantity())
                .lotQuantity(lot.getLotQuantity())
                .productTotalStock(product.getTotalStock())
                .newLot(newLot)
                .newInventory(newInventory)
                .expiredLot(lot.isExpired(LocalDate.now()))
                .build();
    }

    /* ==================================================================
     * 조회
     * ================================================================== */

    /** 재고 현황 (로트 × 구역) */
    public List<InventoryDto> getInventories(Long productId, Long binId, String zone) {
        LocalDate today = LocalDate.now();
        return inventoryRepository.search(productId, binId, emptyToNull(zone)).stream()
                .map(inventory -> InventoryDto.of(inventory, today))
                .toList();
    }

    /** 입·출고 이력 */
    public Page<StockMovementDto> getMovements(MovementType movementType,
                                               Long productId,
                                               Pageable pageable) {
        return stockMovementRepository.search(movementType, productId, pageable)
                .map(StockMovementDto::from);
    }

    /** 오늘 입고 건수 */
    public long getTodayInboundCount() {
        LocalDate today = LocalDate.now();
        return stockMovementRepository.countByMovementTypeAndCreatedAtBetween(
                MovementType.INBOUND, today.atStartOfDay(), today.atTime(LocalTime.MAX));
    }

    /** 오늘 입고 수량 */
    public long getTodayInboundQuantity() {
        LocalDate today = LocalDate.now();
        Long sum = stockMovementRepository.sumQuantityByTypeBetween(
                MovementType.INBOUND, today.atStartOfDay(), today.atTime(LocalTime.MAX));
        return sum == null ? 0L : sum;
    }

    /** 재고가 있는 (로트 × 구역) 건수 */
    public long getStockedLocationCount() {
        return inventoryRepository.countWithStock();
    }

    /** 전체 보관 수량 */
    public long getTotalStoredQuantity() {
        Long sum = inventoryRepository.sumAllQuantity();
        return sum == null ? 0L : sum;
    }

    /* ==================================================================
     * 내부 로직
     * ================================================================== */

    /**
     * 로트번호 결정.
     * 입력값이 있으면 대문자로 정규화해서 사용하고, 없으면 자동 생성한다.
     * <p>
     * 자동 생성 규칙 : L{제조일 yyMMdd}-{품목코드}-{같은 날짜 순번 2자리}
     */
    private String resolveLotNo(String requestedLotNo, Product product, LocalDate manufacturedDate) {
        if (requestedLotNo != null && !requestedLotNo.isBlank()) {
            return requestedLotNo.trim().toUpperCase();
        }
        return generateLotNo(product, manufacturedDate);
    }

    private String generateLotNo(Product product, LocalDate manufacturedDate) {
        long sequence = productLotRepository
                .countByProduct_ProductIdAndManufacturedDate(product.getProductId(), manufacturedDate) + 1;

        return "L" + manufacturedDate.format(LOT_DATE_FORMATTER)
                + "-" + product.getProductCode()
                + "-" + String.format("%02d", sequence);
    }

    /** 구역 적재 용량 초과 검증 */
    private void validateBinCapacity(WarehouseBin bin, int quantity) {
        Long stored = inventoryRepository.sumQuantityByBinId(bin.getBinId());
        int currentQuantity = (stored == null) ? 0 : stored.intValue();
        int maxCapacity = bin.getMaxCapacity() == null ? 0 : bin.getMaxCapacity();

        if (currentQuantity + quantity > maxCapacity) {
            throw new BusinessRuleException(
                    "구역 [" + bin.getBinCode() + "] 의 최대 적재 수량을 초과합니다."
                            + " (현재 " + currentQuantity + " + 입고 " + quantity
                            + " > 최대 " + maxCapacity + ")");
        }
    }

    private String emptyToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
