package com.jung.reservation.booking.application.service;

import com.jung.reservation.payment.application.outputport.PgClient;
import com.jung.reservation.payment.application.outputport.PgPaymentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 웹훅 수신 시 비동기 복구 진입점
 * - HTTP 200 반환 후 별도 스레드에서 실행 (PG 재전송 방지)
 * - 실제 복구 로직은 BookingRecoveryProcessor(@Transactional)에 위임
 *   → @Async와 @Transactional을 같은 빈에 두면 self-invocation으로 트랜잭션이 무시되므로 분리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingRecoveryService {

    private final PgClient pgClient;
    private final BookingRecoveryProcessor bookingRecoveryProcessor;

    @Async
    public void recoverAsync(String orderId) {
        try {
            // 웹훅 status를 그대로 믿지 않고 PG 조회 API로 직접 재확인 (위변조 방지)
            PgPaymentStatus pgStatus = pgClient.queryStatus(orderId);
            bookingRecoveryProcessor.recover(orderId, pgStatus);
        } catch (Exception e) {
            log.error("[웹훅 비동기 복구 실패] orderId: {} - {}", orderId, e.getMessage());
        }
    }
}
