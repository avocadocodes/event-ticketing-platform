package com.nikita.ticketing.booking.repository;

import com.nikita.ticketing.booking.domain.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, Long> {

    boolean existsByEventKey(String eventKey);
}
