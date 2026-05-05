package com.jung.reservation.booking.framework.web.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Getter
@NoArgsConstructor
public class BookingRequest {

    private String orderId;
    private Long userId;
    private Long roomTypeId;
    private Long promotionRoomTypeId; // nullable (일반 예약이면 null)
    private Long totalAmount;
    private LocalDate checkInDate;
    private LocalDate checkOutDate;
    private List<PaymentMethodRequest> paymentMethods;

    @Getter
    @NoArgsConstructor
    public static class PaymentMethodRequest {
        private String type; // CREDIT_CARD, Y_PAY, Y_POINT
        private Long amount;
    }
}
