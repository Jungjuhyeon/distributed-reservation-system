package com.jung.reservation.user.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    @Test
    @DisplayName("User 생성")
    void create() {
        User user = User.create("정주현", "010-1234-5678");

        assertThat(user.getName()).isEqualTo("정주현");
        assertThat(user.getPhone()).isEqualTo("010-1234-5678");
    }
}
