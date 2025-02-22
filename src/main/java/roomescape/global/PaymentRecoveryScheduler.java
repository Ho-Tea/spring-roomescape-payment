package roomescape.global;

import static roomescape.exception.type.RoomescapeExceptionType.NOT_FOUND_RESERVATION_PAYMENT;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import roomescape.exception.RoomescapeException;
import roomescape.payment.api.PaymentClient;
import roomescape.payment.domain.PaymentResult;
import roomescape.payment.entity.Payment;
import roomescape.payment.repository.PaymentRepository;
import roomescape.reservation.entity.Reservation;
import roomescape.reservation.service.ReservationService;

@Slf4j
@Component
public class PaymentRecoveryScheduler {

    private final ReservationService reservationService;
    private final PaymentClient paymentClient;
    private final PaymentRepository paymentRepository;

    public PaymentRecoveryScheduler(ReservationService reservationService, PaymentClient paymentClient, PaymentRepository paymentRepository) {
        this.reservationService = reservationService;
        this.paymentClient = paymentClient;
        this.paymentRepository = paymentRepository;
    }

    @Scheduled(cron = "0 * * * * *")
    public void checkPaymentConsistency() {
        log.info("결제 일관성 검사 시작 시각: {}", LocalDateTime.now());
        List<Reservation> pendingReservations = reservationService.findPendingDetailedPayments();

        pendingReservations.forEach(
                reservation -> {
                    try {
                        Payment payment = paymentRepository.findByReservationId(reservation.getId())
                                .orElseThrow(() -> new RoomescapeException(NOT_FOUND_RESERVATION_PAYMENT, reservation.getId()));
                        PaymentResult paymentResult = paymentClient.lookup(payment.getPaymentKey());
                        if (paymentResult.paymentKey().equals(payment.getPaymentKey())) {
                            reservationService.confirmReservationPayment(reservation, paymentResult);
                        }
                    } catch (Exception ex) {
                        log.error("예약번호 {} 결제 일관성 검사 중 오류 발생: {}", reservation.getId(), ex.getMessage(), ex);
                    }
                }
        );
        log.info("결제 일관성 검사 완료 시각: {}", LocalDateTime.now());
    }
}

