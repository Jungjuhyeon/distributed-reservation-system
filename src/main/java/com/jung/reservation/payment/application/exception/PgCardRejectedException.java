package com.jung.reservation.payment.application.exception;

/**
 * PG가 정상 동작하여 카드를 거절한 경우 (한도초과, 잔액부족 등)
 * PG 자체는 살아있으므로 Circuit Breaker 실패 집계에서 제외(ignore)한다.
 */
public class PgCardRejectedException extends PgPaymentException {

    public PgCardRejectedException(String pgErrorCode, String message) {
        super(PgErrorCategory.RETRYABLE, pgErrorCode, message);
    }
}
