package com.jung.reservation.payment.application.service;

import com.jung.reservation.booking.framework.web.dto.BookingRequest;
import com.jung.reservation.common.exception.BusinessException;
import com.jung.reservation.common.exception.errorcode.CommonErrorCode;
import com.jung.reservation.payment.domain.model.enumeration.PaymentType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PaymentValidator {

    /**
     * 복합 결제 조합 검증
     * - 주결제 수단(Y_POINT 제외)은 1개만 허용
     * - Y포인트 단독 또는 주결제 + Y포인트 조합만 가능
     */
    public void validate(List<BookingRequest.PaymentMethodRequest> methods) {
        long primaryCount = methods.stream()
                .filter(m -> !PaymentType.Y_POINT.name().equals(m.getType()))
                .count();

        if (primaryCount > 1) {
            throw new BusinessException(CommonErrorCode.INVALID_PAYMENT_COMBINATION);
        }
    }
}
