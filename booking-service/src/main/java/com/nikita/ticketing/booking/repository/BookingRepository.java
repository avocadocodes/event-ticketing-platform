package com.nikita.ticketing.booking.repository;

import com.nikita.ticketing.booking.domain.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRepository extends JpaRepository<Booking, Long> {
}
