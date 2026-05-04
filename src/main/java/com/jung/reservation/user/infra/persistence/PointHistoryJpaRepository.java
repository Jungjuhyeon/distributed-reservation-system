package com.jung.reservation.user.infra.persistence;

import com.jung.reservation.user.domain.model.PointHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointHistoryJpaRepository extends JpaRepository<PointHistory, Long> {
}
