package roomescape.application.facade;

import org.springframework.stereotype.Service;

import roomescape.application.service.ReservationApplicationService;
import roomescape.member.domain.LoginMember;
import roomescape.payment.dto.PaymentResponse;
import roomescape.reservation.domain.ReservationPaymentResult;
import roomescape.reservation.dto.ReservationPaymentRequest;
import roomescape.reservation.dto.ReservationPaymentResponse;
import roomescape.reservation.dto.ReservationResponse;
import roomescape.reservation.repository.LockRepository;

@Service
public class ReservationFacade {

    private final ReservationApplicationService reservationApplicationService;
    private final LockRepository lockRepository;

    public ReservationFacade(ReservationApplicationService reservationApplicationService, LockRepository lockRepository) {
        this.reservationApplicationService = reservationApplicationService;
        this.lockRepository = lockRepository;
    }

    public ReservationPaymentResponse saveReservationPayment(
            LoginMember loginMember,
            ReservationPaymentRequest reservationPaymentRequest
    ) {
        String key = getKey(reservationPaymentRequest);
        ReservationPaymentResult reservationPaymentResult = null;
        try {
            lockRepository.getLock(key);
            reservationPaymentResult = reservationApplicationService.saveAdvanceReservationPayment(loginMember, reservationPaymentRequest);
        } finally {
            lockRepository.releaseLock(key);
        }
        try {
            return reservationApplicationService.saveDetailedReservationPayment(reservationPaymentResult.reservation(), reservationPaymentResult.paymentResult());
        } catch (Exception e) {
            return new ReservationPaymentResponse(
                    ReservationResponse.from(reservationPaymentResult.reservation()),
                    PaymentResponse.from(reservationPaymentResult.paymentResult()));
        }
    }

    private static String getKey(ReservationPaymentRequest request) {
        return "reservation_"
                + request.date() + "_"
                + request.themeId() + "_"
                + request.timeId();
    }
}
