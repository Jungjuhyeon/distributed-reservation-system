package com.jung.reservation.promotion.application.outputport;

public enum StockResult {
    SUCCESS,           // 재고 차감 성공
    RATE_LIMITED,      // 요청 횟수 초과 (1초 내 3회 초과)
    NOT_STARTED,       // 프로모션 오픈 시간 전
    ALREADY_PROCESSED, // 이미 처리된 주문 (멱등성 차단)
    SOLD_OUT           // 재고 소진
}
