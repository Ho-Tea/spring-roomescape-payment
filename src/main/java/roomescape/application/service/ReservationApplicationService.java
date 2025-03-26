package roomescape.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import roomescape.exception.RoomescapeException;
import roomescape.exception.type.RoomescapeExceptionType;
import roomescape.member.domain.LoginMember;
import roomescape.payment.api.PaymentClient;
import roomescape.payment.domain.PaymentResult;
import roomescape.payment.dto.CancelReason;
import roomescape.payment.entity.Payment;
import roomescape.payment.repository.PaymentRepository;
import roomescape.reservation.dto.ReservationPaymentRequest;
import roomescape.reservation.dto.ReservationPaymentResponse;
import roomescape.reservation.service.ReservationService;

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

    @Transactional
    public ReservationPaymentResponse saveReservationPayment(
            LoginMember loginMember,
            ReservationPaymentRequest reservationPaymentRequest
    ) {
        PaymentResult paymentResult = paymentClient.purchase(reservationPaymentRequest.toPaymentRequest());
        // 트랜잭션 동기화 콜백을 등록하여, 트랜잭션이 롤백될 경우 보상 처리(결제 취소)를 수행함
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                    // 트랜잭션 롤백 시 결제 취소 요청 수행
                    try {
                        paymentClient.cancel(reservationPaymentRequest.paymentKey(), new CancelReason("관리자 권한 취소"));
                    } catch (Exception ex) {
                        // 보상 처리 실패 시 로그 등을 통해 모니터링
                        System.err.println("결제 취소 보상 처리 실패: " + ex.getMessage());
                    }
                }
            }
        });
        return reservationService.saveReservationPayment(loginMember, reservationPaymentRequest.toReservationRequest(), paymentResult);
    }

    public void cancelReservationPayment(long reservationId) {
        Payment payment = paymentRepository.findByReservationId(reservationId)
                .orElseThrow(() -> new RoomescapeException(RoomescapeExceptionType.NOT_FOUND_RESERVATION_PAYMENT, reservationId));
        paymentClient.cancel(payment.getPaymentKey(), new CancelReason("관리자 권한 취소"));
        reservationService.cancelReservationPayment(reservationId, payment.getId());
    }
}

