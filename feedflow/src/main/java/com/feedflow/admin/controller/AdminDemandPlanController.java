package com.feedflow.admin.controller;

import com.feedflow.admin.service.DemandPlanService;
import com.feedflow.domain.CoverageStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.LocalDate;

/**
 * 수요 계획 화면 — 담당 농장의 월 예상 사료량과 센터의 출고 가능 재고를 대조한다.
 *
 * <h3>조회 전용이다</h3>
 * 이 화면은 판단을 돕는 것이 목적이고 데이터를 바꾸지 않는다. 부족한 것을 보고
 * 실제로 채우는 일은 입고 등록 · 센터 간 이관이 한다. 여기서 발주까지 하려면
 * 발주 도메인이 필요하고, 그건 이 프로젝트 범위가 아니다.
 *
 * <h3>권한</h3>
 * {@code /admin/**} 는 STAFF · ADMIN 이 접근한다. 이 화면은 <b>STAFF 도 봐야 한다.</b>
 * 어느 축종 사료가 모자라는지 모르면 입고 우선순위를 정할 수 없다.
 * 매출처럼 책임자에게만 보여야 할 정보가 아니다.
 */
@Controller
@RequestMapping("/admin/demand-plan")
@RequiredArgsConstructor
public class AdminDemandPlanController {

    private final DemandPlanService demandPlanService;

    @GetMapping
    public String demandPlan(Model model) {
        model.addAttribute("plan", demandPlanService.getDemandPlan(LocalDate.now()));

        // 화면이 임계값을 하드코딩하지 않도록 넘긴다. 기준이 바뀌면 enum 만 고치면 된다.
        model.addAttribute("tightThreshold", CoverageStatus.TIGHT_THRESHOLD);
        model.addAttribute("adequateThreshold", CoverageStatus.ADEQUATE_THRESHOLD);
        model.addAttribute("surplusThreshold", CoverageStatus.SURPLUS_THRESHOLD);

        model.addAttribute("menu", "demandPlan");
        return "admin/demand-plan";
    }
}
