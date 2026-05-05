package com.jung.reservation.booking.infra.redisadapter;

import com.jung.reservation.booking.application.outputport.RateLimitOutputPort;
import com.jung.reservation.common.util.RedisKeyPrefix;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class RateLimitRedisAdapter implements RateLimitOutputPort {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final int MAX_COUNT = 3;
    private static final int TTL_SECONDS = 1;

    @Override
    @CircuitBreaker(name = "redisCircuitBreaker")
    public boolean isAllowed(Long userId) {
        String key = RedisKeyPrefix.RATE_LIMIT + userId;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count == 1) {
            redisTemplate.expire(key, TTL_SECONDS, TimeUnit.SECONDS);
        }
        return count <= MAX_COUNT;
    }
}
