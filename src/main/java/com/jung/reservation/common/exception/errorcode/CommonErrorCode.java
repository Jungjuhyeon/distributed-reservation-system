package com.jung.reservation.common.exception.errorcode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
@Getter
@RequiredArgsConstructor
public enum CommonErrorCode implements ErrorCode{
    INVALID_PARAMETER(400, "4000", "잘못된 요청 파라미터입니다."),
    RESOURCE_NOT_FOUND(404, "4040", "요청한 리소스를 찾을 수 없습니다."),
    INTERNAL_SERVER_ERROR(500, "5000", "알 수 없는 에러가 발생했습니다.");

    private final Integer httpStatus;
    private final String code;
    private final String message;
}
