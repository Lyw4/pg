package com.ex.controller;

import com.ex.entity.Product;
import com.ex.repository.ProductRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void 비회원주문후전화번호로취소한다() throws Exception {
        Product product = productRepository.findAllByActiveTrueOrderByIdAsc().getFirst();
        int initialStock = product.getLots().stream().mapToInt(lot -> lot.getQuantity()).sum();

        String orderBody = """
                {
                  "customerName": "김농부",
                  "phone": "010-4270-5271",
                  "address": "충남 천안시 서북구 농장로 24",
                  "detailAddress": "제2축사",
                  "unloadingLocation": "사료창고 앞",
                  "deliveryRequest": "도착 전 연락",
                  "paymentMethod": "CARD",
                  "regularDelivery": false,
                  "items": [{"productId": %d, "quantity": 2}]
                }
                """.formatted(product.getId());

        String response = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.orderNumber").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertEquals(
                initialStock - 2,
                product.getLots().stream().mapToInt(lot -> lot.getQuantity()).sum()
        );

        JsonNode json = objectMapper.readTree(response);
        String orderNumber = json.get("orderNumber").asText();

        mockMvc.perform(patch("/api/orders/{orderNumber}/cancel", orderNumber)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"010-4270-5271\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertEquals(
                initialStock,
                product.getLots().stream().mapToInt(lot -> lot.getQuantity()).sum()
        );
    }
}
