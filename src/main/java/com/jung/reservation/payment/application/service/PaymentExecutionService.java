package com.jung.reservation.payment.application.service;

import com.jung.reservation.booking.domain.model.Booking;
import com.jung.reservation.booking.framework.web.dto.BookingRequest;
import com.jung.reservation.payment.application.outputport.PaymentOutputPort;
import com.jung.reservation.payment.application.usecase.PaymentProcessor;
import com.jung.reservation.payment.application.usecase.PaymentProcessorRegistry;
import com.jung.reservation.payment.domain.model.Payment;
import com.jung.reservation.payment.domain.model.enumeration.PaymentType;
import com.jung.reservation.user.application.outputport.PointHistoryOutputPort;
import com.jung.reservation.user.application.outputport.UserPointOutputPort;
import com.jung.reservation.user.domain.model.PointHistory;
import com.jung.reservation.user.domain.model.UserPoint;
import com.jung.reservation.user.domain.model.enumeration.PointHistoryType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentExecutionService {

    private final PaymentProcessorRegistry paymentProcessorRegistry;
    private final PaymentOutputPort paymentOutputPort;
    private final UserPointOutputPort userPointOutputPort;
    private final PointHistoryOutputPort pointHistoryOutputPort;

    /**
     * 결제 실행 (부분 실패 시 보상 트랜잭션)
     *
     * Y_POINT를 항상 먼저 처리한다.
     * 포인트 잔액 부족 시 PG를 호출하지 않으므로 외부 cancel API 호출이 불필요하다.
     * Y_POINT가 없는 단건 결제는 정렬 후에도 순서가 동일하다.
     */
    public void execute(BookingRequest request) {
        List<BookingRequest.PaymentMethodRequest> ordered = request.getPaymentMethods().stream()
                .sorted((a, b) -> {
                    boolean aIsPoint = PaymentType.valueOf(a.getType()) == PaymentType.Y_POINT;
                    boolean bIsPoint = PaymentType.valueOf(b.getType()) == PaymentType.Y_POINT;
                    return Boolean.compare(!aIsPoint, !bIsPoint);
                })
                .toList();

        List<BookingRequest.PaymentMethodRequest> successfulPayments = new ArrayList<>();
        try {
            for (BookingRequest.PaymentMethodRequest method : ordered) {
                PaymentType paymentType = PaymentType.valueOf(method.getType());
                PaymentProcessor processor = paymentProcessorRegistry.getProcessor(paymentType);
                processor.pay(request.getUserId(), method.getAmount(), request.getOrderId(), request.getPgTransactionId());
                successfulPayments.add(method);
            }
        } catch (Exception paymentException) {
            compensate(request, successfulPayments);
            throw paymentException;
        }
    }

    /**
     * Payment 저장 + PointHistory 생성
     */
    public void savePaymentsAndPointHistory(BookingRequest request, Booking booking) {
        for (BookingRequest.PaymentMethodRequest method : request.getPaymentMethods()) {
            PaymentType paymentType = PaymentType.valueOf(method.getType());

            Payment payment = Payment.create(booking, paymentType, method.getAmount());
            if (paymentType != PaymentType.Y_POINT) {
                payment.success(request.getPgTransactionId()); // PG 결제만 pgTransactionId 세팅
            }
            paymentOutputPort.save(payment);

            if (paymentType == PaymentType.Y_POINT) {
                UserPoint userPoint = userPointOutputPort.findByUserId(request.getUserId()).orElseThrow();
                pointHistoryOutputPort.save(
                        PointHistory.create(userPoint, method.getAmount(), booking, PointHistoryType.USE, "예약 포인트 사용"));
            }
        }
    }

    /**
     * 보상 트랜잭션: 외부 PG 결제만 역순 취소
     * Y_POINT는 @Transactional 롤백이 자동으로 되돌리므로 명시적 cancel 불필요
     */
    private void compensate(BookingRequest request, List<BookingRequest.PaymentMethodRequest> successfulPayments) {
        for (int i = successfulPayments.size() - 1; i >= 0; i--) {
            BookingRequest.PaymentMethodRequest paid = successfulPayments.get(i);
            PaymentType paymentType = PaymentType.valueOf(paid.getType());

            if (paymentType == PaymentType.Y_POINT) {
                continue; // DB 트랜잭션 롤백으로 자동 복구
            }

            try {
                PaymentProcessor processor = paymentProcessorRegistry.getProcessor(paymentType);
                processor.cancel(request.getUserId(), paid.getAmount(), request.getOrderId(), request.getPgTransactionId());
            } catch (Exception cancelException) {
                log.error("[보상 트랜잭션 실패] 수동 확인 필요 - orderId: {}, method: {}",
                        request.getOrderId(), paid.getType(), cancelException);
            }
        }
    }
}
