package com.jung.reservation.booking.application.outputport;

public interface CheckoutCacheOutputPort {
    void saveCheckoutCache(String orderId, Long totalAmount);

    Long getCheckoutAmount(String orderId);
}
