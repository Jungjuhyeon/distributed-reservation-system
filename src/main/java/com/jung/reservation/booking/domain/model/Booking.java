package com.jung.reservation.booking.domain.model;

import com.jung.reservation.accommodation.domain.model.RoomType;
import com.jung.reservation.booking.domain.model.enumeration.BookingStatus;
import com.jung.reservation.common.entity.BaseEntity;
import com.jung.reservation.promotion.domain.model.PromotionRoomType;
import com.jung.reservation.user.domain.model.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "booking")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Booking extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String orderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_type_id", nullable = false)
    private RoomType roomType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promotion_room_type_id")
    private PromotionRoomType promotionRoomType; // 일반 예약이면 NULL

    @Column(nullable = false)
    private LocalDate checkInDate;

    @Column(nullable = false)
    private LocalDate checkOutDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;

    @Column(nullable = false)
    private Long totalAmount;

    private Booking(String orderId, User user, RoomType roomType, PromotionRoomType promotionRoomType,
                    LocalDate checkInDate, LocalDate checkOutDate, BookingStatus status, Long totalAmount) {
        this.orderId = orderId;
        this.user = user;
        this.roomType = roomType;
        this.promotionRoomType = promotionRoomType;
        this.checkInDate = checkInDate;
        this.checkOutDate = checkOutDate;
        this.status = status;
        this.totalAmount = totalAmount;
    }

    public static Booking create(String orderId, User user, RoomType roomType, PromotionRoomType promotionRoomType,
                                 LocalDate checkInDate, LocalDate checkOutDate, Long totalAmount) {
        return new Booking(orderId, user, roomType, promotionRoomType, checkInDate, checkOutDate,
                BookingStatus.PENDING, totalAmount);
    }

    public void complete() {
        this.status = BookingStatus.COMPLETED;
    }

    public void fail() {
        this.status = BookingStatus.FAILED;
    }

    public void cancel() {
        this.status = BookingStatus.CANCELLED;
    }
}
