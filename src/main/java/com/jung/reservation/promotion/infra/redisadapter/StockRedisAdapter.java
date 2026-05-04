package com.jung.reservation.promotion.infra.redisadapter;

import com.jung.reservation.promotion.application.outputport.StockOutputPort;
import com.jung.reservation.promotion.application.outputport.StockResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class StockRedisAdapter implements StockOutputPort {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final int RATE_LIMIT_MAX = 3;
    private static final int RATE_LIMIT_TTL_SECONDS = 1;

    @Override
    public StockResult decreaseStock(Long userId, Long promotionId, Long promotionRoomTypeId) {
        DefaultRedisScript<String> script = new DefaultRedisScript<>(StockLuaScript.DECREASE_STOCK, String.class);

        List<String> keys = List.of(
                "rate_limit:" + userId,
                "sale_start:promotion:" + promotionId,
                "stock:promotionRoomType:" + promotionRoomTypeId
        );

        String result = redisTemplate.execute(script, keys,
                String.valueOf(RATE_LIMIT_MAX),
                String.valueOf(RATE_LIMIT_TTL_SECONDS));

        return StockResult.valueOf(result);
    }
}
