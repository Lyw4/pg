package com.ex.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.ex.entity.DataInitializationMarker;
import com.ex.repository.DataInitializationMarkerRepository;
import com.ex.service.WarehouseInventoryRebalanceService;

import lombok.RequiredArgsConstructor;

/**
 * 과거 고정 월 배치량과 실제 시연 재고가 서로 달라 대부분의 품목이 부족으로
 * 표시되던 데이터를 농장 수요 기준으로 한 번만 정합화한다.
 *
 * <p>완료 표식을 별도 테이블에 남기므로 이후 주문·출고로 생긴 실제 부족 재고를
 * 애플리케이션 재시작이 자동으로 보충하지 않는다.</p>
 */
@Component
@Order(400)
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "feedflow.inventory.demand-baseline-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class DemandStockBaselineInitializer implements ApplicationRunner {

    static final String MARKER_KEY = "warehouse-demand-stock-baseline-v1";

    private final DataInitializationMarkerRepository markerRepository;
    private final WarehouseInventoryRebalanceService rebalanceService;

    @Override
    public void run(ApplicationArguments args) {
        if (markerRepository.existsById(MARKER_KEY)) {
            return;
        }

        var report = rebalanceService.rebalanceAll("초기 농장 수요 재고 정합화");
        if (!report.successful()) {
            throw new IllegalStateException(
                    "초기 농장 수요 재고 정합화에 실패했습니다: "
                            + String.join(" / ", report.failures()));
        }

        markerRepository.save(new DataInitializationMarker(MARKER_KEY));
    }
}
