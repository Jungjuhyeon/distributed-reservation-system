package com.jung.reservation.accommodation.domain.model;

import com.jung.reservation.user.domain.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AccommodationTest {

    @Test
    @DisplayName("Accommodation 생성 - 설명 없이")
    void create_withoutDescription() {
        User host = User.create("호스트", "010-0000-0000");
        Accommodation accommodation = Accommodation.create(host, "제주 호텔", "제주시 중앙로 1");

        assertThat(accommodation.getHost()).isEqualTo(host);
        assertThat(accommodation.getName()).isEqualTo("제주 호텔");
        assertThat(accommodation.getAddress()).isEqualTo("제주시 중앙로 1");
        assertThat(accommodation.getDescription()).isNull();
    }

    @Test
    @DisplayName("Accommodation 생성 - 설명 포함")
    void create_withDescription() {
        User host = User.create("호스트", "010-0000-0000");
        Accommodation accommodation = Accommodation.create(host, "제주 호텔", "제주시 중앙로 1", "오션뷰 호텔");

        assertThat(accommodation.getDescription()).isEqualTo("오션뷰 호텔");
    }
}
