package roomescape.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import static roomescape.fixture.MemberFixture.DEFAULT_MEMBER;
import static roomescape.fixture.PaymentFixture.INVALID_PAYMENT_INFO;
import static roomescape.fixture.PaymentFixture.PAYMENT_INFO;
import static roomescape.fixture.PaymentFixture.PAYMENT_REQUEST;
import static roomescape.fixture.ReservationTimeFixture.DEFAULT_RESERVATION_TIME;
import static roomescape.fixture.ThemeFixture.DEFAULT_THEME;
import static roomescape.reservation.domain.ReservationStatus.ADVANCE_BOOKED;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.Sql.ExecutionPhase;

import roomescape.application.service.ReservationApplicationService;
import roomescape.exception.PaymentException;
import roomescape.exception.response.UserPaymentExceptionResponse;
import roomescape.fixture.MemberFixture;
import roomescape.fixture.PaymentFixture;
import roomescape.fixture.ReservationFixture;
import roomescape.fixture.ReservationTimeFixture;
import roomescape.fixture.ThemeFixture;
import roomescape.member.domain.LoginMember;
import roomescape.member.repository.MemberRepository;
import roomescape.payment.api.PaymentClient;
import roomescape.payment.dto.CancelReason;
import roomescape.payment.dto.PaymentRequest;
import roomescape.payment.entity.Payment;
import roomescape.payment.repository.PaymentRepository;
import roomescape.reservation.domain.ReservationPaymentResult;
import roomescape.reservation.dto.ReservationPaymentRequest;
import roomescape.reservation.entity.Reservation;
import roomescape.reservation.repository.ReservationRepository;
import roomescape.reservation.service.ReservationService;
import roomescape.theme.repository.ThemeRepository;
import roomescape.time.repository.ReservationTimeRepository;

@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@Sql(value = "/clear.sql", executionPhase = ExecutionPhase.BEFORE_TEST_METHOD)
class ReservationApplicationServiceTest {

    @MockBean
    private PaymentClient paymentClient;
    @Autowired
    private ReservationApplicationService reservationApplicationService;
    @SpyBean
    private ReservationService reservationService;
    @Autowired
    private ThemeRepository themeRepository;
    @Autowired
    private ReservationTimeRepository reservationTimeRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    private final LoginMember loginMember = MemberFixture.DEFAULT_LOGIN_MEMBER;

    @BeforeEach
    void initData() {
        themeRepository.save(DEFAULT_THEME);
        reservationTimeRepository.save(DEFAULT_RESERVATION_TIME);
        memberRepository.save(DEFAULT_MEMBER);
    }

    @DisplayName("결제에 성공할 경우 예약에 성공한다.")
    @Test
    void saveReservationPayment() {
        ReservationPaymentRequest request = new ReservationPaymentRequest(
                LocalDate.now().plusDays(1),
                DEFAULT_THEME.getId(),
                DEFAULT_RESERVATION_TIME.getId(),
                PAYMENT_REQUEST.paymentKey(),
                PAYMENT_REQUEST.orderId(),
                PAYMENT_REQUEST.amount());

        when(paymentClient.purchase(PAYMENT_REQUEST)).thenReturn(PAYMENT_INFO);

        ReservationPaymentResult reservationPaymentResult = reservationApplicationService.saveAdvanceReservationPayment(loginMember, request);
        assertAll(
                () -> assertThat(reservationPaymentResult.reservation().getStatus()).isEqualTo(ADVANCE_BOOKED),
                () -> assertThat(reservationPaymentResult.paymentResult().paymentKey()).isEqualTo(PAYMENT_REQUEST.paymentKey())
        );
    }

    @DisplayName("결제에 실패할 경우 예약에 실패한다.")
    @Test
    void failSaveReservationPayment() {
        PaymentRequest invalidPaymentRequest = new PaymentRequest("invalidPaymentKey", "invalidOrderId", BigDecimal.valueOf(1000));
        ReservationPaymentRequest request = new ReservationPaymentRequest(
                LocalDate.now().plusDays(1),
                DEFAULT_THEME.getId(),
                DEFAULT_RESERVATION_TIME.getId(),
                invalidPaymentRequest.paymentKey(),
                invalidPaymentRequest.orderId(),
                invalidPaymentRequest.amount());

        when(paymentClient.purchase(invalidPaymentRequest))
                .thenThrow(new PaymentException(UserPaymentExceptionResponse.of("INVALID_PAYMENT_KEY", "올바르지 않은 PaymentKey 입니다.")));

        assertThatThrownBy(() -> reservationApplicationService.saveAdvanceReservationPayment(loginMember, request))
                .isInstanceOf(PaymentException.class);
    }

    @DisplayName("결제 취소에 성공할 경우 예약, 결제 내역 삭제에 성공한다.")
    @Test
    void cancelReservationPayment() {
        Reservation reservation = ReservationFixture.ReservationOfDate(LocalDate.now().plusDays(1));
        Reservation savedReservation = reservationRepository.save(reservation);
        paymentRepository.save(new Payment(savedReservation, PAYMENT_INFO));

        when(paymentClient.cancel(PAYMENT_REQUEST.paymentKey(), new CancelReason("관리자 권한 취소")))
                .thenReturn(PAYMENT_INFO);

        assertThatCode(() -> reservationApplicationService.cancelReservationPayment(savedReservation.getId()))
                .doesNotThrowAnyException();
    }

    @DisplayName("결제 취소에 실패할 경우 예약, 결제 내역 삭제에 실패한다.")
    @Test
    void failCancelReservationPayment() {
        Reservation reservation = ReservationFixture.ReservationOfDate(LocalDate.now().plusDays(1));
        Reservation savedReservation = reservationRepository.save(reservation);
        paymentRepository.save(new Payment(savedReservation, INVALID_PAYMENT_INFO));

        when(paymentClient.cancel(INVALID_PAYMENT_INFO.paymentKey(), new CancelReason("관리자 권한 취소")))
                .thenThrow(new PaymentException(UserPaymentExceptionResponse.of("INVALID_PAYMENT_KEY", "올바르지 않은 PaymentKey 입니다.")));

        assertThatThrownBy(() -> reservationApplicationService.cancelReservationPayment(savedReservation.getId()))
                .isInstanceOf(PaymentException.class);
    }

    @DisplayName("사전 예약 정보 저장 시 트랜잭션 timeout 시간을 초과할 경우 예외가 발생한다.")
    @Test
    void transactionTimeOut() {
        // given
        ReservationPaymentRequest request = new ReservationPaymentRequest(
                LocalDate.now().plusDays(1),
                ThemeFixture.DEFAULT_THEME.getId(),
                ReservationTimeFixture.DEFAULT_RESERVATION_TIME.getId(),
                PaymentFixture.PAYMENT_REQUEST.paymentKey(),
                PaymentFixture.PAYMENT_REQUEST.orderId(),
                PaymentFixture.PAYMENT_REQUEST.amount()
        );

        // when
        doAnswer(invocation -> {
            Thread.sleep(7000L); // 7초 지연 → 타임아웃 발생 예상
            return invocation.callRealMethod();
        }).when(reservationService)
                .saveAdvanceReservationPayment(loginMember, request.toReservationRequest(), request.toPaymentRequest());

        // then
        assertThatThrownBy(() -> reservationApplicationService.saveAdvanceReservationPayment(loginMember, request))
                .isInstanceOf(org.springframework.orm.jpa.JpaSystemException.class)
                .hasMessage("transaction timeout expired");
    }
}
