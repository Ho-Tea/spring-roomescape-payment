package roomescape.time.domain;

import java.util.Collections;
import java.util.List;

import roomescape.time.entity.ReservationTime;

public class ReservationTimes {
    private final List<ReservationTime> reservationTimes;

    public ReservationTimes(List<ReservationTime> reservationTimes) {
        this.reservationTimes = reservationTimes;
    }

    public List<ReservationTime> getReservationTimes() {
        return Collections.unmodifiableList(reservationTimes);
    }
}
