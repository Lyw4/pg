package com.feedflow.admin.controller;

import com.feedflow.admin.dto.StockSyncResultDto;
import com.feedflow.admin.dto.StockSyncSummaryDto;
import com.feedflow.admin.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * 재고 정합성 점검 / 재계산(Sync) 화면 컨트롤러.
 *
 * <h3>왜 필요한가</h3>
 * {@code Product.totalStock} 은 목록 조회 성능을 위해 비정규화해 둔 값이고,
 * 실제 재고의 근거는 {@code ProductLot.lotQuantity} 합계다.
 * 동시 처리 중 예외, 외부(B2C 쇼핑몰) 의 직접 수정, 데이터 이관 등으로 두 값이 어긋날 수 있으므로
 * <b>로트 합계를 정답으로 간주</b>하고 장부를 맞춰주는 보정 창구를 둔다.
 *
 * <h3>권한 정책</h3>
 * <ul>
 *     <li><b>진단(조회)</b> : STAFF · ADMIN 공통. 값을 변경하지 않으므로 실무자도 확인할 수 있다.</li>
 *     <li><b>보정(실행)</b> : ADMIN 전용. 재고 장부를 직접 덮어쓰는 작업이라
 *         {@code @PreAuthorize("hasRole('ADMIN')")} 로 차단한다.</li>
 * </ul>
 */
@Controller
@RequestMapping("/admin/inventory/sync")
@RequiredArgsConstructor
public class AdminStockSyncController {

    private static final String SYNC_VIEW = "admin/inventory/sync";
    private static final String REDIRECT_SYNC = "redirect:/admin/inventory/sync";

    private final ProductService productService;

    /** GNB 활성화용 (재고 관리 > 재고 정합성 점검) */
    @ModelAttribute("menu")
    public String menu() {
        return "inventory";
    }

    @ModelAttribute("subMenu")
    public String subMenu() {
        return "sync";
    }

    /* ------------------------------------------------------------------
     * 진단 (읽기 전용)
     * ------------------------------------------------------------------ */

    /**
     * 정합성 점검 화면.
     * 아무것도 변경하지 않고 품목별 장부 재고 vs 로트 합계의 차이만 보여준다.
     */
    @GetMapping
    public String page(Model model) {
        List<StockSyncResultDto> rows = productService.getStockSyncDiagnosis();

        model.addAttribute("rows", rows);
        model.addAttribute("summary", StockSyncSummaryDto.of(rows));
        return SYNC_VIEW;
    }

    /* ------------------------------------------------------------------
     * 보정 실행 (ADMIN 전용)
     * ------------------------------------------------------------------ */

    /**
     * 전체 품목 정합성 재계산.
     * 어긋난 품목의 totalStock 을 로트 합계로 덮어쓴다.
     */
    @PostMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public String syncAll(RedirectAttributes redirectAttributes) {
        List<StockSyncResultDto> results = productService.syncAllTotalStocks();

        List<StockSyncResultDto> adjusted = results.stream()
                .filter(StockSyncResultDto::isAdjusted)
                .toList();

        if (adjusted.isEmpty()) {
            redirectAttributes.addFlashAttribute("infoMessage",
                    "전체 " + results.size() + "개 품목을 점검했습니다. 보정할 항목이 없습니다.");
        } else {
            redirectAttributes.addFlashAttribute("successMessage",
                    "전체 " + results.size() + "개 품목 중 " + adjusted.size() + "건의 재고를 보정했습니다.");
            redirectAttributes.addFlashAttribute("syncResults", adjusted);
        }

        return REDIRECT_SYNC;
    }

    /**
     * 특정 품목만 정합성 재계산.
     * 전체 보정 전에 한 건씩 확인하며 처리할 때 사용한다.
     */
    @PostMapping("/{productId}")
    @PreAuthorize("hasRole('ADMIN')")
    public String syncOne(@PathVariable("productId") Long productId,
                          RedirectAttributes redirectAttributes) {

        StockSyncResultDto result = productService.syncTotalStock(productId);

        if (result.isAdjusted()) {
            redirectAttributes.addFlashAttribute("successMessage", result.getSummaryMessage());
            redirectAttributes.addFlashAttribute("syncResults", List.of(result));
        } else {
            redirectAttributes.addFlashAttribute("infoMessage", result.getSummaryMessage());
        }

        return REDIRECT_SYNC;
    }
}
