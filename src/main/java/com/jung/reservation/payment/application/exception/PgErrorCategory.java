package com.jung.reservation.payment.application.exception;

public enum PgErrorCategory {
    RETRYABLE,  // 유저 잘못 (한도초과, 잔액부족, 카드 오류) → 재고 복구, 재시도 허용
    TEMPORARY,  // 일시적 오류 (PG사 내부 오류) → 재고 복구, 잠시 후 재시도 안내
    SYSTEM      // 시스템/인증 오류 (API키, 알 수 없는 오류) → 관리자 확인 필요
}
