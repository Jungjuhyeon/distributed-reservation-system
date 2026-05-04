package com.jung.reservation.accommodation.infra.persistence;

import com.jung.reservation.accommodation.application.outputport.RoomTypeOutputPort;
import com.jung.reservation.accommodation.domain.model.RoomType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RoomTypeAdapter implements RoomTypeOutputPort {

    private final RoomTypeJpaRepository roomTypeJpaRepository;

    @Override
    public Optional<RoomType> findById(Long id) {
        return roomTypeJpaRepository.findById(id);
    }
}
