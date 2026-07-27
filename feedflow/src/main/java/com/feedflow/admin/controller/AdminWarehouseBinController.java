package com.feedflow.admin.controller;

import com.feedflow.admin.dto.WarehouseBinForm;
import com.feedflow.admin.service.WarehouseBinService;
import com.feedflow.common.exception.DuplicateCodeException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * 기준 정보 - 창고 구역(WarehouseBin) 관리 화면 컨트롤러.
 */
@Controller
@RequestMapping("/admin/bins")
@RequiredArgsConstructor
public class AdminWarehouseBinController {

    private static final String LIST_VIEW = "admin/bins/list";
    private static final String FORM_VIEW = "admin/bins/form";

    private final WarehouseBinService warehouseBinService;

    @ModelAttribute("zones")
    public List<String> zones() {
        return warehouseBinService.getZones();
    }

    @ModelAttribute("menu")
    public String menu() {
        return "bins";
    }

    /* ------------------------------------------------------------------
     * 목록
     * ------------------------------------------------------------------ */

    @GetMapping
    public String list(@RequestParam(name = "zone", required = false) String zone,
                       @RequestParam(name = "active", required = false) Boolean active,
                       Model model) {

        model.addAttribute("bins", warehouseBinService.getBins(zone, active));
        model.addAttribute("activeBinCount", warehouseBinService.countActiveBins());
        model.addAttribute("selectedZone", zone);
        model.addAttribute("selectedActive", active);
        return LIST_VIEW;
    }

    /* ------------------------------------------------------------------
     * 등록
     * ------------------------------------------------------------------ */

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("binForm", new WarehouseBinForm());
        prepareForm(model, false, null);
        return FORM_VIEW;
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("binForm") WarehouseBinForm binForm,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {

        prepareForm(model, false, null);

        if (bindingResult.hasErrors()) {
            return FORM_VIEW;
        }

        try {
            warehouseBinService.create(binForm);
        } catch (DuplicateCodeException e) {
            bindingResult.rejectValue("binCode", "duplicate", e.getMessage());
            return FORM_VIEW;
        }

        redirectAttributes.addFlashAttribute("successMessage",
                "창고 구역 [" + binForm.getBinCode() + "] 을 등록했습니다.");
        return "redirect:/admin/bins";
    }

    /* ------------------------------------------------------------------
     * 수정
     * ------------------------------------------------------------------ */

    @GetMapping("/{binId}/edit")
    public String editForm(@PathVariable("binId") Long binId, Model model) {
        model.addAttribute("binForm", warehouseBinService.getBinForm(binId));
        prepareForm(model, true, binId);
        return FORM_VIEW;
    }

    @PostMapping("/{binId}")
    public String update(@PathVariable("binId") Long binId,
                         @Valid @ModelAttribute("binForm") WarehouseBinForm binForm,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {

        prepareForm(model, true, binId);
        binForm.setBinId(binId);

        if (bindingResult.hasErrors()) {
            return FORM_VIEW;
        }

        try {
            warehouseBinService.update(binId, binForm);
        } catch (DuplicateCodeException e) {
            bindingResult.rejectValue("binCode", "duplicate", e.getMessage());
            return FORM_VIEW;
        }

        redirectAttributes.addFlashAttribute("successMessage",
                "창고 구역 [" + binForm.getBinCode() + "] 을 수정했습니다.");
        return "redirect:/admin/bins";
    }

    /** 폼 화면 공통 모델 세팅 (form action URL 을 서버에서 결정) */
    private void prepareForm(Model model, boolean editMode, Long binId) {
        model.addAttribute("editMode", editMode);
        model.addAttribute("formAction",
                editMode ? "/admin/bins/" + binId : "/admin/bins");
    }

    /* ------------------------------------------------------------------
     * 사용 중지 / 재사용
     * ------------------------------------------------------------------ */

    @PostMapping("/{binId}/active")
    @PreAuthorize("hasRole('ADMIN')")
    public String changeActive(@PathVariable("binId") Long binId,
                              @RequestParam("active") boolean active,
                              RedirectAttributes redirectAttributes) {

        String binCode = warehouseBinService.changeActive(binId, active);
        redirectAttributes.addFlashAttribute("successMessage",
                "구역 [" + binCode + "] 을 " + (active ? "다시 사용" : "사용 중지") + " 처리했습니다.");
        return "redirect:/admin/bins";
    }
}
