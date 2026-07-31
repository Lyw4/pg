package com.ex.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 구매화면을Thymeleaf로렌더링한다() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeExists("products"))
                .andExpect(content().string(containsString("FEED FLOW")))
                .andExpect(content().string(containsString("H2 연동됨")))
                .andExpect(content().string(containsString("/js/feedflow.js")));
    }

    @Test
    void 관리자화면을Thymeleaf로렌더링한다() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin"))
                .andExpect(content().string(containsString("상품·LOT 관리")))
                .andExpect(content().string(containsString("/js/admin.js")));
    }
}
