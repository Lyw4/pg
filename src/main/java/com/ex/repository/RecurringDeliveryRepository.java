package com.ex.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.ex.entity.RecurringDelivery;

public interface RecurringDeliveryRepository
		extends JpaRepository<RecurringDelivery, Long> {

	@EntityGraph(attributePaths = {
			"manufacturer",
			"product",
			"warehouse"
	})
	List<RecurringDelivery> findAllByOrderByNextDeliveryDateAsc();

	boolean existsByWarehouseWarehouseIdAndProductProductIdAndDeliveryDay(
			Long warehouseId,
			Long productId,
			int deliveryDay);

	boolean existsByNotes(String notes);

	long deleteByWarehouseIsNull();

	long countByActiveTrue();
}
