package com.jung.reservation.accommodation.infra.persistence;

import com.jung.reservation.accommodation.application.outputport.RoomAvailabilityOutputPort;
import com.jung.reservation.accommodation.domain.model.RoomAvailability;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class RoomAvailabilityAdapter implements RoomAvailabilityOutputPort {

    private final RoomAvailabilityJpaRepository roomAvailabilityJpaRepository;

    @Override
    public List<RoomAvailability> findByRoomTypeIdAndDateRange(Long roomTypeId, LocalDate startDate, LocalDate endDate) {
        return roomAvailabilityJpaRepository.findByRoomTypeIdAndDateBetween(roomTypeId, startDate, endDate);
    }
}
