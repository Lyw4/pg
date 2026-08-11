package com.ex.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ex.service.WmsOperationsService;
import com.ex.service.WmsOperationsService.BinDetail;

import lombok.RequiredArgsConstructor;

/** 창고 도면의 구역 타일을 클릭했을 때 모달에 표시할 상세 정보 API. */
@RestController
@RequestMapping("/api/admin/warehouse-map")
@RequiredArgsConstructor
public class WmsWarehouseMapApiController {

    private final WmsOperationsService wmsOperationsService;

    @GetMapping("/bins/{binId}")
    public BinDetail binDetail(
            @PathVariable(name = "binId") Long binId) {
        return wmsOperationsService.binDetail(binId);
    }
}
