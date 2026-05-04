package com.jung.reservation.user.infra.persistence;

import com.jung.reservation.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserJpaRepository extends JpaRepository<User, Long> {
}
