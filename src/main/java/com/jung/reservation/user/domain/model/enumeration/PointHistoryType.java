package com.jung.reservation.user.domain.model.enumeration;

public enum PointHistoryType {
    EARN,   // 포인트 적립
    USE,    // 포인트 사용 (결제 시 차감)
    REFUND  // 포인트 환불 (결제 실패/취소 시 복구)
}
