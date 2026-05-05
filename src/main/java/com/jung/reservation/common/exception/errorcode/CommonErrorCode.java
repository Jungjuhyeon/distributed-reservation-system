package com.jung.reservation.common.exception.errorcode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
@Getter
@RequiredArgsConstructor
public enum CommonErrorCode implements ErrorCode{
    INVALID_PARAMETER(400, "4000", "잘못된 요청 파라미터입니다."),
    RESOURCE_NOT_FOUND(404, "4040", "요청한 리소스를 찾을 수 없습니다."),
    INTERNAL_SERVER_ERROR(500, "5000", "알 수 없는 에러가 발생했습니다."),

    ROOM_TYPE_NOT_FOUND(404, "4041", "해당 객실 타입을 찾을 수 없습니다."),
    PROMOTION_ROOM_TYPE_NOT_FOUND(404, "4042", "해당 프로모션 상품을 찾을 수 없습니다."),
    USER_NOT_FOUND(404, "4043", "해당 사용자를 찾을 수 없습니다."),
    USER_POINT_NOT_FOUND(404, "4044", "해당 사용자의 포인트 정보를 찾을 수 없습니다."),

    PROMOTION_NOT_STARTED(403, "4030", "프로모션이 아직 시작되지 않았습니다."),
    RATE_LIMITED(429, "4290", "요청이 너무 많습니다. 잠시 후 다시 시도해주세요."),
    ALREADY_PROCESSED(409, "4090", "이미 처리된 주문입니다."),
    SOLD_OUT(409, "4091", "재고가 소진되었습니다."),
    INVALID_PAYMENT_COMBINATION(400, "4001", "주결제 수단은 1개만 선택 가능합니다."),
    ROOM_NOT_AVAILABLE(409, "4092", "해당 날짜에 예약 가능한 객실이 없습니다."),
    DUPLICATE_REQUEST(409, "4093", "이미 처리 중인 요청입니다."),
    SERVICE_UNAVAILABLE(503, "5030", "서비스가 일시적으로 불가합니다. 잠시 후 다시 시도해주세요."),
    UNSUPPORTED_PAYMENT_TYPE(400, "4004", "지원하지 않는 결제 수단입니다."),
    INSUFFICIENT_POINT(400, "4003", "포인트가 부족합니다."),
    PAYMENT_REJECTED(400, "4002", "결제가 거절되었습니다. 카드 정보를 확인해주세요."),
    PAYMENT_TEMPORARY_ERROR(503, "5031", "결제 처리 중 일시적 오류가 발생했습니다. 잠시 후 다시 시도해주세요."),
    PAYMENT_SYSTEM_ERROR(500, "5001", "결제 처리 중 시스템 오류가 발생했습니다. 관리자에게 문의해주세요.");

    private final Integer httpStatus;
    private final String code;
    private final String message;
}
