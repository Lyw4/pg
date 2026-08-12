package com.ex.controller;

import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.Map;

import jakarta.servlet.http.HttpSession;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ex.dto.FarmDashboardResponse;
import com.ex.service.FarmInsightService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/farm-insights")
@RequiredArgsConstructor
public class FarmInsightController {

    private final FarmInsightService farmInsightService;

    public record UsageRequest(String month, int actualQuantity, String note) {}
    public record FeedbackRequest(Long productId, boolean suitable, String comment) {}

    @GetMapping
    public FarmDashboardResponse dashboard(HttpSession session) {
        return farmInsightService.dashboard(memberId(session));
    }

    @PostMapping("/usages")
    public FarmDashboardResponse.UsagePoint usage(
            @RequestBody UsageRequest request,
            HttpSession session) {
        YearMonth month;
        try {
            month = YearMonth.parse(request.month());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("사용 월을 YYYY-MM 형식으로 입력해 주세요.");
        }
        return farmInsightService.recordUsage(
                memberId(session), month,
                request.actualQuantity(), request.note());
    }

    @PostMapping("/feedback")
    public Map<String, String> feedback(
            @RequestBody FeedbackRequest request,
            HttpSession session) {
        farmInsightService.saveFeedback(
                memberId(session), request.productId(),
                request.suitable(), request.comment());
        return Map.of("message", "추천 평가가 저장되어 다음 추천에 반영됩니다.");
    }

    @GetMapping(value = "/monthly-report.csv", produces = "text/csv")
    public ResponseEntity<byte[]> monthlyReport(HttpSession session) {
        byte[] report = farmInsightService.monthlyReportCsv(memberId(session));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType(
                "text", "csv", StandardCharsets.UTF_8));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("feedflow-farm-monthly-report.csv",
                        StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(headers).body(report);
    }

    private Long memberId(HttpSession session) {
        Object value = session.getAttribute("memberId");
        if (value instanceof Number number) return number.longValue();
        throw new IllegalArgumentException("로그인이 필요합니다.");
    }
}
