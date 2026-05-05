package com.jung.reservation.booking.application.usecase;

import com.jung.reservation.booking.framework.web.dto.BookingRequest;
import com.jung.reservation.booking.framework.web.dto.BookingResponse;

public interface BookingUseCase {
    BookingResponse book(BookingRequest request);
}
