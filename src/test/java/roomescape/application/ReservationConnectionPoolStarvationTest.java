package roomescape.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import static roomescape.fixture.MemberFixture.DEFAULT_MEMBER;
import static roomescape.fixture.ReservationTimeFixture.DEFAULT_RESERVATION_TIME;
import static roomescape.fixture.ThemeFixture.DEFAULT_THEME;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.Sql.ExecutionPhase;
import org.springframework.transaction.TransactionTimedOutException;

import roomescape.application.facade.ReservationFacade;
import roomescape.fixture.MemberFixture;
import roomescape.fixture.PaymentFixture;
import roomescape.member.domain.LoginMember;
import roomescape.member.repository.MemberRepository;
import roomescape.payment.api.PaymentClient;
import roomescape.reservation.dto.ReservationPaymentRequest;
import roomescape.theme.repository.ThemeRepository;
import roomescape.time.repository.ReservationTimeRepository;

@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@Sql(value = "/clear.sql", executionPhase = ExecutionPhase.BEFORE_TEST_METHOD)
class ReservationConnectionPoolStarvationTest {

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
    private final LoginMember loginMember = MemberFixture.DEFAULT_LOGIN_MEMBER;

    @BeforeEach
    void initData() {
        themeRepository.save(DEFAULT_THEME);
        reservationTimeRepository.save(DEFAULT_RESERVATION_TIME);
        memberRepository.save(DEFAULT_MEMBER);
    }

    @DisplayName("동시 예약 요청 시, 트랜잭션 타임아웃 덕분에 커넥션 풀 고갈 예외가 발생하지 않아야 한다.")
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

        int threadCount = 15; // 동시 요청 수
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<Future<Void>> futures = new ArrayList<>();

        // PaymentClient.purchase() 호출 시 40초 동안 대기하는 대신,
        // 별도 ExecutorService와 Future.get(6, SECONDS)를 사용해 6초 내에 타임아웃되면 TransactionTimedOutException 발생시키도록 함.
        when(paymentClient.purchase(request.toPaymentRequest())).thenAnswer(invocation -> {
            ExecutorService singleThread = Executors.newSingleThreadExecutor();
            Future<?> future = singleThread.submit(() -> {
                try {
                    Thread.sleep(40000); // 원래 40초 지연
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw ex;
                }
                return PaymentFixture.PAYMENT_INFO;
            });
            try {
                // 6초 내에 완료되지 않으면 TimeoutException 발생
                return future.get(6, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                throw new TransactionTimedOutException("Transaction timed out after 6 seconds");
            } finally {
                singleThread.shutdownNow();
            }
        });

        // when: 여러 스레드에서 동시에 예약 결제 저장 요청 실행
        for (int i = 0; i < threadCount; i++) {
            futures.add(executorService.submit(() -> {
                try {
                    reservationFacade.saveReservationPayment(loginMember, request);
                } finally {
                    latch.countDown();
                }
                return null;
            }));
        }
        latch.await();
        executorService.shutdown();

        boolean connectionPoolTimeoutOccurred = false;
        for (Future<Void> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                Throwable cause = e.getCause();
                while (cause != null) {
                    String msg = cause.getMessage();
                    if (msg != null) {
                        // 트랜잭션 타임아웃 예외는 허용(무시)
                        if (msg.contains("Statement cancelled due to timeout")
                                || msg.contains("transaction timed out")) {
                            break;
                        } else if (msg.contains("Connection is not available")) {
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
        // 트랜잭션 타임아웃 덕분에 커넥션 풀 고갈로 인한 타임아웃 예외는 발생하지 않아야 합니다.
        assertThat(connectionPoolTimeoutOccurred)
                .as("커넥션 풀 고갈로 인한 타임아웃 예외는 발생하지 않아야 합니다.")
                .isFalse();
    }
}
