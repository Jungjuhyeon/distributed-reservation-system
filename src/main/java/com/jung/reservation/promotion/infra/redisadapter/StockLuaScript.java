package com.jung.reservation.promotion.infra.redisadapter;

public class StockLuaScript {

    private StockLuaScript() {}

    // KEYS[1] = rate_limit:{userId}
    // KEYS[2] = sale_start:promotion:{promotionId}
    // KEYS[3] = idempotency:booking:{orderId}
    // KEYS[4] = stock:promotionRoomType:{id}
    // ARGV[1] = rate limit 최대 허용 횟수
    // ARGV[2] = rate limit TTL 초
    // ARGV[3] = 멱등성 키 TTL 초 (PROCESSING)
    public static final String DECREASE_STOCK = """
            -- 1. 오픈 시간 검증 (Redis TIME 사용)
            local saleStartTime = redis.call('GET', KEYS[2])
            if saleStartTime then
                local now = redis.call('TIME')
                local currentTimestamp = tonumber(now[1])
                if currentTimestamp < tonumber(saleStartTime) then
                    return 'NOT_STARTED'
                end
            end

            -- 2. Rate Limit 선점 (실패해도 복구하지 않음)
            local rateLimitCount = redis.call('INCR', KEYS[1])
            if rateLimitCount == 1 then
                redis.call('EXPIRE', KEYS[1], tonumber(ARGV[2]))
            end
            if rateLimitCount > tonumber(ARGV[1]) then
                return 'RATE_LIMITED'
            end

            -- 3. 멱등성 체크 (SET NX, TTL PROCESSING)
            local acquired = redis.call('SET', KEYS[3], 'PROCESSING', 'NX', 'EX', tonumber(ARGV[3]))
            if not acquired then
                return 'ALREADY_PROCESSED'
            end

            -- 4. 재고 차감 (실패 시 재고 복구 + 멱등성 키 삭제)
            local stock = redis.call('DECR', KEYS[4])
            if stock < 0 then
                redis.call('INCR', KEYS[4])
                redis.call('DEL', KEYS[3])
                return 'SOLD_OUT'
            end

            return 'SUCCESS'
            """;
}
