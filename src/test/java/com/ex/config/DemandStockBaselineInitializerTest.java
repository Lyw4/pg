package com.ex.config;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import com.ex.entity.DataInitializationMarker;
import com.ex.repository.DataInitializationMarkerRepository;
import com.ex.service.WarehouseInventoryRebalanceService;
import com.ex.service.WarehousePlanSeeder;

class DemandStockBaselineInitializerTest {

    private DataInitializationMarkerRepository markerRepository;
    private WarehouseInventoryRebalanceService rebalanceService;
    private DemandStockBaselineInitializer initializer;

    @BeforeEach
    void setUp() {
        markerRepository = org.mockito.Mockito.mock(
                DataInitializationMarkerRepository.class);
        rebalanceService = org.mockito.Mockito.mock(
                WarehouseInventoryRebalanceService.class);
        initializer = new DemandStockBaselineInitializer(
                markerRepository, rebalanceService);
    }

    @Test
    void completedBaselineIsNotAppliedAgain() throws Exception {
        when(markerRepository.existsById(
                DemandStockBaselineInitializer.MARKER_KEY)).thenReturn(true);

        initializer.run(new DefaultApplicationArguments(new String[0]));

        verify(rebalanceService, never()).rebalanceAll(
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void successfulBaselineIsMarkedComplete() throws Exception {
        when(rebalanceService.rebalanceAll(
                "초기 농장 수요 재고 정합화"))
                .thenReturn(new WarehouseInventoryRebalanceService
                        .RebalanceReport(recommendation(), List.of(), List.of()));

        initializer.run(new DefaultApplicationArguments(new String[0]));

        verify(markerRepository).save(
                org.mockito.ArgumentMatchers.argThat(marker ->
                        DemandStockBaselineInitializer.MARKER_KEY.equals(
                                marker.getMarkerKey())));
    }

    @Test
    void failedBaselineIsNotMarkedComplete() {
        when(rebalanceService.rebalanceAll(
                "초기 농장 수요 재고 정합화"))
                .thenReturn(new WarehouseInventoryRebalanceService
                        .RebalanceReport(
                                recommendation(), List.of(), List.of("입고 실패")));

        assertThrows(IllegalStateException.class, () -> initializer.run(
                new DefaultApplicationArguments(new String[0])));

        verify(markerRepository, never()).save(
                org.mockito.ArgumentMatchers.any(DataInitializationMarker.class));
    }

    private WarehousePlanSeeder.DemandRecommendationResult recommendation() {
        return new WarehousePlanSeeder.DemandRecommendationResult(
                0, 0, 0, 0, 0, 0);
    }
}
