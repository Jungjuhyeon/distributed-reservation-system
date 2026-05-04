package com.jung.reservation.promotion.infra.redisadapter;

public class StockLuaScript {

    private StockLuaScript() {}

    public static final String DECREASE_STOCK = """
            -- 1. Rate Limit 선점 (실패해도 복구하지 않음)
            local rateLimitCount = redis.call('INCR', KEYS[1])
            if rateLimitCount == 1 then
                redis.call('EXPIRE', KEYS[1], tonumber(ARGV[2]))
            end
            if rateLimitCount > tonumber(ARGV[1]) then
                return 'RATE_LIMITED'
            end

            -- 2. 오픈 시간 검증 (Redis TIME 사용)
            local saleStartTime = redis.call('GET', KEYS[2])
            if saleStartTime then
                local now = redis.call('TIME')
                local currentTimestamp = tonumber(now[1])
                if currentTimestamp < tonumber(saleStartTime) then
                    return 'NOT_STARTED'
                end
            end

            -- 3. 재고 차감 (실패 시 재고만 복구, Rate Limit은 복구 안 함)
            local stock = redis.call('DECR', KEYS[3])
            if stock < 0 then
                redis.call('INCR', KEYS[3])
                return 'SOLD_OUT'
            end

            return 'SUCCESS'
            """;
}
