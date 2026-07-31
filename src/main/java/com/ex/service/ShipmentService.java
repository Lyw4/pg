package com.ex.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ex.entity.CustomerOrder;
import com.ex.entity.CustomerOrder.OrderStatus;
import com.ex.entity.OrderItem;
import com.ex.entity.ProductLot;
import com.ex.entity.Shipment;
import com.ex.entity.Shipment.ShipmentStatus;
import com.ex.entity.ShipmentItem;
import com.ex.entity.StockLog;
import com.ex.entity.StockLog.ChangeType;
import com.ex.repository.CustomerOrderRepository;
import com.ex.repository.DeliveryRepository;
import com.ex.repository.OrderItemRepository;
import com.ex.repository.ShipmentItemRepository;
import com.ex.repository.ShipmentRepository;
import com.ex.repository.StockLogRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShipmentService {
    private final ShipmentRepository shipmentRepository;
    private final ShipmentItemRepository shipmentItemRepository;
    private final CustomerOrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final StockLogRepository stockLogRepository;
    private final DeliveryRepository deliveryRepository;
    private final WarehouseFulfillmentService warehouseFulfillmentService;

    public List<Shipment> shipments() {
        return shipmentRepository.findAllByOrderByCreatedAtDesc();
    }

    public List<Shipment> activeShipments() {
        return shipments().stream()
                .filter(shipment -> shipment.getStatus() != ShipmentStatus.CANCELLED)
                .toList();
    }

    public List<Shipment> cancelledShipments() {
        return shipments().stream()
                .filter(shipment -> shipment.getStatus() == ShipmentStatus.CANCELLED)
                .toList();
    }

    public Map<Long, Shipment> shipmentByOrder() {
        Map<Long, Shipment> result = new HashMap<>();
        shipments().forEach(shipment ->
                result.put(shipment.getOrder().getOrderId(), shipment));
        return result;
    }

    public Map<Long, List<ShipmentItem>> itemsByShipment() {
        Map<Long, List<ShipmentItem>> result = new HashMap<>();
        shipments().forEach(shipment -> result.put(
                shipment.getShipmentId(),
                shipmentItemRepository.findByShipmentShipmentId(shipment.getShipmentId())));
        return result;
    }

    public long activeCount() {
        return shipmentRepository.countByStatusNotIn(
                List.of(ShipmentStatus.SHIPPED, ShipmentStatus.CANCELLED));
    }

    @Transactional
    public void create(Long orderId, String worker, String note) {
        if (shipmentRepository.findByOrderOrderId(orderId).isPresent()) {
            throw new IllegalStateException("이미 출고 지시가 생성된 주문입니다.");
        }
        CustomerOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다."));
        if (order.getStatus() != OrderStatus.PAID
                && order.getStatus() != OrderStatus.PREPARING) {
            throw new IllegalStateException("결제 완료 또는 준비 중 주문만 출고할 수 있습니다.");
        }
        List<OrderItem> orderItems = orderItemRepository.findByOrderOrderId(orderId);
        if (orderItems.isEmpty()) {
            throw new IllegalStateException("출고할 주문 상품이 없습니다.");
        }
        requireText(worker, "출고 담당자를 입력해 주세요.");
        Shipment shipment = shipmentRepository.save(
                new Shipment(order, worker.trim(), note));
        shipmentItemRepository.saveAll(orderItems.stream()
                .map(item -> new ShipmentItem(shipment, item)).toList());
        order.changeStatus(OrderStatus.PREPARING);
    }

    @Transactional
    public void startPicking(Long shipmentId, String worker) {
        requireText(worker, "피킹 담당자를 입력해 주세요.");
        find(shipmentId).startPicking(worker.trim());
    }

    @Transactional
    public void inspect(Long shipmentId, String worker) {
        requireText(worker, "검수 담당자를 입력해 주세요.");
        Shipment shipment = find(shipmentId);
        List<ShipmentItem> items =
                shipmentItemRepository.findByShipmentShipmentId(shipmentId);
        items.forEach(ShipmentItem::completePicking);
        shipment.inspect(worker.trim());
    }

    @Transactional
    public void complete(Long shipmentId, String worker) {
        requireText(worker, "출고 담당자를 입력해 주세요.");
        Shipment shipment = find(shipmentId);
        List<ShipmentItem> items =
                shipmentItemRepository.findByShipmentShipmentId(shipmentId);

        items.forEach(item -> {
            if (item.getPickedQuantity() != item.getPlannedQuantity()) {
                throw new IllegalStateException("피킹 수량 검수가 완료되지 않았습니다.");
            }
            if (item.getLot().getLotQuantity() < item.getPickedQuantity()) {
                throw new IllegalStateException(
                        "출고할 LOT 재고가 부족합니다: " + item.getLot().getLotNo());
            }
        });

        warehouseFulfillmentService.deductStock(
                shipment.getOrder(), items);

        items.forEach(item -> {
            ProductLot lot = item.getLot();
            int quantity = item.getPickedQuantity();
            lot.changeQuantity(-quantity);
            lot.getProduct().changeStock(-quantity);
            stockLogRepository.save(new StockLog(
                    lot, 1L, ChangeType.OUTBOUND, -quantity,
                    shipment.getShipmentNo() + " 출고"));
        });
        shipment.complete(worker.trim());
        shipment.getOrder().changeStatus(OrderStatus.SHIPPING);
    }

    @Transactional
    public void cancel(Long shipmentId, String note) {
        requireText(note, "취소 사유를 입력해 주세요.");
        Shipment shipment = find(shipmentId);
        shipment.cancel(note.trim());
        shipment.getOrder().changeStatus(OrderStatus.PAID);
    }

    @Transactional
    public void cancelCompleted(Long shipmentId, String note) {
        requireText(note, "출고 취소 사유를 입력해 주세요.");
        Shipment shipment = find(shipmentId);
        if (deliveryRepository.findByOrderOrderId(
                shipment.getOrder().getOrderId()).isPresent()) {
            throw new IllegalStateException(
                    "운송장이 등록된 출고는 먼저 배송 처리를 취소해야 합니다.");
        }
        List<ShipmentItem> items =
                shipmentItemRepository.findByShipmentShipmentId(shipmentId);
        items.forEach(item -> {
            int quantity = item.getPickedQuantity();
            item.getLot().changeQuantity(quantity);
            item.getProduct().changeStock(quantity);
            stockLogRepository.save(new StockLog(
                    item.getLot(), 1L, ChangeType.ADJUSTMENT, quantity,
                    shipment.getShipmentNo() + " 출고 취소: " + note.trim()));
        });
        warehouseFulfillmentService.restoreStock(
                shipment.getOrder(), items);
        shipment.cancelCompleted(note.trim());
        shipment.getOrder().changeStatus(OrderStatus.PAID);
    }

    private Shipment find(Long id) {
        return shipmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("출고 지시를 찾을 수 없습니다."));
    }

    private void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
