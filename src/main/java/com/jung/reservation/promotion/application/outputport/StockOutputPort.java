package com.jung.reservation.promotion.application.outputport;

public interface StockOutputPort {
    StockResult decreaseStock(Long userId, Long promotionId, Long promotionRoomTypeId);
}
