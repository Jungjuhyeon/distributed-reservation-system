package com.jung.reservation.user.application.outputport;

import com.jung.reservation.user.domain.model.UserPoint;

import java.util.Optional;

public interface UserPointOutputPort {
    Optional<UserPoint> findByUserId(Long userId);
}
