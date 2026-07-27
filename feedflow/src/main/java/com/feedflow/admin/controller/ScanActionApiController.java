package com.feedflow.admin.controller;

import com.feedflow.admin.dto.InboundResultDto;
import com.feedflow.admin.dto.OutboundResultDto;
import com.feedflow.admin.dto.ScanInboundRequest;
import com.feedflow.admin.dto.ScanOutboundRequest;
import com.feedflow.admin.service.BarcodeScanService;
import com.feedflow.security.LoginUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 바코드 스캔 후 즉시 입출고 처리 API (현장 작업용).
 * <p>
 * 스캔 화면에서 코드를 인식한 뒤 수량만 입력하면 바로 처리된다.
 * 실제 재고 처리는 검증된 {@code InventoryService} / {@code OutboundService} 로직을 재사용한다.
 */
@RestController
@RequestMapping("/api/admin/scan")
@RequiredArgsConstructor
public class ScanActionApiController {

    private final BarcodeScanService barcodeScanService;

    /** 스캔 즉시 입고 : POST /api/admin/scan/inbound */
    @PostMapping("/inbound")
    public ResponseEntity<InboundResultDto> inbound(@Valid @RequestBody ScanInboundRequest request,
                                                    @AuthenticationPrincipal LoginUser loginUser) {
        Long userId = (loginUser == null) ? null : loginUser.getUserId();
        String userName = (loginUser == null) ? null : loginUser.getDisplayName();

        return ResponseEntity.ok(barcodeScanService.receiveByCode(request, userId, userName));
    }

    /** 스캔 즉시 출고 (FEFO) : POST /api/admin/scan/outbound */
    @PostMapping("/outbound")
    public ResponseEntity<OutboundResultDto> outbound(@Valid @RequestBody ScanOutboundRequest request,
                                                      @AuthenticationPrincipal LoginUser loginUser) {
        Long userId = (loginUser == null) ? null : loginUser.getUserId();
        String userName = (loginUser == null) ? null : loginUser.getDisplayName();

        return ResponseEntity.ok(barcodeScanService.dispatchByCode(request, userId, userName));
    }
}
