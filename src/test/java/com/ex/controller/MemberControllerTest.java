package com.ex.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.test.context.support.WithMockUser;

import com.ex.repository.FarmCustomerRepository;
import com.ex.repository.MemberRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private FarmCustomerRepository farmCustomerRepository;

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void 회원가입하고중복이메일을거부한다() throws Exception {
        String body = """
                {
                  "username": "happyfarm",
                  "email": "farm@example.com",
                  "password": "Feedflow!123",
                  "name": "김농부",
                  "farmName": "행복농장",
                  "phone": "010-4270-5271",
                  "businessNumber": "123-45-67890",
                  "regularDeliveryDay": 15,
                  "farmProfile": {
                    "animalType": "소",
                    "livestockCount": 180,
                    "monthlyFeedQuantity": 720,
                    "preferredFeed": "한우 성장 플러스"
                  },
                  "homeAddress": {
                    "addressType": "HOME",
                    "recipientName": "김농부",
                    "phone": "010-4270-5271",
                    "postalCode": "31000",
                    "baseAddress": "충남 천안시",
                    "detailAddress": "101호",
                    "defaultAddress": true
                  },
                  "farmAddress": {
                    "addressType": "FARM",
                    "recipientName": "김농부",
                    "phone": "010-4270-5271",
                    "postalCode": "31000",
                    "baseAddress": "충남 천안시 농장로 24",
                    "unloadingLocation": "제2축사 앞",
                    "defaultAddress": false
                  }
                }
                """;

        mockMvc.perform(post("/api/members/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("happyfarm"))
                .andExpect(jsonPath("$.email").value("farm@example.com"))
                .andExpect(jsonPath("$.farmName").value("행복농장"))
                .andExpect(jsonPath("$.addresses.length()").value(2))
                .andExpect(jsonPath("$.addresses[0].addressType").value("HOME"))
                .andExpect(jsonPath("$.addresses[1].addressType").value("FARM"))
                .andExpect(jsonPath("$.addresses[1].unloadingLocation")
                        .value("제2축사 앞"))
                .andExpect(jsonPath("$.farmAssignment.warehouseCode")
                        .value("W01"))
                .andExpect(jsonPath("$.farmAssignment.warehouseName")
                        .exists());

        var member = memberRepository.findByEmail("farm@example.com")
                .orElseThrow();
        var farmCustomer = farmCustomerRepository
                .findByMemberId(member.getId())
                .orElseThrow();
        org.junit.jupiter.api.Assertions.assertFalse(
                farmCustomer.isDemoData());
        org.junit.jupiter.api.Assertions.assertEquals(
                "소", farmCustomer.getAnimalType());
        org.junit.jupiter.api.Assertions.assertEquals(
                "W01", farmCustomer.getAssignedWarehouse().getCode());

        mockMvc.perform(get("/distribution")
                        .queryParam("view", "farms"))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("행복농장")))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("F-M")));

        mockMvc.perform(post("/api/members/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "happyfarm",
                                  "password": "Feedflow!123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("김농부"));

        mockMvc.perform(post("/api/members/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "happyfarm",
                                  "password": "wrong-password"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("아이디 또는 비밀번호가 일치하지 않습니다."));

        mockMvc.perform(post("/api/members/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("이미 사용 중인 아이디입니다."));
    }
}
