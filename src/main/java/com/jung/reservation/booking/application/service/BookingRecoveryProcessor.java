package com.jung.reservation.booking.application.service;

import com.jung.reservation.accommodation.application.service.RoomAvailabilityService;
import com.jung.reservation.booking.application.outputport.BookingOutputPort;
import com.jung.reservation.booking.domain.model.Booking;
import com.jung.reservation.booking.domain.model.enumeration.BookingStatus;
import com.jung.reservation.common.exception.BusinessException;
import com.jung.reservation.common.exception.errorcode.CommonErrorCode;
import com.jung.reservation.payment.application.outputport.PgPaymentStatus;
import com.jung.reservation.promotion.application.service.PromotionStockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PENDING 예약 복구 실행 서비스 (@Transactional)
 * BookingRecoveryService(@Async)와 분리된 별도 빈으로 선언하여
 * Spring AOP 프록시를 통한 트랜잭션이 정상 적용되도록 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingRecoveryProcessor {

    private final BookingOutputPort bookingOutputPort;
    private final RoomAvailabilityService roomAvailabilityService;
    private final PromotionStockService promotionStockService;

    /**
     * PG 조회 결과를 바탕으로 PENDING 예약 상태를 복구한다.
     * - 멱등 처리: PENDING 상태가 아니면 이미 처리된 것으로 스킵
     */
    @Transactional
    public void recover(String orderId, PgPaymentStatus pgStatus) {
        Booking booking = bookingOutputPort.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.BOOKING_NOT_FOUND));

        // 멱등 처리 — 이미 COMPLETED/FAILED이면 중복 웹훅이므로 스킵
        if (booking.getStatus() != BookingStatus.PENDING) {
            log.info("[예약 복구] 이미 처리됨 스킵 - orderId: {}, status: {}", orderId, booking.getStatus());
            return;
        }

        log.info("[예약 복구] orderId: {}, pgStatus: {}", orderId, pgStatus);

        switch (pgStatus) {
            case DONE -> {
                booking.complete();
                log.info("[예약 복구] COMPLETED - orderId: {}", orderId);
            }
            case ABORTED, CANCELED, PARTIAL_CANCELED -> {
                booking.fail();
                roomAvailabilityService.restore(
                        booking.getRoomType().getId(),
                        booking.getCheckInDate(),
                        booking.getCheckOutDate()
                );
                if (booking.getPromotionRoomType() != null) {
                    promotionStockService.rollbackRedisResources(
                            booking.getPromotionRoomType().getId(),
                            orderId
                    );
                }
                log.info("[예약 복구] FAILED + 재고 복구 - orderId: {}", orderId);
            }
            case IN_PROGRESS ->
                log.info("[예약 복구] IN_PROGRESS 스킵 - orderId: {}", orderId);
        }
    }
}
