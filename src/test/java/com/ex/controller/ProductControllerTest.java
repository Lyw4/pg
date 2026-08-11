package com.ex.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 전체상품과Lot재고를조회한다() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(40))
                .andExpect(jsonPath("$[0].name").value("한우 송아지 스타터"))
                .andExpect(jsonPath("$[0].lot").value(
                        org.hamcrest.Matchers.startsWith("LOT-CATTLE-")))
                .andExpect(jsonPath("$[0].stock").value(
                        org.hamcrest.Matchers.greaterThanOrEqualTo(80)))
                .andExpect(jsonPath("$[?(@.name == '한우 마스터 700')]").isEmpty());
    }

    @Test
    void 축종별로상품을필터링한다() throws Exception {
        mockMvc.perform(get("/api/products").param("animalType", "PIG"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(10))
                .andExpect(jsonPath("$[0].animal").value("돼지"));
    }
}
