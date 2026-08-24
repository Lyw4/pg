package com.ex.controller;

import com.ex.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "feedflow.admin.username=admin",
        "feedflow.admin.password=1234",
        "feedflow.security.remember-me-key=feedflow-test-remember-key"
})
class PageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Test
    void storefrontRendersWithProducts() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeExists("products"))
                .andExpect(content().string(
                        containsString("FEED FLOW")))
                .andExpect(content().string(
                        containsString("/js/feedflow.js")))
                .andExpect(content().string(
                        containsString("회원가입")))
                .andExpect(content().string(
                        containsString("id=\"login-username\"")));
    }

    @Test
    void myPageRedirectsToLoginWhenMemberSessionIsMissingOrStale()
            throws Exception {
        mockMvc.perform(get("/mypage").session(new MockHttpSession()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/?account=login&sessionExpired=true"));

        MockHttpSession staleSession = new MockHttpSession();
        staleSession.setAttribute("memberId", -999_999L);
        mockMvc.perform(get("/mypage").session(staleSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/?account=login&sessionExpired=true"));
    }

    @Test
    void storefrontProductDetailUsesCanonicalInventoryProduct()
            throws Exception {
        Long productId = productRepository
                .findByName("한우 송아지 스타터")
                .orElseThrow()
                .getProductId();

        mockMvc.perform(get("/shop/products/{productId}", productId))
                .andExpect(status().isOk())
                .andExpect(view().name("storefront-product-detail"))
                .andExpect(model().attributeExists("product", "relatedProducts"))
                .andExpect(content().string(containsString("한우 송아지 스타터")))
                .andExpect(content().string(containsString("판매 가능 재고")))
                .andExpect(content().string(containsString("상품 상세 정보")));
    }

    /**
     * 로그인 진입점은 상단 유틸리티 바 하나만 둔다.
     *
     * <p>예전에는 유틸리티 바의 '로그인' 링크와 장바구니 옆 계정 버튼이 함께
     * 노출되어, 같은 화면에 로그인 버튼이 위아래로 두 개 보였다. 비로그인
     * 상태에서도 계정 버튼이 없어야 하고, 장바구니는 남아 있어야 한다.
     */
    @Test
    void storefrontExposesSingleLoginEntryPointForAnonymousVisitor()
            throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        not(containsString("id=\"header-account-button\""))))
                .andExpect(content().string(
                        not(containsString("id=\"myfarm-label\""))))
                .andExpect(content().string(
                        containsString("id=\"utility-login\"")))
                .andExpect(content().string(
                        containsString("id=\"open-cart\"")))
                .andExpect(content().string(
                        containsString("id=\"cart-count\"")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void storefrontShowsCurrentAdminSession() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        containsString("로그인 유지 중")))
                .andExpect(content().string(
                        containsString("관리자 대시보드")))
                .andExpect(content().string(
                        containsString("관리자 로그아웃")))
                .andExpect(content().string(
                        containsString("id=\"header-admin-logout\"")))
                .andExpect(content().string(
                        not(containsString("id=\"header-account-button\""))));
    }

    @Test
    void adminPageRedirectsAnonymousUserToLogin() throws Exception {
        mockMvc.perform(get("/admin")
                        .accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/admin/login"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void authenticatedAdminEntryRedirectsToDashboard() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/dashboard"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void authenticatedAdminRendersWmsDashboard() throws Exception {
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/dashboard"))
                .andExpect(model().attributeExists(
                        "todayTask",
                        "network",
                        "sales",
                        "safetyStockAlerts",
                        "expiringLots",
                        "catalogProducts",
                        "catalogProductCount"))
                .andExpect(content().string(
                        containsString("전국 물류망 현황")))
                .andExpect(content().string(
                        containsString("ff-donut-chart")))
                .andExpect(content().string(
                        containsString("ff-distribution-legend")))
                .andExpect(content().string(
                        containsString("통합 판매 상품")))
                .andExpect(content().string(
                        containsString("한우 송아지 스타터")))
                .andExpect(content().string(
                        containsString("/css/wms-admin.css")))
                .andExpect(content().string(
                        containsString("매출 통계")))
                .andExpect(content().string(
                        containsString("수요·정기입고 계획")))
                .andExpect(content().string(
                        containsString("QR 라벨 출력")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void authenticatedAdminRendersProductManagementPage() throws Exception {
        mockMvc.perform(get("/admin/products"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin"))
                .andExpect(content().string(
                        containsString("/js/admin.js")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void administratorCanOpenEmployeeAndDirectOutboundPages()
            throws Exception {
        mockMvc.perform(get("/admin/employees"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/employees"))
                .andExpect(content().string(
                        containsString("사원 계정 및 권한")));

        mockMvc.perform(get("/admin/outbound/direct"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/wms"))
                .andExpect(content().string(
                        containsString("상품 직접 출고")))
                .andExpect(content().string(
                        containsString("FEFO")));
    }

    @Test
    @WithMockUser(username = "staff@feedflow.co.kr", roles = "STAFF")
    void staffDashboardHidesSalesAndEmployeeManagement() throws Exception {
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().isOk())
                .andExpect(model().attributeDoesNotExist("sales"))
                .andExpect(content().string(not(
                        containsString("사원 관리"))));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void inventoryRunsInsideWmsDashboardShell() throws Exception {
        mockMvc.perform(get("/inventory")
                        .queryParam("view", "stock"))
                .andExpect(status().isOk())
                .andExpect(view().name("inventory"))
                .andExpect(content().string(
                        containsString("class=\"ff-sidebar\"")))
                .andExpect(content().string(
                        containsString("class=\"ff-content\"")))
                .andExpect(content().string(
                        containsString("/js/wms-admin-layout.js")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void distributionRunsInsideWmsDashboardShell() throws Exception {
        mockMvc.perform(get("/distribution")
                        .queryParam("view", "farms"))
                .andExpect(status().isOk())
                .andExpect(view().name("distribution"))
                .andExpect(content().string(
                        containsString("class=\"ff-sidebar\"")))
                .andExpect(content().string(
                        containsString("농장 고객사")))
                .andExpect(content().string(
                        containsString("/css/wms-admin.css")));
    }

    @Test
    void adminLoginRedirectsAnonymousUserToUnifiedLogin() throws Exception {
        mockMvc.perform(get("/admin/login"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/?account=login"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void authenticatedAdminCannotReturnToLoginForm() throws Exception {
        mockMvc.perform(get("/admin/login"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void adminLoginUsesBrowserSessionCookieOnly()
            throws Exception {
        var loginResult = mockMvc.perform(
                        post("/admin/login")
                                .with(csrf())
                                .param("username", "admin")
                                .param("password", "1234"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(cookie().doesNotExist(
                        "FEEDFLOW_ADMIN_SESSION_REMEMBER"))
                .andExpect(cookie().doesNotExist(
                        "FEEDFLOW_ADMIN_REMEMBER"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult
                .getRequest()
                .getSession(false);

        mockMvc.perform(get("/").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        containsString("로그인 유지 중")));

        mockMvc.perform(get("/admin/dashboard")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/dashboard"));
    }
}
