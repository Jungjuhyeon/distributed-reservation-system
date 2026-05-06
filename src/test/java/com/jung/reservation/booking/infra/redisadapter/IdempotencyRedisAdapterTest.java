package com.jung.reservation.booking.infra.redisadapter;

import com.jung.reservation.booking.application.outputport.IdempotencyOutputPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class IdempotencyRedisAdapterTest {

    @Autowired
    private IdempotencyOutputPort idempotencyOutputPort;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @BeforeEach
    void setUp() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    @DisplayName("첫 번째 요청 - 중복 아님")
    void isDuplicate_firstRequest_false() {
        assertThat(idempotencyOutputPort.isDuplicate("ORD-IDEMP-001")).isFalse();
    }

    @Test
    @DisplayName("같은 orderId 두 번째 요청 - 중복")
    void isDuplicate_secondRequest_true() {
        idempotencyOutputPort.isDuplicate("ORD-IDEMP-002");
        assertThat(idempotencyOutputPort.isDuplicate("ORD-IDEMP-002")).isTrue();
    }

    @Test
    @DisplayName("다른 orderId - 중복 아님")
    void isDuplicate_differentOrderId_false() {
        idempotencyOutputPort.isDuplicate("ORD-IDEMP-003");
        assertThat(idempotencyOutputPort.isDuplicate("ORD-IDEMP-004")).isFalse();
    }
}
