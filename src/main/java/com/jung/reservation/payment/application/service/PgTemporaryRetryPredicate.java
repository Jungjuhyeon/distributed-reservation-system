package com.jung.reservation.payment.application.service;

import com.jung.reservation.payment.application.exception.PgErrorCategory;
import com.jung.reservation.payment.application.exception.PgPaymentException;

import java.util.function.Predicate;

/**
 * PG Retry 대상 판별: TEMPORARY(일시적 PG 오류)만 재시도
 * - RETRYABLE(한도초과 등): 재시도해도 동일하게 실패 → 재시도 금지
 * - SYSTEM(인증 오류 등): 재시도로 해결 불가 → 재시도 금지
 */
public class PgTemporaryRetryPredicate implements Predicate<Throwable> {

    @Override
    public boolean test(Throwable throwable) {
        if (throwable instanceof PgPaymentException pgEx) {
            return pgEx.getCategory() == PgErrorCategory.TEMPORARY;
        }
        return false;
    }
}
