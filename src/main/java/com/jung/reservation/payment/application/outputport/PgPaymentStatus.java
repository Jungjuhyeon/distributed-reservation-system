package com.jung.reservation.payment.application.outputport;

/**
 * PG사 결제 상태 (PG 조회 응답값)
 * 우리 도메인의 PaymentStatus와 분리하여 PG 계층에서만 사용한다.
 */
public enum PgPaymentStatus {
    DONE,           // 결제 완료
    ABORTED,        // 결제 승인 실패 (한도초과, 잔액부족 등)
    IN_PROGRESS,    // 결제 처리 중 (아직 확정 안 됨)
    CANCELED,       // 결제 취소 (전액)
    PARTIAL_CANCELED // 부분 취소
}
