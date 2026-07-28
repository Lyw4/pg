package com.feedflow.admin.controller;

import com.feedflow.admin.dto.WarehouseMapZoneDto;
import com.feedflow.admin.service.WarehouseMapService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

/**
 * 창고 2D 도면 맵 화면 컨트롤러 (HTML 렌더링).
 * <p>
 * 구역별 적재 상태를 색으로 구분해 한눈에 보여준다.
 * 조회 전용이므로 STAFF · ADMIN 모두 접근할 수 있다.
 * ({@code /admin/**} 는 SecurityConfig 에서 {@code hasAnyRole("STAFF","ADMIN")} 로 제한)
 * <p>
 * 구역 클릭 시의 상세 데이터는 {@link WarehouseMapApiController} 가 JSON 으로 제공한다.
 */
@Controller
@RequestMapping("/admin/warehouse-map")
@RequiredArgsConstructor
public class AdminWarehouseMapController {

    private static final String MAP_VIEW = "admin/warehouse/map";

    private final WarehouseMapService warehouseMapService;

    /** GNB 활성화용 (기준 정보 > 창고 도면) */
    @ModelAttribute("menu")
    public String menu() {
        return "warehouseMap";
    }

    /** 구역 그룹 필터 목록 */
    @ModelAttribute("zoneCodes")
    public List<String> zoneCodes() {
        return warehouseMapService.getZoneCodes();
    }

    @GetMapping
    public String map(@RequestParam(name = "zone", required = false) String zone,
                      Model model) {

        LocalDate today = LocalDate.now();
        List<WarehouseMapZoneDto> zones = warehouseMapService.getZones(zone, today);

        model.addAttribute("zones", zones);
        model.addAttribute("summary", warehouseMapService.getSummary(zones));
        model.addAttribute("selectedZone", zone);
        return MAP_VIEW;
    }
}
