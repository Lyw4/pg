package com.ex.repository;

import com.ex.entity.CustomerOrder;
import com.ex.entity.ProductLot;
import com.ex.entity.WarehouseAllocation;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;

import java.lang.reflect.Method;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConcurrencyConstraintTest {

    @Test
    void stockAndOrderMutationQueriesUsePessimisticWriteLocks() throws Exception {
        Method lotQuery = ProductLotRepository.class.getMethod(
                "findByProductProductIdAndLotQuantityGreaterThanOrderByExpirationDateAsc",
                Long.class, int.class);
        Method binQuery = BinInventoryRepository.class.getMethod(
                "findByLotLotIdAndQuantityGreaterThanOrderByBinBinCodeAsc",
                Long.class, int.class);
        Method orderQuery = CustomerOrderRepository.class.getMethod(
                "findByOrderNumberForUpdate", String.class);

        assertEquals(LockModeType.PESSIMISTIC_WRITE,
                lotQuery.getAnnotation(Lock.class).value());
        assertEquals(LockModeType.PESSIMISTIC_WRITE,
                binQuery.getAnnotation(Lock.class).value());
        assertEquals(LockModeType.PESSIMISTIC_WRITE,
                orderQuery.getAnnotation(Lock.class).value());
    }

    @Test
    void customerOrderKeepsProviderTransactionIndexUnique() {
        jakarta.persistence.Table table = CustomerOrder.class
                .getAnnotation(jakarta.persistence.Table.class);
        assertNotNull(table);
        assertEquals(4, table.indexes().length);
        assertEquals("idx_customer_order_provider_tx", table.indexes()[1].name());
        assertEquals("provider_transaction_id", table.indexes()[1].columnList());
        assertEquals(true, table.indexes()[1].unique());
        assertEquals("idx_customer_order_status", table.indexes()[2].name());
        assertEquals("idx_customer_order_farm_schedule", table.indexes()[3].name());
        assertEquals(true, table.indexes()[3].unique());
    }

    @Test
    void lotAndWarehouseAllocationUseOptimisticVersions() throws Exception {
        assertNotNull(ProductLot.class.getDeclaredField("version")
                .getAnnotation(jakarta.persistence.Version.class));
        assertNotNull(WarehouseAllocation.class.getDeclaredField("version")
                .getAnnotation(jakarta.persistence.Version.class));
        assertTrue(ProductLot.class.getDeclaredField("version")
                .getAnnotation(jakarta.persistence.Column.class).nullable() == false);
    }
}
