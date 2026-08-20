package com.ex.service;

import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class FarmDeliveryScheduler {

    private final FarmDeliveryAutomationService automationService;

    @Scheduled(
            cron = "${feedflow.farm-delivery.cron:0 5 0 * * *}",
            zone = "${feedflow.farm-delivery.zone:Asia/Seoul}")
    public void createDueFarmDeliveries() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        var result = automationService.execute(today, "SCHEDULED");
        if (result.createdCount() > 0 || result.requestedCount() > 0
                || result.failedCount() > 0) {
            log.info("농장 정기 납품 자동 처리: date={}, created={}, requested={}, skipped={}, failed={}",
                    today, result.createdCount(), result.requestedCount(),
                    result.skippedCount(), result.failedCount());
        }
    }
}
