package com.ex.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/** 기존 6자리 비밀번호 찾기 인증번호를 선택적으로 Naver SENS SMS로 전달한다. */
@Service
public class PasswordResetSmsSender {
    private final boolean enabled;
    private final String serviceId;
    private final String accessKey;
    private final String secretKey;
    private final String senderPhone;
    private final RestClient client = RestClient.builder().baseUrl("https://sens.apigw.ntruss.com").build();

    public PasswordResetSmsSender(
            @Value("${feedflow.password-reset.sms-enabled:false}") boolean enabled,
            @Value("${feedflow.sms.naver.service-id:}") String serviceId,
            @Value("${feedflow.sms.naver.access-key:}") String accessKey,
            @Value("${feedflow.sms.naver.secret-key:}") String secretKey,
            @Value("${feedflow.sms.naver.sender-phone:}") String senderPhone) {
        this.enabled = enabled;
        this.serviceId = serviceId;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.senderPhone = senderPhone;
    }

    public void sendCode(String phone, String code) {
        if (!enabled) return;
        if (!StringUtils.hasText(phone) || !StringUtils.hasText(serviceId)
                || !StringUtils.hasText(accessKey) || !StringUtils.hasText(secretKey)
                || !StringUtils.hasText(senderPhone)) {
            throw new IllegalStateException("비밀번호 찾기 SMS 설정이 완전하지 않습니다.");
        }
        String timestamp = String.valueOf(Instant.now().toEpochMilli());
        String uri = "/sms/v2/services/" + serviceId + "/messages";
        String signature = sign("POST", uri, timestamp);
        String body = "{\"type\":\"SMS\",\"from\":\"" + senderPhone
                + "\",\"content\":\"[FeedFlow] 비밀번호 찾기 인증번호: " + code
                + " (5분 이내 입력)\",\"messages\":[{\"to\":\""
                + phone.replaceAll("[^0-9]", "") + "\"}]}";
        client.post().uri(uri).contentType(MediaType.APPLICATION_JSON)
                .header("x-ncp-apigw-timestamp", timestamp)
                .header("x-ncp-iam-access-key", accessKey)
                .header("x-ncp-apigw-signature-v2", signature)
                .body(body).retrieve().toBodilessEntity();
    }

    private String sign(String method, String uri, String timestamp) {
        try {
            String message = method + " " + uri + "\n" + timestamp + "\n" + accessKey;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SMS 서명 생성에 실패했습니다.", exception);
        }
    }
}
