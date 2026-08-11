package com.ex.repository;

import com.ex.entity.CustomerOrder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CustomerOrderRepositoryConstraintTest {

    @Autowired
    private CustomerOrderRepository orderRepository;

    @Test
    void providerTransactionIdCannotBeReusedByAnotherOrder() {
        CustomerOrder first = new CustomerOrder(
                0L, BigDecimal.valueOf(1000), BigDecimal.ZERO, "테스트 주소");
        first.completePayment("imp_unique_test", null);
        orderRepository.saveAndFlush(first);

        CustomerOrder duplicate = new CustomerOrder(
                0L, BigDecimal.valueOf(2000), BigDecimal.ZERO, "테스트 주소 2");
        duplicate.completePayment("imp_unique_test", null);

        assertThrows(DataIntegrityViolationException.class,
                () -> orderRepository.saveAndFlush(duplicate));
    }
}
