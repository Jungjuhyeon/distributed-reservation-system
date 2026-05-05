package com.jung.reservation.booking.infra.redisadapter;

import com.jung.reservation.booking.application.outputport.CheckoutCacheOutputPort;
import com.jung.reservation.common.util.RedisKeyPrefix;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class CheckoutCacheAdapter implements CheckoutCacheOutputPort {

    private final RedisTemplate<String, Object> redisTemplate;
    private static final long CHECKOUT_CACHE_TTL_MINUTES = 10;

    @Override
    @CircuitBreaker(name = "redisCircuitBreaker")
    public void saveCheckoutCache(String orderId, Long totalAmount) {
        String key = RedisKeyPrefix.CHECKOUT + orderId;
        redisTemplate.opsForValue().set(key, String.valueOf(totalAmount), CHECKOUT_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
    }

    @Override
    @CircuitBreaker(name = "redisCircuitBreaker")
    public Long getCheckoutAmount(String orderId) {
        Object value = redisTemplate.opsForValue().get(RedisKeyPrefix.CHECKOUT + orderId);
        return value != null ? Long.parseLong(value.toString()) : null;
    }
}
