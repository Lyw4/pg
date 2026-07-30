package com.feedflow.admin.controller;

import com.feedflow.admin.dto.StockMoveForm;
import com.feedflow.admin.dto.StockMoveResultDto;
import com.feedflow.admin.service.InventoryMoveService;
import com.feedflow.admin.service.WarehouseBinService;
import com.feedflow.common.exception.BusinessRuleException;
import com.feedflow.security.LoginUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

/**
 * 구역 간 재고 이동 화면 컨트롤러.
 * <p>
 * 창고 안에서 물건을 옮기는 것은 현장 실무이므로 STAFF · ADMIN 모두 처리할 수 있다.
 * 총 재고가 변하지 않아(위치만 바뀜) 매출이나 장부에 영향을 주지 않기 때문에
 * 폐기 · 출고 취소처럼 ADMIN 전용으로 제한하지 않는다.
 */
@Controller
@RequestMapping("/admin/inventory/move")
@RequiredArgsConstructor
public class AdminInventoryMoveController {

    private static final String MOVE_VIEW = "admin/inventory/move";

    private final InventoryMoveService inventoryMoveService;
    private final WarehouseBinService warehouseBinService;

    @ModelAttribute("menu")
    public String menu() {
        return "inventory";
    }

    /**
     * 이동 화면.
     *
     * @param binId       특정 구역의 재고만 골라 볼 때 (2D 도면 모달에서 넘어오는 경로)
     * @param inventoryId 이동 대상을 미리 선택해 둘 재고 행
     */
    @GetMapping
    public String moveForm(@RequestParam(name = "binId", required = false) Long binId,
                           @RequestParam(name = "inventoryId", required = false) Long inventoryId,
                           Model model) {

        StockMoveForm form = new StockMoveForm();
        form.setInventoryId(inventoryId);

        model.addAttribute("stockMoveForm", form);
        prepareForm(model, binId);
        return MOVE_VIEW;
    }

    @PostMapping
    public String move(@Valid @ModelAttribute("stockMoveForm") StockMoveForm stockMoveForm,
                       BindingResult bindingResult,
                       @RequestParam(name = "binId", required = false) Long binId,
                       @AuthenticationPrincipal LoginUser loginUser,
                       Model model,
                       RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            prepareForm(model, binId);
            return MOVE_VIEW;
        }

        Long userId = LoginUser.idOf(loginUser);
        String userName = LoginUser.nameOf(loginUser);

        try {
            StockMoveResultDto result = inventoryMoveService.move(stockMoveForm, userId, userName);
            redirectAttributes.addFlashAttribute(FlashAttr.SUCCESS, result.getSummaryMessage());
            redirectAttributes.addFlashAttribute("moveResult", result);
        } catch (BusinessRuleException e) {
            // 업무 규칙 위반은 폼 전역 오류로 표시해 입력값을 유지한다
            bindingResult.reject("businessRule", e.getMessage());
            prepareForm(model, binId);
            return MOVE_VIEW;
        }

        return "redirect:/admin/inventory/move";
    }

    /** 이동 폼의 재고 목록 / 구역 선택 목록 세팅 */
    private void prepareForm(Model model, Long binId) {
        model.addAttribute("inventories", inventoryMoveService.getMovableInventories(binId));

        // 도착 구역은 센터별로 묶어 내려준다 (화면에서 optgroup 으로 렌더링)
        model.addAttribute("binsByCenter", warehouseBinService.getActiveBinsByCenter());

        model.addAttribute("selectedBinId", binId);
        model.addAttribute("subMenu", "move");
    }
}
