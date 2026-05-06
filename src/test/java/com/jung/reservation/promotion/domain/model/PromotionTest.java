package com.jung.reservation.promotion.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class PromotionTest {

    @Test
    @DisplayName("Promotion 생성")
    void create() {
        Promotion promotion = Promotion.create("5월 초특가",
                LocalDateTime.of(2026, 5, 1, 0, 0),
                LocalDateTime.of(2026, 5, 31, 23, 59),
                LocalTime.of(0, 0), LocalTime.of(1, 0));

        assertThat(promotion.getName()).isEqualTo("5월 초특가");
        assertThat(promotion.getStartDateTime()).isEqualTo(LocalDateTime.of(2026, 5, 1, 0, 0));
        assertThat(promotion.getEndDateTime()).isEqualTo(LocalDateTime.of(2026, 5, 31, 23, 59));
        assertThat(promotion.getDailyStartTime()).isEqualTo(LocalTime.of(0, 0));
        assertThat(promotion.getDailyEndTime()).isEqualTo(LocalTime.of(1, 0));
    }
}
