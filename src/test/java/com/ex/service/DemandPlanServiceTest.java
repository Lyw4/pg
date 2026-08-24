package com.ex.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.ex.entity.FarmCustomer;
import com.ex.entity.FarmCustomer.CustomerStatus;
import com.ex.entity.Member;
import com.ex.entity.Warehouse;
import com.ex.repository.BinInventoryRepository;
import com.ex.repository.FarmCustomerRepository;
import com.ex.repository.WarehouseRepository;

class DemandPlanServiceTest {

    /** application.properties 의 기본값과 같은 값이다. */
    private static final String TEST_ACCOUNT_DOMAIN = "@feedflow.test";

    private final Warehouse warehouse = new Warehouse(
            "W05", "나주 문평 창고", "주소", "전남", "조류",
            35.0, 126.0, 5);

    @Test
    void automatedTestFarmIsExcludedFromOperationalDemand() {
        Member member = Member.builder()
                .email("QA123@FEEDFLOW.TEST")
                .build();
        FarmCustomer farm = FarmCustomer.registeredMember(
                member, "F-QA", "QA 자동화 농장", "QA", "010",
                "00000", "주소", 35.0, 126.0, "미등록",
                0, 40, "상담 후 지정", 0, warehouse, 0, "QA");

        assertFalse(DemandPlanService.isOperationalFarm(
                farm, TEST_ACCOUNT_DOMAIN));
    }

    /** 걸러낼 도메인을 비우면 필터가 꺼져 QA 계정도 집계에 들어간다. */
    @Test
    void blankTestAccountDomainDisablesTheFilter() {
        Member member = Member.builder()
                .email("QA123@FEEDFLOW.TEST")
                .build();
        FarmCustomer farm = FarmCustomer.registeredMember(
                member, "F-QA", "QA 자동화 농장", "QA", "010",
                "00000", "주소", 35.0, 126.0, "미등록",
                0, 40, "상담 후 지정", 0, warehouse, 0, "QA");

        assertTrue(DemandPlanService.isOperationalFarm(farm, ""));
        assertTrue(DemandPlanService.isOperationalFarm(farm, null));
    }

    /** 다른 QA 도메인을 쓰면 그 도메인이 걸러진다. */
    @Test
    void configuredDomainIsHonoured() {
        Member member = Member.builder()
                .email("qa1@qa.example.com")
                .build();
        FarmCustomer farm = FarmCustomer.registeredMember(
                member, "F-QA2", "QA 농장", "QA", "010",
                "00000", "주소", 35.0, 126.0, "미등록",
                0, 40, "상담 후 지정", 0, warehouse, 0, "QA");

        assertFalse(DemandPlanService.isOperationalFarm(
                farm, "@qa.example.com"));
        assertTrue(DemandPlanService.isOperationalFarm(
                farm, TEST_ACCOUNT_DOMAIN));
    }

    @Test
    void activeFarmWithoutTestAccountRemainsInOperationalDemand() {
        FarmCustomer farm = new FarmCustomer(
                "F-001", "운영 농장", "대표", "010", "00000", "주소",
                35.0, 126.0, "소", 100, 300, "한우 사료", 10,
                warehouse, 0, CustomerStatus.ACTIVE, "운영");

        assertTrue(DemandPlanService.isOperationalFarm(
                farm, TEST_ACCOUNT_DOMAIN));
    }

    @Test
    void pausedFarmIsExcludedFromOperationalDemand() {
        FarmCustomer farm = new FarmCustomer(
                "F-002", "중지 농장", "대표", "010", "00000", "주소",
                35.0, 126.0, "소", 100, 300, "한우 사료", 10,
                warehouse, 0, CustomerStatus.PAUSED, "중지");

        assertFalse(DemandPlanService.isOperationalFarm(
                farm, TEST_ACCOUNT_DOMAIN));
    }

    @Test
    void deliveryScheduleIncludesClickableFarmDetails() {
        ReflectionTestUtils.setField(warehouse, "warehouseId", 5L);
        FarmCustomer farm = new FarmCustomer(
                "F-003", "문평 한우농장", "대표", "010", "00000", "주소",
                35.0, 126.0, "한우", 100, 690, "한우 사료", 24,
                warehouse, 0, CustomerStatus.ACTIVE, "운영");
        ReflectionTestUtils.setField(farm, "farmCustomerId", 3L);

        WarehouseRepository warehouseRepository = mock(WarehouseRepository.class);
        FarmCustomerRepository farmRepository = mock(FarmCustomerRepository.class);
        BinInventoryRepository inventoryRepository = mock(BinInventoryRepository.class);
        SellableStockQuery sellableStockQuery = mock(SellableStockQuery.class);
        when(warehouseRepository.findAllByActiveTrueOrderByDisplayOrderAsc())
                .thenReturn(List.of(warehouse));
        when(farmRepository
                .findAllByOrderByAssignedWarehouseDisplayOrderAscFarmNameAsc())
                .thenReturn(List.of(farm));
        when(inventoryRepository.findAllByOrderByBinBinCodeAsc())
                .thenReturn(List.of());
        when(sellableStockQuery.sellableByWarehouseAndProductIds(List.of()))
                .thenReturn(Map.of());

        DemandPlanService service = new DemandPlanService(
                warehouseRepository, farmRepository, inventoryRepository,
                sellableStockQuery);
        var schedule = service.plan(LocalDate.of(2026, 8, 21))
                .schedule().getFirst();

        assertEquals(24, schedule.day());
        assertEquals(1, schedule.farmCount());
        assertEquals(690, schedule.amount());
        assertEquals("문평 한우농장", schedule.farms().getFirst().farmName());
        assertEquals("나주 문평 창고", schedule.farms().getFirst().warehouseName());
        assertEquals("소", schedule.farms().getFirst().animalType());
    }
}
