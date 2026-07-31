package com.feedflow.admin.controller;

import com.feedflow.admin.dto.AllocationPlanDto;
import com.feedflow.admin.service.OutboundService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 출고 관련 JSON API.
 * <p>
 * 출고 화면에서 품목/수량을 선택하는 즉시 <b>실제 출고 가능 재고와 FEFO 할당 계획</b>을
 * 미리 보여주기 위한 엔드포인트다.
 * <p>
 * 품목의 totalStock(전체 재고)과 출고 가능 재고는 다를 수 있다.
 * 유통기한 경과 로트, 사용 중지 구역, 입고 대기 · 검수 구역, 운송 중 재고가
 * 출고 대상에서 제외되기 때문이다.
 * ({@code InventoryRepository.findAllocatableByProductId} 참고)
 */
@RestController
@RequestMapping("/api/admin/outbound")
@RequiredArgsConstructor
public class OutboundApiController {

    private final OutboundService outboundService;

    /**
     * FEFO 할당 미리보기 (재고를 변경하지 않는다).
     * GET /api/admin/outbound/preview?productId=1&quantity=300
     */
    @GetMapping("/preview")
    public ResponseEntity<AllocationPlanDto> preview(
            @RequestParam(name = "productId") Long productId,
            @RequestParam(name = "quantity", defaultValue = "0") int quantity) {

        return ResponseEntity.ok(outboundService.previewAllocation(productId, Math.max(quantity, 0)));
    }
}
