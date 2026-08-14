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

	@EntityGraph(attributePaths = {"order", "product"})
	List<OrderItem> findByOrderMemberId(Long memberId);
}
