package com.jung.reservation.booking.domain;

public enum BookingStatus {
    PENDING,    // 결제 진행 중
    COMPLETED,  // 결제 완료
    FAILED,     // 결제 실패
    CANCELLED   // 예약 취소
}
