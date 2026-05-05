package com.jung.reservation.booking.application.inputport;

import com.jung.reservation.accommodation.application.outputport.RoomTypeOutputPort;
import com.jung.reservation.accommodation.domain.model.RoomType;
import com.jung.reservation.booking.application.outputport.CheckoutCacheOutputPort;
import com.jung.reservation.booking.application.usecase.BookingUseCase;
import com.jung.reservation.booking.domain.model.Booking;
import com.jung.reservation.booking.framework.web.dto.BookingRequest;
import com.jung.reservation.booking.framework.web.dto.BookingResponse;
import com.jung.reservation.booking.infra.persistence.BookingJpaRepository;
import com.jung.reservation.common.exception.BusinessException;
import com.jung.reservation.common.exception.errorcode.CommonErrorCode;
import com.jung.reservation.promotion.application.outputport.PromotionRoomTypeOutputPort;
import com.jung.reservation.promotion.application.outputport.StockOutputPort;
import com.jung.reservation.promotion.application.outputport.StockResult;
import com.jung.reservation.promotion.domain.model.PromotionRoomType;
import com.jung.reservation.user.application.outputport.UserOutputPort;
import com.jung.reservation.user.domain.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookingInputPort implements BookingUseCase {

    private final CheckoutCacheOutputPort checkoutCacheOutputPort;
    private final StockOutputPort stockOutputPort;
    private final RoomTypeOutputPort roomTypeOutputPort;
    private final PromotionRoomTypeOutputPort promotionRoomTypeOutputPort;
    private final UserOutputPort userOutputPort;
    private final BookingJpaRepository bookingJpaRepository;

    @Override
    @Transactional
    public BookingResponse book(BookingRequest request) {
        // 1. 사전 금액 검증 (checkout 캐시 vs 요청 금액)
        Long cachedAmount = checkoutCacheOutputPort.getCheckoutAmount(request.getOrderId());
        if (cachedAmount == null || !cachedAmount.equals(request.getTotalAmount())) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER);
        }

        // 2. Lua Script 실행 (시간검증 → Rate Limit → 멱등성 → 재고차감)
        PromotionRoomType promotionRoomType = promotionRoomTypeOutputPort.findById(request.getPromotionRoomTypeId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.PROMOTION_ROOM_TYPE_NOT_FOUND));

        StockResult stockResult = stockOutputPort.decreaseStock(
                request.getUserId(),
                promotionRoomType.getPromotion().getId(),
                request.getPromotionRoomTypeId(),
                request.getOrderId());

        handleStockResult(stockResult);

        try {
            // 3. 결제 처리 (TODO: PaymentProcessor 통합)

            // 4. DB 저장
            RoomType roomType = roomTypeOutputPort.findById(request.getRoomTypeId())
                    .orElseThrow(() -> new BusinessException(CommonErrorCode.ROOM_TYPE_NOT_FOUND));
            User user = userOutputPort.findById(request.getUserId())
                    .orElseThrow(() -> new BusinessException(CommonErrorCode.USER_NOT_FOUND));

            Booking booking = Booking.create(
                    request.getOrderId(), user, roomType, promotionRoomType,
                    request.getCheckInDate(), request.getCheckOutDate(), request.getTotalAmount());
            booking.complete();
            bookingJpaRepository.save(booking);

            // 5. 멱등성 키 COMPLETED로 변경
            stockOutputPort.completeIdempotency(request.getOrderId());

            return BookingResponse.builder()
                    .bookingId(booking.getId())
                    .orderId(booking.getOrderId())
                    .totalAmount(booking.getTotalAmount())
                    .build();

        } catch (Exception e) {
            stockOutputPort.releaseIdempotency(request.getOrderId());
            throw e;
        }
    }

    private void handleStockResult(StockResult result) {
        switch (result) {
            case SUCCESS -> {}
            case NOT_STARTED -> throw new BusinessException(CommonErrorCode.PROMOTION_NOT_STARTED);
            case RATE_LIMITED -> throw new BusinessException(CommonErrorCode.RATE_LIMITED);
            case ALREADY_PROCESSED -> throw new BusinessException(CommonErrorCode.ALREADY_PROCESSED);
            case SOLD_OUT -> throw new BusinessException(CommonErrorCode.SOLD_OUT);
        }
    }
}
