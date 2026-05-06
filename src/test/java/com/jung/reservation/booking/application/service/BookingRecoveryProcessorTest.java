package com.jung.reservation.booking.application.service;

import com.jung.reservation.accommodation.domain.model.Accommodation;
import com.jung.reservation.accommodation.domain.model.RoomAvailability;
import com.jung.reservation.accommodation.domain.model.RoomType;
import com.jung.reservation.accommodation.infra.persistence.AccommodationJpaRepository;
import com.jung.reservation.accommodation.infra.persistence.RoomAvailabilityJpaRepository;
import com.jung.reservation.accommodation.infra.persistence.RoomTypeJpaRepository;
import com.jung.reservation.booking.domain.model.Booking;
import com.jung.reservation.booking.domain.model.enumeration.BookingStatus;
import com.jung.reservation.booking.infra.persistence.BookingJpaRepository;
import com.jung.reservation.payment.application.outputport.PgPaymentStatus;
import com.jung.reservation.promotion.domain.model.Promotion;
import com.jung.reservation.promotion.domain.model.PromotionRoomType;
import com.jung.reservation.promotion.infra.persistence.PromotionJpaRepository;
import com.jung.reservation.promotion.infra.persistence.PromotionRoomTypeJpaRepository;
import com.jung.reservation.user.domain.model.User;
import com.jung.reservation.user.infra.persistence.UserJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BookingRecoveryProcessorTest {

    @Autowired
    private BookingRecoveryProcessor bookingRecoveryProcessor;

    @Autowired
    private BookingJpaRepository bookingJpaRepository;
    @Autowired
    private UserJpaRepository userJpaRepository;
    @Autowired
    private AccommodationJpaRepository accommodationJpaRepository;
    @Autowired
    private RoomTypeJpaRepository roomTypeJpaRepository;
    @Autowired
    private RoomAvailabilityJpaRepository roomAvailabilityJpaRepository;
    @Autowired
    private PromotionJpaRepository promotionJpaRepository;
    @Autowired
    private PromotionRoomTypeJpaRepository promotionRoomTypeJpaRepository;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private RoomType savedRoomType;
    private PromotionRoomType savedPromotionRoomType;
    private User savedUser;

    @BeforeEach
    void setUp() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();

        User host = userJpaRepository.save(User.create("호스트", "010-0000-0000"));
        Accommodation accommodation = accommodationJpaRepository.save(
                Accommodation.create(host, "제주 호텔", "제주시 중앙로 1"));
        savedRoomType = roomTypeJpaRepository.save(
                RoomType.create(accommodation, "디럭스", 200000L, 2, 5,
                        LocalTime.of(15, 0), LocalTime.of(11, 0)));

        roomAvailabilityJpaRepository.save(
                RoomAvailability.create(savedRoomType, LocalDate.of(2026, 5, 10), 2, 200000L));
        roomAvailabilityJpaRepository.save(
                RoomAvailability.create(savedRoomType, LocalDate.of(2026, 5, 11), 2, 200000L));

        Promotion promotion = promotionJpaRepository.save(
                Promotion.create("5월 초특가",
                        LocalDateTime.now().minusHours(1), LocalDateTime.now().plusDays(30),
                        LocalTime.of(0, 0), LocalTime.of(23, 59)));
        savedPromotionRoomType = promotionRoomTypeJpaRepository.save(
                PromotionRoomType.create(promotion, savedRoomType, 99000L, 10));

        savedUser = userJpaRepository.save(User.create("정주현", "010-1234-5678"));

        redisTemplate.opsForValue().set(
                "stock:promotionRoomType:" + savedPromotionRoomType.getId(), "9");
    }

    private Booking savePendingBooking(String orderId, boolean isPromotion) {
        Booking booking = Booking.create(
                orderId, savedUser, savedRoomType,
                isPromotion ? savedPromotionRoomType : null,
                LocalDate.of(2026, 5, 10), LocalDate.of(2026, 5, 12), 99000L);
        return bookingJpaRepository.save(booking);
    }

    @Test
    @DisplayName("DONE → Booking COMPLETED")
    void recover_done_completed() {
        savePendingBooking("ORD-RECOVER-001", false);

        bookingRecoveryProcessor.recover("ORD-RECOVER-001", PgPaymentStatus.DONE);

        Booking booking = bookingJpaRepository.findByOrderId("ORD-RECOVER-001").orElseThrow();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.COMPLETED);
    }

    @Test
    @DisplayName("ABORTED → Booking FAILED + 재고 복구")
    void recover_aborted_failedAndRestoreStock() {
        savePendingBooking("ORD-RECOVER-002", false);

        bookingRecoveryProcessor.recover("ORD-RECOVER-002", PgPaymentStatus.ABORTED);

        Booking booking = bookingJpaRepository.findByOrderId("ORD-RECOVER-002").orElseThrow();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.FAILED);

        List<RoomAvailability> availabilities = roomAvailabilityJpaRepository
                .findByRoomTypeIdAndDateBetween(savedRoomType.getId(),
                        LocalDate.of(2026, 5, 10), LocalDate.of(2026, 5, 11));
        for (RoomAvailability a : availabilities) {
            assertThat(a.getAvailableCount()).isEqualTo(3); // 2 + 1 복구
        }
    }

    @Test
    @DisplayName("ABORTED + 프로모션 예약 → Redis 재고 복구")
    void recover_aborted_promotion_restoreRedisStock() {
        savePendingBooking("ORD-RECOVER-003", true);

        bookingRecoveryProcessor.recover("ORD-RECOVER-003", PgPaymentStatus.ABORTED);

        Booking booking = bookingJpaRepository.findByOrderId("ORD-RECOVER-003").orElseThrow();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.FAILED);

        Object redisStock = redisTemplate.opsForValue()
                .get("stock:promotionRoomType:" + savedPromotionRoomType.getId());
        assertThat(Integer.parseInt(redisStock.toString())).isEqualTo(10); // 9 + 1 복구
    }

    @Test
    @DisplayName("IN_PROGRESS → 상태 변경 없이 스킵")
    void recover_inProgress_skip() {
        savePendingBooking("ORD-RECOVER-004", false);

        bookingRecoveryProcessor.recover("ORD-RECOVER-004", PgPaymentStatus.IN_PROGRESS);

        Booking booking = bookingJpaRepository.findByOrderId("ORD-RECOVER-004").orElseThrow();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    @DisplayName("이미 COMPLETED인 경우 중복 처리 스킵 (멱등)")
    void recover_alreadyCompleted_skip() {
        Booking booking = savePendingBooking("ORD-RECOVER-005", false);
        booking.complete();

        bookingRecoveryProcessor.recover("ORD-RECOVER-005", PgPaymentStatus.ABORTED);

        // ABORTED가 왔어도 이미 COMPLETED면 상태 변경 안 됨
        Booking found = bookingJpaRepository.findByOrderId("ORD-RECOVER-005").orElseThrow();
        assertThat(found.getStatus()).isEqualTo(BookingStatus.COMPLETED);
    }
}
