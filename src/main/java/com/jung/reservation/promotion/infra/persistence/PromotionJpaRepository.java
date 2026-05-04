package com.jung.reservation.promotion.infra.persistence;

import com.jung.reservation.promotion.domain.Promotion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PromotionJpaRepository extends JpaRepository<Promotion, Long> {
}
