package com.nikita.ticketing.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "event_inventory")
@Getter
@Setter
@NoArgsConstructor
public class EventInventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true)
    private String eventId;

    @Column(name = "total_seats", nullable = false)
    private int totalSeats;

    @Column(name = "available_seats", nullable = false)
    private int availableSeats;

    @Version
    private Long version;

    public EventInventory(Long id, String eventId, int totalSeats, int availableSeats, Long version) {
        this.id = id;
        this.eventId = eventId;
        this.totalSeats = totalSeats;
        this.availableSeats = availableSeats;
        this.version = version;
    }
}
