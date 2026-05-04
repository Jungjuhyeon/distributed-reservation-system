package com.jung.reservation.accommodation.infra.persistence;

import com.jung.reservation.accommodation.domain.model.RoomType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomTypeJpaRepository extends JpaRepository<RoomType, Long> {
}
