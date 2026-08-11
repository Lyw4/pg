package com.ex.controller;

import com.ex.dto.SignupRequest;
import com.ex.entity.AddressType;
import com.ex.repository.ProductRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ex.service.MemberService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CustomerAccountFeatureTest {

    @Autowired MockMvc mockMvc;
    @Autowired MemberService memberService;
    @Autowired ProductRepository productRepository;
    @Autowired ObjectMapper objectMapper;

    @Test
    void customerSessionRecoveryProfileAndWishlistWorkTogether() throws Exception {
        String email = "account-" + UUID.randomUUID() + "@feedflow.test";
        var signedUp = memberService.signup(new SignupRequest(
                email, "Farm!234", "계정테스트", "세션농장", "010-2222-3333",
                "123-45-67890", 12,
                address(AddressType.HOME, "서울시 농장로 1"),
                address(AddressType.FARM, "충남 예산군 농장로 2"),
                null));

        var login = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"identifier":"%s","password":"Farm!234"}
                                """.formatted(signedUp.username())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountType").value("CUSTOMER"))
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

        mockMvc.perform(get("/api/members/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));

        mockMvc.perform(get("/mypage").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("mypage"))
                .andExpect(content().string(containsString("MY FEED FLOW")))
                .andExpect(content().string(containsString(signedUp.username())));

        Long productId = productRepository.findAllByActiveTrueOrderByIdAsc()
                .getFirst().getProductId();
        mockMvc.perform(post("/api/wishlist/{productId}", productId).session(session))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/wishlist").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value(productId));

        mockMvc.perform(put("/api/members/me")
                        .session(session)
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"변경회원","farmName":"변경농장","phone":"010-4444-5555",
                                  "businessNumber":"321-54-98765","regularDeliveryDay":18,
                                  "homeAddress":"서울시 변경로 1","homeDetailAddress":"101호",
                                  "farmAddress":"충남 변경군 농장로 9","unloadingLocation":"정문 앞"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("변경회원"))
                .andExpect(jsonPath("$.regularDeliveryDay").value(18));

        mockMvc.perform(post("/api/members/find-username")
                        .contentType("application/json")
                        .content("""
                                {"name":"변경회원","email":"%s"}
                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(signedUp.username()));

        var codeResult = mockMvc.perform(post("/api/members/password-reset/code")
                        .contentType("application/json")
                        .content("""
                                {"username":"%s","email":"%s","phone":"010-4444-5555"}
                                """.formatted(signedUp.username(), email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debugCode").exists())
                .andReturn();
        String resetCode = objectMapper.readTree(
                codeResult.getResponse().getContentAsString())
                .get("debugCode").asText();

        mockMvc.perform(post("/api/members/reset-password")
                        .contentType("application/json")
                        .content("""
                                {
                                  "username":"%s","email":"%s","phone":"010-4444-5555",
                                  "code":"%s",
                                  "newPassword":"NewFarm!456"
                                }
                                """.formatted(signedUp.username(), email, resetCode)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/members/logout").session(session))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/members/me").session(session))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"identifier":"%s","password":"NewFarm!456"}
                                """.formatted(signedUp.username())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountType").value("CUSTOMER"));
    }

    private SignupRequest.AddressRequest address(AddressType type, String address) {
        return new SignupRequest.AddressRequest(
                type, "계정테스트", "010-2222-3333", "12345", address,
                type == AddressType.HOME ? "1층" : null,
                type == AddressType.FARM ? "축사 앞" : null,
                type == AddressType.HOME);
    }
}
