package com.ex.service;

import java.util.Comparator;
import java.util.List;

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

    private final WarehouseBinRepository binRepository;
    private final BinInventoryRepository inventoryRepository;
    private final WarehouseStockMovementRepository movementRepository;

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
                null);
    }

    @Transactional
    public void restore(
            ProductLot lot,
            int quantity,
            Warehouse preferredWarehouse,
            String memo,
            String operatorName,
            Long orderId) {
        add(
                lot,
                quantity,
                preferredWarehouse,
                MovementType.CANCEL_RESTORE,
                memo,
                operatorName,
                orderId);
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
                    null);
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

    private void add(
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
        List<WarehouseBin> bins = binRepository
                .findAllByOrderByWarehouseDisplayOrderAscBinCodeAsc()
                .stream()
                .filter(WarehouseBin::isActive)
                .filter(bin -> bin.getPurpose() == BinPurpose.STORAGE
                        || bin.getPurpose() == BinPurpose.RECEIVING)
                .sorted(preferredFirst(preferredWarehouse))
                .toList();
        if (bins.isEmpty()) {
            return;
        }

        WarehouseBin target = existingLocation(lot, preferredWarehouse)
                .filter(bin -> canAccept(bin, quantity))
                .orElseGet(() -> bins.stream()
                        .filter(bin -> canAccept(bin, quantity))
                        .findFirst()
                        .orElse(null));
        if (target == null) {
            return;
        }
        BinInventory inventory = inventoryRepository
                .findByLotLotIdAndBinBinId(
                        lot.getLotId(), target.getBinId())
                .orElseGet(() -> inventoryRepository.save(
                        new BinInventory(lot, target, 0)));
        inventory.add(quantity);
        movementRepository.save(new WarehouseStockMovement(
                type,
                lot,
                null,
                target,
                quantity,
                null,
                memo,
                operatorName,
                orderId));
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
        List<BinInventory> locations = inventoryRepository
                .findByLotLotIdAndQuantityGreaterThanOrderByBinBinCodeAsc(
                        lot.getLotId(), 0)
                .stream()
                .sorted(Comparator.comparingInt(inventory ->
                        isPreferred(
                                inventory.getBin(), preferredWarehouse)
                                ? 0
                                : 1))
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
    }

    private java.util.Optional<WarehouseBin> existingLocation(
            ProductLot lot,
            Warehouse preferredWarehouse) {
        return inventoryRepository
                .findByLotLotIdAndQuantityGreaterThanOrderByBinBinCodeAsc(
                        lot.getLotId(), 0)
                .stream()
                .sorted(Comparator.comparingInt(inventory ->
                        isPreferred(
                                inventory.getBin(), preferredWarehouse)
                                ? 0
                                : 1))
                .map(BinInventory::getBin)
                .findFirst();
    }

    private Comparator<WarehouseBin> preferredFirst(
            Warehouse preferredWarehouse) {
        return Comparator
                .comparingInt((WarehouseBin bin) ->
                        isPreferred(bin, preferredWarehouse) ? 0 : 1)
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

    private boolean canAccept(WarehouseBin bin, int quantity) {
        int current = inventoryRepository.findByBinBinId(bin.getBinId())
                .stream()
                .mapToInt(BinInventory::getQuantity)
                .sum();
        return bin.canAccept(current, quantity);
    }
}
