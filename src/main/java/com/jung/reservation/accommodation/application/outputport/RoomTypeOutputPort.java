package com.jung.reservation.accommodation.application.outputport;

import com.jung.reservation.accommodation.domain.model.RoomType;

import java.util.Optional;

public interface RoomTypeOutputPort {
    Optional<RoomType> findById(Long id);
}
