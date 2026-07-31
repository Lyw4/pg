package com.ex.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 회원가입하고중복이메일을거부한다() throws Exception {
        String body = """
                {
                  "email": "farm@example.com",
                  "password": "Feedflow!123",
                  "name": "김농부",
                  "farmName": "행복농장",
                  "phone": "010-4270-5271",
                  "businessNumber": "123-45-67890",
                  "regularDeliveryDay": 15,
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
                .andExpect(jsonPath("$.email").value("farm@example.com"))
                .andExpect(jsonPath("$.farmName").value("행복농장"));

        mockMvc.perform(post("/api/members/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "farm@example.com",
                                  "password": "Feedflow!123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("김농부"));

        mockMvc.perform(post("/api/members/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "farm@example.com",
                                  "password": "wrong-password"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("이메일 또는 비밀번호가 일치하지 않습니다."));

        mockMvc.perform(post("/api/members/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("이미 사용 중인 이메일입니다."));
    }
}
