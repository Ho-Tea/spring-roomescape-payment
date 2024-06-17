package roomescape.payment.api;

import java.util.Base64;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import lombok.extern.slf4j.Slf4j;
import roomescape.payment.config.PaymentClientResponseErrorHandler;
import roomescape.payment.domain.PaymentResult;
import roomescape.payment.dto.CancelReason;
import roomescape.payment.dto.PaymentRequest;

@Slf4j
@Component
public class TossPaymentClient implements PaymentClient {
    private static final String DELIMITER = ":";
    private static final String AUTH_HEADER = "Authorization";
    private static final String AUTH_METHOD = "Basic ";
    private static final String APPROVE_PAYMENT_URI = "/v1/payments/confirm";
    private static final String CANCEL_PAYMENT_URI = "/v1/payments/{paymentKey}/cancel";
    private final String encodedSecretKey;
    private final RestClient restClient;
    private final PaymentClientResponseErrorHandler paymentClientResponseErrorHandler;

    public TossPaymentClient(@Value("${security.toss.payment.secret-key}") String secretKey,
                             @Value("${security.toss.payment.url}") String paymentUrl,
                             RestClient.Builder restClientBuilder,
                             PaymentClientResponseErrorHandler paymentClientResponseErrorHandler) {
        this.encodedSecretKey = Base64.getEncoder()
                .encodeToString((secretKey + DELIMITER).getBytes());
        this.restClient = restClientBuilder
                .baseUrl(paymentUrl)
                .build();
        this.paymentClientResponseErrorHandler = paymentClientResponseErrorHandler;
    }

    @Override
    public PaymentResult purchase(PaymentRequest paymentRequest) {
        log.info("traceId: {}, PURCHASE_URI: {}", MDC.get("traceId"), APPROVE_PAYMENT_URI);
        PaymentResult paymentResult = restClient.post()
                .uri(APPROVE_PAYMENT_URI)
                .header(AUTH_HEADER, AUTH_METHOD + encodedSecretKey)
                .body(paymentRequest)
                .retrieve()
                .onStatus(paymentClientResponseErrorHandler)
                .body(PaymentResult.class);
        return paymentResult;
    }

    @Override
    public PaymentResult cancel(String paymentKey, CancelReason cancelReason) {
        log.info("traceId: {}, CANCEL_URI: {}", MDC.get("traceId"), CANCEL_PAYMENT_URI);
        PaymentResult paymentResult = restClient.post()
                .uri(CANCEL_PAYMENT_URI, paymentKey)
                .header(AUTH_HEADER, AUTH_METHOD + encodedSecretKey)
                .body(cancelReason)
                .retrieve()
                .onStatus(paymentClientResponseErrorHandler)
                .body(PaymentResult.class);
        return paymentResult;
    }
}
