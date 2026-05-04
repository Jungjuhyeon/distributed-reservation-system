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
}
