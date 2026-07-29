package com.feedflow.admin.controller;

import com.feedflow.admin.service.WarehouseMapService;
import com.feedflow.domain.Warehouse;
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
 * 창고 2D 평면도 화면 컨트롤러 (HTML 렌더링).
 * <p>
 * 창고(동)마다 도면을 한 장씩 그리고 화면에서는 탭으로 전환한다.
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

    /** GNB 활성화용 (기준 정보 > 창고 2D 도면) */
    @ModelAttribute("menu")
    public String menu() {
        return "warehouseMap";
    }

    /** 창고 전환 탭 */
    @ModelAttribute("warehouses")
    public List<Warehouse> warehouses() {
        return warehouseMapService.getWarehouses();
    }

    /**
     * 창고 평면도.
     *
     * @param warehouse 조회할 창고. 지정하지 않으면 제1창고를 보여준다.
     *                  (전체를 한 도면에 겹쳐 그리면 서로 다른 건물의 구역이 섞여 위치를 오해한다)
     */
    @GetMapping
    public String map(@RequestParam(name = "warehouse", required = false) Warehouse warehouse,
                      Model model) {

        Warehouse target = warehouse != null ? warehouse : Warehouse.WH1;

        model.addAttribute("floorPlan", warehouseMapService.getFloorPlan(target, LocalDate.now()));
        model.addAttribute("selectedWarehouse", target);
        return MAP_VIEW;
    }
}
