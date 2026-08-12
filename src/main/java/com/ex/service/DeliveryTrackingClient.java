package com.ex.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * DeliveryAPI의 택배 배송조회 API를 호출하는 서버 전용 클라이언트입니다.
 * API Key와 Secret Key는 브라우저로 전달하지 않고 환경변수에서만 읽습니다.
 */
@Component
public class DeliveryTrackingClient {

    private static final Map<String, String> DIRECT_CODES = Map.of(
            "lotte", "lotte",
            "cj", "cj",
            "hanjin", "hanjin",
            "post", "post",
            "kyungdong", "kyungdong",
            "daesin", "daesin",
            "logen", "logen",
            "hapdong", "hapdong",
            "coupang", "coupang",
            "woori", "woori");

    private final RestClient restClient;
    private final String apiKey;
    private final String secretKey;

    public DeliveryTrackingClient(
            RestClient.Builder restClientBuilder,
            @Value("${feedflow.delivery-api.base-url:https://api.deliveryapi.co.kr}")
            String baseUrl,
            @Value("${feedflow.delivery-api.api-key:}") String apiKey,
            @Value("${feedflow.delivery-api.secret-key:}") String secretKey) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
        this.secretKey = secretKey;
    }

    public boolean isEnabled() {
        return StringUtils.hasText(apiKey) && StringUtils.hasText(secretKey);
    }

    public TrackingSnapshot trace(
            String clientId,
            String carrierName,
            String trackingNumber) {
        if (!isEnabled()) {
            throw new IllegalStateException(
                    "택배 API 환경변수가 없습니다. DELIVERY_API_KEY와 "
                            + "DELIVERY_API_SECRET을 설정한 뒤 서버를 다시 시작해 주세요.");
        }
        if (!StringUtils.hasText(trackingNumber)) {
            throw new IllegalArgumentException("조회할 운송장 번호가 없습니다.");
        }
        String normalizedTrackingNumber = trackingNumber.replaceAll("[^0-9]", "");
        if (normalizedTrackingNumber.length() < 8
                || normalizedTrackingNumber.length() > 20) {
            throw new IllegalArgumentException(
                    "운송장 번호는 숫자만 입력해 주세요. 하이픈과 공백은 자동으로 제거됩니다.");
        }

        String courierCode = courierCode(carrierName);
        var request = Map.of(
                "items", List.of(Map.of(
                        "clientId", clientId,
                        "courierCode", courierCode,
                        "trackingNumber", normalizedTrackingNumber)),
                "includeProgresses", true,
                "skipCache", false);

        try {
            JsonNode response = restClient.post()
                    .uri("/v1/tracking/trace")
                    .header(HttpHeaders.AUTHORIZATION,
                            "Bearer " + apiKey.trim() + ":" + secretKey.trim())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);
            return parse(response);
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            if (status == 401 || status == 403) {
                throw new IllegalStateException(
                        "택배 API 인증에 실패했습니다. 서버 환경변수의 API Key와 Secret Key를 확인해 주세요.");
            }
            if (status == 429) {
                throw new IllegalStateException(
                        "택배 API 무료 호출 한도를 초과했습니다. 잠시 후 다시 조회해 주세요.");
            }
            throw new IllegalStateException(
                    "택배 API 조회에 실패했습니다. 응답 코드: " + status);
        } catch (RestClientException exception) {
            throw new IllegalStateException(
                    "택배 API 서버에 연결하지 못했습니다. 네트워크 상태를 확인해 주세요.");
        }
    }

    static String courierCode(String carrierName) {
        if (!StringUtils.hasText(carrierName)) {
            throw new IllegalArgumentException("운송사를 먼저 등록해 주세요.");
        }
        String normalized = carrierName.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s_-]", "")
                .replace("택배", "");
        if (DIRECT_CODES.containsKey(normalized)) {
            return DIRECT_CODES.get(normalized);
        }
        if (normalized.contains("cj") || normalized.contains("씨제이")
                || normalized.contains("대한통운")) {
            return "cj";
        }
        if (normalized.contains("롯데")) return "lotte";
        if (normalized.contains("한진")) return "hanjin";
        if (normalized.contains("우체국")) return "post";
        if (normalized.contains("경동")) return "kyungdong";
        if (normalized.contains("대신")) return "daesin";
        if (normalized.contains("로젠")) return "logen";
        if (normalized.contains("합동")) return "hapdong";
        if (normalized.contains("쿠팡")) return "coupang";
        if (normalized.contains("우리")) return "woori";
        throw new IllegalArgumentException(
                "지원하지 않는 운송사입니다. CJ대한통운, 롯데, 한진, 우체국, "
                        + "경동, 대신, 로젠, 합동, 쿠팡, 우리택배 중 하나로 등록해 주세요.");
    }

    private TrackingSnapshot parse(JsonNode response) {
        if (response == null || !response.path("isSuccess").asBoolean(false)) {
            throw new IllegalStateException("택배 API가 정상적인 조회 결과를 반환하지 않았습니다.");
        }
        JsonNode results = response.path("data").path("results");
        if (!results.isArray() || results.isEmpty()) {
            throw new IllegalStateException("택배 API 조회 결과가 없습니다.");
        }
        JsonNode result = results.get(0);
        if (!result.path("success").asBoolean(false)) {
            String message = result.path("error").path("message").asText("");
            throw new IllegalStateException(StringUtils.hasText(message)
                    ? "운송장 조회 실패: " + message
                    : "택배사에서 운송장 정보를 찾지 못했습니다.");
        }

        JsonNode data = result.path("data");
        JsonNode progresses = data.path("progresses");
        JsonNode latest = progresses.isArray() && !progresses.isEmpty()
                ? progresses.get(progresses.size() - 1)
                : null;
        return new TrackingSnapshot(
                data.path("courierName").asText(""),
                data.path("deliveryStatus").asText(""),
                data.path("deliveryStatusText").asText(""),
                data.path("isDelivered").asBoolean(false),
                data.path("dateLastProgress").asText(""),
                latest == null ? "" : latest.path("location").asText(""),
                latest == null ? "" : latest.path("description").asText(""));
    }

    public record TrackingSnapshot(
            String courierName,
            String statusCode,
            String statusText,
            boolean delivered,
            String lastProgressAt,
            String latestLocation,
            String latestDescription) {
    }
}
