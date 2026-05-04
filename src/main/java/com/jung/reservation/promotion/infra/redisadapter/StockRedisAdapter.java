package com.jung.reservation.promotion.infra.redisadapter;

import com.jung.reservation.common.util.RedisKeyPrefix;
import com.jung.reservation.promotion.application.outputport.StockOutputPort;
import com.jung.reservation.promotion.application.outputport.StockResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class StockRedisAdapter implements StockOutputPort {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final int RATE_LIMIT_MAX = 3;
    private static final int RATE_LIMIT_TTL_SECONDS = 1;
    private static final int IDEMPOTENCY_PROCESSING_TTL_SECONDS = 30;
    private static final long IDEMPOTENCY_COMPLETED_TTL_MINUTES = 10;

    @Override
    public StockResult decreaseStock(Long userId, Long promotionId, Long promotionRoomTypeId, String orderId) {
        DefaultRedisScript<String> script = new DefaultRedisScript<>(StockLuaScript.DECREASE_STOCK, String.class);

        List<String> keys = List.of(
                RedisKeyPrefix.RATE_LIMIT + userId,
                RedisKeyPrefix.SALE_START + promotionId,
                RedisKeyPrefix.IDEMPOTENCY + orderId,
                RedisKeyPrefix.STOCK + promotionRoomTypeId
        );

        String result = redisTemplate.execute(script, keys,
                String.valueOf(RATE_LIMIT_MAX),
                String.valueOf(RATE_LIMIT_TTL_SECONDS),
                String.valueOf(IDEMPOTENCY_PROCESSING_TTL_SECONDS));

        return StockResult.valueOf(result);
    }

    @Override
    public void completeIdempotency(String orderId) {
        redisTemplate.opsForValue()
                .set(RedisKeyPrefix.IDEMPOTENCY + orderId, "COMPLETED", IDEMPOTENCY_COMPLETED_TTL_MINUTES, TimeUnit.MINUTES);
    }

    @Override
    public void releaseIdempotency(String orderId) {
        redisTemplate.delete(RedisKeyPrefix.IDEMPOTENCY + orderId);
    }
}
