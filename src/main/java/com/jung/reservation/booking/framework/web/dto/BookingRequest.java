package com.jung.reservation.booking.framework.web.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class BookingRequest {

    private String orderId;
    private Long userId;
    private Long roomTypeId;
    private Long promotionRoomTypeId; // nullable (일반 예약이면 null)
    private Long totalAmount;
    private LocalDate checkInDate;
    private LocalDate checkOutDate;
    private String paymentKey; // PG 결제 시에만 (포인트 전액이면 null)
    private List<PaymentMethodRequest> paymentMethods;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentMethodRequest {
        private String type; // CREDIT_CARD, Y_PAY, Y_POINT
        private Long amount;
    }
}
