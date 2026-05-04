package com.jung.reservation.accommodation.domain.model;

import com.jung.reservation.user.domain.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class RoomTypeTest {

    @Test
    @DisplayName("RoomType 생성")
    void create() {
        User host = User.create("호스트", "010-0000-0000");
        Accommodation accommodation = Accommodation.create(host, "제주 호텔", "제주시 중앙로 1");
        RoomType roomType = RoomType.create(accommodation, "디럭스 더블", 200000L, 2, 5,
                LocalTime.of(15, 0), LocalTime.of(11, 0));

        assertThat(roomType.getAccommodation()).isEqualTo(accommodation);
        assertThat(roomType.getName()).isEqualTo("디럭스 더블");
        assertThat(roomType.getAmount()).isEqualTo(200000L);
        assertThat(roomType.getCapacity()).isEqualTo(2);
        assertThat(roomType.getRoomCount()).isEqualTo(5);
        assertThat(roomType.getCheckInTime()).isEqualTo(LocalTime.of(15, 0));
        assertThat(roomType.getCheckOutTime()).isEqualTo(LocalTime.of(11, 0));
    }
}
