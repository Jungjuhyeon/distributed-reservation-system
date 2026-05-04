package com.jung.reservation.user.domain.model;

import com.jung.reservation.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_point")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserPoint extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false)
    private Long currentPoint;

    private UserPoint(User user, Long currentPoint) {
        this.user = user;
        this.currentPoint = currentPoint;
    }

    public static UserPoint create(User user, Long currentPoint) {
        return new UserPoint(user, currentPoint);
    }
}
