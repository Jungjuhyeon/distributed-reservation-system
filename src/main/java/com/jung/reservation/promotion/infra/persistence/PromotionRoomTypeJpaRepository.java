package com.jung.reservation.promotion.infra.persistence;

import com.jung.reservation.promotion.domain.model.PromotionRoomType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PromotionRoomTypeJpaRepository extends JpaRepository<PromotionRoomType, Long> {
}
