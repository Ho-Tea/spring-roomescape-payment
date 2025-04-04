package roomescape.fixture;

import java.time.LocalDate;

import roomescape.reservation.domain.ReservationStatus;
import roomescape.member.entity.Member;
import roomescape.reservation.entity.Reservation;
import roomescape.theme.entity.Theme;

public class ReservationFixture {
    public static final Reservation ReservationOfDate(LocalDate date) {
        return new Reservation(
                date,
                ReservationTimeFixture.DEFAULT_RESERVATION_TIME,
                ThemeFixture.DEFAULT_THEME,
                MemberFixture.DEFAULT_MEMBER,
                ReservationStatus.BOOKED);
    }

    public static final Reservation ReservationOfDateAndTheme(LocalDate date, Theme theme) {
        return new Reservation(
                date,
                ReservationTimeFixture.DEFAULT_RESERVATION_TIME,
                theme,
                MemberFixture.DEFAULT_MEMBER,
                ReservationStatus.BOOKED);
    }

    public static final Reservation ReservationOfDateAndStatus(LocalDate date, ReservationStatus status) {
        return new Reservation(
                date,
                ReservationTimeFixture.DEFAULT_RESERVATION_TIME,
                ThemeFixture.DEFAULT_THEME,
                MemberFixture.DEFAULT_MEMBER,
                status);
    }

    public static final Reservation ReservationOfDateAndMemberAndStatus(LocalDate date, Member member, ReservationStatus status) {
        return new Reservation(
                date,
                ReservationTimeFixture.DEFAULT_RESERVATION_TIME,
                ThemeFixture.DEFAULT_THEME,
                member,
                status);
    }

    public static final Reservation ReservationOfDateAndStatusWithId(Long id, LocalDate date, ReservationStatus status) {
        return new Reservation(
                id,
                date,
                ReservationTimeFixture.DEFAULT_RESERVATION_TIME,
                ThemeFixture.DEFAULT_THEME,
                MemberFixture.DEFAULT_MEMBER,
                status);
    }
}
