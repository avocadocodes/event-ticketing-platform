package com.nikita.ticketing.booking.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nikita.ticketing.booking.dto.BookingResponse;
import com.nikita.ticketing.booking.dto.CreateBookingRequest;
import com.nikita.ticketing.booking.exception.GlobalExceptionHandler;
import com.nikita.ticketing.booking.service.BookingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BookingController.class)
@ActiveProfiles("test")
@Import(GlobalExceptionHandler.class)
class BookingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BookingService bookingService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createReturns201() throws Exception {
        BookingResponse response = new BookingResponse(1L, "EVT-1", "CUST-1", 2, "RESERVED", LocalDateTime.now());
        when(bookingService.createBooking(any())).thenReturn(response);

        CreateBookingRequest req = new CreateBookingRequest("EVT-1", "CUST-1", 2);

        mockMvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L));
    }

    @Test
    void listReturnsBookings() throws Exception {
        BookingResponse response = new BookingResponse(1L, "EVT-1", "CUST-1", 2, "RESERVED", LocalDateTime.now());
        when(bookingService.getAllBookings()).thenReturn(List.of(response));

        mockMvc.perform(get("/bookings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1L));
    }

    @Test
    void getReturnsBooking() throws Exception {
        BookingResponse response = new BookingResponse(1L, "EVT-1", "CUST-1", 2, "RESERVED", LocalDateTime.now());
        when(bookingService.getBooking(1L)).thenReturn(response);

        mockMvc.perform(get("/bookings/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L));
    }
}
