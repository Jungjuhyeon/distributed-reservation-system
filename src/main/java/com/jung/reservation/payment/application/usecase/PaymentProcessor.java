package com.jung.reservation.payment.application.usecase;

import com.jung.reservation.payment.domain.model.enumeration.PaymentType;

public interface PaymentProcessor {

    PaymentType getType();

    void pay(Long userId, Long amount, String orderId, String pgTransactionId);

    void cancel(Long userId, Long amount, String orderId, String pgTransactionId);
}
