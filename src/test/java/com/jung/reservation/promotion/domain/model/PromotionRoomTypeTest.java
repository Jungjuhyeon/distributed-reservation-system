package com.jung.reservation.promotion.domain.model;

import com.jung.reservation.accommodation.domain.model.Accommodation;
import com.jung.reservation.accommodation.domain.model.RoomType;
import com.jung.reservation.user.domain.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class PromotionRoomTypeTest {

    @Test
    @DisplayName("PromotionRoomType 생성")
    void create() {
        User host = User.create("호스트", "010-0000-0000");
        Accommodation accommodation = Accommodation.create(host, "제주 호텔", "제주시 중앙로 1");
        RoomType roomType = RoomType.create(accommodation, "디럭스 더블", 200000L, 2, 5,
                LocalTime.of(15, 0), LocalTime.of(11, 0));
        Promotion promotion = Promotion.create("5월 초특가",
                LocalDateTime.of(2026, 5, 5, 0, 0),
                LocalDateTime.of(2026, 5, 31, 23, 59),
                LocalTime.of(0, 0), LocalTime.of(1, 0));

        PromotionRoomType promotionRoomType = PromotionRoomType.create(promotion, roomType, 99000L, 10);

        assertThat(promotionRoomType.getPromotion()).isEqualTo(promotion);
        assertThat(promotionRoomType.getRoomType()).isEqualTo(roomType);
        assertThat(promotionRoomType.getPromotionAmount()).isEqualTo(99000L);
        assertThat(promotionRoomType.getStock()).isEqualTo(10);
    }

    @Test
    @DisplayName("재고 감소 가능 여부 확인")
    void canDecreaseStock() {
        User host = User.create("호스트", "010-0000-0000");
        Accommodation accommodation = Accommodation.create(host, "제주 호텔", "제주시 중앙로 1");
        RoomType roomType = RoomType.create(accommodation, "디럭스", 200000L, 2, 5,
                LocalTime.of(15, 0), LocalTime.of(11, 0));
        Promotion promotion = Promotion.create("초특가",
                LocalDateTime.of(2026, 5, 1, 0, 0),
                LocalDateTime.of(2026, 5, 31, 23, 59),
                LocalTime.of(0, 0), LocalTime.of(1, 0));

        PromotionRoomType withStock = PromotionRoomType.create(promotion, roomType, 99000L, 1);
        PromotionRoomType empty = PromotionRoomType.create(promotion, roomType, 99000L, 0);

        assertThat(withStock.canDecreaseStock()).isTrue();
        assertThat(empty.canDecreaseStock()).isFalse();
    }

    @Test
    @DisplayName("재고 감소")
    void decreaseStock() {
        User host = User.create("호스트", "010-0000-0000");
        Accommodation accommodation = Accommodation.create(host, "제주 호텔", "제주시 중앙로 1");
        RoomType roomType = RoomType.create(accommodation, "디럭스", 200000L, 2, 5,
                LocalTime.of(15, 0), LocalTime.of(11, 0));
        Promotion promotion = Promotion.create("초특가",
                LocalDateTime.of(2026, 5, 1, 0, 0),
                LocalDateTime.of(2026, 5, 31, 23, 59),
                LocalTime.of(0, 0), LocalTime.of(1, 0));

        PromotionRoomType prt = PromotionRoomType.create(promotion, roomType, 99000L, 10);
        prt.decreaseStock();

        assertThat(prt.getStock()).isEqualTo(9);
    }
}
