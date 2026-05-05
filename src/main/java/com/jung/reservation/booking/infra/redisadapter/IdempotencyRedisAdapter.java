package com.jung.reservation.booking.infra.redisadapter;

import com.jung.reservation.booking.application.outputport.IdempotencyOutputPort;
import com.jung.reservation.common.util.RedisKeyPrefix;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class IdempotencyRedisAdapter implements IdempotencyOutputPort {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final long TTL_SECONDS = 3;

    @Override
    @CircuitBreaker(name = "redisCircuitBreaker")
    public boolean isDuplicate(String orderId) {
        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(RedisKeyPrefix.IDEMPOTENCY + orderId, "PROCESSING", TTL_SECONDS, TimeUnit.SECONDS);
        return !Boolean.TRUE.equals(success);
    }
}
