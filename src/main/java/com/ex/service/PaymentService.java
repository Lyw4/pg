package com.ex.service;

import com.ex.config.PaymentProperties;
import com.ex.dto.OrderResponse;
import com.ex.dto.PaymentConfigResponse;
import com.ex.entity.CustomerOrder;
import com.ex.entity.PaymentStatus;
import com.ex.repository.CustomerOrderRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.util.UriUtils;

@Slf4j
@Service
public class PaymentService {

    private static final String TOKEN_URL = "https://api.iamport.kr/users/getToken";
    private static final String PAYMENT_URL = "https://api.iamport.kr/payments/";
    private static final String CANCEL_URL = "https://api.iamport.kr/payments/cancel";
    private static final String VIRTUAL_ACCOUNT_URL = "https://api.iamport.kr/vbanks/";

    private final RestClient restClient;
    private final PaymentProperties properties;
    private final CustomerOrderRepository orderRepository;
    private final OrderService orderService;
    private final PaymentApplyService paymentApplyService;

    public PaymentService(
            RestClient.Builder restClientBuilder,
            PaymentProperties properties,
            CustomerOrderRepository orderRepository,
            OrderService orderService,
            PaymentApplyService paymentApplyService) {
        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(3_000);
        requestFactory.setReadTimeout(5_000);
        this.restClient = restClientBuilder
                .requestFactory(requestFactory)
                .build();
        this.properties = properties;
        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.paymentApplyService = paymentApplyService;
    }

    public PaymentConfigResponse paymentConfig(Long memberId) {
        requireMemberId(memberId);
        PaymentProperties.Portone portone = properties.getPortone();
        boolean enabled = properties.isPortOneEnabled();
        if (enabled) {
            validatePortOneCredentials();
        }
        return new PaymentConfigResponse(
                enabled,
                portone.getCustomerCode(),
                enabled && StringUtils.hasText(portone.getCardChannelKey()),
                portone.getCardChannelKey(),
                enabled && StringUtils.hasText(portone.getKakaoChannelKey()),
                portone.getKakaoChannelKey(),
                enabled && StringUtils.hasText(portone.getVirtualAccountChannelKey()),
                portone.getVirtualAccountChannelKey());
    }

    private void validatePortOneCredentials() {
        try {
            accessToken();
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 401) {
                throw new IllegalArgumentException(
                        "포트원 REST API Key/Secret 인증에 실패했습니다. "
                                + "현재 고객사에서 발급한 V1 키를 다시 확인해주세요.");
            }
            throw new IllegalArgumentException(
                    "포트원 연결 확인에 실패했습니다. 잠시 후 다시 시도해주세요.");
        } catch (RestClientException exception) {
            throw new IllegalArgumentException(
                    "포트원 서버에 연결하지 못했습니다. 네트워크 상태를 확인해주세요.");
        }
    }

    public OrderResponse completePortOne(
            String impUid,
            String merchantUid,
            String paymentToken,
            Long memberId) {
        CustomerOrder order = requireMemberOrder(merchantUid, memberId);
        requireCallbackToken(order, paymentToken);
        requireConfiguration();
        JsonNode payment = getPayment(accessToken(), impUid, merchantUid);
        return paymentApplyService.applyForMember(
                merchantUid, paymentToken, memberId, impUid, payment);
    }

    public OrderResponse completePortOneByCallback(
            String impUid,
            String merchantUid,
            String paymentToken) {
        CustomerOrder order = requireCallbackOrder(merchantUid, paymentToken);
        requireConfiguration();
        JsonNode payment = getPayment(accessToken(), impUid, merchantUid);
        return paymentApplyService.applyForCallback(
                merchantUid, paymentToken, impUid, payment);
    }

    public OrderResponse reconcilePortOne(String orderNumber, Long memberId) {
        CustomerOrder order = requireMemberOrder(orderNumber, memberId);
        JsonNode payment = findPaymentByMerchantUid(accessToken(), orderNumber);
        return paymentApplyService.applyForMember(
                orderNumber, order.getPaymentCallbackToken(), memberId,
                requiredText(payment, "imp_uid", "결제번호"), payment);
    }

    @Transactional
    public void failPendingPayment(String orderNumber, String token, Long memberId) {
        CustomerOrder order = requireMemberOrder(orderNumber, memberId);
        requireCallbackToken(order, token);
        failUnstarted(order);
    }

    @Transactional
    public void failPendingPaymentByCallback(String orderNumber, String token) {
        failUnstarted(requireCallbackOrder(orderNumber, token));
    }

    public void handlePortOneWebhook(JsonNode payload) {
        String impUid = payload.path("imp_uid").asText("");
        String merchantUid = payload.path("merchant_uid").asText("");
        if (!StringUtils.hasText(impUid) || !StringUtils.hasText(merchantUid)) {
            throw new IllegalArgumentException("포트원 웹훅에 결제번호 또는 주문번호가 없습니다.");
        }
        requireConfiguration();
        JsonNode payment = getPayment(accessToken(), impUid, merchantUid);
        paymentApplyService.applyForWebhook(merchantUid, impUid, payment);
    }

    public OrderResponse cancelOrder(String orderNumber, Long memberId) {
        CustomerOrder order = requireMemberOrder(orderNumber, memberId);
        if (order.getStatus() == CustomerOrder.OrderStatus.CANCELLED) {
            throw new IllegalStateException("이미 취소된 주문입니다.");
        }
        if (order.getStatus() == CustomerOrder.OrderStatus.SHIPPING
                || order.getStatus() == CustomerOrder.OrderStatus.DELIVERED) {
            throw new IllegalStateException("배송이 시작된 주문은 고객이 직접 취소할 수 없습니다.");
        }

        if (StringUtils.hasText(order.getProviderTransactionId())) {
            requireConfiguration();
            if (order.getPaymentStatus() == PaymentStatus.WAITING_FOR_DEPOSIT) {
                cancelVirtualAccount(order.getProviderTransactionId());
            } else if (order.getPaymentStatus() == PaymentStatus.DONE) {
                cancelTransaction(
                        order.getProviderTransactionId(),
                        order.getOrderNumber(),
                        order.getFinalPrice().intValueExact(),
                        "회원 마이페이지 주문 취소");
            }
        }

        return paymentApplyService.cancelForMember(orderNumber, memberId);
    }


    private void failUnstarted(CustomerOrder order) {
        if (StringUtils.hasText(order.getProviderTransactionId())
                || order.getPaymentStatus() == PaymentStatus.DONE
                || order.getPaymentStatus() == PaymentStatus.WAITING_FOR_DEPOSIT) {
            throw new IllegalStateException("외부 거래가 시작된 주문은 자동 실패 처리할 수 없습니다.");
        }
        order.failPayment();
        orderService.releasePaymentReservation(order, "결제창 취소 또는 결제 실패");
    }

    private CustomerOrder requireMemberOrder(String orderNumber, Long memberId) {
        requireMemberId(memberId);
        CustomerOrder order = requireOrder(orderNumber);
        if (order.getMember() == null || !memberId.equals(order.getMember().getId())) {
            throw new IllegalArgumentException("본인 주문만 결제 처리할 수 있습니다.");
        }
        return order;
    }

    private CustomerOrder requireCallbackOrder(String orderNumber, String token) {
        CustomerOrder order = requireOrder(orderNumber);
        requireCallbackToken(order, token);
        return order;
    }

    private CustomerOrder requireOrder(String orderNumber) {
        // 결제 콜백·복구·취소가 동시에 들어와도 한 트랜잭션만 상태를 변경하도록 잠급니다.
        return orderRepository.findPaymentOrderByOrderNumber(orderNumber)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다."));
    }

    private void requireCallbackToken(CustomerOrder order, String token) {
        if (!secureEquals(order.getPaymentCallbackToken(), token)) {
            throw new IllegalArgumentException("결제 확인 토큰이 올바르지 않습니다.");
        }
    }

    private String accessToken() {
        requireConfiguration();
        JsonNode body = restClient.post()
                .uri(TOKEN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "imp_key", properties.getPortone().getApiKey(),
                        "imp_secret", properties.getPortone().getApiSecret()))
                .retrieve()
                .body(JsonNode.class);
        return responseNode(body).path("access_token").asText("");
    }

    /**
     * 결제창이 성공을 반환한 직후에는 포트원 조회 API에 거래가 아직 전파되지
     * 않아 단건 조회가 잠깐 404를 반환하는 경우가 있습니다. 짧게 재시도한
     * 뒤에도 찾지 못하면 merchant_uid 조회로 한 번 더 확인합니다.
     */
    private JsonNode getPayment(String token, String impUid, String merchantUid) {
        RestClientResponseException notFound = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                JsonNode body = restClient.get()
                        .uri(PAYMENT_URL + UriUtils.encodePathSegment(impUid, StandardCharsets.UTF_8))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .retrieve()
                        .body(JsonNode.class);
                return responseNode(body);
            } catch (RestClientResponseException exception) {
                if (exception.getStatusCode().value() != 404) {
                    throw exception;
                }
                notFound = exception;
                if (attempt < 2) {
                    pauseBeforePaymentRetry();
                }
            }
        }

        if (StringUtils.hasText(merchantUid)) {
            try {
                JsonNode payment = findPaymentByMerchantUid(token, merchantUid);
                String foundImpUid = payment.path("imp_uid").asText("");
                if (secureEquals(impUid, foundImpUid)) {
                    return payment;
                }
                throw new IllegalArgumentException(
                        "포트원 결제 식별자와 주문번호의 결제 정보가 일치하지 않습니다. "
                                + "고객사 식별코드와 결제 채널 키가 같은 포트원 상점의 값인지 확인해 주세요.");
            } catch (RestClientResponseException exception) {
                if (exception.getStatusCode().value() != 404) {
                    throw exception;
                }
            }
        }

        log.warn("PortOne payment lookup returned 404 (merchantUid={})", merchantUid);
        throw new IllegalArgumentException(
                "포트원에서 결제 정보를 찾지 못했습니다. 결제에 사용한 고객사 식별코드·채널 키와 "
                        + "서버의 REST API Key/Secret이 같은 상점의 V1 연동 정보인지 확인해 주세요.",
                notFound);
    }

    private JsonNode findPaymentByMerchantUid(String token, String merchantUid) {
        JsonNode body = restClient.get()
                .uri(PAYMENT_URL + "find/"
                        + UriUtils.encodePathSegment(merchantUid, StandardCharsets.UTF_8))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(JsonNode.class);
        return responseNode(body);
    }

    private void pauseBeforePaymentRetry() {
        try {
            Thread.sleep(250L);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("결제 확인이 중단되었습니다.", exception);
        }
    }

    private void cancelTransaction(
            String impUid,
            String merchantUid,
            int amount,
            String reason) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("imp_uid", impUid);
        body.put("merchant_uid", merchantUid);
        body.put("amount", amount);
        body.put("reason", reason);
        JsonNode wrapper = restClient.post()
                .uri(CANCEL_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        responseNode(wrapper);
    }

    private void cancelVirtualAccount(String impUid) {
        String encodedImpUid = UriUtils.encodePathSegment(impUid, StandardCharsets.UTF_8);
        JsonNode wrapper = restClient.delete()
                .uri(VIRTUAL_ACCOUNT_URL + encodedImpUid)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken())
                .retrieve()
                .body(JsonNode.class);
        responseNode(wrapper);
    }

    private JsonNode responseNode(JsonNode body) {
        if (body == null || body.path("code").asInt(-1) != 0) {
            String message = body == null ? "응답 없음" : body.path("message").asText("알 수 없는 오류");
            throw new IllegalArgumentException("포트원 API 처리에 실패했습니다: " + message);
        }
        JsonNode response = body.path("response");
        if (response.isMissingNode() || response.isNull()) {
            throw new IllegalArgumentException("포트원 API 응답 데이터가 없습니다.");
        }
        return response;
    }

    private String requiredText(JsonNode node, String field, String label) {
        String value = node.path(field).asText("");
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("포트원 " + label + "가 없습니다.");
        }
        return value;
    }

    private void requireConfiguration() {
        if (!properties.isPortOneEnabled()) {
            throw new IllegalStateException("포트원 환경변수가 설정되지 않았습니다.");
        }
    }

    private void requireMemberId(Long memberId) {
        if (memberId == null) {
            throw new IllegalArgumentException("로그인이 필요합니다.");
        }
    }

    private boolean secureEquals(String left, String right) {
        if (left == null || right == null) return false;
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8));
    }
}
