package com.ex.controller;

import com.ex.repository.ProductRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "feedflow.admin.username=admin",
        "feedflow.admin.password=Admin!1234"
})
@Transactional
class AdminProductControllerTest {

    private static final byte[] ONE_PIXEL_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductRepository productRepository;

    @Test
    void 관리자만상품을등록수정삭제한다() throws Exception {
        String createBody = productBody("관리자 등록 사료", 42_000);

        mockMvc.perform(post("/api/admin/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isUnauthorized());

        String createdResponse = mockMvc.perform(post("/api/admin/products")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("관리자 등록 사료"))
                .andExpect(jsonPath("$.lot").value("ADMIN-LOT-001"))
                .andExpect(jsonPath("$.tone").value("amber"))
                .andExpect(jsonPath("$.shape").value("pellet"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode created = objectMapper.readTree(createdResponse);
        long productId = created.get("id").asLong();

        mockMvc.perform(put("/api/admin/products/{productId}", productId)
                        .header(HttpHeaders.AUTHORIZATION, basicAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productBody("관리자 수정 사료", 44_000)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("관리자 수정 사료"))
                .andExpect(jsonPath("$.price").value(44_000));

        mockMvc.perform(delete("/api/admin/products/{productId}", productId)
                        .header(HttpHeaders.AUTHORIZATION, basicAuth()))
                .andExpect(status().isNoContent());

        assertFalse(productRepository.findById(productId).orElseThrow().isActive());
    }

    @Test
    void 상품이미지를파일로첨부한다() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "image", "feed.png", MediaType.IMAGE_PNG_VALUE, ONE_PIXEL_PNG);

        mockMvc.perform(multipart("/api/admin/products/image").file(image))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(multipart("/api/admin/products/image")
                        .file(image)
                        .header(HttpHeaders.AUTHORIZATION, basicAuth()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imageUrl")
                        .value(org.hamcrest.Matchers.startsWith(
                                "/uploads/product-images/")));
    }

    private String basicAuth() {
        String credentials = Base64.getEncoder().encodeToString(
                "admin:Admin!1234".getBytes(StandardCharsets.UTF_8)
        );
        return "Basic " + credentials;
    }

    private String productBody(String name, int price) {
        return """
                {
                  "manufacturerName": "관리자 테스트 유통",
                  "name": "%s",
                  "animalType": "CATTLE",
                  "feedStage": "비육 후기",
                  "description": "관리자 CRUD 검증용 배합사료",
                  "weightKg": 25,
                  "price": %d,
                  "originalPrice": 48000,
                  "proteinPercent": 16.5,
                  "fatPercent": 3.2,
                  "fiberPercent": 8.0,
                  "calciumPercent": 1.1,
                  "imageUrl": null,
                  "badge": "관리자",
                  "lotNumber": "ADMIN-LOT-001",
                  "manufacturedDate": "%s",
                  "expirationDate": "%s",
                  "lotQuantity": 120
                }
                """.formatted(
                name,
                price,
                LocalDate.now().minusDays(1),
                LocalDate.now().plusYears(1)
        );
    }
}
