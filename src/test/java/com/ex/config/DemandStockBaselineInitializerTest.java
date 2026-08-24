package com.ex.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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

    /**
     * 정합화 실패는 기동을 막지 않는다.
     *
     * <p>예전에는 예외를 던져 애플리케이션이 아예 뜨지 못했다. 완료 표식은
     * 성공 시에만 저장하므로, 데이터를 사람이 고치기 전까지 영구히 시작 불가
     * 상태가 됐다. 기준 데이터 보정 실패로 본업까지 멈추지 않도록 바꿨다.
     */
    @Test
    void failedBaselineDoesNotBlockStartupAndIsNotMarkedComplete() {
        when(rebalanceService.rebalanceAll(
                "초기 농장 수요 재고 정합화"))
                .thenReturn(new WarehouseInventoryRebalanceService
                        .RebalanceReport(
                                recommendation(), List.of(), List.of("입고 실패")));

        assertDoesNotThrow(() -> initializer.run(
                new DefaultApplicationArguments(new String[0])));

        verify(markerRepository, never()).save(
                org.mockito.ArgumentMatchers.any(DataInitializationMarker.class));
    }

    /** 표식을 남기지 않았으므로 다음 기동에서 다시 시도한다. */
    @Test
    void failedBaselineIsRetriedOnNextStartup() throws Exception {
        when(rebalanceService.rebalanceAll(
                "초기 농장 수요 재고 정합화"))
                .thenReturn(new WarehouseInventoryRebalanceService
                        .RebalanceReport(
                                recommendation(), List.of(), List.of("입고 실패")));

        initializer.run(new DefaultApplicationArguments(new String[0]));
        initializer.run(new DefaultApplicationArguments(new String[0]));

        verify(rebalanceService, org.mockito.Mockito.times(2))
                .rebalanceAll("초기 농장 수요 재고 정합화");
    }

    private WarehousePlanSeeder.DemandRecommendationResult recommendation() {
        return new WarehousePlanSeeder.DemandRecommendationResult(
                0, 0, 0, 0, 0, 0);
    }
}
