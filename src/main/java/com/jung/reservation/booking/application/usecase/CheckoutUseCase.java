package com.jung.reservation.booking.application.usecase;

import com.jung.reservation.booking.framework.web.dto.CheckoutResponse;

public interface CheckoutUseCase {
    CheckoutResponse checkout(Long roomTypeId, Long userId, Long promotionRoomTypeId);
}
