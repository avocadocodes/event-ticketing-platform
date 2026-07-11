package com.nikita.ticketing.inventory.controller;

import com.nikita.ticketing.inventory.dto.EventAvailabilityResponse;
import com.nikita.ticketing.inventory.service.InventoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/{eventId}/availability")
    public EventAvailabilityResponse availability(@PathVariable String eventId) {
        return inventoryService.getEventAvailability(eventId);
    }
}
