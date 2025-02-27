package roomescape.payment.api;

import roomescape.payment.domain.PaymentResult;
import roomescape.payment.dto.CancelReason;
import roomescape.payment.dto.PaymentRequest;

public interface PaymentClient {
    PaymentResult purchase(PaymentRequest paymentRequest);

    PaymentResult cancel(String paymentKey, CancelReason cancelReason);

    PaymentResult lookup(String paymentKey);
}
