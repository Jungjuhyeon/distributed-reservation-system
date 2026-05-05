package com.jung.reservation.accommodation.application.outputport;

import com.jung.reservation.accommodation.domain.model.RoomAvailability;

import java.time.LocalDate;
import java.util.List;

public interface RoomAvailabilityOutputPort {
    List<RoomAvailability> findByRoomTypeIdAndDateRange(Long roomTypeId, LocalDate startDate, LocalDate endDate);
}
