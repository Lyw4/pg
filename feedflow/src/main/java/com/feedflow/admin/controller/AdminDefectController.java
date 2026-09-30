package com.feedflow.admin.controller;

import com.feedflow.admin.dto.DefectForm;
import com.feedflow.admin.dto.DefectRecordDto;
import com.feedflow.admin.dto.DefectResolveForm;
import com.feedflow.admin.service.DefectService;
import com.feedflow.admin.service.WarehouseBinService;
import com.feedflow.common.exception.BusinessRuleException;
import com.feedflow.common.exception.ResourceNotFoundException;
import com.feedflow.common.util.Texts;
import com.feedflow.domain.DefectResolution;
import com.feedflow.domain.DefectStage;
import com.feedflow.domain.DefectStatus;
import com.feedflow.domain.DefectType;
import com.feedflow.repository.CenterRepository;
import com.feedflow.security.LoginUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

/**
 * 불량 관리 화면.
 *
 * <h3>등록은 사원, 처리 완료는 책임자</h3>
 * 권한을 다르게 둔다.
 * <ul>
 *     <li><b>등록 · 검사 착수</b> — 사원도 할 수 있다. 불량은 현장에서 발견되고,
 *         책임자를 기다려야 기록할 수 있으면 담당자는 기록을 아예 남기지 않는 쪽을
 *         택한다. 그러면 이 기능이 답하려던 질문("제조사별로 반복되는가")이
 *         데이터 부족으로 무의미해진다.</li>
 *     <li><b>처리 완료</b> — 책임자만 한다. 반품은 거래처와의 문제이고 폐기는
 *         비용이 나가는 결정이다. 게다가 처리 완료는 되돌릴 수 없다.</li>
 * </ul>
 * 화면에서 버튼을 숨기는 것과 별개로 {@code @PreAuthorize} 로 막는다. 화면을 숨기는
 * 것만으로는 주소를 직접 호출하는 것을 막지 못한다.
 */
@Controller
@RequestMapping("/admin/defects")
@RequiredArgsConstructor
public class AdminDefectController {

    private final DefectService defectService;
    private final WarehouseBinService warehouseBinService;
    private final CenterRepository centerRepository;

    /**
     * 불량 목록 + 등록 폼.
     *
     * @param status     처리 상태 필터
     * @param defectType 불량 유형 필터
     * @param stage      발견 단계 필터
     * @param centerId   센터 필터
     */
    @GetMapping
    public String defects(@RequestParam(name = "status", required = false) DefectStatus status,
                          @RequestParam(name = "defectType", required = false) DefectType defectType,
                          @RequestParam(name = "stage", required = false) DefectStage stage,
                          @RequestParam(name = "centerId", required = false) Long centerId,
                          Model model) {

        model.addAttribute("search", defectService.search(status, defectType, stage, centerId));

        // 방치된 건은 필터와 무관하게 항상 띄운다. 상태 필터를 '처리 완료' 로 둔
        // 상태에서도 "격리해 둔 채 잊은 재고" 는 보여야 한다.
        model.addAttribute("staleDefects", defectService.getStaleDefects());

        model.addAttribute("lotOptions", defectService.getLotOptions());
        model.addAttribute("binsByCenter", warehouseBinService.getActiveBinsByCenter());
        model.addAttribute("centers", centerRepository.findByActiveTrueOrderByCenterCodeAsc());

        model.addAttribute("defectTypes", DefectType.values());
        model.addAttribute("stages", DefectStage.values());
        model.addAttribute("statuses", DefectStatus.values());
        model.addAttribute("resolutions", DefectResolution.values());

        if (!model.containsAttribute("defectForm")) {
            model.addAttribute("defectForm", new DefectForm());
        }

        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedDefectType", defectType);
        model.addAttribute("selectedStage", stage);
        model.addAttribute("selectedCenterId", centerId);

        model.addAttribute("menu", "defects");
        return "admin/defects";
    }

    /**
     * 불량 발견 등록. 사원도 할 수 있다.
     */
    @PostMapping
    public String register(@Valid @ModelAttribute("defectForm") DefectForm defectForm,
                           BindingResult bindingResult,
                           @AuthenticationPrincipal LoginUser loginUser,
                           RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute(FlashAttr.ERROR, firstError(bindingResult));
            return "redirect:/admin/defects";
        }

        try {
            DefectRecordDto saved = defectService.register(
                    defectForm, LoginUser.nameOf(loginUser));

            redirectAttributes.addFlashAttribute(FlashAttr.SUCCESS, registeredMessage(saved));
        } catch (BusinessRuleException | ResourceNotFoundException e) {
            redirectAttributes.addFlashAttribute(FlashAttr.ERROR, e.getMessage());
        }

        return "redirect:/admin/defects";
    }

    /**
     * 검사 착수 (격리 → 검사 중). 사원도 할 수 있다.
     * <p>
     * 상태를 바꾸는 것만으로도 의미가 있다. 격리된 채 며칠 지난 건과 담당자가
     * 지금 들여다보고 있는 건은 다르게 다뤄야 한다.
     */
    @PostMapping("/{defectId}/inspect")
    public String inspect(@PathVariable("defectId") Long defectId,
                          @RequestParam(name = "memo", required = false) String memo,
                          RedirectAttributes redirectAttributes) {
        try {
            DefectRecordDto updated = defectService.startInspection(defectId, memo);
            redirectAttributes.addFlashAttribute(FlashAttr.SUCCESS,
                    updated.getDefectNo() + " 검사를 시작했습니다.");
        } catch (BusinessRuleException | ResourceNotFoundException e) {
            redirectAttributes.addFlashAttribute(FlashAttr.ERROR, e.getMessage());
        }
        return "redirect:/admin/defects";
    }

    /**
     * 처리 완료. 책임자 전용.
     * <p>
     * 처리해도 <b>재고는 줄지 않는다.</b> 반품 · 폐기로 결정했으면 폐기 화면에서
     * 재고를 차감해야 한다는 안내를 결과 메시지에 붙인다.
     */
    @PostMapping("/{defectId}/resolve")
    @PreAuthorize("hasRole('ADMIN')")
    public String resolve(@PathVariable("defectId") Long defectId,
                          @Valid @ModelAttribute("resolveForm") DefectResolveForm resolveForm,
                          BindingResult bindingResult,
                          @AuthenticationPrincipal LoginUser loginUser,
                          RedirectAttributes redirectAttributes) {

        // 경로의 id 를 정본으로 쓴다. 폼 값과 경로가 어긋나면 경로를 따른다
        resolveForm.setDefectId(defectId);

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute(FlashAttr.ERROR, firstError(bindingResult));
            return "redirect:/admin/defects";
        }

        try {
            DefectRecordDto updated = defectService.resolve(
                    resolveForm, LoginUser.nameOf(loginUser));

            redirectAttributes.addFlashAttribute(FlashAttr.SUCCESS, resolvedMessage(updated));
        } catch (BusinessRuleException | ResourceNotFoundException e) {
            redirectAttributes.addFlashAttribute(FlashAttr.ERROR, e.getMessage());
        }

        return "redirect:/admin/defects";
    }

    /* ------------------------------------------------------------------
     * 결과 메시지
     * ------------------------------------------------------------------ */

    /**
     * 등록 결과 메시지.
     * <p>
     * 관리번호를 반드시 알려 준다. 담당자가 이 번호로 실물에 표시를 붙여야
     * 격리해 둔 재고와 기록이 이어진다.
     */
    private String registeredMessage(DefectRecordDto saved) {
        StringBuilder message = new StringBuilder()
                .append(saved.getDefectNo())
                .append(" 등록 — ")
                .append(Texts.defaultIfBlank(saved.getProductName(), "품목"))
                .append(" / 로트 ")
                .append(saved.getLotNo())
                .append(' ')
                .append(saved.getQuantity())
                .append("포대 (")
                .append(saved.getDefectTypeDescription())
                .append(')');

        if (saved.isSupplierReturnCandidate()) {
            message.append(saved.isManufacturerUnknown()
                           ? " · 공급업체 반품 대상으로 보이지만 품목에 제조사가 등록되어 있지 않습니다."
                           : " · 공급업체(" + saved.getManufacturerName() + ") 반품을 검토하세요.");
        }
        return message.toString();
    }

    /**
     * 처리 결과 메시지.
     * <p>
     * 처리 방법에 딸린 <b>다음에 할 일</b>을 함께 알려 준다. 이 화면은 재고를
     * 건드리지 않으므로, 폐기로 결정했다면 담당자가 폐기 화면으로 가야 한다는 것을
     * 여기서 말해 주지 않으면 재고가 그대로 남는다.
     */
    private String resolvedMessage(DefectRecordDto updated) {
        if (updated.getResolution() == null) {
            return updated.getDefectNo() + " 검사를 시작했습니다.";
        }

        StringBuilder message = new StringBuilder()
                .append(updated.getDefectNo())
                .append(" 처리 완료 — ")
                .append(updated.getResolutionDescription());

        String followUp = updated.getFollowUp();
        if (!Texts.isBlank(followUp)) {
            message.append(" · ").append(followUp);
        }

        // 반품처럼 제조사가 있어야 진행되는 처리인데 품목에 제조사가 없으면
        // 담당자는 "반품하라" 는 안내만 받고 어디로 보낼지 모르는 상태가 된다.
        if (updated.getResolution().isRequiresManufacturer() && updated.isManufacturerUnknown()) {
            message.append(" · 이 품목에는 제조사가 등록되어 있지 않습니다."
                    + " 품목 정보에 제조사를 먼저 등록하세요.");
        }
        return message.toString();
    }

    private String firstError(BindingResult bindingResult) {
        return bindingResult.getFieldErrors().stream()
                .map(error -> error.getDefaultMessage())
                .findFirst()
                .orElse("입력값이 올바르지 않습니다.");
    }
}
