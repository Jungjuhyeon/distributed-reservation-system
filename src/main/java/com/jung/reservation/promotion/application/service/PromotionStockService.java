package com.jung.reservation.promotion.application.service;

import com.jung.reservation.common.exception.BusinessException;
import com.jung.reservation.common.exception.errorcode.CommonErrorCode;
import com.jung.reservation.promotion.application.outputport.PromotionRoomTypeOutputPort;
import com.jung.reservation.promotion.application.outputport.StockOutputPort;
import com.jung.reservation.promotion.application.outputport.StockResult;
import com.jung.reservation.promotion.domain.model.PromotionRoomType;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromotionStockService {

    private final StockOutputPort stockOutputPort;
    private final PromotionRoomTypeOutputPort promotionRoomTypeOutputPort;
    private final PromotionStockDbFallbackService dbFallbackService;

    /**
     * @return true = Redis 경로 (DB stock 추가 차감 필요), false = DB Fallback (이미 차감됨)
     */
    public boolean reserve(Long userId, Long promotionRoomTypeId, String orderId) {
        try {
            reserveByLuaScript(userId, promotionRoomTypeId, orderId);
            return true;
        } catch (CallNotPermittedException e) {
            log.warn("[Redis 장애] DB Fallback 전환 - orderId: {}", orderId);
            dbFallbackService.reserveByDbFallback(promotionRoomTypeId);
            return false;
        }
    }

    private void reserveByLuaScript(Long userId, Long promotionRoomTypeId, String orderId) {
        PromotionRoomType promotionRoomType = promotionRoomTypeOutputPort.findById(promotionRoomTypeId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.PROMOTION_ROOM_TYPE_NOT_FOUND));

        StockResult result = stockOutputPort.decreaseStock(
                userId, promotionRoomType.getPromotion().getId(), promotionRoomTypeId, orderId);

        handleStockResult(result);
    }

    public void decreaseDbStock(Long promotionRoomTypeId) {
        PromotionRoomType locked = promotionRoomTypeOutputPort.findWithLockById(promotionRoomTypeId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.PROMOTION_ROOM_TYPE_NOT_FOUND));
        if (!locked.canDecreaseStock()) {
            throw new BusinessException(CommonErrorCode.SOLD_OUT);
        }
        locked.decreaseStock();
    }

    public void completeIdempotency(String orderId) {
        try {
            stockOutputPort.completeIdempotency(orderId);
        } catch (CallNotPermittedException e) {
            log.warn("[Redis 장애] 멱등성 complete 스킵 - orderId: {}", orderId);
        }
    }

    public void rollbackRedisResources(Long promotionRoomTypeId, String orderId) {
        try {
            stockOutputPort.restoreStock(promotionRoomTypeId);
            stockOutputPort.releaseIdempotency(orderId);
        } catch (Exception e) {
            log.error("[Redis 롤백 실패] 재고 누수 주의 - orderId: {}", orderId, e);
        }
    }

    private void handleStockResult(StockResult result) {
        switch (result) {
            case SUCCESS -> {}
            case NOT_STARTED -> throw new BusinessException(CommonErrorCode.PROMOTION_NOT_STARTED);
            case RATE_LIMITED -> throw new BusinessException(CommonErrorCode.RATE_LIMITED);
            case ALREADY_PROCESSED -> throw new BusinessException(CommonErrorCode.ALREADY_PROCESSED);
            case SOLD_OUT -> throw new BusinessException(CommonErrorCode.SOLD_OUT);
        }
    }
}
