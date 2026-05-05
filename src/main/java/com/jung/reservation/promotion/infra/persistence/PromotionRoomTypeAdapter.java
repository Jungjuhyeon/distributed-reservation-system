package com.jung.reservation.promotion.infra.persistence;

import com.jung.reservation.promotion.application.outputport.PromotionRoomTypeOutputPort;
import com.jung.reservation.promotion.domain.model.PromotionRoomType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PromotionRoomTypeAdapter implements PromotionRoomTypeOutputPort {

    private final PromotionRoomTypeJpaRepository promotionRoomTypeJpaRepository;

    @Override
    public Optional<PromotionRoomType> findById(Long id) {
        return promotionRoomTypeJpaRepository.findById(id);
    }

    @Override
    public Optional<PromotionRoomType> findWithLockById(Long id) {
        return promotionRoomTypeJpaRepository.findWithLockById(id);
    }
}
