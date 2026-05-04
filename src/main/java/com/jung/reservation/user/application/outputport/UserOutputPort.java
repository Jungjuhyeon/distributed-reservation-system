package com.jung.reservation.user.application.outputport;

import com.jung.reservation.user.domain.model.User;

import java.util.Optional;

public interface UserOutputPort {
    Optional<User> findById(Long id);
}
