package com.ex.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 관리자 실사 수량이 자동 재계산에 덮여 사라지지 않는지 확인한다.
 *
 * <p>이전에는 실사 입력과 자동 재계산이 currentStockQuantity 한 필드를 함께
 * 써서, 실사 직후 배치가 돌면 입력값이 흔적도 없이 사라졌다.
 */
class WarehouseAllocationStockAuditTest {

    private WarehouseAllocation allocation() {
        return new WarehouseAllocation(null, null, 100, 80);
    }

    @Test
    void stockAuditSurvivesAutomaticRecalculation() {
        WarehouseAllocation allocation = allocation();

        allocation.recordStockAudit(120);
        assertEquals(120, allocation.getCurrentStockQuantity());
        assertEquals(120, allocation.getAuditedStockQuantity());

        // 자동 재계산이 파생 캐시를 덮어쓴다.
        allocation.refreshCurrentStock(87);

        assertEquals(87, allocation.getCurrentStockQuantity());
        assertEquals(120, allocation.getAuditedStockQuantity(),
                "실사 기록은 재계산에 덮이지 않아야 한다");
        assertEquals(33, allocation.getAuditedStockVariance(),
                "실사 120 - 자동 계산 87 = 33 만큼 실물이 많다");
    }

    @Test
    void auditTimestampIsRecorded() {
        WarehouseAllocation allocation = allocation();
        assertFalse(allocation.hasStockAudit());
        assertEquals(0, allocation.getAuditedStockVariance());

        allocation.recordStockAudit(50);

        assertTrue(allocation.hasStockAudit());
        assertNotNull(allocation.getAuditedStockAt());
    }

    @Test
    void negativeQuantitiesAreRejectedOnBothPaths() {
        WarehouseAllocation allocation = allocation();

        assertThrows(IllegalArgumentException.class,
                () -> allocation.recordStockAudit(-1));
        assertThrows(IllegalArgumentException.class,
                () -> allocation.refreshCurrentStock(-1));
    }
}
