package com.jung.reservation.user.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserPointTest {

    @Test
    @DisplayName("UserPoint 생성")
    void create() {
        User user = User.create("정주현", "010-1234-5678");
        UserPoint userPoint = UserPoint.create(user, 50000L);

        assertThat(userPoint.getUser()).isEqualTo(user);
        assertThat(userPoint.getCurrentPoint()).isEqualTo(50000L);
    }

    @Test
    @DisplayName("포인트 차감 가능 여부 확인")
    void canDeduct() {
        User user = User.create("정주현", "010-1234-5678");
        UserPoint userPoint = UserPoint.create(user, 50000L);

        assertThat(userPoint.canDeduct(50000L)).isTrue();
        assertThat(userPoint.canDeduct(50001L)).isFalse();
    }

    @Test
    @DisplayName("포인트 차감")
    void deduct() {
        User user = User.create("정주현", "010-1234-5678");
        UserPoint userPoint = UserPoint.create(user, 50000L);

        userPoint.deduct(30000L);

        assertThat(userPoint.getCurrentPoint()).isEqualTo(20000L);
    }

    @Test
    @DisplayName("포인트 환불")
    void refund() {
        User user = User.create("정주현", "010-1234-5678");
        UserPoint userPoint = UserPoint.create(user, 50000L);

        userPoint.deduct(30000L);
        userPoint.refund(30000L);

        assertThat(userPoint.getCurrentPoint()).isEqualTo(50000L);
    }
}
