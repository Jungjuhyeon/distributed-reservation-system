package com.jung.reservation.promotion.domain.model;

import com.jung.reservation.accommodation.domain.model.RoomType;
import com.jung.reservation.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "promotion_room_type")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PromotionRoomType extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promotion_id", nullable = false)
    private Promotion promotion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_type_id", nullable = false)
    private RoomType roomType;

    @Column(nullable = false)
    private Long promotionAmount; // 프로모션 할인가

    @Column(nullable = false)
    private int stock; // 프로모션 재고

    private PromotionRoomType(Promotion promotion, RoomType roomType, Long promotionAmount, int stock) {
        this.promotion = promotion;
        this.roomType = roomType;
        this.promotionAmount = promotionAmount;
        this.stock = stock;
    }

    public static PromotionRoomType create(Promotion promotion, RoomType roomType, Long promotionAmount, int stock) {
        return new PromotionRoomType(promotion, roomType, promotionAmount, stock);
    }
}
