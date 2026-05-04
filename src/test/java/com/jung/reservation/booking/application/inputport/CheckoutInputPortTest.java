package com.jung.reservation.booking.application.inputport;

import com.jung.reservation.accommodation.domain.model.Accommodation;
import com.jung.reservation.accommodation.domain.model.RoomType;
import com.jung.reservation.accommodation.infra.persistence.AccommodationJpaRepository;
import com.jung.reservation.accommodation.infra.persistence.RoomTypeJpaRepository;
import com.jung.reservation.booking.application.usecase.CheckoutUseCase;
import com.jung.reservation.booking.framework.web.dto.CheckoutResponse;
import com.jung.reservation.common.exception.BusinessException;
import com.jung.reservation.promotion.domain.model.Promotion;
import com.jung.reservation.promotion.domain.model.PromotionRoomType;
import com.jung.reservation.promotion.infra.persistence.PromotionJpaRepository;
import com.jung.reservation.promotion.infra.persistence.PromotionRoomTypeJpaRepository;
import com.jung.reservation.user.domain.model.User;
import com.jung.reservation.user.domain.model.UserPoint;
import com.jung.reservation.user.infra.persistence.UserJpaRepository;
import com.jung.reservation.user.infra.persistence.UserPointJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CheckoutInputPortTest {

    @Autowired
    private CheckoutUseCase checkoutUseCase;

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

    private RoomType savedRoomType;
    private PromotionRoomType savedPromotionRoomType;
    private User savedUser;
    private UserPoint savedUserPoint;

    @BeforeEach
    void setUp() {
        User host = userJpaRepository.save(User.create("호스트", "010-0000-0000"));
        Accommodation accommodation = accommodationJpaRepository.save(
                Accommodation.create(host, "제주 호텔", "제주시 중앙로 1"));

        savedRoomType = roomTypeJpaRepository.save(
                RoomType.create(accommodation, "디럭스 더블", 200000L, 2, 5,
                        LocalTime.of(15, 0), LocalTime.of(11, 0)));

        Promotion promotion = promotionJpaRepository.save(
                Promotion.create("5월 초특가", LocalDateTime.of(2026, 5, 5, 0, 0),
                        LocalDateTime.of(2026, 5, 31, 23, 59),
                        LocalTime.of(0, 0), LocalTime.of(1, 0)));

        savedPromotionRoomType = promotionRoomTypeJpaRepository.save(
                PromotionRoomType.create(promotion, savedRoomType, 99000L, 10));

        savedUser = userJpaRepository.save(User.create("정주현", "010-1234-5678"));
        savedUserPoint = userPointJpaRepository.save(UserPoint.create(savedUser, 50000L));
    }

    @Test
    @DisplayName("프로모션 주문서 진입 - 상품/포인트/orderId 반환 확인")
    void checkout_withPromotion() {
        CheckoutResponse response = checkoutUseCase.checkout(
                savedRoomType.getId(), savedUser.getId(), savedPromotionRoomType.getId());

        assertThat(response.getOrderId()).startsWith("ORD-");
        assertThat(response.getAccommodationName()).isEqualTo("제주 호텔");
        assertThat(response.getRoomTypeName()).isEqualTo("디럭스 더블");
        assertThat(response.getOriginalAmount()).isEqualTo(200000L);
        assertThat(response.getPromotionAmount()).isEqualTo(99000L);
        assertThat(response.getTotalAmount()).isEqualTo(99000L);
        assertThat(response.getCheckInTime()).isEqualTo(LocalTime.of(15, 0));
        assertThat(response.getCheckOutTime()).isEqualTo(LocalTime.of(11, 0));
        assertThat(response.getUserName()).isEqualTo("정주현");
        assertThat(response.getPhone()).isEqualTo("010-1234-5678");
        assertThat(response.getAvailablePoint()).isEqualTo(50000L);
    }

    @Test
    @DisplayName("일반 주문서 진입 - 프로모션 없이 원가 반환")
    void checkout_withoutPromotion() {
        CheckoutResponse response = checkoutUseCase.checkout(
                savedRoomType.getId(), savedUser.getId(), null);

        assertThat(response.getOrderId()).startsWith("ORD-");
        assertThat(response.getPromotionAmount()).isNull();
        assertThat(response.getTotalAmount()).isEqualTo(200000L);
    }

    @Test
    @DisplayName("존재하지 않는 객실 타입 - 예외 발생")
    void checkout_roomTypeNotFound() {
        assertThatThrownBy(() -> checkoutUseCase.checkout(999L, savedUser.getId(), null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("존재하지 않는 사용자 - 예외 발생")
    void checkout_userNotFound() {
        assertThatThrownBy(() -> checkoutUseCase.checkout(savedRoomType.getId(), 999L, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("존재하지 않는 프로모션 상품 - 예외 발생")
    void checkout_promotionNotFound() {
        assertThatThrownBy(() -> checkoutUseCase.checkout(savedRoomType.getId(), savedUser.getId(), 999L))
                .isInstanceOf(BusinessException.class);
    }
}
