package com.jung.reservation.payment.application.exception;

import com.jung.reservation.common.exception.errorcode.CommonErrorCode;
import lombok.Getter;

/**
 * PG 결제 결과가 불분명한 경우 (타임아웃, 시스템 오류 등)
 *
 * @Transactional(noRollbackFor = PgUncertainException.class) 와 함께 사용된다.
 * 이 예외가 던져지면 트랜잭션이 롤백되지 않고 커밋된다.
 * → PENDING Booking이 DB에 유지 → 배치/웹훅으로 PG 조회 후 정합성 복구
 */
@Getter
public class PgUncertainException extends RuntimeException {

    private final CommonErrorCode errorCode;

    public PgUncertainException(CommonErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
