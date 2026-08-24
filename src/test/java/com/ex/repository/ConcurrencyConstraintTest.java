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
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void productLotKeepsOptimisticVersion() throws Exception {
        assertNotNull(ProductLot.class.getDeclaredField("version")
                .getAnnotation(jakarta.persistence.Version.class));
        assertTrue(ProductLot.class.getDeclaredField("version")
                .getAnnotation(jakarta.persistence.Column.class).nullable() == false);
    }

    /**
     * WarehouseAllocation은 낙관적 락을 쓰지 않습니다. 가장 자주 바뀌는
     * currentStockQuantity가 매번 재계산되는 파생 캐시라 락이 지킬 무결성이
     * 없는데, 결제 대기 정리 스케줄러와 고객 주문 생성이 같은 행을 동시에
     * 갱신하면 충돌이 409로 새어 나가 고객 결제를 실패시켰습니다.
     * 재계산이 불가능한 관리자 입력값은 비관적 락으로 보호합니다.
     */
    @Test
    void warehouseAllocationUsesPessimisticLockInsteadOfVersion() throws Exception {
        assertNull(WarehouseAllocation.class.getDeclaredField("version")
                .getAnnotation(jakarta.persistence.Version.class));

        Method allocationQuery = WarehouseAllocationRepository.class
                .getMethod("findByIdForUpdate", Long.class);
        assertEquals(LockModeType.PESSIMISTIC_WRITE,
                allocationQuery.getAnnotation(Lock.class).value());
    }
}
