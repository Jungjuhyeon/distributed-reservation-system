package com.jung.reservation.payment.application.inputport;

import com.jung.reservation.payment.application.outputport.PgClient;
import com.jung.reservation.payment.application.usecase.PaymentProcessor;
import com.jung.reservation.payment.domain.model.enumeration.PaymentType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class YPayPaymentProcessor implements PaymentProcessor {

    private final PgClient pgClient;

    @Override
    public PaymentType getType() {
        return PaymentType.Y_PAY;
    }

    @Override
    public void pay(Long userId, Long amount, String orderId, String pgTransactionId) {
        pgClient.confirm(pgTransactionId, orderId, amount);
    }

    @Override
    public void cancel(Long userId, Long amount, String orderId, String pgTransactionId) {
        pgClient.cancel(pgTransactionId, "결제 취소");
    }
}
