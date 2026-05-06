package com.jung.reservation.payment.application.service;

import com.jung.reservation.accommodation.application.outputport.RoomTypeOutputPort;
import com.jung.reservation.booking.application.outputport.CheckoutCacheOutputPort;
import com.jung.reservation.promotion.application.outputport.PromotionRoomTypeOutputPort;
import com.jung.reservation.booking.framework.web.dto.BookingRequest;
import com.jung.reservation.common.exception.BusinessException;
import com.jung.reservation.common.exception.errorcode.CommonErrorCode;
import com.jung.reservation.payment.domain.model.enumeration.PaymentType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentValidator {

    private final CheckoutCacheOutputPort checkoutCacheOutputPort;
    private final RoomTypeOutputPort roomTypeOutputPort;
    private final PromotionRoomTypeOutputPort promotionRoomTypeOutputPort;

    /**
     * 복합 결제 조합 검증
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
     * - Redis checkout 캐시 우선 조회
     * - Redis 장애(null 반환) 시 DB에서 원본 가격 조회 (Fallback)
     */
    public void validateAmount(String orderId, Long totalAmount, Long roomTypeId, Long promotionRoomTypeId) {
        Long cachedAmount = checkoutCacheOutputPort.getCheckoutAmount(orderId);

        if (cachedAmount != null) {
            if (!cachedAmount.equals(totalAmount)) {
                throw new BusinessException(CommonErrorCode.INVALID_PARAMETER);
            }
            return;
        }

        // Redis 장애 Fallback: DB에서 원본 가격 비교
        log.warn("[Redis Fallback] DB에서 금액 검증 - orderId: {}", orderId);
        Long dbAmount;
        if (promotionRoomTypeId != null) {
            dbAmount = promotionRoomTypeOutputPort.findById(promotionRoomTypeId)
                    .orElseThrow(() -> new BusinessException(CommonErrorCode.PROMOTION_ROOM_TYPE_NOT_FOUND))
                    .getPromotionAmount();
        } else {
            dbAmount = roomTypeOutputPort.findById(roomTypeId)
                    .orElseThrow(() -> new BusinessException(CommonErrorCode.ROOM_TYPE_NOT_FOUND))
                    .getAmount();
        }
        if (!dbAmount.equals(totalAmount)) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER);
        }
    }
}
