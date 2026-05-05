package com.jung.reservation.payment.application.outputport;

public interface PgClient {

    void confirm(String pgTransactionId, String orderId, Long amount);

    void cancel(String pgTransactionId, String cancelReason);
}
