package com.jung.reservation.payment.application.inputport;

import com.jung.reservation.payment.application.outputport.PgClient;
import com.jung.reservation.payment.application.usecase.PaymentProcessor;
import com.jung.reservation.payment.domain.model.enumeration.PaymentType;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CreditCardPaymentProcessor implements PaymentProcessor {

    private final PgClient pgClient;

    @Override
    public PaymentType getType() {
        return PaymentType.CREDIT_CARD;
    }

    /**
     * Retry(pgRetry): TEMPORARY 오류 시 1회 재시도 (PgTemporaryRetryPredicate)
     * orderId를 PG 멱등성 키로 전달 → 재시도 시 PG가 중복 청구 방지
     */
    @Retry(name = "pgRetry")
    @Override
    public void pay(Long userId, Long amount, String orderId, String pgTransactionId) {
        pgClient.confirm(pgTransactionId, orderId, amount);
    }

    @Override
    public void cancel(Long userId, Long amount, String orderId, String pgTransactionId) {
        pgClient.cancel(pgTransactionId, "결제 취소");
    }
}
