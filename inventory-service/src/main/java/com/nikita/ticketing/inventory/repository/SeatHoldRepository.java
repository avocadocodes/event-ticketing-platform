package com.nikita.ticketing.inventory.repository;

import com.nikita.ticketing.inventory.domain.SeatHold;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SeatHoldRepository extends JpaRepository<SeatHold, Long> {

    @Query("SELECT COALESCE(SUM(h.seats), 0) FROM SeatHold h WHERE h.eventId = :eventId AND h.expiresAt > :now")
    int sumActiveHolds(@Param("eventId") String eventId, @Param("now") LocalDateTime now);

    List<SeatHold> findByExpiresAtBefore(LocalDateTime cutoff);
}
