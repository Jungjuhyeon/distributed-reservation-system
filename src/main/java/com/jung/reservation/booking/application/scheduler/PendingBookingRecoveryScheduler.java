package com.jung.reservation.booking.application.scheduler;

import com.jung.reservation.booking.application.outputport.BookingOutputPort;
import com.jung.reservation.booking.application.service.BookingRecoveryProcessor;
import com.jung.reservation.booking.domain.model.Booking;
import com.jung.reservation.payment.application.outputport.PgClient;
import com.jung.reservation.payment.application.outputport.PgPaymentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * PENDING 상태의 예약을 주기적으로 스캔해 PG에 직접 결제 결과를 조회한다.
 * - 웹훅이 누락되거나 PG 응답 타임아웃으로 결과가 불분명한 경우의 안전망(safety net)
 * - 5분 이상 PENDING인 예약을 대상으로 PG queryStatus API 호출
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PendingBookingRecoveryScheduler {

    private static final int PENDING_THRESHOLD_MINUTES = 5;

    private final BookingOutputPort bookingOutputPort;
    private final PgClient pgClient;
    private final BookingRecoveryProcessor bookingRecoveryProcessor;

    @Scheduled(fixedDelay = 60_000) // 1분마다 실행
    public void recover() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(PENDING_THRESHOLD_MINUTES);
        List<Booking> pendingBookings = bookingOutputPort.findPendingOlderThan(threshold);

        if (pendingBookings.isEmpty()) {
            return;
        }

        log.info("[PENDING 복구 배치] 대상 건수: {}", pendingBookings.size());

        for (Booking booking : pendingBookings) {
            try {
                PgPaymentStatus pgStatus = pgClient.queryStatus(booking.getOrderId());
                bookingRecoveryProcessor.recover(booking.getOrderId(), pgStatus);
            } catch (Exception e) {
                log.error("[PENDING 복구 실패] orderId: {} - {}", booking.getOrderId(), e.getMessage());
            }
        }
    }
}
