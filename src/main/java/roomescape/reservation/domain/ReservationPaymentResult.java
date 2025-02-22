package roomescape.reservation.domain;

import roomescape.payment.domain.PaymentResult;
import roomescape.reservation.entity.Reservation;

public record ReservationPaymentResult(Reservation reservation, PaymentResult paymentResult) {
}
