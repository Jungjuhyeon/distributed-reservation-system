package com.jung.reservation.common.init;

import com.jung.reservation.accommodation.domain.model.Accommodation;
import com.jung.reservation.accommodation.domain.model.RoomAvailability;
import com.jung.reservation.accommodation.domain.model.RoomType;
import com.jung.reservation.accommodation.infra.persistence.AccommodationJpaRepository;
import com.jung.reservation.accommodation.infra.persistence.RoomAvailabilityJpaRepository;
import com.jung.reservation.accommodation.infra.persistence.RoomTypeJpaRepository;
import com.jung.reservation.promotion.domain.model.Promotion;
import com.jung.reservation.promotion.domain.model.PromotionRoomType;
import com.jung.reservation.promotion.infra.persistence.PromotionJpaRepository;
import com.jung.reservation.promotion.infra.persistence.PromotionRoomTypeJpaRepository;
import com.jung.reservation.user.domain.model.User;
import com.jung.reservation.user.domain.model.UserPoint;
import com.jung.reservation.user.infra.persistence.UserJpaRepository;
import com.jung.reservation.user.infra.persistence.UserPointJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 앱 시작 시 테스트용 초기 데이터를 DB + Redis에 자동 세팅한다.
 * 채점자가 앱 시작 즉시 Checkout / Booking API를 호출할 수 있도록 한다.
 * 테스트 환경(@Profile("test"))에서는 실행하지 않는다.
 */
@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private static final int PROMOTION_STOCK = 10;

    private final UserJpaRepository userJpaRepository;
    private final UserPointJpaRepository userPointJpaRepository;
    private final AccommodationJpaRepository accommodationJpaRepository;
    private final RoomTypeJpaRepository roomTypeJpaRepository;
    private final RoomAvailabilityJpaRepository roomAvailabilityJpaRepository;
    private final PromotionJpaRepository promotionJpaRepository;
    private final PromotionRoomTypeJpaRepository promotionRoomTypeJpaRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("[DataInitializer] 초기 데이터 세팅 시작...");

        // Redis 초기화
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
        log.info("[DataInitializer] Redis 초기화 완료");

        // 1. 사용자 + 포인트
        User user = userJpaRepository.save(User.create("테스트유저", "010-1234-5678"));
        userPointJpaRepository.save(UserPoint.create(user, 100_000L));
        log.info("[DataInitializer] User 생성 - id: {}, 포인트: 100,000원", user.getId());

        // 2. 숙소 + 객실
        User host = userJpaRepository.save(User.create("호스트", "010-0000-0000"));
        Accommodation accommodation = accommodationJpaRepository.save(
                Accommodation.create(host, "제주 초특가 호텔", "제주시 중앙로 1"));
        RoomType roomType = roomTypeJpaRepository.save(
                RoomType.create(accommodation, "디럭스 더블", 200_000L, 2, 5,
                        LocalTime.of(15, 0), LocalTime.of(11, 0)));
        log.info("[DataInitializer] RoomType 생성 - id: {}", roomType.getId());

        // 3. RoomAvailability (오늘 ~ 오늘+7일, availableCount = PROMOTION_STOCK)
        LocalDate today = LocalDate.now();
        for (int i = 0; i < 7; i++) {
            roomAvailabilityJpaRepository.save(
                    RoomAvailability.create(roomType, today.plusDays(i), PROMOTION_STOCK, 200_000L));
        }
        log.info("[DataInitializer] RoomAvailability 생성 - {} ~ {}", today, today.plusDays(6));

        // 4. 프로모션 + 프로모션 객실 (현재 오픈 상태)
        Promotion promotion = promotionJpaRepository.save(
                Promotion.create(
                        "5월 초특가",
                        LocalDateTime.now().minusHours(1),   // 이미 시작됨
                        LocalDateTime.now().plusDays(30),    // 30일 후 종료
                        LocalTime.of(0, 0),
                        LocalTime.of(23, 59)
                ));
        PromotionRoomType promotionRoomType = promotionRoomTypeJpaRepository.save(
                PromotionRoomType.create(promotion, roomType, 99_000L, PROMOTION_STOCK));
        log.info("[DataInitializer] Promotion 생성 - id: {}, PromotionRoomType id: {}",
                promotion.getId(), promotionRoomType.getId());

        // 5. Redis 세팅
        redisTemplate.opsForValue().set(
                "stock:promotionRoomType:" + promotionRoomType.getId(),
                String.valueOf(PROMOTION_STOCK));
        redisTemplate.opsForValue().set(
                "sale_start:promotion:" + promotion.getId(),
                String.valueOf(Instant.now().getEpochSecond() - 60)); // 즉시 오픈 상태
        log.info("[DataInitializer] Redis 세팅 완료 - stock: {}, sale_start: 즉시 오픈",
                PROMOTION_STOCK);

        // 6. k6 동시성 테스트용 유저 300명 생성 (각 VU가 다른 userId → rate limit 회피)
        List<User> k6Users = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            k6Users.add(User.create("k6유저" + i,
                    "010-" + String.format("%04d", i / 100) + "-" + String.format("%04d", i + 1)));
        }
        List<User> savedK6Users = userJpaRepository.saveAll(k6Users);
        long k6BaseUserId = savedK6Users.get(0).getId();

        // k6 유저 UserPoint 일괄 생성 (checkout API 호출 시 필요, CREDIT_CARD 사용이므로 0원)
        List<UserPoint> k6Points = new ArrayList<>();
        for (User k6User : savedK6Users) {
            k6Points.add(UserPoint.create(k6User, 100000L));
        }
        userPointJpaRepository.saveAll(k6Points);

        log.info("[DataInitializer] 초기 데이터 세팅 완료!");
        log.info("=================================================");
        log.info("테스트 계정 - userId: {}", user.getId());
        log.info("roomTypeId: {}", roomType.getId());
        log.info("promotionRoomTypeId: {}", promotionRoomType.getId());
        log.info("k6 유저 범위 - userId: {} ~ {}", k6BaseUserId, k6BaseUserId + 499);
        log.info("=================================================");
    }
}
