package com.feedflow.admin.controller;

import com.feedflow.admin.dto.BinDetailDto;
import com.feedflow.admin.service.WarehouseMapService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 창고 2D 맵 상세 조회 API (JSON).
 * <p>
 * 도면에서 구역 타일을 클릭했을 때 모달에 뿌릴 재고 상세를 비동기(fetch)로 내려준다.
 * 화면 전체를 다시 그리지 않기 위해 REST 로 분리했다.
 * <p>
 * {@code /api/admin/**} 경로는 SecurityConfig 에서 {@code hasAnyRole("STAFF","ADMIN")} 로
 * 차단되므로 인증 없는 호출은 도달하지 못한다.
 */
@RestController
@RequestMapping("/api/admin/warehouse-map")
@RequiredArgsConstructor
public class WarehouseMapApiController {

    private final WarehouseMapService warehouseMapService;

    /**
     * 구역 상세 (보관 중인 품목 / 로트 / 수량 / 유통기한).
     *
     * @param binId 조회할 구역
     */
    @GetMapping("/bins/{binId}")
    public BinDetailDto binDetail(@PathVariable("binId") Long binId) {
        return warehouseMapService.getBinDetail(binId, LocalDate.now());
    }
}
