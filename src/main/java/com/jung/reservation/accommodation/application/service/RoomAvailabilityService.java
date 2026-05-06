package com.jung.reservation.accommodation.application.service;

import com.jung.reservation.accommodation.application.outputport.RoomAvailabilityOutputPort;
import com.jung.reservation.accommodation.domain.model.RoomAvailability;
import com.jung.reservation.common.exception.BusinessException;
import com.jung.reservation.common.exception.errorcode.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RoomAvailabilityService {

    private final RoomAvailabilityOutputPort roomAvailabilityOutputPort;

    /**
     * room_availability 재고 차감 (비관적 락, 체크인~체크아웃 전날 범위)
     */
    public void decrease(Long roomTypeId, LocalDate checkInDate, LocalDate checkOutDate) {
        List<RoomAvailability> availabilities = roomAvailabilityOutputPort
                .findByRoomTypeIdAndDateRange(roomTypeId, checkInDate, checkOutDate.minusDays(1));
        for (RoomAvailability availability : availabilities) {
            if (!availability.canDecreaseCount()) {
                throw new BusinessException(CommonErrorCode.ROOM_NOT_AVAILABLE);
            }
            availability.decreaseCount();
        }
    }

    /**
     * room_availability 재고 복구 (PENDING 배치 복구 시 사용)
     */
    public void restore(Long roomTypeId, LocalDate checkInDate, LocalDate checkOutDate) {
        List<RoomAvailability> availabilities = roomAvailabilityOutputPort
                .findByRoomTypeIdAndDateRange(roomTypeId, checkInDate, checkOutDate.minusDays(1));
        for (RoomAvailability availability : availabilities) {
            availability.increaseCount();
        }
    }
}
