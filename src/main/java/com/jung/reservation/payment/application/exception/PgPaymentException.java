package com.jung.reservation.payment.application.exception;

import lombok.Getter;

@Getter
public class PgPaymentException extends RuntimeException {

    private final PgErrorCategory category;
    private final String pgErrorCode;

    public PgPaymentException(PgErrorCategory category, String pgErrorCode, String message) {
        super(message);
        this.category = category;
        this.pgErrorCode = pgErrorCode;
    }
}
