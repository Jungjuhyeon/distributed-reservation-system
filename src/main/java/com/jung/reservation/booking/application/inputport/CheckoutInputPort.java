package com.jung.reservation.booking.application.inputport;

import com.jung.reservation.accommodation.application.outputport.RoomTypeOutputPort;
import com.jung.reservation.booking.application.outputport.CheckoutCacheOutputPort;
import com.jung.reservation.accommodation.domain.model.RoomType;
import com.jung.reservation.booking.application.usecase.CheckoutUseCase;
import com.jung.reservation.booking.framework.web.dto.CheckoutResponse;
import com.jung.reservation.common.exception.BusinessException;
import com.jung.reservation.common.exception.errorcode.CommonErrorCode;
import com.jung.reservation.promotion.application.outputport.PromotionRoomTypeOutputPort;
import com.jung.reservation.promotion.domain.model.PromotionRoomType;
import com.jung.reservation.user.application.outputport.UserOutputPort;
import com.jung.reservation.user.application.outputport.UserPointOutputPort;
import com.jung.reservation.user.domain.model.User;
import com.jung.reservation.user.domain.model.UserPoint;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CheckoutInputPort implements CheckoutUseCase {

    private final RoomTypeOutputPort roomTypeOutputPort;
    private final PromotionRoomTypeOutputPort promotionRoomTypeOutputPort;
    private final UserOutputPort userOutputPort;
    private final UserPointOutputPort userPointOutputPort;
    private final CheckoutCacheOutputPort checkoutCacheOutputPort;

    @Override
    public CheckoutResponse checkout(Long roomTypeId, Long userId, Long promotionRoomTypeId) {
        // 1. RoomType 조회
        RoomType roomType = roomTypeOutputPort.findById(roomTypeId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.ROOM_TYPE_NOT_FOUND));

        // 2. User + UserPoint 조회
        User user = userOutputPort.findById(userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.USER_NOT_FOUND));

        UserPoint userPoint = userPointOutputPort.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.USER_POINT_NOT_FOUND));

        // 3. 프로모션 정보 조회 (optional)
        Long promotionAmount = Optional.ofNullable(promotionRoomTypeId)
                .map(id -> promotionRoomTypeOutputPort.findById(id)
                        .orElseThrow(() -> new BusinessException(CommonErrorCode.PROMOTION_ROOM_TYPE_NOT_FOUND))
                        .getPromotionAmount())
                .orElse(null);

        // 4. 총 금액 계산 (프로모션이면 프로모션가, 아니면 원가)
        Long totalAmount = promotionAmount != null ? promotionAmount : roomType.getAmount();

        // 5. orderId 생성
        String orderId = "ORD-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-" + UUID.randomUUID();

        // 6. Redis 주문서 캐시 저장 (결제 금액 위변조 방지)
        checkoutCacheOutputPort.saveCheckoutCache(orderId, totalAmount);

        // 7. 응답 생성
        return CheckoutResponse.mapToDTO(orderId, roomType, promotionRoomTypeId,
                promotionAmount, totalAmount, user, userPoint);
    }
}
