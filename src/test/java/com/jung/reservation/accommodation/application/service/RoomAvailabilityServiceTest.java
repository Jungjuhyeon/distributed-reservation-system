package com.jung.reservation.accommodation.application.service;

import com.jung.reservation.accommodation.domain.model.Accommodation;
import com.jung.reservation.accommodation.domain.model.RoomAvailability;
import com.jung.reservation.accommodation.domain.model.RoomType;
import com.jung.reservation.accommodation.infra.persistence.AccommodationJpaRepository;
import com.jung.reservation.accommodation.infra.persistence.RoomAvailabilityJpaRepository;
import com.jung.reservation.accommodation.infra.persistence.RoomTypeJpaRepository;
import com.jung.reservation.common.exception.BusinessException;
import com.jung.reservation.user.domain.model.User;
import com.jung.reservation.user.infra.persistence.UserJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RoomAvailabilityServiceTest {

    @Autowired
    private RoomAvailabilityService roomAvailabilityService;

    @Autowired
    private AccommodationJpaRepository accommodationJpaRepository;
    @Autowired
    private RoomTypeJpaRepository roomTypeJpaRepository;
    @Autowired
    private RoomAvailabilityJpaRepository roomAvailabilityJpaRepository;
    @Autowired
    private UserJpaRepository userJpaRepository;

    private RoomType savedRoomType;

    @BeforeEach
    void setUp() {
        User host = userJpaRepository.save(User.create("호스트", "010-0000-0000"));
        Accommodation accommodation = accommodationJpaRepository.save(
                Accommodation.create(host, "제주 호텔", "제주시 중앙로 1"));
        savedRoomType = roomTypeJpaRepository.save(
                RoomType.create(accommodation, "디럭스", 200000L, 2, 5,
                        LocalTime.of(15, 0), LocalTime.of(11, 0)));

        roomAvailabilityJpaRepository.save(
                RoomAvailability.create(savedRoomType, LocalDate.of(2026, 5, 10), 3, 200000L));
        roomAvailabilityJpaRepository.save(
                RoomAvailability.create(savedRoomType, LocalDate.of(2026, 5, 11), 3, 200000L));
    }

    @Test
    @DisplayName("객실 재고 차감 성공")
    void decrease_success() {
        roomAvailabilityService.decrease(savedRoomType.getId(),
                LocalDate.of(2026, 5, 10), LocalDate.of(2026, 5, 12));

        List<RoomAvailability> availabilities = roomAvailabilityJpaRepository
                .findByRoomTypeIdAndDateBetween(savedRoomType.getId(),
                        LocalDate.of(2026, 5, 10), LocalDate.of(2026, 5, 11));

        for (RoomAvailability availability : availabilities) {
            assertThat(availability.getAvailableCount()).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("객실 재고 복구 성공")
    void restore_success() {
        // 먼저 차감
        roomAvailabilityService.decrease(savedRoomType.getId(),
                LocalDate.of(2026, 5, 10), LocalDate.of(2026, 5, 12));

        // 복구
        roomAvailabilityService.restore(savedRoomType.getId(),
                LocalDate.of(2026, 5, 10), LocalDate.of(2026, 5, 12));

        List<RoomAvailability> availabilities = roomAvailabilityJpaRepository
                .findByRoomTypeIdAndDateBetween(savedRoomType.getId(),
                        LocalDate.of(2026, 5, 10), LocalDate.of(2026, 5, 11));

        for (RoomAvailability availability : availabilities) {
            assertThat(availability.getAvailableCount()).isEqualTo(3); // 차감 후 복구 → 원복
        }
    }

    @Test
    @DisplayName("객실 재고 0일 때 ROOM_NOT_AVAILABLE")
    void decrease_noAvailability() {
        List<RoomAvailability> availabilities = roomAvailabilityJpaRepository
                .findByRoomTypeIdAndDateBetween(savedRoomType.getId(),
                        LocalDate.of(2026, 5, 10), LocalDate.of(2026, 5, 11));
        for (RoomAvailability a : availabilities) {
            a.decreaseCount();
            a.decreaseCount();
            a.decreaseCount();
        }

        assertThatThrownBy(() -> roomAvailabilityService.decrease(savedRoomType.getId(),
                LocalDate.of(2026, 5, 10), LocalDate.of(2026, 5, 12)))
                .isInstanceOf(BusinessException.class);
    }
}
