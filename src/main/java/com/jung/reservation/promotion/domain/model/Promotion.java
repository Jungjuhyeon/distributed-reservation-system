package com.jung.reservation.promotion.domain.model;

import com.jung.reservation.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "promotion")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Promotion extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private LocalDateTime startDateTime; // 이벤트 전체 시작 일시

    @Column(nullable = false)
    private LocalDateTime endDateTime; // 이벤트 전체 종료 일시

    @Column(nullable = false)
    private LocalTime dailyStartTime; // 매일 선착순 시작 시간 (예: 00:00:00)

    @Column(nullable = false)
    private LocalTime dailyEndTime; // 매일 선착순 종료 시간 (예: 01:00:00)

    private Promotion(String name, LocalDateTime startDateTime, LocalDateTime endDateTime,
                      LocalTime dailyStartTime, LocalTime dailyEndTime) {
        this.name = name;
        this.startDateTime = startDateTime;
        this.endDateTime = endDateTime;
        this.dailyStartTime = dailyStartTime;
        this.dailyEndTime = dailyEndTime;
    }

    public static Promotion create(String name, LocalDateTime startDateTime, LocalDateTime endDateTime,
                                   LocalTime dailyStartTime, LocalTime dailyEndTime) {
        return new Promotion(name, startDateTime, endDateTime, dailyStartTime, dailyEndTime);
    }
}
