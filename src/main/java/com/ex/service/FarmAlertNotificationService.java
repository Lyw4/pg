package com.ex.service;

import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.ex.repository.FarmCustomerRepository;

import lombok.RequiredArgsConstructor;

/** 재고·유통기한·정기배송·배송 지연을 매일 이메일/SMS로 안내합니다. */
@Service
@RequiredArgsConstructor
public class FarmAlertNotificationService {

    private static final Logger log = LoggerFactory.getLogger(
            FarmAlertNotificationService.class);

    private final FarmCustomerRepository farmCustomerRepository;
    private final FarmInsightService farmInsightService;
    private final PasswordResetMailSender mailSender;
    private final PasswordResetSmsSender smsSender;

    @Value("${feedflow.notifications.scheduled-enabled:true}")
    private boolean scheduledEnabled;

    @Scheduled(cron = "${feedflow.notifications.cron:0 0 9 * * *}")
    public void sendDailyAlerts() {
        if (!scheduledEnabled) return;
        farmCustomerRepository
                .findAllByOrderByAssignedWarehouseDisplayOrderAscFarmNameAsc()
                .stream()
                .filter(farm -> !farm.isDemoData() && farm.getMember() != null)
                .forEach(farm -> {
                    try {
                        var dashboard = farmInsightService.dashboard(
                                farm.getMember().getId());
                        if (dashboard.alerts().isEmpty()) return;
                        String summary = dashboard.alerts().stream()
                                .map(alert -> "- " + alert.title() + ": "
                                        + alert.message())
                                .collect(Collectors.joining("\n"));
                        if (mailSender.isConfigured()) {
                            mailSender.sendNotice(
                                    farm.getMember().getEmail(),
                                    "[FeedFlow] " + farm.getFarmName()
                                            + " 운영 알림",
                                    farm.getRepresentativeName()
                                            + "님, 확인할 농장 운영 알림입니다.\n\n"
                                            + summary
                                            + "\n\n마이페이지에서 상세 내용을 확인해 주세요.");
                        }
                        if (smsSender.isEnabled()) {
                            String sms = "[FeedFlow] " + farm.getFarmName()
                                    + " 알림 " + dashboard.alerts().size()
                                    + "건: " + dashboard.alerts().getFirst().title()
                                    + " (마이페이지 확인)";
                            smsSender.sendNotice(farm.getPhone(), sms);
                        }
                    } catch (RuntimeException exception) {
                        log.warn("농장 자동 알림 발송 실패. farmCode={}, reason={}",
                                farm.getFarmCode(), exception.getMessage());
                    }
                });
    }
}
