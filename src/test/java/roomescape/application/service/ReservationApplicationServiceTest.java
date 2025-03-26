package roomescape.application.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static roomescape.fixture.MemberFixture.DEFAULT_MEMBER;
import static roomescape.fixture.PaymentFixture.PAYMENT_INFO;
import static roomescape.fixture.PaymentFixture.PAYMENT_REQUEST;
import static roomescape.fixture.ReservationTimeFixture.DEFAULT_RESERVATION_TIME;
import static roomescape.fixture.ThemeFixture.DEFAULT_THEME;

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

import roomescape.fixture.MemberFixture;
import roomescape.member.domain.LoginMember;
import roomescape.member.repository.MemberRepository;
import roomescape.payment.api.PaymentClient;
import roomescape.payment.domain.PaymentResult;
import roomescape.payment.dto.CancelReason;
import roomescape.reservation.dto.ReservationPaymentRequest;
import roomescape.reservation.service.ReservationService;
import roomescape.theme.repository.ThemeRepository;
import roomescape.time.repository.ReservationTimeRepository;

@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@Sql(value = "/clear.sql", executionPhase = ExecutionPhase.BEFORE_TEST_METHOD)
class ReservationApplicationServiceTest {

    @SpyBean
    private ReservationService reservationService;
    @MockBean
    private PaymentClient paymentClient;
    @Autowired
    private ThemeRepository themeRepository;
    @Autowired
    private ReservationTimeRepository reservationTimeRepository;
    @Autowired
    private MemberRepository memberRepository;
    private final LoginMember loginMember = MemberFixture.DEFAULT_LOGIN_MEMBER;
    @Autowired
    private ReservationApplicationService reservationApplicationService;

    @BeforeEach
    void initData() {
        themeRepository.save(DEFAULT_THEME);
        reservationTimeRepository.save(DEFAULT_RESERVATION_TIME);
        memberRepository.save(DEFAULT_MEMBER);
    }

    @DisplayName("예약 저장 실패 시 결제 취소 보상 로직이 실행되어야 한다.")
    @Test
    public void compensationOnReservationSaveFailure() {
        // given
        ReservationPaymentRequest request = new ReservationPaymentRequest(
                LocalDate.now().plusDays(1),
                DEFAULT_THEME.getId(),
                DEFAULT_RESERVATION_TIME.getId(),
                PAYMENT_REQUEST.paymentKey(),
                PAYMENT_REQUEST.orderId(),
                PAYMENT_REQUEST.amount()
        );

        // paymentClient.purchase()는 정상적으로 PaymentResult를 반환함.
        when(paymentClient.purchase(request.toPaymentRequest())).thenReturn(PAYMENT_INFO);

        // reservationService.saveReservationPayment()가 실행 중 예외를 발생시켜 트랜잭션이 롤백되도록 함.
        doThrow(new RuntimeException("Reservation save failure"))
                .when(reservationService)
                .saveReservationPayment(any(LoginMember.class), any(), any(PaymentResult.class));

        // when & then: reservationFacade.saveReservationPayment 호출 시 예외가 발생해야 하며,
        // 트랜잭션 롤백 후, 트랜잭션 동기화 콜백을 통해 paymentClient.cancel()이 호출되어야 함.
        assertThatThrownBy(() -> reservationApplicationService.saveReservationPayment(loginMember, request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Reservation save failure");

        // verify: 결제 취소가 실행되었는지 검증 (PAYMENT_INFO는 정상 결제 결과에서 얻은 PaymentResult)
        verify(paymentClient).cancel(PAYMENT_INFO.paymentKey(), new CancelReason("관리자 권한 취소"));
    }
}

