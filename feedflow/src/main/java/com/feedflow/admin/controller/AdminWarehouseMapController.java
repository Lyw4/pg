package com.feedflow.admin.controller;

import com.feedflow.admin.dto.CenterDto;
import com.feedflow.admin.dto.WarehouseFloorPlanDto;
import com.feedflow.admin.service.WarehouseBinService;
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
 * 창고 2D 평면도 화면 컨트롤러 (HTML 렌더링).
 * <p>
 * 물류센터마다 도면을 한 장씩 그리고 화면에서는 탭으로 전환한다.
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
    private final WarehouseBinService warehouseBinService;

    /** GNB 활성화용 (기준 정보 > 창고 2D 도면) */
    @ModelAttribute("menu")
    public String menu() {
        return "warehouseMap";
    }

    /**
     * 센터 평면도.
     * <p>
     * 센터 목록은 탭을 그리는 데도 쓰고 기본 센터를 정하는 데도 쓰므로
     * {@code @ModelAttribute} 로 따로 조회하지 않고 한 번만 읽어 모델에 담는다.
     * (별도 메서드로 두면 요청마다 같은 조회가 두 번 나간다)
     *
     * @param centerId 조회할 센터. 지정하지 않으면 첫 번째 센터를 보여준다.
     *                 (전체를 한 도면에 겹쳐 그리면 서로 다른 센터의 구역이 섞여 위치를 오해한다)
     */
    @GetMapping
    public String map(@RequestParam(name = "centerId", required = false) Long centerId,
                      Model model) {

        List<CenterDto> centers = warehouseBinService.getActiveCenters();

        // 센터를 지정하지 않았으면 목록의 첫 센터를 보여준다.
        // enum 시절에는 WH1 을 기본값으로 썼지만 센터는 운영 중에 늘고 줄어드므로
        // 특정 코드를 코드에 박아두면 그 센터가 사라졌을 때 화면이 빈다.
        Long target = centerId != null ? centerId
                : (centers.isEmpty() ? null : centers.get(0).getCenterId());

        // 운영 중인 센터가 하나도 없으면 조회하지 않는다.
        // centerId 를 null 로 넘기면 전체 센터의 구역이 한 도면에 섞여 내려온다.
        WarehouseFloorPlanDto floorPlan = target == null
                ? WarehouseFloorPlanDto.empty()
                : warehouseMapService.getFloorPlan(target, LocalDate.now());

        model.addAttribute("centers", centers);
        model.addAttribute("floorPlan", floorPlan);
        model.addAttribute("selectedCenterId", target);
        return MAP_VIEW;
    }
}
