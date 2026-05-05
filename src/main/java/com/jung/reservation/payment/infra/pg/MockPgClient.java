package com.jung.reservation.payment.infra.pg;

import com.jung.reservation.payment.application.exception.PgErrorCategory;
import com.jung.reservation.payment.application.exception.PgPaymentException;
import com.jung.reservation.payment.application.outputport.PgClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MockPgClient implements PgClient {

    @Override
    public void confirm(String pgTransactionId, String orderId, Long amount) {
        if (pgTransactionId != null) {
            simulateError(pgTransactionId);
        }
        log.info("[Mock PG] 결제 승인 - pgTransactionId: {}, orderId: {}, amount: {}", pgTransactionId, orderId, amount);
    }

    @Override
    public void cancel(String pgTransactionId, String cancelReason) {
        log.info("[Mock PG] 결제 취소 - pgTransactionId: {}, reason: {}", pgTransactionId, cancelReason);
    }

    private void simulateError(String pgTransactionId) {
        if (pgTransactionId.contains("FAIL_REJECT")) {
            throw new PgPaymentException(PgErrorCategory.RETRYABLE,
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
