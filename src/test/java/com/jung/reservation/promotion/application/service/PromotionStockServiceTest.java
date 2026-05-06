package com.jung.reservation.promotion.application.service;

import com.jung.reservation.accommodation.domain.model.Accommodation;
import com.jung.reservation.accommodation.domain.model.RoomType;
import com.jung.reservation.accommodation.infra.persistence.AccommodationJpaRepository;
import com.jung.reservation.accommodation.infra.persistence.RoomTypeJpaRepository;
import com.jung.reservation.common.exception.BusinessException;
import com.jung.reservation.promotion.domain.model.Promotion;
import com.jung.reservation.promotion.domain.model.PromotionRoomType;
import com.jung.reservation.promotion.infra.persistence.PromotionJpaRepository;
import com.jung.reservation.promotion.infra.persistence.PromotionRoomTypeJpaRepository;
import com.jung.reservation.user.domain.model.User;
import com.jung.reservation.user.infra.persistence.UserJpaRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class PromotionStockServiceTest {

    @Autowired
    private PromotionStockService promotionStockService;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private AccommodationJpaRepository accommodationJpaRepository;
    @Autowired
    private RoomTypeJpaRepository roomTypeJpaRepository;
    @Autowired
    private PromotionJpaRepository promotionJpaRepository;
    @Autowired
    private PromotionRoomTypeJpaRepository promotionRoomTypeJpaRepository;
    @Autowired
    private UserJpaRepository userJpaRepository;

    private PromotionRoomType savedPromotionRoomType;
    private Promotion savedPromotion;

    @BeforeEach
    void setUp() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
        circuitBreakerRegistry.circuitBreaker("redisCircuitBreaker").transitionToClosedState();

        User host = userJpaRepository.save(User.create("호스트", "010-0000-0000"));
        Accommodation accommodation = accommodationJpaRepository.save(
                Accommodation.create(host, "제주 호텔", "제주시 중앙로 1"));
        RoomType roomType = roomTypeJpaRepository.save(
                RoomType.create(accommodation, "디럭스", 200000L, 2, 5,
                        LocalTime.of(15, 0), LocalTime.of(11, 0)));

        savedPromotion = promotionJpaRepository.save(
                Promotion.create("5월 초특가",
                        LocalDateTime.of(2020, 1, 1, 0, 0),
                        LocalDateTime.of(2030, 12, 31, 23, 59),
                        LocalTime.of(0, 0), LocalTime.of(23, 59)));

        savedPromotionRoomType = promotionRoomTypeJpaRepository.save(
                PromotionRoomType.create(savedPromotion, roomType, 99000L, 10));

        // Redis 세팅
        redisTemplate.opsForValue().set("stock:promotionRoomType:" + savedPromotionRoomType.getId(), "10");
        redisTemplate.opsForValue().set("sale_start:promotion:" + savedPromotion.getId(),
                String.valueOf(Instant.now().getEpochSecond() - 60));
    }

    @Test
    @DisplayName("Redis 정상 - Lua Script로 재고 선점 성공")
    @Transactional
    void reserve_redis_normal_success() {
        boolean usedRedis = promotionStockService.reserve(1000L, savedPromotionRoomType.getId(), "ORD-NORMAL-" + UUID.randomUUID());

        assertThat(usedRedis).isTrue();

        // Redis 재고 확인
        Object redisStock = redisTemplate.opsForValue().get("stock:promotionRoomType:" + savedPromotionRoomType.getId());
        assertThat(Integer.parseInt(redisStock.toString())).isEqualTo(9);

        // DB 재고는 아직 10 (decreaseDbStock 호출 전)
        PromotionRoomType dbPrt = promotionRoomTypeJpaRepository.findById(savedPromotionRoomType.getId()).orElseThrow();
        assertThat(dbPrt.getStock()).isEqualTo(10);
    }

    @Test
    @DisplayName("Redis 정상 - 동시 100명 요청 시 10명만 성공 + 재고 0 확인")
    void reserve_redis_concurrent_10_success() throws InterruptedException {
        int threadCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            long userId = 1000 + i;
            executor.submit(() -> {
                try {
                    promotionStockService.reserve(userId, savedPromotionRoomType.getId(), "ORD-CONCURRENT-" + UUID.randomUUID());
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // SOLD_OUT 등
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(10);

        // Redis 재고 0 확인
        Object redisStock = redisTemplate.opsForValue().get("stock:promotionRoomType:" + savedPromotionRoomType.getId());
        assertThat(Integer.parseInt(redisStock.toString())).isEqualTo(0);
    }

    @Test
    @DisplayName("Redis 장애 - DB Fallback으로 재고 선점 성공 + DB 재고 감소 확인")
    @Transactional
    void reserve_redis_down_dbFallback_success() {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("redisCircuitBreaker");
        cb.transitionToOpenState();

        boolean usedRedis = promotionStockService.reserve(2000L, savedPromotionRoomType.getId(), "ORD-FALLBACK-" + UUID.randomUUID());

        assertThat(usedRedis).isFalse();

        // DB 재고 9로 감소
        PromotionRoomType dbPrt = promotionRoomTypeJpaRepository.findById(savedPromotionRoomType.getId()).orElseThrow();
        assertThat(dbPrt.getStock()).isEqualTo(9);

        // Redis 재고는 변경 안 됨 (Redis 다운이니까)
        Object redisStock = redisTemplate.opsForValue().get("stock:promotionRoomType:" + savedPromotionRoomType.getId());
        assertThat(Integer.parseInt(redisStock.toString())).isEqualTo(10);

        cb.transitionToClosedState();
    }

    @Test
    @DisplayName("Redis 장애 - DB 재고 소진 시 SOLD_OUT")
    @Transactional
    void reserve_redis_down_dbFallback_soldOut() {
        PromotionRoomType prt = promotionRoomTypeJpaRepository.findById(savedPromotionRoomType.getId()).orElseThrow();
        for (int i = 0; i < 10; i++) prt.decreaseStock();

        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("redisCircuitBreaker");
        cb.transitionToOpenState();

        assertThatThrownBy(() -> promotionStockService.reserve(3000L, savedPromotionRoomType.getId(), "ORD-SOLDOUT-" + UUID.randomUUID()))
                .isInstanceOf(BusinessException.class);

        // DB 재고 여전히 0
        PromotionRoomType dbPrt = promotionRoomTypeJpaRepository.findById(savedPromotionRoomType.getId()).orElseThrow();
        assertThat(dbPrt.getStock()).isEqualTo(0);

        cb.transitionToClosedState();
    }

    @Test
    @DisplayName("Redis 장애 - Bulkhead 포화 시 일부 거부")
    void reserve_redis_down_bulkheadFull() throws InterruptedException {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("redisCircuitBreaker");
        cb.transitionToOpenState();

        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    promotionStockService.reserve(4000L, savedPromotionRoomType.getId(), "ORD-BULK-" + UUID.randomUUID());
                    successCount.incrementAndGet();
                } catch (BusinessException e) {
                    if ("5030".equals(e.getErrorCode().getCode())) {
                        rejectedCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // 기타
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // Bulkhead 5개 제한이므로 일부 거부됨
        assertThat(rejectedCount.get()).isGreaterThan(0);

        // 성공한 수 + 거부된 수 = 전체
        assertThat(successCount.get() + rejectedCount.get()).isLessThanOrEqualTo(threadCount);

        // DB 재고 확인 (성공한 만큼 감소)
        PromotionRoomType dbPrt = promotionRoomTypeJpaRepository.findById(savedPromotionRoomType.getId()).orElseThrow();
        assertThat(dbPrt.getStock()).isEqualTo(10 - successCount.get());

        cb.transitionToClosedState();
    }

    @Autowired
    private StockSyncService stockSyncService;

    @Test
    @DisplayName("Redis 다운 → DB Fallback 차감 → Redis 복구 → 재고 동기화")
    @Transactional
    void reserve_redis_recovery_stockSync() {
        // 1. Redis 정상 상태에서 Redis stock = 10, DB stock = 10
        Object initialRedisStock = redisTemplate.opsForValue().get("stock:promotionRoomType:" + savedPromotionRoomType.getId());
        assertThat(Integer.parseInt(initialRedisStock.toString())).isEqualTo(10);

        // 2. Redis 장애 → DB Fallback으로 3건 차감
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("redisCircuitBreaker");
        cb.transitionToOpenState();

        for (int i = 0; i < 3; i++) {
            promotionStockService.reserve(5000L + i, savedPromotionRoomType.getId(), "ORD-SYNC-" + UUID.randomUUID());
        }

        // DB stock = 7, Redis stock = 10 (불일치)
        PromotionRoomType dbPrt = promotionRoomTypeJpaRepository.findById(savedPromotionRoomType.getId()).orElseThrow();
        assertThat(dbPrt.getStock()).isEqualTo(7);

        Object redisStockBeforeSync = redisTemplate.opsForValue().get("stock:promotionRoomType:" + savedPromotionRoomType.getId());
        assertThat(Integer.parseInt(redisStockBeforeSync.toString())).isEqualTo(10);

        // 3. Redis 복구 → 재고 동기화
        cb.transitionToClosedState();
        stockSyncService.syncActiveStockToRedis();

        // Redis stock = 7 (DB와 동기화됨)
        Object redisStockAfterSync = redisTemplate.opsForValue().get("stock:promotionRoomType:" + savedPromotionRoomType.getId());
        assertThat(Integer.parseInt(redisStockAfterSync.toString())).isEqualTo(7);
    }
}
