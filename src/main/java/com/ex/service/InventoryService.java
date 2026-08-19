package com.ex.service;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ex.entity.Product;
import com.ex.entity.ProductLot;
import com.ex.entity.OrderItem;
import com.ex.entity.CustomerOrder.OrderStatus;
import com.ex.entity.StockLog;
import com.ex.entity.StockLog.ChangeType;
import com.ex.entity.Warehouse;
import com.ex.repository.ProductLotRepository;
import com.ex.repository.ProductRepository;
import com.ex.repository.OrderItemRepository;
import com.ex.repository.StockLogRepository;
import com.ex.repository.WarehouseAllocationRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryService {

	private final ProductRepository productRepository;
	private final ProductLotRepository lotRepository;
	private final StockLogRepository stockLogRepository;
	private final OrderItemRepository orderItemRepository;
	private final WmsStockCoordinator wmsStockCoordinator;
	private final WarehouseAllocationRepository allocationRepository;
	private final SellableStockQuery sellableStockQuery;

	private static final EnumSet<OrderStatus> RESERVING_STATUSES =
			EnumSet.of(
					OrderStatus.PAYMENT_PENDING,
					OrderStatus.PAID,
					OrderStatus.PREPARING);

	public List<Product> products() {
		return productRepository.findAllByOrderByNameAsc()
				.stream()
				.filter(Product::isActive)
				.toList();
	}

	public List<ProductLot> lots() {
		return lotRepository.findAllByOrderByExpirationDateAsc()
				.stream()
				.filter(lot -> lot.getProduct().isActive())
				.toList();
	}

	public List<StockLog> recentLogs() {
		return stockLogRepository.findTop30ByOrderByCreatedAtDesc();
	}

	public List<StockLog> outboundLogs() {
		return stockLogRepository.findTop100ByChangeTypeOrderByCreatedAtDesc(
				ChangeType.OUTBOUND);
	}

	public List<StockLog> activeOutboundLogs() {
		return outboundLogs().stream()
				.filter(log -> !log.isCancelled())
				.toList();
	}

	public List<StockLog> cancelledOutboundLogs() {
		return outboundLogs().stream()
				.filter(StockLog::isCancelled)
				.toList();
	}

	@Transactional
	public void cancelDirectOutbound(Long logId, String cancelReason) {
		if (cancelReason == null || cancelReason.isBlank()) {
			throw new IllegalArgumentException("출고 취소 사유를 입력해 주세요.");
		}
		StockLog log = stockLogRepository.findByLogId(logId)
				.orElseThrow(() -> new IllegalArgumentException("출고 내역을 찾을 수 없습니다."));
		if (log.getReason() != null && log.getReason().startsWith("SHP-")) {
			throw new IllegalStateException("주문 출고는 해당 출고 지시에서 전체 취소해 주세요.");
		}
		log.cancel(cancelReason.trim());
		int restored = -log.getChangedQty();
		log.getLot().changeQuantity(restored);
		log.getLot().getProduct().changeStock(restored);
		wmsStockCoordinator.restore(
				log.getLot(), restored, null,
				"직접 출고 취소: " + cancelReason.trim(),
				"관리자", null);
		stockLogRepository.save(new StockLog(
				log.getLot(), 1L, ChangeType.ADJUSTMENT, restored,
				"출고 취소 #" + log.getLogId() + ": " + cancelReason.trim()));
		synchronizeWarehouseStock(log.getLot().getProduct());
	}

	public Map<Long, Integer> reservedStockByProduct() {
		Map<Long, Integer> result = new HashMap<>();
		activeReservations().forEach(item -> result.merge(
				item.getProduct().getProductId(), item.getQuantity(), Integer::sum));
		return result;
	}

	public Map<Long, Integer> reservedStockByLot() {
		Map<Long, Integer> result = new HashMap<>();
		activeReservations().forEach(item ->
				item.getLotAllocations().forEach(allocation ->
						result.merge(
								allocation.getProductLot().getLotId(),
								allocation.getQuantity(),
								Integer::sum)));
		return result;
	}

	public int availableLotStock(ProductLot lot) {
		return lot.getLotQuantity()
				- reservedStockByLot().getOrDefault(lot.getLotId(), 0);
	}

	public String createAutomaticLotNo(Long productId, LocalDate manufacturedDate) {
		Product product = findProduct(productId);
		String categoryCode = switch (product.getAnimalType()) {
			case "소" -> "CATTLE";
			case "돼지" -> "PIG";
			case "조류(닭/오리)" -> "BIRD";
			default -> "SUP";
		};
		String date = manufacturedDate.toString().replace("-", "");
		int sequence = Math.toIntExact(productId);
		String lotNo;
		do {
			lotNo = "LOT-%s-%s-%03d".formatted(
					categoryCode, date, sequence++);
		} while (lotRepository.existsByLotNo(lotNo));
		return lotNo;
	}

	public Product productDetail(Long productId) {
		Product product = productRepository.findDetailByProductId(productId)
				.orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));

		if (!product.isActive()) {
			throw new IllegalArgumentException("삭제되었거나 존재하지 않는 상품입니다.");
		}

		return product;
	}

	public ProductLot lotDetail(Long lotId) {
		return findLot(lotId);
	}

	public List<StockLog> lotLogs(Long lotId) {
		findLot(lotId);
		return stockLogRepository.findByLotLotIdOrderByCreatedAtDesc(lotId);
	}

	public int initialReceivedQuantity(Long lotId) {
		return lotLogs(lotId).stream()
				.filter(log -> log.getChangeType() == ChangeType.INBOUND)
				.filter(log -> !log.isCancelled())
				.mapToInt(StockLog::getChangedQty)
				.sum();
	}

	public List<ProductLot> productLots(Long productId) {
		findProduct(productId);
		return lotRepository.findByProductProductIdOrderByExpirationDateAsc(productId);
	}

	@Transactional
	public void deleteProduct(Long productId) {
		findProduct(productId).deactivate();
	}

	@Transactional
	public void receive(Long productId, String lotNo, LocalDate manufacturedDate,
			LocalDate expirationDate, int quantity, String reason) {
		receive(
				productId,
				lotNo,
				manufacturedDate,
				expirationDate,
				quantity,
				reason,
				null);
	}

	@Transactional
	public void receive(Long productId, String lotNo, LocalDate manufacturedDate,
			LocalDate expirationDate, int quantity, String reason,
			Warehouse preferredWarehouse) {
		if (quantity <= 0) throw new IllegalArgumentException("입고 수량은 1개 이상이어야 합니다.");
		if (lotRepository.existsByLotNo(lotNo)) throw new IllegalArgumentException("이미 존재하는 LOT 번호입니다.");
		if (!expirationDate.isAfter(manufacturedDate)) {
			throw new IllegalArgumentException("유통기한은 제조일보다 뒤여야 합니다.");
		}
		Product product = findProduct(productId);
		ProductLot lot = lotRepository.save(
				new ProductLot(product, lotNo, manufacturedDate, expirationDate, quantity));
		product.changeStock(quantity);
		stockLogRepository.save(new StockLog(lot, 1L, ChangeType.INBOUND, quantity, reason));
		wmsStockCoordinator.inbound(
				lot, quantity, preferredWarehouse, reason, "관리자");
		synchronizeWarehouseStock(product);
	}

	@Transactional
	public void adjust(Long lotId, int changedQty, String reason) {
		if (changedQty == 0) throw new IllegalArgumentException("조정 수량은 0일 수 없습니다.");
		ProductLot lot = findLot(lotId);
		lot.changeQuantity(changedQty);
		lot.getProduct().changeStock(changedQty);
		stockLogRepository.save(new StockLog(lot, 1L, ChangeType.ADJUSTMENT, changedQty, reason));
		wmsStockCoordinator.adjust(
				lot, changedQty, null, reason, "관리자");
		synchronizeWarehouseStock(lot.getProduct());
	}

	@Transactional
	public void updateWarehouseLocation(Long lotId, String warehouseLocation) {
		findLot(lotId).changeWarehouseLocation(warehouseLocation);
	}

	@Transactional
	public void auditLot(Long lotId, int actualQuantity, String reason) {
		if (actualQuantity < 0) {
			throw new IllegalArgumentException("실사 수량은 0개 이상이어야 합니다.");
		}
		ProductLot lot = findLot(lotId);
		int reserved = reservedStockByLot().getOrDefault(lotId, 0);
		if (actualQuantity < reserved) {
			throw new IllegalStateException(
					"실사 수량은 예약 재고 " + reserved + "개보다 적을 수 없습니다.");
		}
		int difference = actualQuantity - lot.getLotQuantity();
		if (difference == 0) {
			throw new IllegalArgumentException("전산 재고와 실제 재고가 동일합니다.");
		}
		String auditReason = reason == null || reason.isBlank()
				? "정기 재고 실사"
				: reason.trim();
		lot.changeQuantity(difference);
		lot.getProduct().changeStock(difference);
		stockLogRepository.save(new StockLog(
				lot, 1L, ChangeType.INVENTORY_AUDIT, difference,
				auditReason + " (실사 수량 " + actualQuantity + "개)"));
		wmsStockCoordinator.adjust(
				lot, difference, null,
				"재고 실사: " + auditReason, "관리자");
		synchronizeWarehouseStock(lot.getProduct());
	}

	@Transactional
	public void cancelInbound(Long lotId, Long logId, String cancelReason) {
		if (cancelReason == null || cancelReason.isBlank()) {
			throw new IllegalArgumentException("입고 취소 사유를 입력해 주세요.");
		}
		StockLog log = stockLogRepository.findByLogId(logId)
				.orElseThrow(() -> new IllegalArgumentException("입고 이력을 찾을 수 없습니다."));
		if (log.getChangeType() != ChangeType.INBOUND) {
			throw new IllegalArgumentException("입고 이력만 취소할 수 있습니다.");
		}
		if (!log.getLot().getLotId().equals(lotId)) {
			throw new IllegalArgumentException("해당 LOT의 입고 이력이 아닙니다.");
		}
		if (log.isCancelled()) {
			throw new IllegalStateException("이미 취소된 입고 내역입니다.");
		}
		ProductLot lot = log.getLot();
		int received = log.getChangedQty();
		int reserved = reservedStockByLot().getOrDefault(lot.getLotId(), 0);
		if (lot.getLotQuantity() - reserved < received) {
			throw new IllegalStateException(
					"출고 또는 예약된 수량이 있어 이 입고 건 전체를 취소할 수 없습니다.");
		}
		log.cancelInbound(cancelReason.trim());
		lot.changeQuantity(-received);
		lot.getProduct().changeStock(-received);
		wmsStockCoordinator.adjust(
				lot, -received, null,
				"입고 취소: " + cancelReason.trim(), "관리자");
		stockLogRepository.save(new StockLog(
				lot, 1L, ChangeType.INBOUND_CANCEL, -received,
				"입고 #" + logId + " 취소: " + cancelReason.trim()));
		synchronizeWarehouseStock(lot.getProduct());
	}

	@Transactional
	public void releaseFifo(Long productId, int quantity, String reason) {
		Map<Long, Integer> reservedProducts = reservedStockByProduct();
		Map<Long, Integer> reservedLots = reservedStockByLot();
		Product reservingProduct = findProduct(productId);
		if (reservingProduct.getTotalStock()
				- reservedProducts.getOrDefault(productId, 0) < quantity) {
			throw new IllegalStateException("예약 수량을 제외한 가용 재고가 부족합니다.");
		}
		if (quantity <= 0) throw new IllegalArgumentException("출고 수량은 1개 이상이어야 합니다.");
		Product product = findProduct(productId);
		if (product.getTotalStock() < quantity) throw new IllegalStateException("출고 가능한 재고가 부족합니다.");

		int remaining = quantity;
		LocalDate sellableFrom = LocalDate.now()
				.plusDays(SellableStockQuery.MINIMUM_SELLABLE_DAYS);
		for (ProductLot lot : lotRepository
				.findByProductProductIdAndLotQuantityGreaterThanOrderByExpirationDateAsc(productId, 0)) {
			if (lot.getExpirationDate().isBefore(sellableFrom)) continue;
			int lotAvailable = lot.getLotQuantity()
					- reservedLots.getOrDefault(lot.getLotId(), 0);
			int released = Math.min(remaining, Math.max(lotAvailable, 0));
			if (released == 0) continue;
			lot.changeQuantity(-released);
			product.changeStock(-released);
			wmsStockCoordinator.outbound(
					lot, released, null, reason, "관리자", null);
			stockLogRepository.save(new StockLog(lot, 1L, ChangeType.OUTBOUND, -released, reason));
			remaining -= released;
			if (remaining == 0) {
				synchronizeWarehouseStock(product);
				return;
			}
		}
		throw new IllegalStateException("LOT별 출고 가능한 재고가 부족합니다.");
	}

	@Transactional
	public void shipReservedOrder(List<OrderItem> items) {
		Map<ProductLot, Integer> quantitiesByLot = new HashMap<>();
		items.forEach(item ->
				item.getLotAllocations().forEach(allocation ->
						quantitiesByLot.merge(
								allocation.getProductLot(),
								allocation.getQuantity(),
								Integer::sum)));

		quantitiesByLot.forEach((lot, quantity) -> {
			if (lot.getLotQuantity() < quantity) {
				throw new IllegalStateException(
						"예약된 LOT 재고가 부족합니다: " + lot.getLotNo());
			}
		});

		quantitiesByLot.forEach((lot, quantity) -> {
			lot.changeQuantity(-quantity);
			lot.getProduct().changeStock(-quantity);
			wmsStockCoordinator.outbound(
					lot,
					quantity,
					items.isEmpty()
							? null
							: items.getFirst().getOrder()
									.getFulfillmentWarehouse(),
					"주문 예약 재고 출고",
					"관리자",
					items.isEmpty()
							? null
							: items.getFirst().getOrder().getOrderId());
			stockLogRepository.save(new StockLog(
					lot, 1L, ChangeType.OUTBOUND, -quantity, "주문 예약 재고 출고"));
		});
		quantitiesByLot.keySet().stream()
				.map(ProductLot::getProduct)
				.distinct()
				.forEach(this::synchronizeWarehouseStock);
	}

	void synchronizeWarehouseStock(Product product) {
		allocationRepository.findByProductProductId(product.getProductId())
				.forEach(allocation -> allocation.adjustCurrentStock(
						sellableStockQuery.sellableAtWarehouse(
								allocation.getWarehouse().getWarehouseId(),
								product.getProductId())));
	}

	private List<OrderItem> activeReservations() {
		return orderItemRepository.findByOrderStatusIn(
						RESERVING_STATUSES)
				.stream()
				.filter(item ->
						!item.getOrder().isInventoryCommitted())
				.toList();
	}

	private Product findProduct(Long id) {
		Product product = productRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));

		if (!product.isActive()) {
			throw new IllegalArgumentException("삭제되었거나 존재하지 않는 상품입니다.");
		}

		return product;
	}

	private ProductLot findLot(Long id) {
		return lotRepository.findDetailByLotId(id)
				.orElseThrow(() -> new IllegalArgumentException("LOT를 찾을 수 없습니다."));
	}
}
