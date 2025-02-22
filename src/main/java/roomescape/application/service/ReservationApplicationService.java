package roomescape.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;
import roomescape.exception.RoomescapeException;
import roomescape.exception.type.RoomescapeExceptionType;
import roomescape.member.domain.LoginMember;
import roomescape.payment.api.PaymentClient;
import roomescape.payment.domain.PaymentResult;
import roomescape.payment.dto.CancelReason;
import roomescape.payment.entity.Payment;
import roomescape.payment.repository.PaymentRepository;
import roomescape.reservation.domain.ReservationPaymentResult;
import roomescape.reservation.dto.ReservationPaymentRequest;
import roomescape.reservation.dto.ReservationPaymentResponse;
import roomescape.reservation.entity.Reservation;
import roomescape.reservation.service.ReservationService;

@Slf4j
@Service
public class ReservationApplicationService {
    private final PaymentClient paymentClient;
    private final ReservationService reservationService;
    private final PaymentRepository paymentRepository;

    public ReservationApplicationService(
            PaymentClient paymentClient,
            ReservationService reservationService,
            PaymentRepository paymentRepository
    ) {
        this.paymentClient = paymentClient;
        this.reservationService = reservationService;
        this.paymentRepository = paymentRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ReservationPaymentResult saveAdvanceReservationPayment(LoginMember loginMember, ReservationPaymentRequest reservationPaymentRequest) {
        Reservation reservation = reservationService.saveAdvanceReservationPayment(loginMember, reservationPaymentRequest.toReservationRequest(), reservationPaymentRequest.toPaymentRequest());
        PaymentResult paymentResult = paymentClient.purchase(reservationPaymentRequest.toPaymentRequest());
        return new ReservationPaymentResult(reservation, paymentResult);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ReservationPaymentResponse saveDetailedReservationPayment(
            Reservation reservation,
            PaymentResult paymentResult
    ) {
        return reservationService.confirmReservationPayment(reservation, paymentResult);
    }

    @Transactional
    public void cancelReservationPayment(long reservationId) {
        Payment payment = paymentRepository.findByReservationId(reservationId)
                .orElseThrow(() -> new RoomescapeException(RoomescapeExceptionType.NOT_FOUND_RESERVATION_PAYMENT, reservationId));
        paymentClient.cancel(payment.getPaymentKey(), new CancelReason("관리자 권한 취소"));
        reservationService.cancelReservationPayment(reservationId, payment.getId());
    }
}
