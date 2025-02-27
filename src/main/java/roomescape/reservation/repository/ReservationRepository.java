package roomescape.reservation.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import roomescape.reservation.domain.ReservationStatus;
import roomescape.reservation.entity.Reservation;
import roomescape.theme.entity.Theme;
import roomescape.time.entity.ReservationTime;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findByThemeAndDate(Theme theme, LocalDate date);

    List<Reservation> findByThemeIdAndMemberIdAndDateBetween(Long themeId, Long memberId, LocalDate dateFrom, LocalDate dateTo);

    @Query("""
            SELECT r.theme, COUNT(r) AS themeCount 
            FROM Reservation r 
            WHERE r.date BETWEEN :startDate AND :endDate 
            GROUP BY r.theme 
            ORDER BY themeCount DESC 
            LIMIT :limit""")
    List<Theme> findAndOrderByPopularity(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate, @Param("limit") int limit);

    boolean existsByTimeId(long timeId);

    boolean existsByThemeId(long themeId);

    List<Reservation> findAllByMemberId(long memberId);

    void deleteByMemberIdAndId(long memberId, long reservationId);

    @Query("""
            SELECT COUNT(r) FROM Reservation AS r 
            WHERE r.date = :date 
            AND r.time = :time 
            AND r.theme = :theme 
            AND r.createdAt < :createdAt""")
    long findAndCountWaitingNumber(
            @Param("date") LocalDate date,
            @Param("time") ReservationTime time,
            @Param("theme") Theme theme,
            @Param("createdAt") LocalDateTime localDateTime);

    List<Reservation> findAllByStatus(ReservationStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r")
    List<Reservation> findAllWithPessimisticLock();
}
