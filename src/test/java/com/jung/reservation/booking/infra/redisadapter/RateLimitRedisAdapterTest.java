package com.jung.reservation.booking.infra.redisadapter;

import com.jung.reservation.booking.application.outputport.RateLimitOutputPort;
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
class RateLimitRedisAdapterTest {

    @Autowired
    private RateLimitOutputPort rateLimitOutputPort;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @BeforeEach
    void setUp() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    @DisplayName("1~3번째 요청 허용")
    void isAllowed_withinLimit() {
        assertThat(rateLimitOutputPort.isAllowed(1000L)).isTrue();
        assertThat(rateLimitOutputPort.isAllowed(1000L)).isTrue();
        assertThat(rateLimitOutputPort.isAllowed(1000L)).isTrue();
    }

    @Test
    @DisplayName("4번째 요청부터 거부")
    void isAllowed_exceedLimit() {
        rateLimitOutputPort.isAllowed(2000L);
        rateLimitOutputPort.isAllowed(2000L);
        rateLimitOutputPort.isAllowed(2000L);

        assertThat(rateLimitOutputPort.isAllowed(2000L)).isFalse();
    }

    @Test
    @DisplayName("다른 유저는 별도 카운트")
    void isAllowed_differentUser() {
        rateLimitOutputPort.isAllowed(3000L);
        rateLimitOutputPort.isAllowed(3000L);
        rateLimitOutputPort.isAllowed(3000L);

        // 3000번 유저는 4번째 거부
        assertThat(rateLimitOutputPort.isAllowed(3000L)).isFalse();
        // 3001번 유저는 1번째 허용
        assertThat(rateLimitOutputPort.isAllowed(3001L)).isTrue();
    }
}
