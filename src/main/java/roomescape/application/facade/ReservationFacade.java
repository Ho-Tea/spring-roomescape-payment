package roomescape.application.facade;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

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
    private final Lock lock = new ReentrantLock(true);

    public ReservationFacade(ReservationApplicationService reservationApplicationService) {
        this.reservationApplicationService = reservationApplicationService;
    }

    public ReservationPaymentResponse saveReservationPayment(
            LoginMember loginMember,
            ReservationPaymentRequest reservationPaymentRequest
    ) {
        lock.lock();
        try {
            ReservationPaymentResult reservationPaymentResult = reservationApplicationService.saveAdvanceReservationPayment(loginMember, reservationPaymentRequest);
            try {
                return reservationApplicationService.saveDetailedReservationPayment(
                        reservationPaymentResult.reservation(),
                        reservationPaymentResult.paymentResult());
            } catch (Exception e) {
                return new ReservationPaymentResponse(
                        ReservationResponse.from(reservationPaymentResult.reservation()),
                        PaymentResponse.from(reservationPaymentResult.paymentResult()));
            }
        } finally {
            lock.unlock();
        }
    }
}
