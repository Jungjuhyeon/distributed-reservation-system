package com.jung.reservation.booking.infra.redisadapter;

import com.jung.reservation.booking.application.outputport.IdempotencyOutputPort;
import com.jung.reservation.common.util.RedisKeyPrefix;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyRedisAdapter implements IdempotencyOutputPort {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final long TTL_SECONDS = 3;

    @Override
    @CircuitBreaker(name = "redisCircuitBreaker", fallbackMethod = "fallback")
    public boolean isDuplicate(String orderId) {
        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(RedisKeyPrefix.IDEMPOTENCY + orderId, "PROCESSING", TTL_SECONDS, TimeUnit.SECONDS);
        return !Boolean.TRUE.equals(success);
    }

    private boolean fallback(String orderId, Exception e) {
        log.warn("[Redis 장애] 멱등성 체크 스킵 (DB UNIQUE로 방어) - orderId: {}", orderId);
        return false;
    }
}
