package com.jung.reservation.payment.framework.web;

import com.jung.reservation.booking.application.service.BookingRecoveryService;
import com.jung.reservation.payment.application.outputport.PgClient;
import com.jung.reservation.payment.application.outputport.PgPaymentStatus;
import com.jung.reservation.payment.framework.web.dto.PgWebhookRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PG사 웹훅 수신 엔드포인트
 * 실제 PG 연동 시 PG사가 결제 결과를 이 API로 푸시한다.
 *
 * [웹훅 처리 원칙]
 * 1. PG 조회 API로 금액·상태 재확인 — 웹훅 데이터만 믿으면 위변조 위험
 * 2. 멱등 처리 — 웹훅은 여러 번 올 수 있으므로 이미 처리된 예약은 스킵 (BookingRecoveryService)
 * 3. HTTP 200 빠르게 반환 — 실제 처리는 @Async 비동기, 느리면 PG가 재전송
 *
 * 웹훅 누락 시 PendingBookingRecoveryScheduler가 폴링으로 보완
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/pg")
@RequiredArgsConstructor
public class PgWebhookController {

    private final PgClient pgClient;
    private final BookingRecoveryService bookingRecoveryService;

    @PostMapping("/webhook")
    public ResponseEntity<Void> receiveWebhook(@Valid @RequestBody PgWebhookRequest request) {
        log.info("[PG 웹훅 수신] orderId: {}, paymentKey: {}", request.getOrderId(), request.getPaymentKey());

        // 3. 200 먼저 반환 후 비동기 처리 (PG 재전송 방지)
        bookingRecoveryService.recoverAsync(request.getOrderId());

        return ResponseEntity.ok().build();
    }
}
