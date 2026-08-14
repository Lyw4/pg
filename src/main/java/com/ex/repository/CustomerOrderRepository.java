package com.ex.repository;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import com.ex.entity.CustomerOrder;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {
	@EntityGraph(attributePaths = {
			"fulfillmentWarehouse",
			"farmCustomer"
	})
	List<CustomerOrder> findAllByOrderByCreatedAtDesc();

	Optional<CustomerOrder> findByOrderNumber(String orderNumber);

	@EntityGraph(attributePaths = "member")
	@Query("select o from CustomerOrder o where o.orderNumber = :orderNumber")
	Optional<CustomerOrder> findPaymentOrderByOrderNumber(
			@Param("orderNumber") String orderNumber);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select o from CustomerOrder o where o.orderNumber = :orderNumber")
	Optional<CustomerOrder> findByOrderNumberForUpdate(@Param("orderNumber") String orderNumber);

	Optional<CustomerOrder> findByProviderTransactionId(String providerTransactionId);

	@EntityGraph(attributePaths = {"fulfillmentWarehouse", "farmCustomer"})
	List<CustomerOrder> findByMember_IdAndCreatedAtAfterOrderByCreatedAtDesc(
			Long memberId,
			LocalDateTime createdAfter);
}
