package com.feedflow.admin.controller;

import com.feedflow.admin.dto.TraceabilityDto;
import com.feedflow.admin.service.TraceabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 제품 이력 추적 조회 API (JSON).
 * <p>
 * 화면 전체를 다시 그리지 않고 특정 로트의 이력만 가져올 때 쓴다.
 * (재고 현황 · 창고 도면 등 다른 화면에서 로트 이력을 팝업으로 띄우는 용도)
 * <p>
 * {@code /api/admin/**} 경로는 SecurityConfig 에서
 * {@code hasAnyRole("STAFF","ADMIN")} 로 차단되므로 인증 없는 호출은 도달하지 못한다.
 */
@RestController
@RequestMapping("/api/admin/traceability")
@RequiredArgsConstructor
public class TraceabilityApiController {

    private final TraceabilityService traceabilityService;

    /**
     * 로트 하나의 생애주기 조회.
     *
     * @param lotId 추적할 로트
     */
    @GetMapping("/lots/{lotId}")
    public TraceabilityDto trace(@PathVariable("lotId") Long lotId) {
        return traceabilityService.trace(lotId, LocalDate.now());
    }
}
