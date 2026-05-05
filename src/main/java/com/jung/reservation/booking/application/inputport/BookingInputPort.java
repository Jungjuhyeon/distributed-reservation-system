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
import com.jung.reservation.payment.application.service.PaymentValidator;
import com.jung.reservation.payment.application.usecase.PaymentProcessor;
import com.jung.reservation.payment.application.usecase.PaymentProcessorRegistry;
import com.jung.reservation.payment.domain.model.Payment;
import com.jung.reservation.payment.domain.model.enumeration.PaymentType;
import com.jung.reservation.payment.infra.persistence.PaymentJpaRepository;
import com.jung.reservation.promotion.application.outputport.PromotionRoomTypeOutputPort;
import com.jung.reservation.promotion.application.outputport.StockOutputPort;
import com.jung.reservation.promotion.application.outputport.StockResult;
import com.jung.reservation.promotion.domain.model.PromotionRoomType;
import com.jung.reservation.user.application.outputport.UserOutputPort;
import com.jung.reservation.user.application.outputport.UserPointOutputPort;
import com.jung.reservation.user.domain.model.PointHistory;
import com.jung.reservation.user.domain.model.User;
import com.jung.reservation.user.domain.model.UserPoint;
import com.jung.reservation.user.domain.model.enumeration.PointHistoryType;
import com.jung.reservation.user.infra.persistence.PointHistoryJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingInputPort implements BookingUseCase {

    private final CheckoutCacheOutputPort checkoutCacheOutputPort;
    private final StockOutputPort stockOutputPort;
    private final RoomTypeOutputPort roomTypeOutputPort;
    private final PromotionRoomTypeOutputPort promotionRoomTypeOutputPort;
    private final UserOutputPort userOutputPort;
    private final BookingJpaRepository bookingJpaRepository;
    private final PaymentJpaRepository paymentJpaRepository;
    private final PaymentProcessorRegistry paymentProcessorRegistry;
    private final PaymentValidator paymentValidator;
    private final UserPointOutputPort userPointOutputPort;
    private final PointHistoryJpaRepository pointHistoryJpaRepository;

    @Override
    @Transactional
    public BookingResponse book(BookingRequest request) {
        // 0. 복합 결제 validation (주결제 수단은 1개만, Y포인트와만 조합 가능)
        paymentValidator.validate(request.getPaymentMethods());

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
            // 3. Booking 저장 (PENDING)
            RoomType roomType = roomTypeOutputPort.findById(request.getRoomTypeId())
                    .orElseThrow(() -> new BusinessException(CommonErrorCode.ROOM_TYPE_NOT_FOUND));
            User user = userOutputPort.findById(request.getUserId())
                    .orElseThrow(() -> new BusinessException(CommonErrorCode.USER_NOT_FOUND));

            Booking booking = Booking.create(
                    request.getOrderId(), user, roomType, promotionRoomType,
                    request.getCheckInDate(), request.getCheckOutDate(), request.getTotalAmount());
            bookingJpaRepository.save(booking);

            // 4. 결제 실행 (부분 실패 시 보상 트랜잭션)
            List<BookingRequest.PaymentMethodRequest> successfulPayments = new java.util.ArrayList<>();
            try {
                for (BookingRequest.PaymentMethodRequest method : request.getPaymentMethods()) {
                    PaymentType paymentType = PaymentType.valueOf(method.getType());
                    PaymentProcessor processor = paymentProcessorRegistry.getProcessor(paymentType);
                    processor.pay(request.getUserId(), method.getAmount(), request.getOrderId(), request.getPgTransactionId());
                    successfulPayments.add(method);
                }
            } catch (Exception paymentException) {
                // 이미 성공한 결제를 역순으로 취소 (개별 try-catch로 끝까지 시도)
                for (int i = successfulPayments.size() - 1; i >= 0; i--) {
                    try {
                        BookingRequest.PaymentMethodRequest paid = successfulPayments.get(i);
                        PaymentType paymentType = PaymentType.valueOf(paid.getType());
                        PaymentProcessor processor = paymentProcessorRegistry.getProcessor(paymentType);
                        processor.cancel(request.getUserId(), paid.getAmount(), request.getOrderId(), request.getPgTransactionId());
                    } catch (Exception cancelException) {
                        // TODO: Phase 6에서 취소 실패 건 DB 저장 + 재처리 구현
                        log.error("[결제 취소 실패] orderId={}, type={}, amount={}",
                                request.getOrderId(), successfulPayments.get(i).getType(),
                                successfulPayments.get(i).getAmount(), cancelException);
                    }
                }
                throw paymentException;
            }

            // 5. Payment 저장 + PointHistory 생성
            for (BookingRequest.PaymentMethodRequest method : request.getPaymentMethods()) {
                PaymentType paymentType = PaymentType.valueOf(method.getType());

                Payment payment = Payment.create(booking, paymentType, method.getAmount());
                if (request.getPgTransactionId() != null) {
                    payment.success(request.getPgTransactionId());
                }
                paymentJpaRepository.save(payment);

                // 포인트 사용 시 이력 저장
                if (paymentType == PaymentType.Y_POINT) {
                    UserPoint userPoint = userPointOutputPort.findByUserId(request.getUserId()).orElseThrow();
                    pointHistoryJpaRepository.save(
                            PointHistory.create(userPoint, method.getAmount(), booking, PointHistoryType.USE, "프로모션 예약 포인트 사용"));
                }
            }

            // 6. Booking → COMPLETED
            booking.complete();

            // 7. 멱등성 키 COMPLETED로 변경
            stockOutputPort.completeIdempotency(request.getOrderId());

            return BookingResponse.builder()
                    .bookingId(booking.getId())
                    .orderId(booking.getOrderId())
                    .totalAmount(booking.getTotalAmount())
                    .build();

        } catch (Exception e) {
            stockOutputPort.restoreStock(request.getPromotionRoomTypeId());
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
