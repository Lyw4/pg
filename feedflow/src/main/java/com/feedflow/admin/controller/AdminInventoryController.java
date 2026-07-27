package com.feedflow.admin.controller;

import com.feedflow.admin.dto.InboundForm;
import com.feedflow.admin.dto.InboundResultDto;
import com.feedflow.admin.service.InventoryService;
import com.feedflow.admin.service.ProductService;
import com.feedflow.admin.service.WarehouseBinService;
import com.feedflow.common.exception.BusinessRuleException;
import com.feedflow.domain.MovementType;
import com.feedflow.security.LoginUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

/**
 * 재고 관리 화면 컨트롤러 (재고 현황 / 입고 등록 / 입출고 이력).
 * <p>
 * 입고는 창고 실무자의 업무이므로 STAFF·ADMIN 모두 처리할 수 있다.
 */
@Controller
@RequestMapping("/admin/inventory")
@RequiredArgsConstructor
public class AdminInventoryController {

    private static final int MOVEMENT_PAGE_SIZE = 15;

    private static final String LIST_VIEW = "admin/inventory/list";
    private static final String INBOUND_VIEW = "admin/inventory/inbound";
    private static final String MOVEMENTS_VIEW = "admin/inventory/movements";

    private final InventoryService inventoryService;
    private final ProductService productService;
    private final WarehouseBinService warehouseBinService;

    @ModelAttribute("menu")
    public String menu() {
        return "inventory";
    }

    /* ------------------------------------------------------------------
     * 재고 현황
     * ------------------------------------------------------------------ */

    @GetMapping
    public String list(@RequestParam(name = "productId", required = false) Long productId,
                       @RequestParam(name = "binId", required = false) Long binId,
                       @RequestParam(name = "zone", required = false) String zone,
                       Model model) {

        model.addAttribute("inventories", inventoryService.getInventories(productId, binId, zone));
        model.addAttribute("stockedLocationCount", inventoryService.getStockedLocationCount());
        model.addAttribute("totalStoredQuantity", inventoryService.getTotalStoredQuantity());
        model.addAttribute("todayInboundCount", inventoryService.getTodayInboundCount());
        model.addAttribute("todayInboundQuantity", inventoryService.getTodayInboundQuantity());

        model.addAttribute("products", productService.getActiveProducts());
        model.addAttribute("bins", warehouseBinService.getActiveBins());
        model.addAttribute("zones", warehouseBinService.getZones());

        model.addAttribute("selectedProductId", productId);
        model.addAttribute("selectedBinId", binId);
        model.addAttribute("selectedZone", zone);
        model.addAttribute("subMenu", "list");
        return LIST_VIEW;
    }

    /* ------------------------------------------------------------------
     * 입고 등록
     * ------------------------------------------------------------------ */

    @GetMapping("/inbound")
    public String inboundForm(Model model) {
        InboundForm form = new InboundForm();
        form.setManufacturedDate(LocalDate.now());

        model.addAttribute("inboundForm", form);
        prepareInboundForm(model);
        return INBOUND_VIEW;
    }

    @PostMapping("/inbound")
    public String receive(@Valid @ModelAttribute("inboundForm") InboundForm inboundForm,
                          BindingResult bindingResult,
                          @AuthenticationPrincipal LoginUser loginUser,
                          Model model,
                          RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            prepareInboundForm(model);
            return INBOUND_VIEW;
        }

        Long userId = (loginUser == null) ? null : loginUser.getUserId();
        String userName = (loginUser == null) ? null : loginUser.getDisplayName();

        try {
            InboundResultDto result = inventoryService.receive(inboundForm, userId, userName);
            redirectAttributes.addFlashAttribute("successMessage", result.getSummaryMessage());
            redirectAttributes.addFlashAttribute("inboundResult", result);
        } catch (BusinessRuleException e) {
            // 업무 규칙 위반은 폼 전역 오류로 표시하여 입력값을 유지한다.
            bindingResult.reject("businessRule", e.getMessage());
            prepareInboundForm(model);
            return INBOUND_VIEW;
        }

        return "redirect:/admin/inventory/inbound";
    }

    /* ------------------------------------------------------------------
     * 입·출고 이력
     * ------------------------------------------------------------------ */

    @GetMapping("/movements")
    public String movements(@RequestParam(name = "movementType", required = false) MovementType movementType,
                            @RequestParam(name = "productId", required = false) Long productId,
                            @RequestParam(name = "page", defaultValue = "0") int page,
                            Model model) {

        Pageable pageable = PageRequest.of(Math.max(page, 0), MOVEMENT_PAGE_SIZE,
                Sort.by("movementId").descending());

        model.addAttribute("movements", inventoryService.getMovements(movementType, productId, pageable));
        model.addAttribute("movementTypes", MovementType.values());
        model.addAttribute("products", productService.getActiveProducts());
        model.addAttribute("selectedMovementType", movementType);
        model.addAttribute("selectedProductId", productId);
        model.addAttribute("subMenu", "movements");
        return MOVEMENTS_VIEW;
    }

    /* ------------------------------------------------------------------
     * 내부
     * ------------------------------------------------------------------ */

    /** 입고 폼의 품목 / 구역 선택 목록 세팅 */
    private void prepareInboundForm(Model model) {
        model.addAttribute("products", productService.getActiveProducts());
        model.addAttribute("bins", warehouseBinService.getActiveBins());
        model.addAttribute("subMenu", "inbound");
    }
}
