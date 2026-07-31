package com.ex.repository;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import com.ex.entity.CustomerOrder;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {
	@EntityGraph(attributePaths = {
			"fulfillmentWarehouse",
			"farmCustomer"
	})
	List<CustomerOrder> findAllByOrderByCreatedAtDesc();
}
