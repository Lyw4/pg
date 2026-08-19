package com.ex.repository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.ex.entity.CustomerOrder.OrderStatus;
import com.ex.entity.OrderItem;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
	List<OrderItem> findByOrderOrderId(Long orderId);

	@EntityGraph(attributePaths = {
			"order",
			"order.fulfillmentWarehouse",
			"order.farmCustomer",
			"product",
			"lotAllocations",
			"lotAllocations.productLot"
	})
	List<OrderItem> findByOrderStatusIn(Collection<OrderStatus> statuses);

	@Query("""
			select o.fulfillmentWarehouse.warehouseId,
			       item.product.productId,
			       sum(item.quantity)
			from OrderItem item
			  join item.order o
			where o.status in :statuses
			  and o.fulfillmentWarehouse is not null
			  and o.inventoryCommitted = false
			group by o.fulfillmentWarehouse.warehouseId,
			         item.product.productId
			""")
	List<Object[]> sumReservedQuantities(
			@Param("statuses") Collection<OrderStatus> statuses);

	@Query("""
			select allocation.productLot.lotId, sum(allocation.quantity)
			from OrderItem item
			  join item.order o
			  join item.lotAllocations allocation
			where o.status in :statuses
			  and o.inventoryCommitted = false
			  and allocation.productLot.lotId in :lotIds
			group by allocation.productLot.lotId
			""")
	List<Object[]> sumReservedQuantitiesByLotIds(
			@Param("statuses") Collection<OrderStatus> statuses,
			@Param("lotIds") Collection<Long> lotIds);

	@Query("""
			select allocation.productLot.lotId, sum(allocation.quantity)
			from OrderItem item
			  join item.order o
			  join item.lotAllocations allocation
			where o.status in :statuses
			  and o.inventoryCommitted = false
			  and o.fulfillmentWarehouse.warehouseId = :warehouseId
			  and allocation.productLot.lotId in :lotIds
			group by allocation.productLot.lotId
			""")
	List<Object[]> sumReservedQuantitiesByLotIdsAtWarehouse(
			@Param("statuses") Collection<OrderStatus> statuses,
			@Param("lotIds") Collection<Long> lotIds,
			@Param("warehouseId") Long warehouseId);

	@Query("""
			select item.product.productId, sum(item.quantity)
			from OrderItem item join item.order o
			where o.status in :statuses
			  and o.inventoryCommitted = false
			  and item.product.productId in :productIds
			group by item.product.productId
			""")
	List<Object[]> sumReservedQuantitiesByProductIds(
			@Param("statuses") Collection<OrderStatus> statuses,
			@Param("productIds") Collection<Long> productIds);

	@EntityGraph(attributePaths = {"order", "product"})
	List<OrderItem> findByOrderMemberId(Long memberId);
}
