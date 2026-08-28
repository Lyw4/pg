package com.feedflow.admin.controller;

import com.feedflow.admin.service.FarmCustomerService;
import com.feedflow.domain.AnimalType;
import com.feedflow.domain.CustomerStatus;
import com.feedflow.common.exception.ResourceNotFoundException;
import com.feedflow.repository.CenterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 농장 고객사 관리 화면.
 *
 * <h3>권한을 조회와 변경으로 나눈다</h3>
 * <ul>
 *     <li><b>조회는 STAFF 도 가능</b> — 담당 농장과 정기 배송일을 모르면 출고 준비를
 *         할 수 없다. 창고 담당자에게 필요한 정보다.</li>
 *     <li><b>거래 상태 변경은 ADMIN 전용</b> — 거래를 보류하거나 재개하는 것은 영업
 *         판단이다. 폐기 · 출고 취소 · 사원 권한 변경과 같은 등급으로 다룬다.</li>
 * </ul>
 * 화면에서 버튼을 숨기는 것과 별개로 {@code @PreAuthorize} 로 차단한다.
 * 버튼을 숨기는 것은 안내이고, 차단은 서버가 해야 한다.
 */
@Controller
@RequestMapping("/admin/farm-customers")
@RequiredArgsConstructor
public class AdminFarmCustomerController {

    private final FarmCustomerService farmCustomerService;
    private final CenterRepository centerRepository;

    /**
     * 농장 목록 + 센터별 현황.
     *
     * @param centerId   담당 센터 필터 (없으면 전국)
     * @param animalType 축종 필터
     * @param status     거래 상태 필터
     * @param keyword    농장명 · 대표자 · 주소 · 농장코드 검색어
     */
    @GetMapping
    public String farmCustomers(@RequestParam(name = "centerId", required = false) Long centerId,
                                @RequestParam(name = "animalType", required = false) AnimalType animalType,
                                @RequestParam(name = "status", required = false) CustomerStatus status,
                                @RequestParam(name = "keyword", required = false) String keyword,
                                Model model) {

        model.addAttribute("search", farmCustomerService.search(centerId, animalType, status, keyword));

        // 센터 카드는 필터와 무관하게 전국 기준이다. 필터를 걸어도 "다른 센터에는
        // 농장이 몇 곳인지" 를 볼 수 있어야 한다.
        model.addAttribute("farmNetwork", farmCustomerService.getNetwork());

        // 담당 센터가 그 축종 사료를 취급하지 않는 농장 (배정 검토 안내)
        model.addAttribute("misassignedFarms", farmCustomerService.getFarmsWithUnsupportedAnimalType());

        model.addAttribute("centers", centerRepository.findByActiveTrueOrderByCenterCodeAsc());
        model.addAttribute("animalTypes", AnimalType.values());
        model.addAttribute("statuses", CustomerStatus.values());

        model.addAttribute("selectedCenterId", centerId);
        model.addAttribute("selectedAnimalType", animalType);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("keyword", keyword);

        model.addAttribute("menu", "farmCustomers");
        return "admin/farm-customers";
    }

    /**
     * 거래 상태 변경 (거래 중 ↔ 거래 보류). 책임자 전용.
     * <p>
     * 처리 후 <b>필터 조건을 유지한 채</b> 목록으로 돌아간다. 필터가 초기화되면
     * 방금 바꾼 농장을 다시 찾아야 해서, 여러 건을 연속으로 처리할 수 없다.
     */
    @PostMapping("/{farmCustomerId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public String changeStatus(@PathVariable("farmCustomerId") Long farmCustomerId,
                               @RequestParam("status") CustomerStatus status,
                               @RequestParam(name = "centerId", required = false) Long centerId,
                               @RequestParam(name = "animalType", required = false) AnimalType animalType,
                               @RequestParam(name = "filterStatus", required = false) CustomerStatus filterStatus,
                               @RequestParam(name = "keyword", required = false) String keyword,
                               RedirectAttributes redirectAttributes) {

        try {
            String farmName = farmCustomerService.changeStatus(farmCustomerId, status);
            redirectAttributes.addFlashAttribute(FlashAttr.SUCCESS,
                    farmName + " 을(를) " + status.getDescription() + " 상태로 변경했습니다."
                            + (status == CustomerStatus.PAUSED
                               ? " 이 농장의 물량은 월 예상 사료량 합계에서 제외됩니다." : ""));
        } catch (ResourceNotFoundException | IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute(FlashAttr.ERROR, e.getMessage());
        }

        // 필터 조건을 그대로 다시 붙여 돌아간다 (null 인 값은 URL 에 나타나지 않는다)
        redirectAttributes.addAttribute("centerId", centerId);
        redirectAttributes.addAttribute("animalType", animalType);
        redirectAttributes.addAttribute("status", filterStatus);
        redirectAttributes.addAttribute("keyword", keyword);

        return "redirect:/admin/farm-customers";
    }
}
