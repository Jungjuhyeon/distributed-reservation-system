package com.jung.reservation.user.domain.model;

import com.jung.reservation.accommodation.domain.model.Accommodation;
import com.jung.reservation.accommodation.domain.model.RoomType;
import com.jung.reservation.booking.domain.model.Booking;
import com.jung.reservation.user.domain.model.enumeration.PointHistoryType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class PointHistoryTest {

    @Test
    @DisplayName("PointHistory 생성 - 포인트 사용")
    void create_use() {
        User user = User.create("정주현", "010-1234-5678");
        UserPoint userPoint = UserPoint.create(user, 100000L);

        User host = User.create("호스트", "010-0000-0000");
        Accommodation accommodation = Accommodation.create(host, "제주 호텔", "제주시 중앙로 1");
        RoomType roomType = RoomType.create(accommodation, "디럭스", 200000L, 2, 5,
                LocalTime.of(15, 0), LocalTime.of(11, 0));
        Booking booking = Booking.create("ORD-TEST", user, roomType, null,
                LocalDate.of(2026, 5, 10), LocalDate.of(2026, 5, 12), 99000L);

        PointHistory history = PointHistory.create(userPoint, 20000L, booking, PointHistoryType.USE, "프로모션 예약 포인트 사용");

        assertThat(history.getUserPoint()).isEqualTo(userPoint);
        assertThat(history.getAmount()).isEqualTo(20000L);
        assertThat(history.getBooking()).isEqualTo(booking);
        assertThat(history.getType()).isEqualTo(PointHistoryType.USE);
        assertThat(history.getDescription()).isEqualTo("프로모션 예약 포인트 사용");
    }

    @Test
    @DisplayName("PointHistory 생성 - 포인트 환불")
    void create_refund() {
        User user = User.create("정주현", "010-1234-5678");
        UserPoint userPoint = UserPoint.create(user, 100000L);

        PointHistory history = PointHistory.create(userPoint, 20000L, null, PointHistoryType.REFUND, "예약 취소 포인트 환불");

        assertThat(history.getType()).isEqualTo(PointHistoryType.REFUND);
        assertThat(history.getBooking()).isNull();
    }
}
