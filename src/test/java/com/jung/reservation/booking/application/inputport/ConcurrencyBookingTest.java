package com.jung.reservation.booking.application.inputport;

import com.jung.reservation.accommodation.domain.model.Accommodation;
import com.jung.reservation.accommodation.domain.model.RoomAvailability;
import com.jung.reservation.accommodation.domain.model.RoomType;
import com.jung.reservation.accommodation.infra.persistence.AccommodationJpaRepository;
import com.jung.reservation.accommodation.infra.persistence.RoomAvailabilityJpaRepository;
import com.jung.reservation.accommodation.infra.persistence.RoomTypeJpaRepository;
import com.jung.reservation.booking.application.usecase.BookingUseCase;
import com.jung.reservation.booking.domain.model.enumeration.BookingStatus;
import com.jung.reservation.booking.framework.web.dto.BookingRequest;
import com.jung.reservation.booking.infra.persistence.BookingJpaRepository;
import com.jung.reservation.payment.infra.persistence.PaymentJpaRepository;
import com.jung.reservation.promotion.domain.model.Promotion;
import com.jung.reservation.promotion.domain.model.PromotionRoomType;
import com.jung.reservation.promotion.infra.persistence.PromotionJpaRepository;
import com.jung.reservation.promotion.infra.persistence.PromotionRoomTypeJpaRepository;
import com.jung.reservation.user.domain.model.User;
import com.jung.reservation.user.infra.persistence.UserJpaRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ConcurrencyBookingTest {

    // @Transactional 없음 — 동시성 테스트는 스레드 간 커밋된 데이터가 보여야 함

    @Autowired private BookingUseCase bookingUseCase;
    @Autowired private RedisTemplate<String, Object> redisTemplate;
    @Autowired private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired private UserJpaRepository userJpaRepository;
    @Autowired private AccommodationJpaRepository accommodationJpaRepository;
    @Autowired private RoomTypeJpaRepository roomTypeJpaRepository;
    @Autowired private RoomAvailabilityJpaRepository roomAvailabilityJpaRepository;
    @Autowired private PromotionJpaRepository promotionJpaRepository;
    @Autowired private PromotionRoomTypeJpaRepository promotionRoomTypeJpaRepository;
    @Autowired private BookingJpaRepository bookingJpaRepository;
    @Autowired private PaymentJpaRepository paymentJpaRepository;

    private static final int STOCK = 10;
    private static final int THREAD_COUNT = 1000;

    private Long roomTypeId;
    private Long promotionRoomTypeId;
    private Long promotionId;
    private final List<Long> userIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
        circuitBreakerRegistry.circuitBreaker("redisCircuitBreaker").transitionToClosedState();
        circuitBreakerRegistry.circuitBreaker("pgCircuitBreaker").transitionToClosedState();

        // 숙소 + 객실 세팅
        User host = userJpaRepository.save(User.create("호스트", "010-0000-0000"));
        Accommodation accommodation = accommodationJpaRepository.save(
                Accommodation.create(host, "제주 동시성 호텔", "제주시 중앙로 1"));
        RoomType roomType = roomTypeJpaRepository.save(
                RoomType.create(accommodation, "디럭스 더블", 200000L, 2, STOCK,
                        LocalTime.of(15, 0), LocalTime.of(11, 0)));
        roomTypeId = roomType.getId();

        // RoomAvailability: 재고 STOCK개 (10명 예약 수용 가능)
        roomAvailabilityJpaRepository.save(
                RoomAvailability.create(roomType, LocalDate.of(2026, 5, 10), STOCK, 200000L));
        roomAvailabilityJpaRepository.save(
                RoomAvailability.create(roomType, LocalDate.of(2026, 5, 11), STOCK, 200000L));

        // 프로모션 세팅
        Promotion promotion = promotionJpaRepository.save(
                Promotion.create("동시성 테스트 특가",
                        LocalDateTime.of(2020, 1, 1, 0, 0),
                        LocalDateTime.of(2030, 12, 31, 23, 59),
                        LocalTime.of(0, 0), LocalTime.of(23, 59)));
        promotionId = promotion.getId();

        PromotionRoomType prt = promotionRoomTypeJpaRepository.save(
                PromotionRoomType.create(promotion, roomType, 99000L, STOCK));
        promotionRoomTypeId = prt.getId();

        // 1000명 유저 일괄 생성 (CREDIT_CARD → UserPoint 불필요)
        List<User> users = new ArrayList<>();
        for (int i = 0; i < THREAD_COUNT; i++) {
            users.add(User.create("유저" + i,
                    "010-" + String.format("%04d", i / 10000) + "-" + String.format("%04d", i)));
        }
        userJpaRepository.saveAll(users).forEach(u -> userIds.add(u.getId()));

        // Redis 초기화
        redisTemplate.opsForValue().set("stock:promotionRoomType:" + promotionRoomTypeId, String.valueOf(STOCK));
        redisTemplate.opsForValue().set("sale_start:promotion:" + promotionId,
                String.valueOf(Instant.now().getEpochSecond() - 60));
    }

    @AfterEach
    void cleanUp() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
        paymentJpaRepository.deleteAll();
        bookingJpaRepository.deleteAll();
        promotionRoomTypeJpaRepository.deleteAll();
        promotionJpaRepository.deleteAll();
        roomAvailabilityJpaRepository.deleteAll();
        roomTypeJpaRepository.deleteAll();
        accommodationJpaRepository.deleteAll();
        userJpaRepository.deleteAll();
        userIds.clear();
    }

    @Test
    @DisplayName("1000명 동시 예약 → 재고 10개만 COMPLETED, 초과판매 없음")
    void concurrent_1000requests_only10Succeed() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(100);
        CountDownLatch ready = new CountDownLatch(THREAD_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREAD_COUNT);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        for (int i = 0; i < THREAD_COUNT; i++) {
            final int idx = i;
            final long userId = userIds.get(i);
            final String orderId = String.format("ORD-CONC-%04d", i);
            redisTemplate.opsForValue().set("checkout:" + orderId, "99000");

            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await(); // 모든 스레드가 준비된 후 동시에 출발
                    BookingRequest request = new BookingRequest(
                            orderId, userId, roomTypeId, promotionRoomTypeId,
                            99000L,
                            LocalDate.of(2026, 5, 10), LocalDate.of(2026, 5, 12),
                            "pg-txn-" + idx,
                            List.of(new BookingRequest.PaymentMethodRequest("CREDIT_CARD", 99000L)));
                    bookingUseCase.book(request);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();  // 전원 준비 완료 대기
        start.countDown(); // 동시 출발
        done.await();
        executor.shutdown();

        // 정확히 10개만 성공
        assertThat(successCount.get()).isEqualTo(STOCK);
        assertThat(failCount.get()).isEqualTo(THREAD_COUNT - STOCK);

        // Redis 재고 = 0 (초과판매 없음)
        Object remaining = redisTemplate.opsForValue().get("stock:promotionRoomType:" + promotionRoomTypeId);
        assertThat(Integer.parseInt(remaining.toString())).isZero();

        // DB COMPLETED 정확히 10건
        long completedCount = bookingJpaRepository.findAll().stream()
                .filter(b -> b.getStatus() == BookingStatus.COMPLETED)
                .count();
        assertThat(completedCount).isEqualTo(STOCK);

        // DB PromotionRoomType 재고 = 0
        PromotionRoomType dbPrt = promotionRoomTypeJpaRepository.findById(promotionRoomTypeId).orElseThrow();
        assertThat(dbPrt.getStock()).isZero();
    }
}
