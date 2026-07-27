package com.feedflow.admin.controller;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.feedflow.admin.dto.ScanResultDto;
import com.feedflow.admin.service.BarcodeScanService;
import com.feedflow.common.exception.BusinessRuleException;
import com.feedflow.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 바코드 스캔 API 단위 테스트.
 * <p>
 * Spring 컨텍스트 없이 MockMvc standaloneSetup 으로 컨트롤러만 띄우고,
 * 서비스는 Mock 으로 대체하여 <b>HTTP 응답 계약</b>(상태 코드 / JSON 구조)을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BarcodeApiController 스캔 API 테스트")
class BarcodeApiControllerTest {

    private static final String SCAN_URL = "/api/admin/scan";

    @Mock
    private BarcodeScanService barcodeScanService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // LocalDate 직렬화를 위해 Spring 의 Jackson 빌더(JavaTimeModule 자동 등록)를 사용한다
        // 날짜를 배열이 아니라 ISO 문자열("2026-08-01")로 직렬화 → 실제 애플리케이션과 동일한 동작
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(
                Jackson2ObjectMapperBuilder.json()
                        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .build());

        mockMvc = MockMvcBuilders
                .standaloneSetup(new BarcodeApiController(barcodeScanService))
                .setControllerAdvice(new AdminApiExceptionHandler())
                .setMessageConverters(converter)
                .build();
    }

    /* ==================================================================
     * 200 OK
     * ================================================================== */

    @Test
    @DisplayName("[200] 로트번호를 스캔하면 품목/로트/구역별 재고가 담긴 DTO 를 반환한다")
    void scan_lotNo_returns200WithLotInfo() throws Exception {
        // given
        given(barcodeScanService.scan("LOT-CT-2601")).willReturn(lotScanResult());

        // when & then
        mockMvc.perform(get(SCAN_URL).param("code", "LOT-CT-2601")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scanType").value("LOT"))
                .andExpect(jsonPath("$.code").value("LOT-CT-2601"))
                .andExpect(jsonPath("$.totalQuantity").value(20))
                // 품목 정보
                .andExpect(jsonPath("$.product.productId").value(1))
                .andExpect(jsonPath("$.product.productCode").value("FD-CT-001"))
                .andExpect(jsonPath("$.product.name").value("프리미엄 육성우 배합사료"))
                .andExpect(jsonPath("$.product.animalType").value("소"))
                .andExpect(jsonPath("$.product.weightKg").value(25))
                .andExpect(jsonPath("$.product.safetyStock").value(50))
                .andExpect(jsonPath("$.product.belowSafetyStock").value(true))
                // 로트 정보
                .andExpect(jsonPath("$.lot.lotId").value(100))
                .andExpect(jsonPath("$.lot.lotNo").value("LOT-CT-2601"))
                .andExpect(jsonPath("$.lot.manufacturedDate").value("2026-02-02"))
                .andExpect(jsonPath("$.lot.expirationDate").value("2026-08-01"))
                .andExpect(jsonPath("$.lot.remainingDays").value(5))
                .andExpect(jsonPath("$.lot.expired").value(false))
                .andExpect(jsonPath("$.lot.lotQuantity").value(20))
                // 구역별 재고
                .andExpect(jsonPath("$.stocks.length()").value(1))
                .andExpect(jsonPath("$.stocks[0].binCode").value("A-01-01"))
                .andExpect(jsonPath("$.stocks[0].locationLabel").value("A구역 · 01랙 · 1단"))
                .andExpect(jsonPath("$.stocks[0].quantity").value(20));
    }

    @Test
    @DisplayName("[200] 품목코드를 스캔하면 로트 정보 없이 품목 + 전체 재고를 반환한다")
    void scan_productCode_returns200WithoutLot() throws Exception {
        // given
        given(barcodeScanService.scan("FD-CT-001")).willReturn(productScanResult());

        // when & then
        mockMvc.perform(get(SCAN_URL).param("code", "FD-CT-001")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scanType").value("PRODUCT"))
                .andExpect(jsonPath("$.code").value("FD-CT-001"))
                .andExpect(jsonPath("$.lot").doesNotExist())
                .andExpect(jsonPath("$.totalQuantity").value(40))
                .andExpect(jsonPath("$.stocks.length()").value(2))
                .andExpect(jsonPath("$.stocks[0].lotNo").value("LOT-CT-2601"))
                .andExpect(jsonPath("$.stocks[1].lotNo").value("LOT-CT-2602"));
    }

    @Test
    @DisplayName("[200] 스캔 값은 가공 없이 서비스로 전달된다 (정규화는 서비스 책임)")
    void scan_passesRawCodeToService() throws Exception {
        // given
        given(barcodeScanService.scan(anyString())).willReturn(lotScanResult());

        // when
        mockMvc.perform(get(SCAN_URL).param("code", " lot-ct-2601 ")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // then
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(barcodeScanService).scan(captor.capture());
        assertThat(captor.getValue()).isEqualTo(" lot-ct-2601 ");
    }

    /* ==================================================================
     * 404 Not Found
     * ================================================================== */

    @Test
    @DisplayName("[404] 등록되지 않은 바코드면 404 와 오류 메시지를 반환한다")
    void scan_unknownCode_returns404() throws Exception {
        // given
        willThrow(new ResourceNotFoundException(
                "등록되지 않은 바코드입니다. 로트번호 또는 품목코드를 확인하세요. (스캔값: NO-SUCH-CODE)"))
                .given(barcodeScanService).scan("NO-SUCH-CODE");

        // when & then
        mockMvc.perform(get(SCAN_URL).param("code", "NO-SUCH-CODE")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value(
                        containsString("등록되지 않은 바코드입니다")))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    /* ==================================================================
     * 400 Bad Request
     * ================================================================== */

    @Test
    @DisplayName("[400] code 파라미터가 없으면 400 을 반환한다")
    void scan_missingParameter_returns400() throws Exception {
        mockMvc.perform(get(SCAN_URL).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(
                        containsString("code")));

        verify(barcodeScanService, never()).scan(anyString());
    }

    @Test
    @DisplayName("[400] 빈 코드를 스캔하면 400 과 안내 메시지를 반환한다")
    void scan_blankCode_returns400() throws Exception {
        // given
        willThrow(new BusinessRuleException("스캔된 코드가 비어 있습니다."))
                .given(barcodeScanService).scan("   ");

        // when & then
        mockMvc.perform(get(SCAN_URL).param("code", "   ")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("스캔된 코드가 비어 있습니다."));
    }

    /* ==================================================================
     * 픽스처
     * ================================================================== */

    private ScanResultDto.ProductInfo productInfo() {
        return new ScanResultDto.ProductInfo(
                1L, "FD-CT-001", "프리미엄 육성우 배합사료", "소",
                25, 32000L, 40, 50, 180, true, true);
    }

    private ScanResultDto lotScanResult() {
        ScanResultDto.LotInfo lot = new ScanResultDto.LotInfo(
                100L, "LOT-CT-2601",
                LocalDate.of(2026, 2, 2),
                LocalDate.of(2026, 8, 1),
                5L, false, 20);

        ScanResultDto.StockLocation stock = new ScanResultDto.StockLocation(
                10L, "A-01-01", "A구역 · 01랙 · 1단",
                "LOT-CT-2601", LocalDate.of(2026, 8, 1), 5L, 20);

        return new ScanResultDto(
                ScanResultDto.ScanType.LOT, "LOT-CT-2601",
                productInfo(), lot, List.of(stock), 20);
    }

    private ScanResultDto productScanResult() {
        ScanResultDto.StockLocation stock1 = new ScanResultDto.StockLocation(
                10L, "A-01-01", "A구역 · 01랙 · 1단",
                "LOT-CT-2601", LocalDate.of(2026, 8, 1), 5L, 20);

        ScanResultDto.StockLocation stock2 = new ScanResultDto.StockLocation(
                11L, "A-01-02", "A구역 · 01랙 · 2단",
                "LOT-CT-2602", LocalDate.of(2026, 8, 21), 25L, 20);

        return new ScanResultDto(
                ScanResultDto.ScanType.PRODUCT, "FD-CT-001",
                productInfo(), null, List.of(stock1, stock2), 40);
    }
}
