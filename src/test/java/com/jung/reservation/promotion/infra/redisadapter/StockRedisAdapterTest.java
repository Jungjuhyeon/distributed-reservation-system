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
import java.util.UUID;
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
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    @DisplayName("동시 100명 요청 시 재고 10개만 성공 (초과판매 없음)")
    void concurrentRequests_noOverselling() throws InterruptedException {
        redisTemplate.opsForValue().set("stock:promotionRoomType:" + PROMOTION_ROOM_TYPE_ID, "10");
        redisTemplate.opsForValue().set("sale_start:promotion:" + PROMOTION_ID,
                String.valueOf(Instant.now().getEpochSecond() - 60));

        int threadCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            long userId = 1000 + i;
            String orderId = "ORD-TEST-" + UUID.randomUUID();
            executor.submit(() -> {
                try {
                    StockResult result = stockOutputPort.decreaseStock(userId, PROMOTION_ID, PROMOTION_ROOM_TYPE_ID, orderId);
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

        Object remainingStock = redisTemplate.opsForValue().get("stock:promotionRoomType:" + PROMOTION_ROOM_TYPE_ID);
        assertThat(Integer.parseInt(remainingStock.toString())).isEqualTo(0);
    }

    @Test
    @DisplayName("오픈 전 요청 차단")
    void beforeSaleStart_blocked() {
        redisTemplate.opsForValue().set("stock:promotionRoomType:" + PROMOTION_ROOM_TYPE_ID, "10");
        redisTemplate.opsForValue().set("sale_start:promotion:" + PROMOTION_ID,
                String.valueOf(Instant.now().getEpochSecond() + 3600));

        StockResult result = stockOutputPort.decreaseStock(2000L, PROMOTION_ID, PROMOTION_ROOM_TYPE_ID, "ORD-TEST-1");

        assertThat(result).isEqualTo(StockResult.NOT_STARTED);
    }

    @Test
    @DisplayName("같은 유저 1초 내 4번째 요청부터 Rate Limit 차단")
    void rateLimit_blocksAfterMax() {
        redisTemplate.opsForValue().set("stock:promotionRoomType:" + PROMOTION_ROOM_TYPE_ID, "10");
        redisTemplate.opsForValue().set("sale_start:promotion:" + PROMOTION_ID,
                String.valueOf(Instant.now().getEpochSecond() - 60));

        Long userId = 3000L;

        assertThat(stockOutputPort.decreaseStock(userId, PROMOTION_ID, PROMOTION_ROOM_TYPE_ID, "ORD-TEST-A"))
                .isEqualTo(StockResult.SUCCESS);
        assertThat(stockOutputPort.decreaseStock(userId, PROMOTION_ID, PROMOTION_ROOM_TYPE_ID, "ORD-TEST-B"))
                .isEqualTo(StockResult.SUCCESS);
        assertThat(stockOutputPort.decreaseStock(userId, PROMOTION_ID, PROMOTION_ROOM_TYPE_ID, "ORD-TEST-C"))
                .isEqualTo(StockResult.SUCCESS);

        assertThat(stockOutputPort.decreaseStock(userId, PROMOTION_ID, PROMOTION_ROOM_TYPE_ID, "ORD-TEST-D"))
                .isEqualTo(StockResult.RATE_LIMITED);
    }

    @Test
    @DisplayName("재고 소진 시 SOLD_OUT 반환")
    void soldOut() {
        redisTemplate.opsForValue().set("stock:promotionRoomType:" + PROMOTION_ROOM_TYPE_ID, "1");
        redisTemplate.opsForValue().set("sale_start:promotion:" + PROMOTION_ID,
                String.valueOf(Instant.now().getEpochSecond() - 60));

        assertThat(stockOutputPort.decreaseStock(4000L, PROMOTION_ID, PROMOTION_ROOM_TYPE_ID, "ORD-TEST-X"))
                .isEqualTo(StockResult.SUCCESS);

        assertThat(stockOutputPort.decreaseStock(4001L, PROMOTION_ID, PROMOTION_ROOM_TYPE_ID, "ORD-TEST-Y"))
                .isEqualTo(StockResult.SOLD_OUT);
    }

    @Test
    @DisplayName("동일 orderId 중복 요청 시 ALREADY_PROCESSED 반환")
    void idempotency_blocksAlreadyProcessed() {
        redisTemplate.opsForValue().set("stock:promotionRoomType:" + PROMOTION_ROOM_TYPE_ID, "10");
        redisTemplate.opsForValue().set("sale_start:promotion:" + PROMOTION_ID,
                String.valueOf(Instant.now().getEpochSecond() - 60));

        String orderId = "ORD-TEST-SAME";

        assertThat(stockOutputPort.decreaseStock(5000L, PROMOTION_ID, PROMOTION_ROOM_TYPE_ID, orderId))
                .isEqualTo(StockResult.SUCCESS);

        assertThat(stockOutputPort.decreaseStock(5000L, PROMOTION_ID, PROMOTION_ROOM_TYPE_ID, orderId))
                .isEqualTo(StockResult.ALREADY_PROCESSED);
    }
}
