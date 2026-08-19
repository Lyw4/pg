package com.ex.controller;

import com.ex.entity.Product;
import com.ex.entity.CustomerOrder;
import com.ex.repository.CustomerOrderRepository;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    private CustomerOrderRepository customerOrderRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void 비회원은주문을생성할수없다() throws Exception {
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

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody))
                .andExpect(status().isUnauthorized());

        assertEquals(
                initialStock,
                product.getLots().stream().mapToInt(lot -> lot.getQuantity()).sum()
        );
    }

    @Test
    void 비회원은전화번호만으로주문을취소할수없다() throws Exception {
        mockMvc.perform(patch("/api/orders/FF-TEST-ORDER/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"010-1111-2222\"}"))
                .andExpect(status().isUnauthorized());
    }
}
