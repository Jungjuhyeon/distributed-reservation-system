package com.jung.reservation.booking.infra.persistence;

import com.jung.reservation.booking.application.outputport.BookingOutputPort;
import com.jung.reservation.booking.domain.model.Booking;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class BookingAdapter implements BookingOutputPort {

    private final BookingJpaRepository bookingJpaRepository;

    @Override
    public Booking save(Booking booking) {
        return bookingJpaRepository.save(booking);
    }

    @Override
    public Optional<Booking> findByOrderId(String orderId) {
        return bookingJpaRepository.findByOrderId(orderId);
    }
}
