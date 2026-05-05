package com.jung.reservation.payment.application.inputport;

import com.jung.reservation.common.exception.BusinessException;
import com.jung.reservation.common.exception.errorcode.CommonErrorCode;
import com.jung.reservation.payment.application.usecase.PaymentProcessor;
import com.jung.reservation.payment.domain.model.enumeration.PaymentType;
import com.jung.reservation.user.application.outputport.UserPointOutputPort;
import com.jung.reservation.user.domain.model.UserPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class YPointPaymentProcessor implements PaymentProcessor {

    private final UserPointOutputPort userPointOutputPort;

    @Override
    public PaymentType getType() {
        return PaymentType.Y_POINT;
    }

    @Override
    @Transactional
    public void pay(Long userId, Long amount, String orderId, String pgTransactionId) {
        UserPoint userPoint = userPointOutputPort.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.USER_POINT_NOT_FOUND));
        if (!userPoint.canDeduct(amount)) {
            throw new BusinessException(CommonErrorCode.INSUFFICIENT_POINT);
        }
        userPoint.deduct(amount);
    }

    @Override
    @Transactional
    public void cancel(Long userId, Long amount, String orderId, String pgTransactionId) {
        UserPoint userPoint = userPointOutputPort.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.USER_POINT_NOT_FOUND));
        userPoint.refund(amount);
    }
}
