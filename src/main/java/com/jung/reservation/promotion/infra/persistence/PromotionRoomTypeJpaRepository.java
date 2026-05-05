package com.jung.reservation.promotion.infra.persistence;

import com.jung.reservation.promotion.domain.model.PromotionRoomType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface PromotionRoomTypeJpaRepository extends JpaRepository<PromotionRoomType, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PromotionRoomType> findWithLockById(Long id);
}
