package roomescape.application.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static com.jayway.jsonpath.internal.path.PathCompiler.fail;

import static roomescape.fixture.MemberFixture.DEFAULT_MEMBER;
import static roomescape.fixture.PaymentFixture.PAYMENT_INFO;
import static roomescape.fixture.PaymentFixture.PAYMENT_REQUEST;
import static roomescape.fixture.ReservationTimeFixture.DEFAULT_RESERVATION_TIME;
import static roomescape.fixture.ThemeFixture.DEFAULT_THEME;
import static roomescape.reservation.domain.ReservationStatus.ADVANCE_BOOKED;

import java.sql.SQLNonTransientConnectionException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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

import roomescape.exception.PaymentException;
import roomescape.exception.response.UserPaymentExceptionResponse;
import roomescape.fixture.MemberFixture;
import roomescape.fixture.PaymentFixture;
import roomescape.member.domain.LoginMember;
import roomescape.member.repository.MemberRepository;
import roomescape.payment.api.PaymentClient;
import roomescape.payment.domain.PaymentResult;
import roomescape.payment.repository.PaymentRepository;
import roomescape.reservation.dto.ReservationPaymentRequest;
import roomescape.reservation.entity.Reservation;
import roomescape.reservation.repository.ReservationRepository;
import roomescape.reservation.service.ReservationService;
import roomescape.theme.repository.ThemeRepository;
import roomescape.time.repository.ReservationTimeRepository;

@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@Sql(value = "/clear.sql", executionPhase = ExecutionPhase.BEFORE_TEST_METHOD)
class ReservationFacadeTest {

    @SpyBean
    private ReservationService reservationService;
    @MockBean
    private PaymentClient paymentClient;
    @Autowired
    private ReservationFacade reservationFacade;
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

    @DisplayName("사전 정보 저장 트랜잭션이 실패할 경우 세부 정보 저장 로직이 실행되지 않는다.")
    @Test
    public void separateTransactionsBetweenAdvanceAndDetail() {
        // given
        ReservationPaymentRequest request = new ReservationPaymentRequest(
                LocalDate.now().plusDays(1),
                DEFAULT_THEME.getId(),
                DEFAULT_RESERVATION_TIME.getId(),
                PAYMENT_REQUEST.paymentKey(),
                PAYMENT_REQUEST.orderId(),
                PAYMENT_REQUEST.amount());

        // when
        when(paymentClient.purchase(request.toPaymentRequest()))
                .thenThrow(new PaymentException(UserPaymentExceptionResponse.of("INVALID_ERROR_CODE", "예외 발생")));

        // then
        assertAll(
                () -> assertThatThrownBy(() -> reservationFacade.saveReservationPayment(loginMember, request)).isInstanceOf(PaymentException.class),
                () -> verify(reservationService, never()).confirmReservationPayment(any(Reservation.class), any(PaymentResult.class))
        );
    }

    @DisplayName("세부 정보 저장 트랜잭션이 롤백되어도 사전 정보 저장 트랜잭션은 정상 커밋 된다. -> saveAdvanceReservationPayment 과 saveDetailedPayment 이 서로 다른 트랜잭션에서 시작하는 것을 검증")
    @Test
    public void separateTransactionsBetweenAdvanceAndDetail2() {
        // given
        ReservationPaymentRequest request = new ReservationPaymentRequest(
                LocalDate.now().plusDays(1),
                DEFAULT_THEME.getId(),
                DEFAULT_RESERVATION_TIME.getId(),
                PAYMENT_REQUEST.paymentKey(),
                PAYMENT_REQUEST.orderId(),
                PAYMENT_REQUEST.amount());

        // when
        when(paymentClient.purchase(request.toPaymentRequest())).thenReturn(PAYMENT_INFO);
        doThrow(new RuntimeException("saveDetailedPayment 실행 중 예외 발생"))
                .when(reservationService).confirmReservationPayment(any(Reservation.class), any(PaymentResult.class));

        reservationFacade.saveReservationPayment(loginMember, request);
        assertAll(
                () -> assertThat(reservationRepository.findAll().size()).isEqualTo(1),
                () -> assertThat(reservationRepository.findById(1L).get().getStatus()).isEqualTo(ADVANCE_BOOKED),
                () -> assertThat(paymentRepository.findAll().size()).isEqualTo(1),
                () -> assertThat(paymentRepository.findById(1L).get()
                        .getPaymentKey()).isEqualTo(PAYMENT_REQUEST.paymentKey())
        );
    }

    @DisplayName("예약 요청이 동시에 100개가 들어왔을 때 특정 시간과 테마에 대한 예약이 단 하나 생성되어야 한다.")
    @Test
    public void concurrency() throws InterruptedException {
        // given
        ReservationPaymentRequest request = new ReservationPaymentRequest(
                LocalDate.now().plusDays(1),
                DEFAULT_THEME.getId(),
                DEFAULT_RESERVATION_TIME.getId(),
                PAYMENT_REQUEST.paymentKey(),
                PAYMENT_REQUEST.orderId(),
                PAYMENT_REQUEST.amount());
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // when
        when(paymentClient.purchase(request.toPaymentRequest())).thenReturn(PAYMENT_INFO);
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    reservationFacade.saveReservationPayment(loginMember, request);
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();

        // then
        assertThat(reservationRepository.findAll().size()).isEqualTo(1);
    }

    @DisplayName("동시 예약 요청 시, 커넥션 풀 고갈로 인한 타임아웃 예외만 검증")
    @Test
    public void connectionPoolExhaustionException() throws Exception {
        // given: 유효한 예약 결제 요청 생성
        ReservationPaymentRequest request = new ReservationPaymentRequest(
                LocalDate.now().plusDays(1),
                1L,
                1L,
                PaymentFixture.PAYMENT_REQUEST.paymentKey(),
                PaymentFixture.PAYMENT_REQUEST.orderId(),
                PaymentFixture.PAYMENT_REQUEST.amount()
        );

        int threadCount = 100; // 동시 요청 수
        ExecutorService executorService = Executors.newFixedThreadPool(26);
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<Future<Void>> futures = new ArrayList<>();

        // PaymentClient.purchase() 호출 시 30초 지연을 발생시켜 커넥션 반환이 늦어지도록 설정
        when(paymentClient.purchase(request.toPaymentRequest())).thenAnswer(invocation -> {
            Thread.sleep(30000); // 30초 지연
            return PaymentFixture.PAYMENT_INFO;
        });

        // when: 여러 스레드에서 동시에 예약 결제 저장 요청 실행
        for (int i = 0; i < threadCount; i++) {
            futures.add(executorService.submit(() -> {
                try {
                    reservationFacade.saveReservationPayment(loginMember, request);
                } catch (Exception e) {
                    throw e;
                } finally {
                    latch.countDown();
                }
                return null;
            }));
        }
        latch.await();
        executorService.shutdown();

        // then: 각 Future의 예외 체인을 검사하여,
        // "Connection is not available" 메시지가 포함된 예외가 있다면 커넥션 풀 고갈로 인한 타임아웃,
        // 단, "Statement cancelled due to timeout"나 "Transaction timed out" 문자열이 있는 경우는 트랜잭션 타임아웃이므로 무시합니다.
        boolean connectionPoolTimeoutOccurred = false;
        for (Future<Void> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                Throwable cause = e.getCause();
                while (cause != null) {
                    String msg = cause.getMessage();
                    if (msg != null) {
                        if (msg.contains("Statement cancelled due to timeout")
                                || msg.contains("Transaction timed out")) {
                            // 트랜잭션 타임아웃 예외는 무시
                            break;
                        }else if (msg.contains("Connection is not available")) {
                            connectionPoolTimeoutOccurred = true;
                            break;
                        }
                    }
                    cause = cause.getCause();
                }
            }
            if (connectionPoolTimeoutOccurred) {
                break;
            }
        }
        // 커넥션 풀 고갈로 인한 타임아웃 예외가 발생하지 않아야 함을 검증
        assertThat(connectionPoolTimeoutOccurred)
                .as("커넥션 풀 고갈로 인한 타임아웃 예외는 발생하지 않아야 합니다.")
                .isFalse();
    }
}
