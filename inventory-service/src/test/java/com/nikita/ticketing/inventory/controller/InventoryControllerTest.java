package com.nikita.ticketing.inventory.controller;

import com.nikita.ticketing.inventory.dto.EventAvailabilityResponse;
import com.nikita.ticketing.inventory.exception.GlobalExceptionHandler;
import com.nikita.ticketing.inventory.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InventoryController.class)
@ActiveProfiles("test")
@Import(GlobalExceptionHandler.class)
class InventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InventoryService inventoryService;

    @Test
    void availabilityReturnsData() throws Exception {
        when(inventoryService.getEventAvailability("EVT-001"))
                .thenReturn(new EventAvailabilityResponse("EVT-001", 100, 90));

        mockMvc.perform(get("/inventory/EVT-001/availability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value("EVT-001"))
                .andExpect(jsonPath("$.totalSeats").value(100))
                .andExpect(jsonPath("$.availableSeats").value(90));
    }
}
