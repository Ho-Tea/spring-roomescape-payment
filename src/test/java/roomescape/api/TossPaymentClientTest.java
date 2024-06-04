package roomescape.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import static roomescape.fixture.PaymentFixture.PAYMENT_REQUEST;
import static roomescape.fixture.PaymentFixture.PAYMENT_RESPONSE;

import java.io.IOException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import roomescape.exception.PaymentException;
import roomescape.exception.response.PaymentExceptionResponse;
import roomescape.payment.api.TossPaymentClient;
import roomescape.payment.config.PaymentClientResponseErrorHandler;
import roomescape.payment.dto.PaymentRequest;

@RestClientTest(TossPaymentClient.class)
class TossPaymentClientTest {

    @Autowired
    private MockRestServiceServer mockServer;
    @Autowired
    private TossPaymentClient tossPaymentClient;
    @MockBean
    private PaymentClientResponseErrorHandler responseErrorHandler;
    @Autowired
    private ObjectMapper objectMapper;

    @DisplayName("적합한 인자를 통한 결제 요청 시 성공한다.")
    @Test
    void payment() throws JsonProcessingException {
        String uri = "https://api.tosspayments.com/v1/payments/confirm";

        mockServer
                .expect(requestTo(uri))
                .andExpect(content().json(objectMapper.writeValueAsString(PAYMENT_REQUEST)))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(objectMapper.writeValueAsString(PAYMENT_RESPONSE), MediaType.APPLICATION_JSON));


        assertThat(tossPaymentClient.payment(PAYMENT_REQUEST)).isEqualTo(PAYMENT_RESPONSE);
        mockServer.verify();
    }

    @DisplayName("적합하지 못한 인자를 통한 결제 요청 시 실패한다.")
    @Test
    void failPayment() throws IOException {
        String uri = "https://api.tosspayments.com/v1/payments/confirm";
        String errorMessage = "적합하지 않은 paymentKey입니다.";
        PaymentRequest invalidPaymentRequest = new PaymentRequest("invalid", "invalidOrderId", 1000);

        mockServer
                .expect(requestTo(uri))
                .andExpect(content().json(objectMapper.writeValueAsString(invalidPaymentRequest)))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).body(errorMessage));
        when(responseErrorHandler.hasError(any()))
                .thenThrow(new PaymentException(PaymentExceptionResponse.of(HttpStatus.BAD_REQUEST, "INVALID_ERRORCIDE", errorMessage)));

        assertThatThrownBy(() -> tossPaymentClient.payment(invalidPaymentRequest))
                .isInstanceOf(PaymentException.class)
                .hasMessage(errorMessage);
    }
}