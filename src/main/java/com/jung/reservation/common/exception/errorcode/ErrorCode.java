package com.jung.reservation.common.exception.errorcode;

public interface ErrorCode {
    Integer getHttpStatus();
    String getCode();
    String getMessage();
}
