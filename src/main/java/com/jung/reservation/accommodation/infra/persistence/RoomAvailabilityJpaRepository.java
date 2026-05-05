package com.jung.reservation.accommodation.infra.persistence;

import com.jung.reservation.accommodation.domain.model.RoomAvailability;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.LocalDate;
import java.util.List;

public interface RoomAvailabilityJpaRepository extends JpaRepository<RoomAvailability, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<RoomAvailability> findByRoomTypeIdAndDateBetween(Long roomTypeId, LocalDate startDate, LocalDate endDate);
}
