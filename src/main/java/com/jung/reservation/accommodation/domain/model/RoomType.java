package com.jung.reservation.accommodation.domain.model;

import com.jung.reservation.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Entity
@Table(name = "room_type")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomType extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accommodation_id", nullable = false)
    private Accommodation accommodation;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Long amount;

    @Column(nullable = false)
    private int capacity; // 수용 인원

    @Column(nullable = false)
    private int roomCount; // 이 방 타입에 몇 개의 방이 존재하는지

    @Column(nullable = false)
    private LocalTime checkInTime; // 입실 시간 (예: 15:00)

    @Column(nullable = false)
    private LocalTime checkOutTime; // 퇴실 시간 (예: 11:00)

    private RoomType(Accommodation accommodation, String name, Long amount, int capacity, int roomCount,
                     LocalTime checkInTime, LocalTime checkOutTime) {
        this.accommodation = accommodation;
        this.name = name;
        this.amount = amount;
        this.capacity = capacity;
        this.roomCount = roomCount;
        this.checkInTime = checkInTime;
        this.checkOutTime = checkOutTime;
    }

    public static RoomType create(Accommodation accommodation, String name, Long amount, int capacity, int roomCount,
                                  LocalTime checkInTime, LocalTime checkOutTime) {
        return new RoomType(accommodation, name, amount, capacity, roomCount, checkInTime, checkOutTime);
    }
}
