package com.feedflow.admin.controller;

import com.feedflow.admin.dto.LotCandidateDto;
import com.feedflow.admin.service.TraceabilityService;
import com.feedflow.common.exception.ResourceNotFoundException;
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
 * 제품 이력 추적 뷰어 화면 컨트롤러 (HTML 렌더링).
 * <p>
 * 로트번호를 검색해 그 로트의 <b>입고 → 보관 → 출고 / 출고취소</b> 전 과정을
 * 타임라인으로 보여준다. CS 대응 시 유통 경로를 즉시 역추적하는 용도다.
 * <p>
 * 조회 전용이므로 STAFF · ADMIN 모두 접근할 수 있다.
 * ({@code /admin/**} 는 SecurityConfig 에서 {@code hasAnyRole("STAFF","ADMIN")} 로 제한)
 * <p>
 * 비동기 조회용 JSON 은 {@link TraceabilityApiController} 가 제공한다.
 */
@Controller
@RequestMapping("/admin/traceability")
@RequiredArgsConstructor
public class AdminTraceabilityController {

    private static final String TRACE_VIEW = "admin/traceability/index";

    private final TraceabilityService traceabilityService;

    /** GNB 활성화용 (재고 관리 > 이력 추적) */
    @ModelAttribute("menu")
    public String menu() {
        return "inventory";
    }

    @ModelAttribute("subMenu")
    public String subMenu() {
        return "traceability";
    }

    /**
     * 이력 추적 화면.
     *
     * @param lotNo 로트번호 검색어. 여러 품목이 같은 번호를 쓸 수 있어 후보가 여러 건일 수 있다.
     * @param lotId 후보 목록에서 특정 로트를 고른 경우
     */
    @GetMapping
    public String index(@RequestParam(name = "lotNo", required = false) String lotNo,
                        @RequestParam(name = "lotId", required = false) Long lotId,
                        Model model) {

        LocalDate today = LocalDate.now();
        model.addAttribute("lotNo", lotNo);

        // 1) 로트를 직접 지정한 경우 : 바로 추적
        if (lotId != null) {
            try {
                model.addAttribute("trace", traceabilityService.trace(lotId, today));
            } catch (ResourceNotFoundException e) {
                model.addAttribute(FlashAttr.ERROR, e.getMessage());
            }
            return TRACE_VIEW;
        }

        // 2) 검색어가 없으면 안내 화면만 보여준다
        if (lotNo == null || lotNo.isBlank()) {
            return TRACE_VIEW;
        }

        // 3) 로트번호로 후보를 찾는다
        List<LotCandidateDto> candidates = traceabilityService.findCandidates(lotNo).stream()
                .map(lot -> LotCandidateDto.of(lot, today))
                .toList();

        if (candidates.isEmpty()) {
            model.addAttribute(FlashAttr.ERROR,
                    "로트번호 '" + lotNo.trim() + "' 로 등록된 로트를 찾을 수 없습니다.");
            return TRACE_VIEW;
        }

        // 후보가 하나면 곧바로 타임라인을 보여준다 (한 번 더 클릭하게 만들지 않는다)
        if (candidates.size() == 1) {
            model.addAttribute("trace",
                    traceabilityService.trace(candidates.get(0).getLotId(), today));
            return TRACE_VIEW;
        }

        // 여러 품목이 같은 로트번호를 쓰는 경우 : 사용자가 고르게 한다
        model.addAttribute("candidates", candidates);
        return TRACE_VIEW;
    }
}
