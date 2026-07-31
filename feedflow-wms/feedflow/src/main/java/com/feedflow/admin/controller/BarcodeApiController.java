package com.feedflow.admin.controller;

import com.feedflow.admin.dto.ScanResultDto;
import com.feedflow.admin.service.BarcodeScanService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 바코드 / QR 스캔 조회 API.
 * <p>
 * 브라우저(HTML5 카메라)에서 인식한 코드를 fetch 로 전달받아
 * 품목 / 로트 / 구역별 재고 정보를 JSON 으로 반환한다.
 * <ul>
 *     <li>200 OK        : 조회 성공</li>
 *     <li>404 Not Found : 등록되지 않은 코드</li>
 *     <li>400 Bad Request : 코드가 비어있거나 형식이 잘못된 경우</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class BarcodeApiController {

    private final BarcodeScanService barcodeScanService;

    /**
     * 스캔 조회.
     * GET /api/admin/scan?code=LOT-CT-2601
     */
    @GetMapping("/scan")
    public ResponseEntity<ScanResultDto> scan(@RequestParam(name = "code") String code) {
        return ResponseEntity.ok(barcodeScanService.scan(code));
    }
}
