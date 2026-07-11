package com.nikita.ticketing.inventory.repository;

import com.nikita.ticketing.inventory.domain.ProcessedBooking;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedBookingRepository extends JpaRepository<ProcessedBooking, Long> {

    boolean existsByBookingId(Long bookingId);
}
