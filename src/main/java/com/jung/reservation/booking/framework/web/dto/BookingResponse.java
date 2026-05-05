package com.jung.reservation.booking.framework.web.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BookingResponse {

    private Long bookingId;
    private String orderId;
    private Long totalAmount;
}
