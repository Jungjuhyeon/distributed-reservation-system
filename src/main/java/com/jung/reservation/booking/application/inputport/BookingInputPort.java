package com.jung.reservation.booking.application.inputport;

import com.jung.reservation.accommodation.application.outputport.RoomTypeOutputPort;
import com.jung.reservation.accommodation.application.service.RoomAvailabilityService;
import com.jung.reservation.accommodation.domain.model.RoomType;
import com.jung.reservation.booking.application.outputport.BookingOutputPort;
import com.jung.reservation.booking.application.outputport.IdempotencyOutputPort;
import com.jung.reservation.booking.application.outputport.RateLimitOutputPort;
import com.jung.reservation.booking.application.usecase.BookingUseCase;
import com.jung.reservation.booking.domain.model.Booking;
import com.jung.reservation.booking.framework.web.dto.BookingRequest;
import com.jung.reservation.booking.framework.web.dto.BookingResponse;
import com.jung.reservation.common.exception.BusinessException;
import com.jung.reservation.common.exception.errorcode.CommonErrorCode;
import com.jung.reservation.payment.application.exception.PgErrorCategory;
import com.jung.reservation.payment.application.exception.PgPaymentException;
import com.jung.reservation.payment.application.exception.PgUncertainException;
import com.jung.reservation.payment.application.service.PaymentExecutionService;
import com.jung.reservation.payment.application.service.PaymentValidator;
import com.jung.reservation.promotion.application.outputport.PromotionRoomTypeOutputPort;
import com.jung.reservation.promotion.application.service.PromotionStockService;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import com.jung.reservation.promotion.domain.model.PromotionRoomType;
import com.jung.reservation.user.application.outputport.UserOutputPort;
import com.jung.reservation.user.domain.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingInputPort implements BookingUseCase {

    private final PromotionStockService promotionStockService;
    private final RoomAvailabilityService roomAvailabilityService;
    private final PaymentExecutionService paymentExecutionService;
    private final PaymentValidator paymentValidator;
    private final RateLimitOutputPort rateLimitOutputPort;
    private final IdempotencyOutputPort idempotencyOutputPort;
    private final RoomTypeOutputPort roomTypeOutputPort;
    private final PromotionRoomTypeOutputPort promotionRoomTypeOutputPort;
    private final UserOutputPort userOutputPort;
    private final BookingOutputPort bookingOutputPort;

    @Override
    @Transactional(noRollbackFor = PgUncertainException.class)
    public BookingResponse book(BookingRequest request) {
        // 0. 복합 결제 validation
        paymentValidator.validatePaymentCombination(request.getPaymentMethods());

        // 1. 프로모션 / 일반 분기
        return request.getPromotionRoomTypeId() != null ? bookPromotion(request) : bookNormal(request);
    }

    private BookingResponse bookPromotion(BookingRequest request) {
        // 2-1. 금액 검증 (Redis 캐시 우선, 실패 시 DB Fallback)
        paymentValidator.validateAmount(request.getOrderId(), request.getTotalAmount(), request.getRoomTypeId(), request.getPromotionRoomTypeId());

        // 2-2. 재고 선점 (Redis Lua Script 우선, 장애 시 Bulkhead + DB Fallback)
        boolean usedRedis = promotionStockService.reserve(request.getUserId(), request.getPromotionRoomTypeId(), request.getOrderId());

        try {
            // 3. DB 락 먼저 획득 (데드락 방지: INSERT 전에 락 순서 확정)
            if (usedRedis) {
                promotionStockService.decreaseDbStock(request.getPromotionRoomTypeId());
            }
            roomAvailabilityService.decrease(request.getRoomTypeId(), request.getCheckInDate(), request.getCheckOutDate());

            // 3-1. 락 획득 후 Booking 저장 (PENDING)
            Booking booking = createAndSaveBooking(request);

            // 4. 결제 실행
            paymentExecutionService.execute(request);

            // 5. Payment/PointHistory 저장
            paymentExecutionService.savePaymentsAndPointHistory(request, booking);

            // 6. Booking → COMPLETED
            booking.complete();

            // 7. 멱등성 키 COMPLETED
            promotionStockService.completeIdempotency(request.getOrderId());

            return BookingResponse.mapToDTO(booking);

        } catch (BusinessException e) {
            log.warn("[결제 비즈니스 실패] 재고 즉시 복구 - orderId: {}, code: {}", request.getOrderId(), e.getErrorCode());
            if (usedRedis) {
                promotionStockService.rollbackRedisResources(request.getPromotionRoomTypeId(), request.getOrderId());
            }
            throw e;
        } catch (PgPaymentException e) {
            if (e.getCategory() == PgErrorCategory.RETRYABLE) {
                log.warn("[PG 결제 거절] 재고 복구 - orderId: {}, pgCode: {}", request.getOrderId(), e.getPgErrorCode());
                if (usedRedis) {
                    promotionStockService.rollbackRedisResources(request.getPromotionRoomTypeId(), request.getOrderId());
                }
                throw new BusinessException(CommonErrorCode.PAYMENT_REJECTED);
            } else if (e.getCategory() == PgErrorCategory.TEMPORARY) {
                // Retry 소진 후 TEMPORARY → 재고 복구 (재시도해도 실패 확정)
                log.warn("[PG 일시 오류] Retry 소진, 재고 복구 - orderId: {}, pgCode: {}", request.getOrderId(), e.getPgErrorCode());
                if (usedRedis) {
                    promotionStockService.rollbackRedisResources(request.getPromotionRoomTypeId(), request.getOrderId());
                }
                throw new BusinessException(CommonErrorCode.PAYMENT_TEMPORARY_ERROR);
            } else {
                // SYSTEM: PG 결과 불분명 → Redis 잠금 유지, noRollbackFor → PENDING 커밋
                log.error("[PG 시스템 오류] 결과 불분명, PENDING 유지 - orderId: {}, pgCode: {}", request.getOrderId(), e.getPgErrorCode());
                throw new PgUncertainException(CommonErrorCode.PAYMENT_SYSTEM_ERROR);
            }
        } catch (CallNotPermittedException e) {
            // CB OPEN: PG 호출 자체 안 됨 → 결제 미발생 확실 → Redis 복구 후 롤백
            log.error("[PG CB OPEN] PG 서비스 불가, 재고 복구 - orderId: {}", request.getOrderId());
            if (usedRedis) {
                promotionStockService.rollbackRedisResources(request.getPromotionRoomTypeId(), request.getOrderId());
            }
            throw e;
        } catch (Exception e) {
            // 타임아웃 등 결과 불분명 → Redis 잠금 유지, noRollbackFor → PENDING 커밋
            log.error("[시스템 장애] 결과 불분명, PENDING 유지 - orderId: {}", request.getOrderId(), e);
            throw new PgUncertainException(CommonErrorCode.PAYMENT_SYSTEM_ERROR);
        }
    }

    private BookingResponse bookNormal(BookingRequest request) {
        // 2-1. Rate Limit 체크 (Redis 장애 시 스킵)
        if (!rateLimitOutputPort.isAllowed(request.getUserId())) {
            throw new BusinessException(CommonErrorCode.RATE_LIMITED);
        }

        // 2-2. 멱등성 체크 (Redis 장애 시 스킵, DB UNIQUE로 방어)
        if (idempotencyOutputPort.isDuplicate(request.getOrderId())) {
            throw new BusinessException(CommonErrorCode.DUPLICATE_REQUEST);
        }

        // 2-3. 금액 검증 (Redis 캐시 우선, 실패 시 DB Fallback)
        paymentValidator.validateAmount(request.getOrderId(), request.getTotalAmount(), request.getRoomTypeId(), request.getPromotionRoomTypeId());

        try {
            // 3. Booking 저장 (PENDING)
            Booking booking = createAndSaveBooking(request);

            // 3-1. room_availability 차감 (비관적 락)
            roomAvailabilityService.decrease(request.getRoomTypeId(), request.getCheckInDate(), request.getCheckOutDate());

            // 4. 결제 실행
            paymentExecutionService.execute(request);

            // 5. Payment/PointHistory 저장
            paymentExecutionService.savePaymentsAndPointHistory(request, booking);

            // 6. Booking → COMPLETED
            booking.complete();

            return BookingResponse.mapToDTO(booking);

        } catch (BusinessException e) {
            log.warn("[일반 예약 실패] orderId: {}, code: {}", request.getOrderId(), e.getErrorCode());
            throw e;
        } catch (PgPaymentException e) {
            if (e.getCategory() == PgErrorCategory.RETRYABLE) {
                log.warn("[일반 예약 PG 거절] orderId: {}, pgCode: {}", request.getOrderId(), e.getPgErrorCode());
                throw new BusinessException(CommonErrorCode.PAYMENT_REJECTED);
            } else if (e.getCategory() == PgErrorCategory.TEMPORARY) {
                log.warn("[일반 예약 PG 일시 오류] Retry 소진 - orderId: {}, pgCode: {}", request.getOrderId(), e.getPgErrorCode());
                throw new BusinessException(CommonErrorCode.PAYMENT_TEMPORARY_ERROR);
            } else {
                // SYSTEM: PG 결과 불분명 → noRollbackFor → PENDING 커밋
                log.error("[일반 예약 PG 시스템 오류] 결과 불분명, PENDING 유지 - orderId: {}, pgCode: {}", request.getOrderId(), e.getPgErrorCode());
                throw new PgUncertainException(CommonErrorCode.PAYMENT_SYSTEM_ERROR);
            }
        } catch (CallNotPermittedException e) {
            // CB OPEN: PG 호출 안 됨 → 결제 미발생 확실 → DB 트랜잭션 롤백
            log.error("[일반 예약 PG CB OPEN] PG 서비스 불가 - orderId: {}", request.getOrderId());
            throw e;
        } catch (Exception e) {
            // 타임아웃 등 결과 불분명 → noRollbackFor → PENDING 커밋
            log.error("[일반 예약 시스템 장애] 결과 불분명, PENDING 유지 - orderId: {}", request.getOrderId(), e);
            throw new PgUncertainException(CommonErrorCode.PAYMENT_SYSTEM_ERROR);
        }
    }

    private Booking createAndSaveBooking(BookingRequest request) {
        RoomType roomType = roomTypeOutputPort.findById(request.getRoomTypeId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.ROOM_TYPE_NOT_FOUND));
        User user = userOutputPort.findById(request.getUserId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.USER_NOT_FOUND));

        PromotionRoomType promotionRoomType = request.getPromotionRoomTypeId() != null
                ? promotionRoomTypeOutputPort.findById(request.getPromotionRoomTypeId())
                        .orElseThrow(() -> new BusinessException(CommonErrorCode.PROMOTION_ROOM_TYPE_NOT_FOUND))
                : null;

        Booking booking = Booking.create(
                request.getOrderId(), user, roomType, promotionRoomType,
                request.getCheckInDate(), request.getCheckOutDate(), request.getTotalAmount());
        bookingOutputPort.save(booking);
        return booking;
    }
}
