package com.ex.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "feedflow.payment")
public class PaymentProperties {

    private Portone portone = new Portone();

    public boolean isPortOneEnabled() {
        return StringUtils.hasText(portone.customerCode)
                && StringUtils.hasText(portone.apiKey)
                && StringUtils.hasText(portone.apiSecret);
    }

    @Getter
    @Setter
    public static class Portone {
        private String customerCode = "";
        private String apiKey = "";
        private String apiSecret = "";
        private String cardChannelKey = "";
        private String kakaoChannelKey = "";
        private String virtualAccountChannelKey = "";
    }
}
