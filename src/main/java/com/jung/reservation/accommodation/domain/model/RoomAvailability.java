package com.jung.reservation.accommodation.domain.model;

import com.jung.reservation.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "room_availability", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"room_type_id", "date"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomAvailability extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_type_id", nullable = false)
    private RoomType roomType;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private int availableCount; // 해당 날짜에 예약 가능한 객실 수

    @Column(nullable = false)
    private Long amount; // 해당 날짜 판매가

    private RoomAvailability(RoomType roomType, LocalDate date, int availableCount, Long amount) {
        this.roomType = roomType;
        this.date = date;
        this.availableCount = availableCount;
        this.amount = amount;
    }

    public static RoomAvailability create(RoomType roomType, LocalDate date, int availableCount, Long amount) {
        return new RoomAvailability(roomType, date, availableCount, amount);
    }

    public boolean canDecreaseCount() {
        return this.availableCount > 0;
    }

    public void decreaseCount() {
        this.availableCount--;
    }

    public void increaseCount() {
        this.availableCount++;
    }
}
