package com.jung.reservation.booking.application.inputport;

import com.jung.reservation.accommodation.domain.model.Accommodation;
import com.jung.reservation.accommodation.domain.model.RoomType;
import com.jung.reservation.accommodation.infra.persistence.AccommodationJpaRepository;
import com.jung.reservation.accommodation.infra.persistence.RoomTypeJpaRepository;
import com.jung.reservation.booking.application.usecase.BookingUseCase;
import com.jung.reservation.booking.domain.model.Booking;
import com.jung.reservation.booking.framework.web.dto.BookingRequest;
import com.jung.reservation.booking.framework.web.dto.BookingResponse;
import com.jung.reservation.booking.infra.persistence.BookingJpaRepository;
import com.jung.reservation.common.exception.BusinessException;
import com.jung.reservation.payment.domain.model.Payment;
import com.jung.reservation.payment.infra.persistence.PaymentJpaRepository;
import com.jung.reservation.promotion.domain.model.Promotion;
import com.jung.reservation.promotion.domain.model.PromotionRoomType;
import com.jung.reservation.promotion.infra.persistence.PromotionJpaRepository;
import com.jung.reservation.promotion.infra.persistence.PromotionRoomTypeJpaRepository;
import com.jung.reservation.user.domain.model.PointHistory;
import com.jung.reservation.user.domain.model.User;
import com.jung.reservation.user.domain.model.UserPoint;
import com.jung.reservation.user.infra.persistence.PointHistoryJpaRepository;
import com.jung.reservation.user.infra.persistence.UserJpaRepository;
import com.jung.reservation.user.infra.persistence.UserPointJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BookingInputPortTest {

    @Autowired
    private BookingUseCase bookingUseCase;

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
    @Autowired
    private UserPointJpaRepository userPointJpaRepository;
    @Autowired
    private BookingJpaRepository bookingJpaRepository;
    @Autowired
    private PaymentJpaRepository paymentJpaRepository;
    @Autowired
    private PointHistoryJpaRepository pointHistoryJpaRepository;

    private RoomType savedRoomType;
    private PromotionRoomType savedPromotionRoomType;
    private Promotion savedPromotion;
    private User savedUser;

    @BeforeEach
    void setUp() {
        // Redis 초기화
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();

        // 테스트 데이터
        User host = userJpaRepository.save(User.create("호스트", "010-0000-0000"));
        Accommodation accommodation = accommodationJpaRepository.save(
                Accommodation.create(host, "제주 호텔", "제주시 중앙로 1"));

        savedRoomType = roomTypeJpaRepository.save(
                RoomType.create(accommodation, "디럭스 더블", 200000L, 2, 5,
                        LocalTime.of(15, 0), LocalTime.of(11, 0)));

        savedPromotion = promotionJpaRepository.save(
                Promotion.create("5월 초특가", LocalDateTime.of(2026, 5, 1, 0, 0),
                        LocalDateTime.of(2026, 5, 31, 23, 59),
                        LocalTime.of(0, 0), LocalTime.of(1, 0)));

        savedPromotionRoomType = promotionRoomTypeJpaRepository.save(
                PromotionRoomType.create(savedPromotion, savedRoomType, 99000L, 10));

        savedUser = userJpaRepository.save(User.create("정주현", "010-1234-5678"));
        userPointJpaRepository.save(UserPoint.create(savedUser, 100000L));

        // Redis 세팅
        redisTemplate.opsForValue().set("stock:promotionRoomType:" + savedPromotionRoomType.getId(), "10");
        redisTemplate.opsForValue().set("sale_start:promotion:" + savedPromotion.getId(),
                String.valueOf(Instant.now().getEpochSecond() - 60));
    }

    @Test
    @DisplayName("포인트 단건 결제 성공 - Booking + Payment + PointHistory 저장, 포인트 차감")
    void book_pointPayment_success() {
        String orderId = "ORD-20260505-TEST-001";
        redisTemplate.opsForValue().set("checkout:" + orderId, "99000");

        BookingRequest request = new BookingRequest(
                orderId, savedUser.getId(), savedRoomType.getId(), savedPromotionRoomType.getId(),
                99000L, LocalDate.of(2026, 5, 10), LocalDate.of(2026, 5, 12), null,
                List.of(new BookingRequest.PaymentMethodRequest("Y_POINT", 99000L)));

        BookingResponse response = bookingUseCase.book(request);

        // 응답 확인
        assertThat(response.getBookingId()).isNotNull();
        assertThat(response.getOrderId()).isEqualTo(orderId);
        assertThat(response.getTotalAmount()).isEqualTo(99000L);

        // Booking DB 확인
        Booking booking = bookingJpaRepository.findByOrderId(orderId).orElseThrow();
        assertThat(booking.getStatus().name()).isEqualTo("COMPLETED");

        // Payment DB 확인
        List<Payment> payments = paymentJpaRepository.findAll();
        assertThat(payments).hasSize(1);
        assertThat(payments.get(0).getAmount()).isEqualTo(99000L);
        assertThat(payments.get(0).getPaymentType().name()).isEqualTo("Y_POINT");

        // PointHistory 확인
        List<PointHistory> histories = pointHistoryJpaRepository.findAll();
        assertThat(histories).hasSize(1);
        assertThat(histories.get(0).getAmount()).isEqualTo(99000L);
        assertThat(histories.get(0).getType().name()).isEqualTo("USE");

        // 포인트 차감 확인
        UserPoint userPoint = userPointJpaRepository.findByUserId(savedUser.getId()).orElseThrow();
        assertThat(userPoint.getCurrentPoint()).isEqualTo(1000L);
    }

    @Test
    @DisplayName("금액 불일치 시 실패")
    void book_amountMismatch_fail() {
        String orderId = "ORD-20260505-TEST-002";
        redisTemplate.opsForValue().set("checkout:" + orderId, "99000");

        BookingRequest request = new BookingRequest(
                orderId, savedUser.getId(), savedRoomType.getId(), savedPromotionRoomType.getId(),
                50000L, LocalDate.of(2026, 5, 10), LocalDate.of(2026, 5, 12), null,
                List.of(new BookingRequest.PaymentMethodRequest("Y_POINT", 50000L)));

        assertThatThrownBy(() -> bookingUseCase.book(request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("포인트 부족 시 실패")
    void book_insufficientPoint_fail() {
        String orderId = "ORD-20260505-TEST-003";
        redisTemplate.opsForValue().set("checkout:" + orderId, "200000");

        BookingRequest request = new BookingRequest(
                orderId, savedUser.getId(), savedRoomType.getId(), savedPromotionRoomType.getId(),
                200000L, LocalDate.of(2026, 5, 10), LocalDate.of(2026, 5, 12), null,
                List.of(new BookingRequest.PaymentMethodRequest("Y_POINT", 200000L)));

        assertThatThrownBy(() -> bookingUseCase.book(request))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("신용카드 + 포인트 복합 결제 성공")
    void book_creditCardAndPoint_success() {
        String orderId = "ORD-20260505-TEST-004";
        redisTemplate.opsForValue().set("checkout:" + orderId, "99000");

        BookingRequest request = new BookingRequest(
                orderId, savedUser.getId(), savedRoomType.getId(), savedPromotionRoomType.getId(),
                99000L, LocalDate.of(2026, 5, 10), LocalDate.of(2026, 5, 12), "pg-txn-12345",
                List.of(
                        new BookingRequest.PaymentMethodRequest("CREDIT_CARD", 79000L),
                        new BookingRequest.PaymentMethodRequest("Y_POINT", 20000L)
                ));

        BookingResponse response = bookingUseCase.book(request);

        assertThat(response.getBookingId()).isNotNull();
        assertThat(response.getTotalAmount()).isEqualTo(99000L);

        // Payment 2건 확인
        List<Payment> payments = paymentJpaRepository.findAll();
        assertThat(payments).hasSize(2);

        // 포인트 차감 확인 (100000 - 20000 = 80000)
        UserPoint userPoint = userPointJpaRepository.findByUserId(savedUser.getId()).orElseThrow();
        assertThat(userPoint.getCurrentPoint()).isEqualTo(80000L);

        // PointHistory 확인
        List<PointHistory> histories = pointHistoryJpaRepository.findAll();
        assertThat(histories).hasSize(1);
        assertThat(histories.get(0).getAmount()).isEqualTo(20000L);
    }

    @Test
    @DisplayName("복합 결제 부분 실패 - 카드 성공 후 포인트 부족으로 롤백")
    void book_partialFailure_rollback() {
        String orderId = "ORD-20260505-TEST-005";
        redisTemplate.opsForValue().set("checkout:" + orderId, "99000");

        // 카드(79000) 성공 → 포인트(120000) 실패 (잔액 100000) → 카드 cancel 호출
        BookingRequest request = new BookingRequest(
                orderId, savedUser.getId(), savedRoomType.getId(), savedPromotionRoomType.getId(),
                99000L, LocalDate.of(2026, 5, 10), LocalDate.of(2026, 5, 12), "pg-txn-fail",
                List.of(
                        new BookingRequest.PaymentMethodRequest("CREDIT_CARD", 79000L),
                        new BookingRequest.PaymentMethodRequest("Y_POINT", 120000L)
                ));

        // TODO: 보상 트랜잭션 제대로 구현할 때 예외를 구체적으로 검증 예정
        assertThatThrownBy(() -> bookingUseCase.book(request))
                .isInstanceOf(Exception.class);

        // 포인트 차감 안 됨 확인
        UserPoint userPoint = userPointJpaRepository.findByUserId(savedUser.getId()).orElseThrow();
        assertThat(userPoint.getCurrentPoint()).isEqualTo(100000L);
    }
}
