package com.jung.reservation.promotion.application.service;

import com.jung.reservation.common.exception.BusinessException;
import com.jung.reservation.common.exception.errorcode.CommonErrorCode;
import com.jung.reservation.promotion.application.outputport.PromotionRoomTypeOutputPort;
import com.jung.reservation.promotion.domain.model.Promotion;
import com.jung.reservation.promotion.domain.model.PromotionRoomType;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromotionStockDbFallbackService {

    private final PromotionRoomTypeOutputPort promotionRoomTypeOutputPort;

    @Bulkhead(name = "promotionDbFallback", fallbackMethod = "bulkheadFallback")
    public void reserveByDbFallback(Long promotionRoomTypeId) {
        PromotionRoomType locked = promotionRoomTypeOutputPort.findWithLockById(promotionRoomTypeId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.PROMOTION_ROOM_TYPE_NOT_FOUND));

        validatePromotionTime(locked.getPromotion());

        if (!locked.canDecreaseStock()) {
            throw new BusinessException(CommonErrorCode.SOLD_OUT);
        }
        locked.decreaseStock();

        log.info("[DB Fallback] 재고 선점 성공 - promotionRoomTypeId: {}", promotionRoomTypeId);
    }

    private void bulkheadFallback(Long promotionRoomTypeId, BulkheadFullException e) {
        log.warn("[Bulkhead Full] DB Fallback 진입 거부 - promotionRoomTypeId: {}", promotionRoomTypeId);
        throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
    }

    private void validatePromotionTime(Promotion promotion) {
        LocalDateTime now = LocalDateTime.now();
        LocalTime nowTime = now.toLocalTime();

        if (now.isBefore(promotion.getStartDateTime()) || now.isAfter(promotion.getEndDateTime())) {
            throw new BusinessException(CommonErrorCode.PROMOTION_NOT_STARTED);
        }
        if (nowTime.isBefore(promotion.getDailyStartTime()) || nowTime.isAfter(promotion.getDailyEndTime())) {
            throw new BusinessException(CommonErrorCode.PROMOTION_NOT_STARTED);
        }
    }
}
