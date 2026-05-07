package com.jung.reservation.promotion.application.outputport;

import com.jung.reservation.promotion.domain.model.PromotionRoomType;

import java.util.Optional;

public interface PromotionRoomTypeOutputPort {
    Optional<PromotionRoomType> findById(Long id);

    Optional<PromotionRoomType> findWithLockById(Long id);

    int decreaseStockById(Long id);
}
