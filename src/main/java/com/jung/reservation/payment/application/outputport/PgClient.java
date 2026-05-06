package com.jung.reservation.payment.application.outputport;

public interface PgClient {

    void confirm(String pgTransactionId, String orderId, Long amount);

    void cancel(String pgTransactionId, String cancelReason);

    /**
     * orderId로 PG 결제 상태 조회
     * 타임아웃/시스템 오류로 confirm() 결과가 불분명한 PENDING 예약 복구 시 사용
     */
    PgPaymentStatus queryStatus(String orderId);
}
