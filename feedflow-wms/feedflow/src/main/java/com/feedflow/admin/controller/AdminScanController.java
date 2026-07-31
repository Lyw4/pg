package com.feedflow.admin.controller;

import com.feedflow.admin.service.BarcodeScanService;
import com.feedflow.admin.service.WarehouseBinService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.LocalDate;

/**
 * 현장 작업자용 바코드 스캔 화면.
 * 실제 조회/처리는 {@link BarcodeApiController} / {@link ScanActionApiController} 를 fetch 로 호출한다.
 */
@Controller
@RequestMapping("/admin/scan")
@RequiredArgsConstructor
public class AdminScanController {

    private final WarehouseBinService warehouseBinService;
    private final BarcodeScanService barcodeScanService;

    @ModelAttribute("menu")
    public String menu() {
        return "scan";
    }

    /** 스캔 화면 (조회 + 즉시 입출고) */
    @GetMapping
    public String scanPage(Model model) {
        // 스캔 후 즉시 입고할 때 선택할 구역 목록
        model.addAttribute("bins", warehouseBinService.getActiveBins());
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("subMenu", "scan");
        return "admin/scan/index";
    }

    /**
     * 테스트 / 현장 부착용 QR 라벨 목록.
     * 화면에 표시된 QR 을 모바일 카메라로 스캔해 동작을 확인할 수 있다.
     */
    @GetMapping("/labels")
    public String labels(Model model) {
        model.addAttribute("lotLabels", barcodeScanService.getLotLabels());
        model.addAttribute("productLabels", barcodeScanService.getProductLabels());
        model.addAttribute("subMenu", "labels");
        return "admin/scan/labels";
    }
}
