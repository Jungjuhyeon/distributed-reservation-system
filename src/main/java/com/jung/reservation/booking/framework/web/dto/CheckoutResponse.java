package com.jung.reservation.booking.framework.web.dto;

import com.jung.reservation.accommodation.domain.model.RoomType;
import com.jung.reservation.user.domain.model.User;
import com.jung.reservation.user.domain.model.UserPoint;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalTime;

@Getter
@Builder
public class CheckoutResponse {

    private String orderId;

    // 상품 정보
    private Long accommodationId;
    private String accommodationName;
    private Long roomTypeId;
    private String roomTypeName;
    private Long promotionRoomTypeId;
    private Long originalAmount;
    private Long promotionAmount;
    private Long totalAmount;
    private LocalTime checkInTime;
    private LocalTime checkOutTime;

    // 사용자 정보
    private String userName;
    private String phone;
    private Long availablePoint;

    public static CheckoutResponse mapToDTO(String orderId, RoomType roomType,
                                            Long promotionRoomTypeId, Long promotionAmount,
                                            Long totalAmount, User user, UserPoint userPoint) {
        return CheckoutResponse.builder()
                .orderId(orderId)
                .accommodationId(roomType.getAccommodation().getId())
                .accommodationName(roomType.getAccommodation().getName())
                .roomTypeId(roomType.getId())
                .roomTypeName(roomType.getName())
                .promotionRoomTypeId(promotionRoomTypeId)
                .originalAmount(roomType.getAmount())
                .promotionAmount(promotionAmount)
                .totalAmount(totalAmount)
                .checkInTime(roomType.getCheckInTime())
                .checkOutTime(roomType.getCheckOutTime())
                .userName(user.getName())
                .phone(user.getPhone())
                .availablePoint(userPoint.getCurrentPoint())
                .build();
    }
}
