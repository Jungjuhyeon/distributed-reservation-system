package com.jung.reservation.payment.infra.pg;

import com.jung.reservation.payment.application.outputport.PgClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MockPgClient implements PgClient {

    @Override
    public void confirm(String pgTransactionId, String orderId, Long amount) {
        log.info("[Mock PG] 결제 승인 - pgTransactionId: {}, orderId: {}, amount: {}", pgTransactionId, orderId, amount);
    }

    @Override
    public void cancel(String pgTransactionId, String cancelReason) {
        log.info("[Mock PG] 결제 취소 - pgTransactionId: {}, reason: {}", pgTransactionId, cancelReason);
    }
}
