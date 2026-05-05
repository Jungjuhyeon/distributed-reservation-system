package com.jung.reservation.promotion.application.service;

import com.jung.reservation.common.exception.BusinessException;
import com.jung.reservation.common.exception.errorcode.CommonErrorCode;
import com.jung.reservation.promotion.application.outputport.PromotionRoomTypeOutputPort;
import com.jung.reservation.promotion.application.outputport.StockOutputPort;
import com.jung.reservation.promotion.application.outputport.StockResult;
import com.jung.reservation.promotion.domain.model.PromotionRoomType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromotionStockService {

    private final StockOutputPort stockOutputPort;
    private final PromotionRoomTypeOutputPort promotionRoomTypeOutputPort;

    /**
     * Lua Script 실행 (시간검증 → Rate Limit → 멱등성 → Redis 재고차감)
     */
    public void reserveByLuaScript(Long userId, Long promotionRoomTypeId, String orderId) {
        PromotionRoomType promotionRoomType = promotionRoomTypeOutputPort.findById(promotionRoomTypeId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.PROMOTION_ROOM_TYPE_NOT_FOUND));

        StockResult result = stockOutputPort.decreaseStock(
                userId, promotionRoomType.getPromotion().getId(), promotionRoomTypeId, orderId);

        handleStockResult(result);
    }

    /**
     * DB promotion_room_type.stock 차감 (비관적 락)
     */
    public void decreaseDbStock(Long promotionRoomTypeId) {
        PromotionRoomType locked = promotionRoomTypeOutputPort.findWithLockById(promotionRoomTypeId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.PROMOTION_ROOM_TYPE_NOT_FOUND));
        if (!locked.canDecreaseStock()) {
            throw new BusinessException(CommonErrorCode.SOLD_OUT);
        }
        locked.decreaseStock();
    }

    /**
     * 멱등성 키 COMPLETED로 변경
     */
    public void completeIdempotency(String orderId) {
        stockOutputPort.completeIdempotency(orderId);
    }

    /**
     * Redis 재고 복구 + 멱등성 키 삭제 (결제 실패 시)
     */
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
