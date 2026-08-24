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
import lombok.extern.slf4j.Slf4j;

/**
 * 과거 고정 월 배치량과 실제 시연 재고가 서로 달라 대부분의 품목이 부족으로
 * 표시되던 데이터를 농장 수요 기준으로 한 번만 정합화한다.
 *
 * <p>완료 표식을 별도 테이블에 남기므로 이후 주문·출고로 생긴 실제 부족 재고를
 * 애플리케이션 재시작이 자동으로 보충하지 않는다.</p>
 *
 * <p>정합화가 실패해도 애플리케이션 기동은 막지 않는다. 이 작업은 기준 데이터를
 * 다듬는 보조 작업이라 서비스 가용성보다 우선하지 않는다.</p>
 */
@Slf4j
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
            // 예외를 던지면 ApplicationRunner 단계에서 기동이 중단됩니다. 완료
            // 표식은 성공했을 때만 저장하므로, 데이터를 사람이 고치기 전까지
            // 애플리케이션이 영구히 뜨지 못하는 상태가 됩니다. 기준 데이터 보정
            // 실패로 주문·출고 같은 본업까지 멈추는 것은 균형이 맞지 않습니다.
            //
            // 그래서 실패를 크게 남기고 기동은 계속합니다. 표식을 남기지 않으니
            // 다음 기동에서 자동으로 다시 시도합니다.
            log.error(
                    "초기 농장 수요 재고 정합화에 실패했습니다."
                            + " 기동은 계속하고 다음 기동에서 다시 시도합니다."
                            + " 실패 {}건: {}",
                    report.failures().size(),
                    String.join(" / ", report.failures()));
            return;
        }

        markerRepository.save(new DataInitializationMarker(MARKER_KEY));
    }
}
