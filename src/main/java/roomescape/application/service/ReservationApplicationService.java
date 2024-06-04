package roomescape.application.service;

import org.springframework.stereotype.Service;

import roomescape.member.domain.LoginMember;
import roomescape.payment.api.PaymentClient;
import roomescape.payment.dto.PaymentRequest;
import roomescape.payment.dto.PaymentResponse;
import roomescape.reservation.dto.ReservationPaymentRequest;
import roomescape.reservation.dto.ReservationPaymentResponse;
import roomescape.reservation.dto.ReservationRequest;
import roomescape.reservation.dto.ReservationResponse;
import roomescape.reservation.service.ReservationService;

@Service
public class ReservationApplicationService {
    private final PaymentClient paymentClient;
    private final ReservationService reservationService;

    public ReservationApplicationService(PaymentClient paymentClient, ReservationService reservationService) {
        this.paymentClient = paymentClient;
        this.reservationService = reservationService;
    }

    public ReservationPaymentResponse reservationPayment(LoginMember loginMember, ReservationPaymentRequest reservationPaymentRequest) {
        PaymentResponse paymentResponse = paymentClient.payment(PaymentRequest.from(reservationPaymentRequest));
        ReservationResponse reservationResponse = reservationService.save(loginMember, ReservationRequest.from(reservationPaymentRequest));
        return new ReservationPaymentResponse(reservationResponse, paymentResponse);
    }
}