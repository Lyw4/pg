package com.ex.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ex.entity.Product;
import com.ex.entity.RecurringDelivery;
import com.ex.entity.Warehouse;
import com.ex.entity.WarehouseAllocation;
import com.ex.repository.ProductRepository;
import com.ex.repository.RecurringDeliveryRepository;
import com.ex.repository.WarehouseAllocationRepository;
import com.ex.repository.WarehouseRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
/*
 * 협력사별 월간 입고 일정과 실제 LOT 입고를 관리합니다.
 */
public class RecurringDeliveryService {

	public record WarehouseDeliverySummary(
			Warehouse warehouse,
			long scheduleCount,
			long activeScheduleCount,
			int monthlyQuantity,
			String deliveryDays,
			LocalDate nextDeliveryDate) {
	}

	private final RecurringDeliveryRepository recurringDeliveryRepository;
	private final ProductRepository productRepository;
	private final WarehouseRepository warehouseRepository;
	private final WarehouseAllocationRepository allocationRepository;
	private final InventoryService inventoryService;

	public List<RecurringDelivery> deliveries() {
		return recurringDeliveryRepository
				.findAllByOrderByNextDeliveryDateAsc()
				.stream()
				.filter(delivery -> delivery.getWarehouse() != null)
				.filter(delivery -> delivery.getWarehouse().isActive())
				.filter(delivery -> delivery.getProduct().isActive())
				.toList();
	}

	public long activeCount() {
		return deliveries().stream()
				.filter(RecurringDelivery::isActive)
				.count();
	}

	public Map<String, WarehouseDeliverySummary> warehouseSummaries() {
		Map<String, List<RecurringDelivery>> grouped = deliveries()
				.stream()
				.collect(Collectors.groupingBy(
						delivery -> delivery.getWarehouse().getCode(),
						LinkedHashMap::new,
						Collectors.toList()));

		Map<String, WarehouseDeliverySummary> result =
				new LinkedHashMap<>();
		warehouseRepository
				.findAllByActiveTrueOrderByDisplayOrderAsc()
				.forEach(warehouse -> {
					List<RecurringDelivery> schedules =
							grouped.getOrDefault(
									warehouse.getCode(),
									List.of());
					String days = schedules.stream()
							.filter(RecurringDelivery::isActive)
							.map(RecurringDelivery::getDeliveryDay)
							.collect(Collectors.toCollection(TreeSet::new))
							.stream()
							.map(day -> day + "일")
							.collect(Collectors.joining(" · "));
					LocalDate nextDate = schedules.stream()
							.filter(RecurringDelivery::isActive)
							.map(RecurringDelivery::getNextDeliveryDate)
							.min(LocalDate::compareTo)
							.orElse(null);

					result.put(
							warehouse.getCode(),
							new WarehouseDeliverySummary(
									warehouse,
									schedules.size(),
									schedules.stream()
											.filter(RecurringDelivery::isActive)
											.count(),
									schedules.stream()
											.filter(RecurringDelivery::isActive)
											.mapToInt(RecurringDelivery::getQuantity)
											.sum(),
									days,
									nextDate));
				});
		return result;
	}

	@Transactional
	public void create(
			Long warehouseId,
			Long productId,
			int quantity,
			int deliveryDay,
			String notes) {

		if (quantity <= 0) {
			throw new IllegalArgumentException(
					"정기 배송 수량은 1개 이상이어야 합니다.");
		}

		if (deliveryDay < 1 || deliveryDay > 28) {
			throw new IllegalArgumentException(
					"월 입고일은 1일부터 28일 사이여야 합니다.");
		}

		Product product =
				productRepository.findDetailByProductId(productId)
					.orElseThrow(() ->
							new IllegalArgumentException(
									"상품을 찾을 수 없습니다."));

		Warehouse warehouse = warehouseRepository.findById(warehouseId)
				.filter(Warehouse::isActive)
				.orElseThrow(() -> new IllegalArgumentException(
						"운영 중인 창고를 찾을 수 없습니다."));

		allocationRepository
				.findByWarehouseWarehouseIdAndProductProductId(
						warehouseId,
						productId)
				.orElseThrow(() -> new IllegalArgumentException(
						"선택한 창고에 배치된 상품이 아닙니다."));

		LocalDate nextDeliveryDate =
				calculateNextDeliveryDate(
						LocalDate.now(),
						deliveryDay);

		recurringDeliveryRepository.save(
				new RecurringDelivery(
						warehouse,
						product.getManufacturer(),
						product,
						quantity,
						deliveryDay,
						0,
						nextDeliveryDate,
						notes));
	}

	@Transactional
	public void receive(
			Long recurringDeliveryId,
			LocalDate manufacturedDate) {

		RecurringDelivery delivery =
				findDelivery(recurringDeliveryId);

		if (!delivery.isActive()) {
			throw new IllegalStateException(
					"중지된 정기 배송 일정입니다.");
		}

		int receiptQuantity =
				delivery.isSafetyStockBased()
					? Math.max(
							0,
							delivery.getProduct().getSafetyStock()
								- delivery.getProduct().getTotalStock())
					: delivery.getQuantity();

		if (receiptQuantity <= 0) {
			throw new IllegalStateException(
					"현재 재고가 안전재고 이상입니다. "
					+ "'확인 완료'로 다음 점검일을 예약해 주세요.");
		}

		WarehouseAllocation allocation = allocationRepository
				.findByWarehouseWarehouseIdAndProductProductId(
						delivery.getWarehouse().getWarehouseId(),
						delivery.getProduct().getProductId())
				.orElseThrow(() -> new IllegalStateException(
						"정기 배송에 연결된 창고 상품 재고가 없습니다."));

		inventoryService.receive(
				delivery.getProduct().getProductId(),
				inventoryService.createAutomaticLotNo(
						delivery.getProduct().getProductId(), manufacturedDate),
				manufacturedDate,
				manufacturedDate.plusMonths(
						delivery.getProduct().getEffectiveShelfLifeMonths()),
				receiptQuantity,
				"정기 배송 입고 - "
						+ delivery.getWarehouse().getName()
						+ " / "
						+ delivery.getManufacturer().getCompanyName());

		allocation.adjustCurrentStock(
				allocation.getCurrentStockQuantity()
						+ receiptQuantity);
		delivery.recordReceipt(LocalDate.now());
	}

	@Transactional
	public void review(Long recurringDeliveryId) {
		RecurringDelivery delivery =
				findDelivery(recurringDeliveryId);

		if (!delivery.isActive()
				|| !delivery.isSafetyStockBased()) {
			throw new IllegalStateException(
					"안전재고 점검 일정이 아닙니다.");
		}

		if (delivery.getProduct().getTotalStock()
				<= delivery.getProduct().getSafetyStock()) {
			throw new IllegalStateException(
					"안전재고 이하이므로 부족분 입고가 필요합니다.");
		}

		delivery.recordReview(LocalDate.now());
	}

	@Transactional
	public void toggle(Long recurringDeliveryId) {
		findDelivery(recurringDeliveryId).toggleActive();
	}

	private RecurringDelivery findDelivery(Long id) {
		RecurringDelivery delivery = recurringDeliveryRepository.findById(id)
				.orElseThrow(() ->
						new IllegalArgumentException(
								"정기 배송 일정을 찾을 수 없습니다."));

		if (!delivery.getProduct().isActive()) {
			throw new IllegalArgumentException(
					"삭제된 상품의 정기 배송 일정입니다.");
		}
		if (delivery.getWarehouse() == null
				|| !delivery.getWarehouse().isActive()) {
			throw new IllegalArgumentException(
					"창고가 지정되지 않은 정기 배송 일정입니다.");
		}

		return delivery;
	}

	private LocalDate calculateNextDeliveryDate(
			LocalDate today,
			int deliveryDay) {

		YearMonth targetMonth =
				YearMonth.from(today);

		LocalDate candidate =
				targetMonth.atDay(deliveryDay);

		if (candidate.isBefore(today)) {
			candidate =
					targetMonth
						.plusMonths(1)
						.atDay(deliveryDay);
		}

		return candidate;
	}
}
