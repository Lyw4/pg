package com.ex.repository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
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

	@EntityGraph(attributePaths = {"order", "product"})
	List<OrderItem> findByOrderMemberId(Long memberId);
}
