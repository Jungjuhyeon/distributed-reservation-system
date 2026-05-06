package com.jung.reservation.booking.domain.model;

import com.jung.reservation.accommodation.domain.model.Accommodation;
import com.jung.reservation.accommodation.domain.model.RoomType;
import com.jung.reservation.booking.domain.model.enumeration.BookingStatus;
import com.jung.reservation.user.domain.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class BookingTest {

    @Test
    @DisplayName("Booking 생성 시 PENDING 상태")
    void create_pendingStatus() {
        User host = User.create("호스트", "010-0000-0000");
        Accommodation accommodation = Accommodation.create(host, "제주 호텔", "제주시 중앙로 1");
        RoomType roomType = RoomType.create(accommodation, "디럭스", 200000L, 2, 5,
                LocalTime.of(15, 0), LocalTime.of(11, 0));
        User user = User.create("정주현", "010-1234-5678");

        Booking booking = Booking.create("ORD-TEST-001", user, roomType, null,
                LocalDate.of(2026, 5, 10), LocalDate.of(2026, 5, 12), 200000L);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(booking.getOrderId()).isEqualTo("ORD-TEST-001");
        assertThat(booking.getTotalAmount()).isEqualTo(200000L);
    }

    @Test
    @DisplayName("Booking 완료 시 COMPLETED 상태")
    void complete() {
        User host = User.create("호스트", "010-0000-0000");
        Accommodation accommodation = Accommodation.create(host, "제주 호텔", "제주시 중앙로 1");
        RoomType roomType = RoomType.create(accommodation, "디럭스", 200000L, 2, 5,
                LocalTime.of(15, 0), LocalTime.of(11, 0));
        User user = User.create("정주현", "010-1234-5678");

        Booking booking = Booking.create("ORD-TEST-002", user, roomType, null,
                LocalDate.of(2026, 5, 10), LocalDate.of(2026, 5, 12), 200000L);
        booking.complete();

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.COMPLETED);
    }

    @Test
    @DisplayName("Booking 취소 시 CANCELLED 상태")
    void cancel() {
        User host = User.create("호스트", "010-0000-0000");
        Accommodation accommodation = Accommodation.create(host, "제주 호텔", "제주시 중앙로 1");
        RoomType roomType = RoomType.create(accommodation, "디럭스", 200000L, 2, 5,
                LocalTime.of(15, 0), LocalTime.of(11, 0));
        User user = User.create("정주현", "010-1234-5678");

        Booking booking = Booking.create("ORD-TEST-003", user, roomType, null,
                LocalDate.of(2026, 5, 10), LocalDate.of(2026, 5, 12), 200000L);
        booking.cancel();

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }
}
