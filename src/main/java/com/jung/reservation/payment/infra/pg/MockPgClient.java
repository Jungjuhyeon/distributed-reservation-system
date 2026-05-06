package com.jung.reservation.payment.infra.pg;

import com.jung.reservation.payment.application.exception.PgCardRejectedException;
import com.jung.reservation.payment.application.exception.PgErrorCategory;
import com.jung.reservation.payment.application.exception.PgPaymentException;
import com.jung.reservation.payment.application.outputport.PgClient;
import com.jung.reservation.payment.application.outputport.PgPaymentStatus;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MockPgClient implements PgClient {

    /**
     * Circuit Breaker 적용 (pgCircuitBreaker)
     * 실제 PG 연동 시 PG 응답 코드를 분석해 아래 예외로 변환한다.
     * MockPgClient는 pgTransactionId 문자열로 각 케이스를 시뮬레이션한다.
     * - PgCardRejectedException : 한도초과·잔액부족 등 카드 거절 → PG 자체는 정상이므로 CB 실패 집계 제외
     * - PgPaymentException(TEMPORARY) : PG 일시 오류 → CB 실패 집계 + Retry 1회 허용
     * - PgPaymentException(SYSTEM)    : PG 인증·시스템 오류 → CB 실패 집계, 재시도 금지
     * fallback 없음 → CB OPEN 시 CallNotPermittedException 전파 → BookingInputPort에서 재고 복구 후 503
     */
    @CircuitBreaker(name = "pgCircuitBreaker")
    @Override
    public void confirm(String pgTransactionId, String orderId, Long amount) {
        if (pgTransactionId != null) {
            simulateError(pgTransactionId);
        }
        log.info("[Mock PG] 결제 승인 - pgTransactionId: {}, orderId: {}, amount: {}", pgTransactionId, orderId, amount);
    }

    @CircuitBreaker(name = "pgCircuitBreaker")
    @Override
    public void cancel(String pgTransactionId, String cancelReason) {
        log.info("[Mock PG] 결제 취소 - pgTransactionId: {}, reason: {}", pgTransactionId, cancelReason);
    }

    /**
     * 실제 PG사는 GET /payments/{orderId} 형태의 조회 API를 제공한다.
     * Mock: orderId에 "FAIL_SYSTEM"이 포함된 경우 ABORTED, 그 외는 DONE으로 시뮬레이션
     */
    @Override
    public PgPaymentStatus queryStatus(String orderId) {
        log.info("[Mock PG] 결제 상태 조회 - orderId: {}", orderId);
        if (orderId.contains("FAIL_SYSTEM")) {
            return PgPaymentStatus.ABORTED;
        }
        return PgPaymentStatus.DONE;
    }

    private void simulateError(String pgTransactionId) {
        if (pgTransactionId.contains("FAIL_REJECT")) {
            throw new PgCardRejectedException(
                    "REJECT_CARD_PAYMENT", "한도초과 혹은 잔액부족으로 결제에 실패했습니다.");
        }
        if (pgTransactionId.contains("FAIL_TEMPORARY")) {
            throw new PgPaymentException(PgErrorCategory.TEMPORARY,
                    "PROVIDER_ERROR", "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
        }
        if (pgTransactionId.contains("FAIL_SYSTEM")) {
            throw new PgPaymentException(PgErrorCategory.SYSTEM,
                    "UNKNOWN_PAYMENT_ERROR", "결제에 실패했어요. 같은 문제가 반복된다면 은행이나 카드사로 문의해주세요.");
        }
    }
}
