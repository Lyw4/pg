package com.ex.service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ex.entity.BinInventory;
import com.ex.entity.BinPurpose;
import com.ex.entity.MovementType;
import com.ex.entity.ProductLot;
import com.ex.entity.Warehouse;
import com.ex.entity.WarehouseBin;
import com.ex.entity.WarehouseStockMovement;
import com.ex.repository.BinInventoryRepository;
import com.ex.repository.ProductLotRepository;
import com.ex.repository.WarehouseBinRepository;
import com.ex.repository.WarehouseStockMovementRepository;

import lombok.RequiredArgsConstructor;

/**
 * 기존 재고·주문·배송 서비스가 LOT 수량을 바꿀 때 구역 재고와 이동 이력을
 * 같은 트랜잭션 안에서 맞춰 주는 연결 계층입니다.
 */
@Service
@RequiredArgsConstructor
public class WmsStockCoordinator {

    private static final List<BinPurpose> INBOUND_TARGETS =
            List.of(BinPurpose.STORAGE, BinPurpose.RECEIVING);
    private static final List<BinPurpose> RETURN_TARGETS =
            List.of(BinPurpose.STORAGE, BinPurpose.SHIPPING);

    private final WarehouseBinRepository binRepository;
    private final BinInventoryRepository inventoryRepository;
    private final WarehouseStockMovementRepository movementRepository;
    private final ProductLotRepository lotRepository;

    @Transactional
    public void inbound(
            ProductLot lot,
            int quantity,
            Warehouse preferredWarehouse,
            String memo,
            String operatorName) {
        add(
                lot,
                quantity,
                preferredWarehouse,
                MovementType.INBOUND,
                memo,
                operatorName,
                null,
                INBOUND_TARGETS);
    }

    @Transactional
    public void restore(
            ProductLot lot,
            int quantity,
            Warehouse preferredWarehouse,
            String memo,
            String operatorName,
            Long orderId) {
        int remaining = restoreToOriginalBins(
                lot, quantity, memo, operatorName, orderId);
        if (remaining > 0) {
            add(
                    lot,
                    remaining,
                    preferredWarehouse,
                    MovementType.CANCEL_RESTORE,
                    memo,
                    operatorName,
                    orderId,
                    RETURN_TARGETS);
        }
    }

    private int restoreToOriginalBins(
            ProductLot lot,
            int quantity,
            String memo,
            String operatorName,
            Long orderId) {
        if (quantity <= 0 || orderId == null) return Math.max(0, quantity);
        List<WarehouseStockMovement> outboundMovements = movementRepository
                .findByOrderIdAndMovementTypeAndLotLotIdOrderByCreatedAtAsc(
                        orderId, MovementType.OUTBOUND, lot.getLotId())
                .stream()
                .filter(movement -> movement.getSourceBin() != null)
                .toList();
        if (outboundMovements.isEmpty()) return quantity;

        List<WarehouseBin> lockedBins = binRepository.findAllByBinIdInForUpdate(
                outboundMovements.stream()
                        .map(movement -> movement.getSourceBin().getBinId())
                        .distinct().toList());
        Map<Long, WarehouseBin> binsById = lockedBins.stream()
                .collect(java.util.stream.Collectors.toMap(
                        WarehouseBin::getBinId, bin -> bin));
        Map<Long, Integer> binQuantities = quantitiesByBin(lockedBins);
        int remaining = quantity;
        for (WarehouseStockMovement outbound : outboundMovements) {
            if (remaining == 0) break;
            WarehouseBin target = binsById.get(
                    outbound.getSourceBin().getBinId());
            if (target == null || !WmsAllocationPolicy.isAllocatable(target)
                    || !matchesAnimalZone(target, lot)) {
                continue;
            }
            int current = binQuantities.getOrDefault(target.getBinId(), 0);
            int accepted = Math.min(
                    Math.min(remaining, outbound.getQuantity()),
                    Math.max(0, target.getEffectiveMaxCapacity() - current));
            if (accepted == 0) continue;
            BinInventory inventory = inventoryRepository
                    .findByLotLotIdAndBinBinId(lot.getLotId(), target.getBinId())
                    .orElseGet(() -> inventoryRepository.save(
                            new BinInventory(lot, target, 0)));
            inventory.add(accepted);
            binQuantities.put(target.getBinId(), current + accepted);
            movementRepository.save(new WarehouseStockMovement(
                    MovementType.CANCEL_RESTORE,
                    lot,
                    null,
                    target,
                    accepted,
                    null,
                    memo,
                    operatorName,
                    orderId));
            remaining -= accepted;
        }
        return remaining;
    }

    @Transactional
    public void adjust(
            ProductLot lot,
            int changedQuantity,
            Warehouse preferredWarehouse,
            String memo,
            String operatorName) {
        if (changedQuantity > 0) {
            add(
                    lot,
                    changedQuantity,
                    preferredWarehouse,
                    MovementType.ADJUSTMENT,
                    memo,
                    operatorName,
                    null,
                    INBOUND_TARGETS);
        } else if (changedQuantity < 0) {
            subtract(
                    lot,
                    -changedQuantity,
                    preferredWarehouse,
                    MovementType.ADJUSTMENT,
                    memo,
                    operatorName,
                    null);
        }
    }

    @Transactional
    public void outbound(
            ProductLot lot,
            int quantity,
            Warehouse preferredWarehouse,
            String memo,
            String operatorName,
            Long orderId) {
        subtract(
                lot,
                quantity,
                preferredWarehouse,
                MovementType.OUTBOUND,
                memo,
                operatorName,
                orderId);
    }

    /** 동일 LOT의 총수량은 유지하면서 두 센터의 실제 구역 재고를 이동합니다. */
    @Transactional
    public void transferProduct(
            Long productId,
            Warehouse sourceWarehouse,
            Warehouse destinationWarehouse,
            int quantity,
            String operatorName) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("센터 이동 수량은 1포대 이상이어야 합니다.");
        }
        if (sourceWarehouse.getWarehouseId().equals(
                destinationWarehouse.getWarehouseId())) {
            throw new IllegalArgumentException("서로 다른 센터를 선택해 주세요.");
        }
        // 잠금 순서는 ProductLot → BinInventory 순서로 고정한다.
        lotRepository
                .findByProductProductIdAndLotQuantityGreaterThanOrderByExpirationDateAsc(
                        productId, 0);
        List<BinInventory> sources = inventoryRepository
                .findByLotProductProductIdAndQuantityGreaterThanOrderByBinBinCodeAsc(
                        productId, 0).stream()
                .filter(inventory -> inventory.getBin().getWarehouse()
                        .getWarehouseId().equals(sourceWarehouse.getWarehouseId()))
                .filter(inventory -> WmsAllocationPolicy.isAllocatable(
                        inventory.getBin()))
                .filter(inventory -> !inventory.getLot().getExpirationDate()
                        .isBefore(LocalDate.now().plusDays(
                                SellableStockQuery.MINIMUM_SELLABLE_DAYS)))
                .sorted(Comparator.comparing(inventory ->
                        inventory.getLot().getExpirationDate()))
                .toList();
        int available = sources.stream().mapToInt(BinInventory::getQuantity).sum();
        if (available < quantity) {
            throw new IllegalStateException(
                    sourceWarehouse.getName() + "의 실제 구역 재고가 부족합니다.");
        }
        List<WarehouseBin> targets = binRepository
                .findByWarehouseWarehouseIdAndActiveTrueOrderByBinCodeAsc(
                        destinationWarehouse.getWarehouseId()).stream()
                .filter(bin -> bin.getPurpose() == BinPurpose.STORAGE)
                .filter(bin -> sources.stream().anyMatch(source ->
                        matchesAnimalZone(bin, source.getLot())))
                .toList();
        targets = binRepository.findAllByBinIdInForUpdate(
                        targets.stream().map(WarehouseBin::getBinId).toList())
                .stream()
                .sorted(Comparator.comparing(WarehouseBin::getBinCode))
                .toList();
        Map<Long, Integer> targetQuantities = quantitiesByBin(targets);
        int capacity = targets.stream().mapToInt(bin -> Math.max(
                0, bin.getEffectiveMaxCapacity()
                        - targetQuantities.getOrDefault(bin.getBinId(), 0)))
                .sum();
        if (capacity < quantity) {
            throw new IllegalStateException(
                    destinationWarehouse.getName() + "의 입고 가능 공간이 부족합니다.");
        }

        int remaining = quantity;
        for (BinInventory source : sources) {
            int sourceRemaining = Math.min(remaining, source.getQuantity());
            while (sourceRemaining > 0) {
                WarehouseBin target = targets.stream()
                        .filter(bin -> canAccept(
                                bin,
                                targetQuantities.getOrDefault(bin.getBinId(), 0),
                                1))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException(
                                "이동 중 대상 센터의 구역 용량이 부족해졌습니다."));
                int current = targetQuantities.getOrDefault(target.getBinId(), 0);
                int movable = Math.min(sourceRemaining,
                        target.getEffectiveMaxCapacity() - current);
                source.subtract(movable);
                BinInventory destination = inventoryRepository
                        .findByLotLotIdAndBinBinId(
                                source.getLot().getLotId(), target.getBinId())
                        .orElseGet(() -> inventoryRepository.save(
                                new BinInventory(source.getLot(), target, 0)));
                destination.add(movable);
                targetQuantities.put(target.getBinId(), current + movable);
                String memo = sourceWarehouse.getName() + " → "
                        + destinationWarehouse.getName() + " 자동 재고 이동";
                movementRepository.save(new WarehouseStockMovement(
                        MovementType.TRANSFER_OUT, source.getLot(),
                        source.getBin(), null, movable, null,
                        memo, operatorName, null));
                movementRepository.save(new WarehouseStockMovement(
                        MovementType.TRANSFER_IN, source.getLot(),
                        null, target, movable, null,
                        memo, operatorName, null));
                sourceRemaining -= movable;
                remaining -= movable;
            }
            if (remaining == 0) return;
        }
    }

    private void add(
            ProductLot lot,
            int quantity,
            Warehouse preferredWarehouse,
            MovementType type,
            String memo,
            String operatorName,
            Long orderId,
            List<BinPurpose> allowedPurposes) {
        if (quantity <= 0) {
            return;
        }
        List<WarehouseBin> bins = binRepository
                .findAllByOrderByWarehouseDisplayOrderAscBinCodeAsc()
                .stream()
                .filter(WarehouseBin::isActive)
                .filter(bin -> allowedPurposes.contains(bin.getPurpose()))
                .filter(bin -> preferredWarehouse == null
                        || isPreferred(bin, preferredWarehouse))
                .filter(bin -> matchesAnimalZone(bin, lot))
                .sorted(preferredFirst(preferredWarehouse, allowedPurposes))
                .toList();
        if (bins.isEmpty()) {
            throw new IllegalStateException(
                    "입고 가능한 구역이 없습니다. LOT=" + lot.getLotNo()
                            + ", 수량=" + quantity
                            + " (활성 보관 또는 입고 대기 구역을 확보하세요.)");
        }

        List<WarehouseBin> lockedBins = binRepository
                .findAllByBinIdInForUpdate(
                        bins.stream().map(WarehouseBin::getBinId).toList())
                .stream()
                .sorted(preferredFirst(preferredWarehouse, allowedPurposes))
                .toList();
        Map<Long, Integer> binQuantities = quantitiesByBin(lockedBins);
        int totalCapacity = lockedBins.stream()
                .mapToInt(bin -> Math.max(0,
                        bin.getEffectiveMaxCapacity()
                                - binQuantities.getOrDefault(bin.getBinId(), 0)))
                .sum();
        if (totalCapacity < quantity) {
            throw new IllegalStateException(
                    "입고 구역 용량이 부족합니다. LOT=" + lot.getLotNo()
                            + ", 수량=" + quantity
                            + " (적재 공간을 확보한 뒤 다시 시도하세요.)");
        }
        int remaining = quantity;
        for (WarehouseBin target : lockedBins) {
            int current = binQuantities.getOrDefault(target.getBinId(), 0);
            int accepted = Math.min(remaining,
                    Math.max(0, target.getEffectiveMaxCapacity() - current));
            if (accepted <= 0) continue;
            BinInventory inventory = inventoryRepository
                    .findByLotLotIdAndBinBinId(
                            lot.getLotId(), target.getBinId())
                    .orElseGet(() -> inventoryRepository.save(
                            new BinInventory(lot, target, 0)));
            inventory.add(accepted);
            movementRepository.save(new WarehouseStockMovement(
                    type, lot, null, target, accepted, null,
                    memo, operatorName, orderId));
            remaining -= accepted;
            if (remaining == 0) return;
        }
    }

    private void subtract(
            ProductLot lot,
            int quantity,
            Warehouse preferredWarehouse,
            MovementType type,
            String memo,
            String operatorName,
            Long orderId) {
        if (quantity <= 0) {
            return;
        }
        if (type == MovementType.OUTBOUND
                && lot.getExpirationDate().isBefore(
                        java.time.LocalDate.now().plusDays(
                                ExpirySaleService.MINIMUM_SELLABLE_DAYS))) {
            throw new IllegalStateException(
                    "유통기한이 임박하거나 만료된 LOT는 출고할 수 없습니다: "
                            + lot.getLotNo());
        }
        List<BinInventory> locations = inventoryRepository
                .findByLotLotIdAndQuantityGreaterThanOrderByBinBinCodeAsc(
                        lot.getLotId(), 0)
                .stream()
                .filter(inventory -> WmsAllocationPolicy
                        .isAllocatable(inventory.getBin()))
                .filter(inventory -> preferredWarehouse == null
                        || isPreferred(inventory.getBin(), preferredWarehouse))
                .sorted(Comparator
                        .comparingInt((BinInventory inventory) ->
                                isPreferred(inventory.getBin(), preferredWarehouse)
                                        ? 0 : 1)
                        .thenComparing(inventory -> inventory.getLot()
                                .getExpirationDate())
                        .thenComparing(inventory -> inventory.getBin()
                                .getBinCode()))
                .toList();
        int remaining = quantity;
        for (BinInventory inventory : locations) {
            int deduction = Math.min(remaining, inventory.getQuantity());
            if (deduction == 0) {
                continue;
            }
            inventory.subtract(deduction);
            movementRepository.save(new WarehouseStockMovement(
                    type,
                    lot,
                    inventory.getBin(),
                    null,
                    deduction,
                    null,
                    memo,
                    operatorName,
                    orderId));
            remaining -= deduction;
            if (remaining == 0) {
                return;
            }
        }
        if (remaining > 0) {
            throw new IllegalStateException(
                    "출고 가능한 구역 재고가 부족합니다. LOT=" + lot.getLotNo()
                            + ", 요청=" + quantity + ", 부족=" + remaining
                            + " (입고 대기·검수·운송 중 구역의 재고는 출고할 수 없습니다. "
                            + "구역 이동으로 보관 구역에 넣은 뒤 다시 시도하세요.)");
        }
    }

    private java.util.Optional<WarehouseBin> existingLocation(
            ProductLot lot,
            Warehouse preferredWarehouse,
            List<BinPurpose> allowedPurposes) {
        return inventoryRepository
                .findByLotLotIdAndQuantityGreaterThanOrderByBinBinCodeAsc(
                        lot.getLotId(), 0)
                .stream()
                .filter(inventory -> inventory.getBin().isActive())
                .filter(inventory -> allowedPurposes.contains(
                        inventory.getBin().getPurpose()))
                .filter(inventory -> preferredWarehouse == null
                        || isPreferred(inventory.getBin(), preferredWarehouse))
                .sorted(Comparator
                        .comparingInt((BinInventory inventory) ->
                                isPreferred(inventory.getBin(), preferredWarehouse)
                                        ? 0 : 1)
                        .thenComparingInt(inventory -> purposeRank(
                                inventory.getBin().getPurpose(),
                                allowedPurposes))
                        .thenComparing(inventory -> inventory.getBin()
                                .getBinCode()))
                .map(BinInventory::getBin)
                .findFirst();
    }

    private Comparator<WarehouseBin> preferredFirst(
            Warehouse preferredWarehouse,
            List<BinPurpose> allowedPurposes) {
        return Comparator
                .comparingInt((WarehouseBin bin) ->
                        isPreferred(bin, preferredWarehouse) ? 0 : 1)
                .thenComparingInt(bin -> purposeRank(
                        bin.getPurpose(), allowedPurposes))
                .thenComparing(bin ->
                        bin.getWarehouse().getDisplayOrder())
                .thenComparing(WarehouseBin::getBinCode);
    }

    private boolean isPreferred(
            WarehouseBin bin,
            Warehouse warehouse) {
        return warehouse != null
                && bin.getWarehouse().getWarehouseId()
                        .equals(warehouse.getWarehouseId());
    }

    private int purposeRank(
            BinPurpose purpose,
            List<BinPurpose> allowedPurposes) {
        int index = allowedPurposes.indexOf(purpose);
        return index < 0 ? Integer.MAX_VALUE : index;
    }

    private Map<Long, Integer> quantitiesByBin(List<WarehouseBin> bins) {
        Map<Long, Integer> quantities = new HashMap<>();
        if (bins.isEmpty()) return quantities;
        inventoryRepository.sumQuantityByBinIds(
                        bins.stream().map(WarehouseBin::getBinId).toList())
                .forEach(row -> quantities.put(
                        ((Number) row[0]).longValue(),
                        ((Number) row[1]).intValue()));
        return quantities;
    }

    private boolean canAccept(
            WarehouseBin bin,
            int current,
            int quantity) {
        return bin.canAccept(current, quantity);
    }

    private boolean matchesAnimalZone(WarehouseBin bin, ProductLot lot) {
        return WmsZonePolicy.matches(bin, lot);
    }
}
