package com.jung.reservation.promotion.infra.persistence;

import com.jung.reservation.promotion.domain.model.PromotionRoomType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PromotionRoomTypeJpaRepository extends JpaRepository<PromotionRoomType, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PromotionRoomType> findWithLockById(Long id);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE PromotionRoomType p SET p.stock = p.stock - 1 WHERE p.id = :id AND p.stock > 0")
    int decreaseStockById(@Param("id") Long id);

    @Query("SELECT prt FROM PromotionRoomType prt JOIN FETCH prt.promotion p WHERE p.startDateTime <= :now AND p.endDateTime >= :now")
    List<PromotionRoomType> findActivePromotionRoomTypes(@Param("now") LocalDateTime now);
}
