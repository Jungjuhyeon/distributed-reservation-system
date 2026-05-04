package com.jung.reservation.booking.infra.redisadapter;

import com.jung.reservation.booking.application.outputport.CheckoutCacheOutputPort;
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
    public void saveCheckoutCache(String orderId, Long totalAmount) {
        String key = "checkout:" + orderId;
        redisTemplate.opsForValue().set(key, String.valueOf(totalAmount), CHECKOUT_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
    }
}
