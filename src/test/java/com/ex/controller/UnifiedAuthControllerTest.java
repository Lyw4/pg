package com.ex.controller;

import com.ex.dto.SignupRequest;
import com.ex.entity.AddressType;
import com.ex.service.MemberService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
class UnifiedAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberService memberService;

    @Test
    void adminCanLoginFromUnifiedLoginAndOpenDashboard() throws Exception {
        var result = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {
                                  "identifier": "admin",
                                  "password": "1234"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountType").value("ADMIN"))
                .andExpect(jsonPath("$.redirectUrl")
                        .value("/admin/dashboard"))
                .andExpect(jsonPath("$.member").doesNotExist())
                .andExpect(cookie().doesNotExist("FEEDFLOW_ADMIN_SESSION_REMEMBER"))
                .andExpect(cookie().doesNotExist("FEEDFLOW_ADMIN_REMEMBER"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest()
                .getSession(false);

        mockMvc.perform(get("/admin/dashboard").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/dashboard"));
    }

    @Test
    void customerCanLoginFromTheSameUnifiedLogin() throws Exception {
        String email = "farm-" + UUID.randomUUID() + "@feedflow.test";
        memberService.signup(new SignupRequest(
                email,
                "Farm!234",
                "테스트 농장주",
                "통합 로그인 농장",
                "010-1234-5678",
                null,
                15,
                address(AddressType.HOME, "서울시 강남구 농장로 1"),
                address(AddressType.FARM, "충남 천안시 농장로 2"),
                null));

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {
                                  "identifier": "%s",
                                  "password": "Farm!234"
                                }
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountType").value("CUSTOMER"))
                .andExpect(jsonPath("$.redirectUrl").doesNotExist())
                .andExpect(jsonPath("$.member.email").value(email))
                .andExpect(jsonPath("$.member.farmName")
                        .value("통합 로그인 농장"))
                .andExpect(jsonPath("$.member.addresses.length()").value(2));
    }

    @Test
    void staffCanLoginAndCannotOpenEmployeeManagement() throws Exception {
        var result = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {
                                  "identifier": "staff@feedflow.co.kr",
                                  "password": "staff123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountType").value("STAFF"))
                .andExpect(jsonPath("$.redirectUrl")
                        .value("/admin/dashboard"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest()
                .getSession(false);
        mockMvc.perform(get("/admin/dashboard").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/dashboard"));
        mockMvc.perform(get("/admin/employees").session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidCredentialsReturnOneSafeMessage() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {
                                  "identifier": "unknown",
                                  "password": "wrong-password"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString(
                        "아이디(이메일) 또는 비밀번호가 일치하지 않습니다.")));
    }

    private SignupRequest.AddressRequest address(
            AddressType type,
            String baseAddress) {
        return new SignupRequest.AddressRequest(
                type,
                "테스트 농장주",
                "010-1234-5678",
                "12345",
                baseAddress,
                null,
                type == AddressType.FARM ? "정문 앞" : null,
                true);
    }
}
