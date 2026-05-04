package com.jung.reservation.user.domain.model;

import com.jung.reservation.booking.domain.model.Booking;
import com.jung.reservation.common.entity.BaseEntity;
import com.jung.reservation.user.domain.model.enumeration.PointHistoryType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "point_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_point_id", nullable = false)
    private UserPoint userPoint;

    @Column(nullable = false)
    private Long amount; // 변동 포인트

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id")
    private Booking booking; // 예약 관련 포인트 변동 시 연결 (nullable)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PointHistoryType type;

    private String description;

    private PointHistory(UserPoint userPoint, Long amount, Booking booking, PointHistoryType type, String description) {
        this.userPoint = userPoint;
        this.amount = amount;
        this.booking = booking;
        this.type = type;
        this.description = description;
    }

    public static PointHistory create(UserPoint userPoint, Long amount, Booking booking, PointHistoryType type, String description) {
        return new PointHistory(userPoint, amount, booking, type, description);
    }
}
