package com.jung.reservation.payment.application.outputport;

public interface PgClient {

    void confirm(String paymentKey, String orderId, Long amount);

    void cancel(String paymentKey, String cancelReason);
}
