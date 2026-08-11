package com.ex.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ex.entity.CustomerOrder;
import com.ex.entity.CustomerOrder.OrderStatus;
import com.ex.entity.Delivery;
import com.ex.entity.Delivery.DeliveryStatus;
import com.ex.entity.DefectRecord.DefectType;
import com.ex.entity.DeliveryStatusHistory;
import com.ex.entity.FarmCustomer.CustomerStatus;
import com.ex.entity.OrderItem;
import com.ex.entity.ProductLot;
import com.ex.entity.Shipment;
import com.ex.entity.ShipmentItem;
import com.ex.entity.Shipment.ShipmentStatus;
import com.ex.entity.StockLog;
import com.ex.entity.StockLog.ChangeType;
import com.ex.repository.CustomerOrderRepository;
import com.ex.repository.DeliveryRepository;
import com.ex.repository.DeliveryStatusHistoryRepository;
import com.ex.repository.FarmCustomerRepository;
import com.ex.repository.OrderItemRepository;
import com.ex.repository.ProductLotRepository;
import com.ex.repository.ShipmentItemRepository;
import com.ex.repository.ShipmentRepository;
import com.ex.repository.StockLogRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DistributionService {

	private final CustomerOrderRepository orderRepository;
	private final OrderItemRepository orderItemRepository;
	private final DeliveryRepository deliveryRepository;
	private final ShipmentRepository shipmentRepository;
	private final ShipmentItemRepository shipmentItemRepository;
	private final DeliveryStatusHistoryRepository deliveryHistoryRepository;
	private final ProductLotRepository productLotRepository;
	private final StockLogRepository stockLogRepository;
	private final DefectService defectService;
	private final WarehouseFulfillmentService warehouseFulfillmentService;
	private final FarmCustomerRepository farmCustomerRepository;
	private final WmsStockCoordinator wmsStockCoordinator;

	public List<CustomerOrder> orders() {
		return orderRepository.findAllByOrderByCreatedAtDesc();
	}

	public List<Delivery> deliveries() {
	    return deliveryRepository.findAllByOrderByDeliveryIdDesc();
	}

	public Delivery deliveryDetail(Long deliveryId) {
		return deliveryRepository.findDetailByDeliveryId(deliveryId)
				.orElseThrow(() -> new IllegalArgumentException("배송 정보를 찾을 수 없습니다."));
	}

	public Shipment shipmentForDelivery(Long deliveryId) {
		Delivery delivery = deliveryDetail(deliveryId);
		return shipmentRepository.findDetailByOrderOrderId(
				delivery.getOrder().getOrderId())
				.orElseThrow(() -> new IllegalStateException("연결된 출고 정보를 찾을 수 없습니다."));
	}

	public List<ShipmentItem> shipmentItemsForDelivery(Long deliveryId) {
		Shipment shipment = shipmentForDelivery(deliveryId);
		return shipmentItemRepository.findByShipmentShipmentId(
				shipment.getShipmentId());
	}

	public List<DeliveryStatusHistory> deliveryHistories(Long deliveryId) {
		deliveryDetail(deliveryId);
		return deliveryHistoryRepository
				.findByDeliveryDeliveryIdOrderByChangedAtDesc(deliveryId);
	}

	public long delayedCount() {
		return deliveries().stream().filter(Delivery::isDelayed).count();
	}

	public long deliveryHistoryCount(String notePrefix) {
		return deliveryHistoryRepository.countByNoteStartingWith(notePrefix);
	}

	public long orderItemCount(Long orderId) {
		return orderItemRepository.findByOrderOrderId(orderId).size();
	}

	@Transactional
	public Long createDemoOrder(
			Long lotId,
			int quantity,
			BigDecimal discountPrice,
			String recipientName,
			String recipientPhone,
			String postalCode,
			String roadAddress,
			String jibunAddress,
			String detailAddress,
			Double latitude,
			Double longitude,
			String deliveryRequest) {
		return createDemoOrder(
				null,
				lotId,
				quantity,
				discountPrice,
				recipientName,
				recipientPhone,
				postalCode,
				roadAddress,
				jibunAddress,
				detailAddress,
				latitude,
				longitude,
				deliveryRequest);
	}

	@Transactional
	public Long createDemoOrder(
			Long farmCustomerId,
			Long lotId,
			int quantity,
			BigDecimal discountPrice,
			String recipientName,
			String recipientPhone,
			String postalCode,
			String roadAddress,
			String jibunAddress,
			String detailAddress,
			Double latitude,
			Double longitude,
			String deliveryRequest) {
		if (quantity <= 0) {
			throw new IllegalArgumentException("주문 수량은 1개 이상이어야 합니다.");
		}
		ProductLot lot = productLotRepository.findDetailByLotId(lotId)
				.orElseThrow(() -> new IllegalArgumentException("주문 LOT를 찾을 수 없습니다."));
		if (!lot.getProduct().isActive()) {
			throw new IllegalStateException("운영 중인 상품만 주문할 수 있습니다.");
		}
		int reserved = orderItemRepository.findByOrderStatusIn(
				List.of(OrderStatus.PAID, OrderStatus.PREPARING))
				.stream()
				.filter(item ->
						!item.getOrder().isInventoryCommitted())
				.flatMap(item ->
						item.getLotAllocations().stream())
				.filter(allocation ->
						allocation.getProductLot()
								.getLotId()
								.equals(lotId))
				.mapToInt(allocation ->
						allocation.getQuantity())
				.sum();
		int available = lot.getLotQuantity() - reserved;
		if (quantity > available) {
			throw new IllegalStateException(
					"선택한 LOT의 가용 재고는 " + available + "포입니다.");
		}

		BigDecimal totalPrice = lot.getProduct().getPrice()
				.multiply(BigDecimal.valueOf(quantity));
		BigDecimal discount = discountPrice == null
				? BigDecimal.ZERO
				: discountPrice;
		if (discount.signum() < 0 || discount.compareTo(totalPrice) > 0) {
			throw new IllegalArgumentException(
					"할인 금액은 0원 이상이며 주문 금액보다 클 수 없습니다.");
		}

		CustomerOrder order = orderRepository.save(new CustomerOrder(
				0L, totalPrice, discount,
				roadAddress != null && !roadAddress.isBlank()
						? roadAddress.trim()
						: jibunAddress));
		order.configureRecipient(
				recipientName, recipientPhone, deliveryRequest);
		order.configureShippingAddress(
				postalCode, roadAddress, jibunAddress,
				detailAddress, latitude, longitude);
		if (farmCustomerId != null) {
			var farmCustomer = farmCustomerRepository
					.findById(farmCustomerId)
					.orElseThrow(() -> new IllegalArgumentException(
							"농장 고객사를 찾을 수 없습니다."));
			if (farmCustomer.getStatus() != CustomerStatus.ACTIVE) {
				throw new IllegalStateException(
						"거래 중인 농장 고객사만 주문할 수 있습니다.");
			}
			order.linkFarmCustomer(farmCustomer);
		}
		warehouseFulfillmentService.assignNearest(
				order, lot.getProduct(), quantity);
		orderItemRepository.save(new OrderItem(
				order, lot.getProduct(), lot, quantity,
				lot.getProduct().getPrice()));
		return order.getOrderId();
	}

	@Transactional
	public void registerDelivery(Long orderId, String carrierName, String trackingNumber) {
		requireText(carrierName, "운송사를 입력해 주세요.");
		requireText(trackingNumber, "운송장 번호를 입력해 주세요.");
		CustomerOrder order = findOrder(orderId);
		var shipment = shipmentRepository.findByOrderOrderId(orderId)
				.orElseThrow(() -> new IllegalStateException("먼저 출고 지시를 생성해 주세요."));
		if (shipment.getStatus() != ShipmentStatus.SHIPPED) {
			throw new IllegalStateException("출고 완료된 주문만 배송 정보를 등록할 수 있습니다.");
		}
		Delivery delivery = deliveryRepository.findByOrderOrderId(orderId)
				.orElseGet(() -> new Delivery(
						order, carrierName.trim(), trackingNumber.trim()));
		DeliveryStatus previousStatus = delivery.getStatus();
		delivery.update(
				carrierName.trim(), trackingNumber.trim(),
				DeliveryStatus.PICKED_UP);
		deliveryRepository.save(delivery);
		deliveryHistoryRepository.save(new DeliveryStatusHistory(
				delivery, previousStatus, DeliveryStatus.PICKED_UP,
				"운송장 등록 및 택배사 인계"));
		order.changeStatus(OrderStatus.SHIPPING);
	}

	@Transactional
	public void updateDelivery(
			Long deliveryId, DeliveryStatus status, String note) {
		Delivery delivery = deliveryRepository.findById(deliveryId)
				.orElseThrow(() -> new IllegalArgumentException("배송 정보를 찾을 수 없습니다."));
		DeliveryStatus previousStatus = delivery.getStatus();
		if (status == DeliveryStatus.CANCELLED) {
			throw new IllegalArgumentException("배송 취소 기능에서 사유와 담당자를 입력해 주세요.");
		}
		if (previousStatus == DeliveryStatus.CANCELLED) {
			throw new IllegalStateException("취소 배송은 재배송 기능으로 다시 등록해 주세요.");
		}
		if (previousStatus == DeliveryStatus.DELIVERED) {
			throw new IllegalStateException("배송 완료 건의 상태는 변경할 수 없습니다.");
		}
		if (previousStatus == status) {
			throw new IllegalArgumentException("현재 배송 상태와 동일합니다.");
		}
		delivery.update(delivery.getCarrierName(), delivery.getTrackingNumber(), status);
		deliveryHistoryRepository.save(new DeliveryStatusHistory(
				delivery, previousStatus, status,
				note == null || note.isBlank()
						? status.getLabel() + " 처리"
						: note.trim()));
		if (status == DeliveryStatus.DELIVERED) {
			delivery.getOrder().changeStatus(OrderStatus.DELIVERED);
		} else {
			delivery.getOrder().changeStatus(OrderStatus.SHIPPING);
		}
	}

	@Transactional
	public void cancelDelivery(
			Long deliveryId, String reason, String manager) {
		Delivery delivery = deliveryRepository.findById(deliveryId)
				.orElseThrow(() -> new IllegalArgumentException("배송 정보를 찾을 수 없습니다."));
		DeliveryStatus previousStatus = delivery.getStatus();
		delivery.cancel(reason, manager);
		deliveryHistoryRepository.save(new DeliveryStatusHistory(
				delivery, previousStatus, DeliveryStatus.CANCELLED,
				"배송 취소 · " + manager.trim() + ": " + reason.trim()));
	}

	@Transactional
	public void cancelOrder(Long orderId, String reason, String manager) {
		requireText(reason, "주문 취소 사유를 입력해 주세요.");
		requireText(manager, "취소 담당자를 입력해 주세요.");

		CustomerOrder order = findOrder(orderId);
		if (order.getStatus() == OrderStatus.DELIVERED) {
			throw new IllegalStateException(
					"배송 완료 주문은 취소할 수 없습니다. 반품으로 처리해 주세요.");
		}
		if (order.getStatus() == OrderStatus.CANCELLED) {
			throw new IllegalStateException("이미 취소된 주문입니다.");
		}

		deliveryRepository.findByOrderOrderId(orderId).ifPresent(delivery -> {
			if (delivery.getStatus() == DeliveryStatus.DELIVERED) {
				throw new IllegalStateException(
						"배송 완료 주문은 취소할 수 없습니다. 반품으로 처리해 주세요.");
			}
			if (delivery.getStatus() != DeliveryStatus.CANCELLED) {
				DeliveryStatus previousStatus = delivery.getStatus();
				delivery.cancel(reason, manager);
				deliveryHistoryRepository.save(new DeliveryStatusHistory(
						delivery, previousStatus, DeliveryStatus.CANCELLED,
						"주문 취소에 따른 배송 종료 · "
								+ manager.trim() + ": " + reason.trim()));
			}
		});

		shipmentRepository.findByOrderOrderId(orderId).ifPresent(shipment -> {
			if (shipment.getStatus() == ShipmentStatus.SHIPPED) {
				restoreShippedStock(shipment, reason);
				shipment.cancelCompleted("주문 취소: " + reason.trim());
			} else if (shipment.getStatus() != ShipmentStatus.CANCELLED) {
				shipment.cancel("주문 취소: " + reason.trim());
			}
		});

		restoreCommittedOrderStock(order, reason);
		order.cancel(reason, manager);
	}

	@Transactional
	public void rescheduleDelivery(
			Long deliveryId,
			java.time.LocalDateTime expectedAt,
			String reason) {
		Delivery delivery = deliveryRepository.findById(deliveryId)
				.orElseThrow(() -> new IllegalArgumentException("배송 정보를 찾을 수 없습니다."));
		delivery.reschedule(expectedAt, reason);
		deliveryHistoryRepository.save(new DeliveryStatusHistory(
				delivery, delivery.getStatus(), delivery.getStatus(),
				"도착 예정일 변경: " + reason.trim()));
	}

	@Transactional
	public void reactivateDelivery(
			Long deliveryId,
			String carrierName,
			String trackingNumber) {
		Delivery delivery = deliveryRepository.findById(deliveryId)
				.orElseThrow(() -> new IllegalArgumentException("배송 정보를 찾을 수 없습니다."));
		if (delivery.getOrder().getStatus() == OrderStatus.CANCELLED) {
			throw new IllegalStateException(
					"취소된 주문은 재배송할 수 없습니다. 새 주문을 생성해 주세요.");
		}
		DeliveryStatus previousStatus = delivery.getStatus();
		delivery.reactivate(carrierName, trackingNumber);
		deliveryHistoryRepository.save(new DeliveryStatusHistory(
				delivery, previousStatus, DeliveryStatus.PICKED_UP,
				"재배송 등록 · " + carrierName.trim()
						+ " / " + trackingNumber.trim()));
		delivery.getOrder().changeStatus(OrderStatus.SHIPPING);
	}

	@Transactional
	public void updateTracking(
			Long deliveryId,
			String carrierName,
			String trackingNumber) {
		Delivery delivery = deliveryRepository.findById(deliveryId)
				.orElseThrow(() -> new IllegalArgumentException("배송 정보를 찾을 수 없습니다."));
		delivery.updateTracking(carrierName, trackingNumber);
		deliveryHistoryRepository.save(new DeliveryStatusHistory(
				delivery, delivery.getStatus(), delivery.getStatus(),
				"운송장 수정 · " + carrierName.trim()
						+ " / " + trackingNumber.trim()));
	}

	@Transactional
	public void requestReturn(
			Long deliveryId, String reason, String manager) {
		Delivery delivery = findDelivery(deliveryId);
		delivery.requestReturn(reason, manager);
		recordReturnHistory(delivery,
				"회수 요청 · " + manager.trim() + ": " + reason.trim());
	}

	@Transactional
	public void startReturn(Long deliveryId) {
		Delivery delivery = findDelivery(deliveryId);
		delivery.startReturn();
		recordReturnHistory(delivery, "택배사 회수 시작");
	}

	@Transactional
	public void receiveReturn(Long deliveryId) {
		Delivery delivery = findDelivery(deliveryId);
		delivery.receiveReturn();
		recordReturnHistory(delivery, "회수품 입고 · 검수 대기");
	}

	@Transactional
	public void inspectReturn(
			Long deliveryId,
			boolean normal,
			DefectType defectType,
			String note,
			String inspector) {
		requireText(note, "검수 내용을 입력해 주세요.");
		requireText(inspector, "검수 담당자를 입력해 주세요.");

		Delivery delivery = findDelivery(deliveryId);
		List<ShipmentItem> items = shipmentItemsForDelivery(deliveryId);
		if (items.isEmpty()) {
			throw new IllegalStateException("회수 처리할 출고 품목이 없습니다.");
		}

		if (normal) {
			for (ShipmentItem item : items) {
				int quantity = item.getPickedQuantity();
				if (quantity <= 0) {
					continue;
				}
				ProductLot lot = item.getLot();
				lot.changeQuantity(quantity);
				lot.getProduct().changeStock(quantity);
				wmsStockCoordinator.restore(
						lot,
						quantity,
						delivery.getOrder().getFulfillmentWarehouse(),
						"고객 회수 정상 재입고 DLV-" + deliveryId,
						inspector.trim(),
						delivery.getOrder().getOrderId());
				stockLogRepository.save(new StockLog(
						lot, 1L, ChangeType.INBOUND, quantity,
						"고객 회수 정상 재입고 DLV-" + deliveryId
								+ ": " + note.trim()));
			}
			warehouseFulfillmentService.restoreStock(
					delivery.getOrder(), items);
			delivery.completeReturn("정상 재입고 · " + note.trim());
		} else {
			if (defectType == null) {
				throw new IllegalArgumentException("불량 유형을 선택해 주세요.");
			}
			for (ShipmentItem item : items) {
				if (item.getPickedQuantity() > 0) {
					defectService.registerReturned(
							item.getLot().getLotId(),
							item.getPickedQuantity(),
							defectType,
							"배송 회수 DLV-" + deliveryId + ": " + note.trim(),
							inspector.trim());
				}
			}
			delivery.completeReturn(
					"불량 격리(" + defectType.getLabel() + ") · " + note.trim());
		}
		recordReturnHistory(delivery,
				"회수 검수 완료 · " + inspector.trim() + ": "
						+ delivery.getReturnInspectionResult());
	}

	private Delivery findDelivery(Long deliveryId) {
		return deliveryRepository.findById(deliveryId)
				.orElseThrow(() -> new IllegalArgumentException(
						"배송 정보를 찾을 수 없습니다."));
	}

	private void recordReturnHistory(Delivery delivery, String note) {
		deliveryHistoryRepository.save(new DeliveryStatusHistory(
				delivery, delivery.getStatus(), delivery.getStatus(), note));
	}

	private CustomerOrder findOrder(Long id) {
		return orderRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다."));
	}

	private void restoreShippedStock(Shipment shipment, String reason) {
		if (!shipment.getOrder().isInventoryCommitted()) {
			return;
		}
		List<ShipmentItem> items = shipmentItemRepository
				.findByShipmentShipmentId(shipment.getShipmentId());
		items.forEach(item -> {
			int quantity = item.getPickedQuantity();
			item.getLot().changeQuantity(quantity);
			item.getProduct().changeStock(quantity);
			wmsStockCoordinator.restore(
					item.getLot(),
					quantity,
					shipment.getOrder().getFulfillmentWarehouse(),
					shipment.getShipmentNo() + " 주문 취소 재고 원복",
					"관리자",
					shipment.getOrder().getOrderId());
			stockLogRepository.save(new StockLog(
					item.getLot(), 1L, ChangeType.ADJUSTMENT, quantity,
					shipment.getShipmentNo() + " 주문 취소 재고 원복: "
							+ reason.trim()));
		});
		warehouseFulfillmentService.restoreStock(
				shipment.getOrder(), items);
		shipment.getOrder().releaseInventoryCommit();
	}

	private void restoreCommittedOrderStock(
			CustomerOrder order,
			String reason) {
		if (!order.isInventoryCommitted()) {
			return;
		}
		orderItemRepository.findByOrderOrderId(
				order.getOrderId()).stream()
				.flatMap(item ->
						item.getLotAllocations().stream())
				.forEach(allocation -> {
					int quantity = allocation.getQuantity();
					ProductLot lot = allocation.getProductLot();
					lot.changeQuantity(quantity);
					lot.getProduct().changeStock(quantity);
					wmsStockCoordinator.restore(
							lot,
							quantity,
							order.getFulfillmentWarehouse(),
							"주문 취소 재고 복원",
							"관리자",
							order.getOrderId());
					stockLogRepository.save(new StockLog(
							lot,
							1L,
							ChangeType.ADJUSTMENT,
							quantity,
							"주문 취소 재고 복원: "
									+ reason.trim()));
				});
		order.releaseInventoryCommit();
	}

	private void requireText(String value, String message) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(message);
		}
	}
}
