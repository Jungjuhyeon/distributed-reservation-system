package com.jung.reservation.promotion.application.outputport;

public interface StockOutputPort {
    StockResult decreaseStock(Long userId, Long promotionId, Long promotionRoomTypeId, String orderId);

    void completeIdempotency(String orderId);

    void releaseIdempotency(String orderId);
}
