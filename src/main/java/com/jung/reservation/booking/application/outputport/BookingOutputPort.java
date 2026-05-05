package com.jung.reservation.booking.application.outputport;

import com.jung.reservation.booking.domain.model.Booking;

import java.util.Optional;

public interface BookingOutputPort {
    Booking save(Booking booking);
    Optional<Booking> findByOrderId(String orderId);
}
