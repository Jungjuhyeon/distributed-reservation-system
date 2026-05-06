package com.jung.reservation.booking.infra.persistence;

import com.jung.reservation.booking.domain.model.Booking;
import com.jung.reservation.booking.domain.model.enumeration.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BookingJpaRepository extends JpaRepository<Booking, Long> {
    Optional<Booking> findByOrderId(String orderId);
    List<Booking> findByStatusAndCreatedAtBefore(BookingStatus status, LocalDateTime threshold);
}
