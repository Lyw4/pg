package com.ex.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class DeliveryTrackingClientTest {

    @ParameterizedTest
    @CsvSource({
        "CJ대한통운,cj",
        "롯데택배,lotte",
        "한진택배,hanjin",
        "우체국택배,post",
        "경동택배,kyungdong",
        "로젠택배,logen",
        "coupang,coupang"
    })
    void mapsCarrierNameToDeliveryApiCode(String carrierName, String expected) {
        assertThat(DeliveryTrackingClient.courierCode(carrierName)).isEqualTo(expected);
    }

    @Test
    void rejectsUnsupportedCarrier() {
        assertThatThrownBy(() -> DeliveryTrackingClient.courierCode("알 수 없는 운송사"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("지원하지 않는 운송사");
    }
}
