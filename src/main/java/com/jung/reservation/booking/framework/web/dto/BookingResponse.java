package com.jung.reservation.booking.framework.web.dto;

import com.jung.reservation.booking.domain.model.Booking;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BookingResponse {

    private Long bookingId;
    private String orderId;
    private Long totalAmount;

    public static BookingResponse mapToDTO(Booking booking) {
        return BookingResponse.builder()
                .bookingId(booking.getId())
                .orderId(booking.getOrderId())
                .totalAmount(booking.getTotalAmount())
                .build();
    }
}
