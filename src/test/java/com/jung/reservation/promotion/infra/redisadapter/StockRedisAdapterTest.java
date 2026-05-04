package com.jung.reservation.promotion.infra.redisadapter;

import com.jung.reservation.promotion.application.outputport.StockOutputPort;
import com.jung.reservation.promotion.application.outputport.StockResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class StockRedisAdapterTest {

    @Autowired
    private StockOutputPort stockOutputPort;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final Long PROMOTION_ID = 1L;
    private static final Long PROMOTION_ROOM_TYPE_ID = 1L;

    @BeforeEach
    void setUp() {
        // Redis 전체 초기화
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    @DisplayName("동시 100명 요청 시 재고 10개만 성공 (초과판매 없음)")
    void concurrentRequests_noOverselling() throws InterruptedException {
        // 재고 10개, 오픈 시간은 과거로 세팅
        redisTemplate.opsForValue().set("stock:promotionRoomType:" + PROMOTION_ROOM_TYPE_ID, "10");
        redisTemplate.opsForValue().set("sale_start:promotion:" + PROMOTION_ID,
                String.valueOf(Instant.now().getEpochSecond() - 60));

        int threadCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            long userId = 1000 + i; // 각각 다른 유저
            executor.submit(() -> {
                try {
                    StockResult result = stockOutputPort.decreaseStock(userId, PROMOTION_ID, PROMOTION_ROOM_TYPE_ID);
                    if (result == StockResult.SUCCESS) {
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(10);

        // Redis 재고가 0인지 확인
        Object remainingStock = redisTemplate.opsForValue().get("stock:promotionRoomType:" + PROMOTION_ROOM_TYPE_ID);
        assertThat(Integer.parseInt(remainingStock.toString())).isEqualTo(0);
    }

    @Test
    @DisplayName("오픈 전 요청 차단")
    void beforeSaleStart_blocked() {
        redisTemplate.opsForValue().set("stock:promotionRoomType:" + PROMOTION_ROOM_TYPE_ID, "10");
        // 오픈 시간을 1시간 뒤로 세팅
        redisTemplate.opsForValue().set("sale_start:promotion:" + PROMOTION_ID,
                String.valueOf(Instant.now().getEpochSecond() + 3600));

        StockResult result = stockOutputPort.decreaseStock(2000L, PROMOTION_ID, PROMOTION_ROOM_TYPE_ID);

        assertThat(result).isEqualTo(StockResult.NOT_STARTED);
    }

    @Test
    @DisplayName("같은 유저 1초 내 4번째 요청부터 Rate Limit 차단")
    void rateLimit_blocksAfterMax() {
        redisTemplate.opsForValue().set("stock:promotionRoomType:" + PROMOTION_ROOM_TYPE_ID, "10");
        redisTemplate.opsForValue().set("sale_start:promotion:" + PROMOTION_ID,
                String.valueOf(Instant.now().getEpochSecond() - 60));

        Long userId = 3000L;

        // 1~3번째 요청: 성공
        assertThat(stockOutputPort.decreaseStock(userId, PROMOTION_ID, PROMOTION_ROOM_TYPE_ID))
                .isEqualTo(StockResult.SUCCESS);
        assertThat(stockOutputPort.decreaseStock(userId, PROMOTION_ID, PROMOTION_ROOM_TYPE_ID))
                .isEqualTo(StockResult.SUCCESS);
        assertThat(stockOutputPort.decreaseStock(userId, PROMOTION_ID, PROMOTION_ROOM_TYPE_ID))
                .isEqualTo(StockResult.SUCCESS);

        // 4번째 요청: Rate Limit
        assertThat(stockOutputPort.decreaseStock(userId, PROMOTION_ID, PROMOTION_ROOM_TYPE_ID))
                .isEqualTo(StockResult.RATE_LIMITED);
    }

    @Test
    @DisplayName("재고 소진 시 SOLD_OUT 반환")
    void soldOut() {
        redisTemplate.opsForValue().set("stock:promotionRoomType:" + PROMOTION_ROOM_TYPE_ID, "1");
        redisTemplate.opsForValue().set("sale_start:promotion:" + PROMOTION_ID,
                String.valueOf(Instant.now().getEpochSecond() - 60));

        // 첫 번째: 성공
        assertThat(stockOutputPort.decreaseStock(4000L, PROMOTION_ID, PROMOTION_ROOM_TYPE_ID))
                .isEqualTo(StockResult.SUCCESS);

        // 두 번째: 품절
        assertThat(stockOutputPort.decreaseStock(4001L, PROMOTION_ID, PROMOTION_ROOM_TYPE_ID))
                .isEqualTo(StockResult.SOLD_OUT);
    }
}
