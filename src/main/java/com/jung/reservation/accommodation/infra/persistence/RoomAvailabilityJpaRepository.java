package com.jung.reservation.accommodation.infra.persistence;

import com.jung.reservation.accommodation.domain.model.RoomAvailability;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomAvailabilityJpaRepository extends JpaRepository<RoomAvailability, Long> {
}
