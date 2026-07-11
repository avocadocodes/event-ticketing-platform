package com.nikita.ticketing.inventory.repository;

import com.nikita.ticketing.inventory.domain.EventInventory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EventInventoryRepository extends JpaRepository<EventInventory, Long> {

    Optional<EventInventory> findByEventId(String eventId);
}
