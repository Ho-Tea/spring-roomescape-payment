package roomescape.application.facade;

import org.springframework.stereotype.Service;

import roomescape.application.service.ReservationApplicationService;
import roomescape.member.domain.LoginMember;
import roomescape.payment.dto.PaymentResponse;
import roomescape.reservation.domain.ReservationPaymentResult;
import roomescape.reservation.dto.ReservationPaymentRequest;
import roomescape.reservation.dto.ReservationPaymentResponse;
import roomescape.reservation.dto.ReservationResponse;

@Service
public class ReservationFacade {

    private final ReservationApplicationService reservationApplicationService;

    public ReservationFacade(ReservationApplicationService reservationApplicationService) {
        this.reservationApplicationService = reservationApplicationService;
    }

    public synchronized ReservationPaymentResponse saveReservationPayment(
            LoginMember loginMember,
            ReservationPaymentRequest reservationPaymentRequest
    ) {
        ReservationPaymentResult reservationPaymentResult = reservationApplicationService.saveAdvanceReservationPayment(loginMember, reservationPaymentRequest);
        try {
            return reservationApplicationService.saveDetailedReservationPayment(reservationPaymentResult.reservation(), reservationPaymentResult.paymentResult());
        } catch (Exception e) {
            return new ReservationPaymentResponse(
                    ReservationResponse.from(reservationPaymentResult.reservation()),
                    PaymentResponse.from(reservationPaymentResult.paymentResult()));
        }
    }
}
