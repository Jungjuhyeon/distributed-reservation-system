package com.jung.reservation.payment.application.service;

import com.jung.reservation.booking.application.outputport.CheckoutCacheOutputPort;
import com.jung.reservation.booking.framework.web.dto.BookingRequest;
import com.jung.reservation.common.exception.BusinessException;
import com.jung.reservation.common.exception.errorcode.CommonErrorCode;
import com.jung.reservation.payment.domain.model.enumeration.PaymentType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PaymentValidator {

    private final CheckoutCacheOutputPort checkoutCacheOutputPort;

    /**
     * 복합 결제 조합 검증
     * - 주결제 수단(Y_POINT 제외)은 1개만 허용
     * - Y포인트 단독 또는 주결제 + Y포인트 조합만 가능
     */
    public void validatePaymentCombination(List<BookingRequest.PaymentMethodRequest> methods) {
        long primaryCount = methods.stream()
                .filter(m -> !PaymentType.Y_POINT.name().equals(m.getType()))
                .count();

        if (primaryCount > 1) {
            throw new BusinessException(CommonErrorCode.INVALID_PAYMENT_COMBINATION);
        }
    }

    /**
     * 결제 금액 위변조 검증
     * - checkout 캐시의 금액과 요청 금액이 일치하는지 비교
     */
    public void validateAmount(String orderId, Long totalAmount) {
        Long cachedAmount = checkoutCacheOutputPort.getCheckoutAmount(orderId);
        if (cachedAmount == null || !cachedAmount.equals(totalAmount)) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER);
        }
    }
}
