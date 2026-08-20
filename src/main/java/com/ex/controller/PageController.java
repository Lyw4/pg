package com.ex.controller;

import com.ex.dto.ProductResponse;
import com.ex.service.ProductCatalogService;
import com.ex.service.AdminDashboardService;
import com.ex.service.WmsOperationsService;
import com.ex.service.MemberService;
import com.ex.service.AdminActivityService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class PageController {

    private final ProductCatalogService productCatalogService;
    private final AdminDashboardService adminDashboardService;
    private final WmsOperationsService wmsOperationsService;
    private final MemberService memberService;
    private final AdminActivityService adminActivityService;

    @Value("${kakao.maps.javascript-key:}")
    private String kakaoMapsJavascriptKey;

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("siteName", "FEED FLOW");
        model.addAttribute("kakaoMapsJavascriptKey", kakaoMapsJavascriptKey);
        model.addAttribute("products", productCatalogService.findProducts(null, null));
        return "index";
    }

    @GetMapping("/mypage")
    public String myPage(
            HttpSession session,
            Authentication authentication,
            Model model) {
        boolean operator = authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)
                && authentication.getAuthorities().stream()
                        .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority())
                                || "ROLE_STAFF".equals(authority.getAuthority()));
        if (operator) {
            return "redirect:/admin/dashboard";
        }
        Long memberId = sessionMemberId(session);
        if (memberId == null) {
            return "redirect:/?account=login&sessionExpired=true";
        }
        try {
            model.addAttribute("siteName", "FEED FLOW 마이페이지");
            model.addAttribute("member", memberService.findById(memberId));
        } catch (IllegalArgumentException exception) {
            // H2 재시작·데이터 초기화 후 이전 JSESSIONID가 남아 있으면
            // 세션의 회원 ID가 현재 DB에 존재하지 않을 수 있습니다.
            // 이 경우 500 오류 대신 로그인 화면에서 새 세션을 만들도록 합니다.
            session.invalidate();
            return "redirect:/?account=login&sessionExpired=true";
        }
        return "mypage";
    }

    @GetMapping("/mypage/orders/{orderNumber}")
    public String myOrderDetail(
            @PathVariable("orderNumber") String orderNumber,
            HttpSession session,
            Authentication authentication,
            Model model) {
        boolean operator = authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)
                && authentication.getAuthorities().stream()
                        .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority())
                                || "ROLE_STAFF".equals(authority.getAuthority()));
        if (operator) {
            return "redirect:/admin/dashboard";
        }
        if (sessionMemberId(session) == null) {
            return "redirect:/?account=login&sessionExpired=true";
        }
        model.addAttribute("siteName", "FEED FLOW 주문 상세");
        model.addAttribute("orderNumber", orderNumber);
        return "order-detail";
    }

    @GetMapping("/mypage/farm-model")
    public String farmModelAnalysis(
            HttpSession session,
            Authentication authentication,
            Model model) {
        boolean operator = authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)
                && authentication.getAuthorities().stream()
                        .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority())
                                || "ROLE_STAFF".equals(authority.getAuthority()));
        if (operator) {
            return "redirect:/admin/dashboard";
        }
        Long memberId = sessionMemberId(session);
        if (memberId == null) {
            return "redirect:/?account=login&sessionExpired=true";
        }
        try {
            var member = memberService.findById(memberId);
            model.addAttribute("siteName", "FEED FLOW 농장 맞춤 상세분석");
            model.addAttribute("member", member);
            return "farm-model-analysis";
        } catch (IllegalArgumentException exception) {
            session.invalidate();
            return "redirect:/?account=login&sessionExpired=true";
        }
    }

    private Long sessionMemberId(HttpSession session) {
        // 화면 컨트롤러는 비로그인 접근 시 로그인 안내로 리다이렉트하므로
        // 예외를 던지지 않는 쪽을 사용합니다.
        return SessionMemberSupport.memberIdOrNull(session);
    }

    @GetMapping("/admin")
    public String adminEntry() {
        return "redirect:/admin/dashboard";
    }

    @GetMapping("/admin/dashboard")
    public String adminDashboard(Authentication authentication, Model model) {
        var dashboard = adminDashboardService.getDashboard();
        List<ProductResponse> catalogProducts =
                productCatalogService.findProducts(null, null);
        model.addAttribute("today", dashboard.today());
        model.addAttribute("todayTask", dashboard.todayTask());
        model.addAttribute("network", dashboard.network());
        boolean administrator = authentication != null
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN"
                        .equals(authority.getAuthority()));
        if (administrator) {
            model.addAttribute("sales", dashboard.sales());
            model.addAttribute("adminMetrics", adminDashboardService.getAdminMetrics());
            model.addAttribute("activityLogs", adminActivityService.findRecent());
        }
        model.addAttribute(
                "safetyStockAlerts",
                dashboard.lowStockAlerts());
        model.addAttribute("expiringLots", dashboard.expiringLots());
		model.addAttribute("wmsOverview", wmsOperationsService.overview());
        model.addAttribute("catalogProducts", catalogProducts);
        model.addAttribute("catalogProductCount", catalogProducts.size());
        model.addAttribute("cattleProductCount", catalogProducts.stream()
                .filter(product -> "CATTLE".equals(product.animalType())
                        || "DAIRY_CATTLE".equals(product.animalType()))
                .count());
        model.addAttribute("pigProductCount", catalogProducts.stream()
                .filter(product -> "PIG".equals(product.animalType()))
                .count());
        model.addAttribute("poultryProductCount", catalogProducts.stream()
                .filter(product -> "CHICKEN".equals(product.animalType())
                        || "DUCK".equals(product.animalType()))
                .count());
        model.addAttribute("supplementProductCount", catalogProducts.stream()
                .filter(product -> "SUPPLEMENT".equals(product.animalType()))
                .count());
        model.addAttribute("menu", "dashboard");
        return "admin/dashboard";
    }

    @GetMapping("/shop/products/{productId}")
    public String storefrontProductDetail(
            @PathVariable("productId") Long productId,
            Model model) {
        ProductResponse product = productCatalogService.findProduct(productId);
        List<ProductResponse> relatedProducts = productCatalogService
                .findProducts(null, null)
                .stream()
                .filter(candidate -> !candidate.id().equals(product.id()))
                .filter(candidate -> sameCatalogGroup(candidate, product))
                .limit(4)
                .toList();

        model.addAttribute("siteName", "FEED FLOW");
        model.addAttribute("product", product);
        model.addAttribute("relatedProducts", relatedProducts);
        return "storefront-product-detail";
    }

    private boolean sameCatalogGroup(
            ProductResponse candidate,
            ProductResponse product) {
        if (isCattle(candidate) && isCattle(product)) {
            return true;
        }
        if (isPoultry(candidate) && isPoultry(product)) {
            return true;
        }
        return candidate.animalType().equals(product.animalType());
    }

    private boolean isCattle(ProductResponse product) {
        return "CATTLE".equals(product.animalType())
                || "DAIRY_CATTLE".equals(product.animalType());
    }

    private boolean isPoultry(ProductResponse product) {
        return "CHICKEN".equals(product.animalType())
                || "DUCK".equals(product.animalType());
    }

    @GetMapping("/admin/products")
    public String admin(Model model) {
        model.addAttribute("siteName", "FEED FLOW 판매자센터");
        model.addAttribute("menu", "products");
        return "admin";
    }

    @GetMapping("/admin/login")
    public String adminLogin(
            Authentication authentication) {
        if (authentication != null
                && authentication.isAuthenticated()
                && !(authentication
                        instanceof AnonymousAuthenticationToken)) {
            return "redirect:/";
        }
        return "redirect:/?account=login";
    }
}
