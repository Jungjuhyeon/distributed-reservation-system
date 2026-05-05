package com.jung.reservation.booking.infra.redisadapter;

import com.jung.reservation.booking.application.outputport.CheckoutCacheOutputPort;
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
public class CheckoutCacheAdapter implements CheckoutCacheOutputPort {

    private final RedisTemplate<String, Object> redisTemplate;
    private static final long CHECKOUT_CACHE_TTL_MINUTES = 10;

    @Override
    @CircuitBreaker(name = "redisCircuitBreaker", fallbackMethod = "saveCheckoutCacheFallback")
    public void saveCheckoutCache(String orderId, Long totalAmount) {
        String key = RedisKeyPrefix.CHECKOUT + orderId;
        redisTemplate.opsForValue().set(key, String.valueOf(totalAmount), CHECKOUT_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
    }

    @Override
    @CircuitBreaker(name = "redisCircuitBreaker", fallbackMethod = "getCheckoutAmountFallback")
    public Long getCheckoutAmount(String orderId) {
        Object value = redisTemplate.opsForValue().get(RedisKeyPrefix.CHECKOUT + orderId);
        return value != null ? Long.parseLong(value.toString()) : null;
    }

    private void saveCheckoutCacheFallback(String orderId, Long totalAmount, Exception e) {
        log.warn("[Redis 장애] checkout 캐시 저장 스킵 - orderId: {}", orderId);
    }

    private Long getCheckoutAmountFallback(String orderId, Exception e) {
        log.warn("[Redis 장애] checkout 캐시 조회 실패 - orderId: {}", orderId);
        return null;
    }
}
