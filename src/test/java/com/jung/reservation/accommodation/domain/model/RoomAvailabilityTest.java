package com.jung.reservation.accommodation.domain.model;

import com.jung.reservation.user.domain.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class RoomAvailabilityTest {

    @Test
    @DisplayName("RoomAvailability 생성")
    void create() {
        User host = User.create("호스트", "010-0000-0000");
        Accommodation accommodation = Accommodation.create(host, "제주 호텔", "제주시 중앙로 1");
        RoomType roomType = RoomType.create(accommodation, "디럭스", 200000L, 2, 5,
                LocalTime.of(15, 0), LocalTime.of(11, 0));

        RoomAvailability availability = RoomAvailability.create(roomType, LocalDate.of(2026, 5, 10), 5, 200000L);

        assertThat(availability.getRoomType()).isEqualTo(roomType);
        assertThat(availability.getDate()).isEqualTo(LocalDate.of(2026, 5, 10));
        assertThat(availability.getAvailableCount()).isEqualTo(5);
        assertThat(availability.getAmount()).isEqualTo(200000L);
    }

    @Test
    @DisplayName("객실 수 감소 가능 여부 확인")
    void canDecreaseCount() {
        User host = User.create("호스트", "010-0000-0000");
        Accommodation accommodation = Accommodation.create(host, "제주 호텔", "제주시 중앙로 1");
        RoomType roomType = RoomType.create(accommodation, "디럭스", 200000L, 2, 5,
                LocalTime.of(15, 0), LocalTime.of(11, 0));

        RoomAvailability available = RoomAvailability.create(roomType, LocalDate.of(2026, 5, 10), 1, 200000L);
        RoomAvailability empty = RoomAvailability.create(roomType, LocalDate.of(2026, 5, 10), 0, 200000L);

        assertThat(available.canDecreaseCount()).isTrue();
        assertThat(empty.canDecreaseCount()).isFalse();
    }

    @Test
    @DisplayName("객실 수 감소")
    void decreaseCount() {
        User host = User.create("호스트", "010-0000-0000");
        Accommodation accommodation = Accommodation.create(host, "제주 호텔", "제주시 중앙로 1");
        RoomType roomType = RoomType.create(accommodation, "디럭스", 200000L, 2, 5,
                LocalTime.of(15, 0), LocalTime.of(11, 0));

        RoomAvailability availability = RoomAvailability.create(roomType, LocalDate.of(2026, 5, 10), 3, 200000L);
        availability.decreaseCount();

        assertThat(availability.getAvailableCount()).isEqualTo(2);
    }
}
