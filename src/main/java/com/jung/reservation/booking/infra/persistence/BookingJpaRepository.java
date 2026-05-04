package com.jung.reservation.booking.infra.persistence;

import com.jung.reservation.booking.domain.model.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BookingJpaRepository extends JpaRepository<Booking, Long> {
    Optional<Booking> findByOrderId(String orderId);
}
