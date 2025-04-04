package roomescape.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static roomescape.exception.type.RoomescapeExceptionType.DELETE_USED_TIME;
import static roomescape.exception.type.RoomescapeExceptionType.DUPLICATE_RESERVATION_TIME;
import static roomescape.fixture.ThemeFixture.DEFAULT_THEME;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.Sql.ExecutionPhase;

import roomescape.fixture.MemberFixture;
import roomescape.reservation.domain.ReservationStatus;
import roomescape.time.domain.ReservationTimes;
import roomescape.time.dto.AvailableTimeResponse;
import roomescape.time.dto.ReservationTimeRequest;
import roomescape.time.dto.ReservationTimeResponse;
import roomescape.reservation.entity.Reservation;
import roomescape.time.entity.ReservationTime;
import roomescape.theme.entity.Theme;
import roomescape.exception.RoomescapeException;
import roomescape.member.repository.MemberRepository;
import roomescape.reservation.repository.ReservationRepository;
import roomescape.time.repository.ReservationTimeRepository;
import roomescape.theme.repository.ThemeRepository;
import roomescape.time.service.ReservationTimeService;

@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@Sql(value = "/clear.sql", executionPhase = ExecutionPhase.BEFORE_TEST_METHOD)
class ReservationTimeServiceTest {

    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private ReservationTimeRepository reservationTimeRepository;
    @Autowired
    private ReservationTimeService reservationTimeService;
    @Autowired
    private ThemeRepository themeRepository;
    @Autowired
    private MemberRepository memberRepository;


    @BeforeEach
    void setUp() {
        themeRepository.save(DEFAULT_THEME);
        memberRepository.save(MemberFixture.DEFAULT_MEMBER);
    }

    @DisplayName("저장된 시간을 모두 조회할 수 있다.")
    @Test
    void findAllTest() {
        //given
        reservationTimeRepository.save(new ReservationTime(LocalTime.of(10, 0)));
        reservationTimeRepository.save(new ReservationTime(LocalTime.of(11, 0)));
        reservationTimeRepository.save(new ReservationTime(LocalTime.of(12, 0)));
        reservationTimeRepository.save(new ReservationTime(LocalTime.of(13, 0)));

        //when
        List<ReservationTimeResponse> reservationTimeResponses = reservationTimeService.findAll();

        //then
        assertThat(reservationTimeResponses)
                .hasSize(4);
    }

    @DisplayName("날짜와 테마, 시간에 대한 예약 내역을 확인할 수 있다.")
    @Test
    void findAvailableTimeTest() {
        //given
        Theme DEFUALT_THEME = new Theme(1L, "name", "description", "thumbnail");
        themeRepository.save(DEFUALT_THEME);

        ReservationTime reservationTime1 = reservationTimeRepository.save(new ReservationTime(LocalTime.of(11, 0)));
        ReservationTime reservationTime2 = reservationTimeRepository.save(new ReservationTime(LocalTime.of(12, 0)));
        ReservationTime reservationTime3 = reservationTimeRepository.save(new ReservationTime(LocalTime.of(13, 0)));
        ReservationTime reservationTime4 = reservationTimeRepository.save(new ReservationTime(LocalTime.of(14, 0)));

        LocalDate selectedDate = LocalDate.of(2024, 1, 1);

        reservationRepository.save(new Reservation(selectedDate, reservationTime1, DEFUALT_THEME,
                MemberFixture.DEFAULT_MEMBER, ReservationStatus.BOOKED));
        reservationRepository.save(new Reservation(selectedDate, reservationTime3, DEFUALT_THEME,
                MemberFixture.DEFAULT_MEMBER, ReservationStatus.BOOKED));

        //when
        List<AvailableTimeResponse> availableTimeResponses = reservationTimeService.findByThemeAndDate(selectedDate,
                DEFUALT_THEME.getId());

        //then
        assertThat(availableTimeResponses).containsExactlyInAnyOrder(
                new AvailableTimeResponse(1L, reservationTime1.getStartAt(), true),
                new AvailableTimeResponse(2L, reservationTime2.getStartAt(), false),
                new AvailableTimeResponse(3L, reservationTime3.getStartAt(), true),
                new AvailableTimeResponse(4L, reservationTime4.getStartAt(), false)
        );
    }

    @DisplayName("예약 시간이 하나 존재할 때")
    @Nested
    class OneReservationTimeExists {
        private static final LocalTime SAVED_TIME = LocalTime.of(10, 0);

        @BeforeEach
        void addDefaultTime() {
            ReservationTimeRequest reservationTimeRequest = new ReservationTimeRequest(SAVED_TIME);
            reservationTimeService.save(reservationTimeRequest);
        }

        @DisplayName("정상적으로 시간을 생성할 수 있다.")
        @Test
        void saveReservationTimeTest() {
            assertThatCode(() ->
                    reservationTimeService.save(new ReservationTimeRequest(SAVED_TIME.plusHours(1))))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("중복된 시간은 생성할 수 없는지 검증")
        void saveFailCauseDuplicate() {
            assertThatThrownBy(() -> reservationTimeService.save(new ReservationTimeRequest(SAVED_TIME)))
                    .isInstanceOf(RoomescapeException.class)
                    .hasMessage(DUPLICATE_RESERVATION_TIME.getMessage());
        }

        @DisplayName("저장된 시간을 삭제할 수 있다.")
        @Test
        void deleteByIdTest() {
            //when
            reservationTimeService.delete(1L);

            //then
            assertThat(new ReservationTimes(reservationTimeRepository.findAll()).getReservationTimes())
                    .isEmpty();
        }

        @DisplayName("예약 시간을 사용하는 예약이 있으면 예약을 삭제할 수 없다.")
        @Test
        void usedReservationTimeDeleteTest() {
            //given
            reservationRepository.save(new Reservation(
                    LocalDate.now(),
                    new ReservationTime(1L, SAVED_TIME),
                    DEFAULT_THEME,
                    MemberFixture.DEFAULT_MEMBER, ReservationStatus.BOOKED
            ));

            //when & then
            assertThatCode(() -> reservationTimeService.delete(1L))
                    .isInstanceOf(RoomescapeException.class)
                    .hasMessage(DELETE_USED_TIME.getMessage());
        }
    }
}
