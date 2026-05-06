package com.jung.reservation.payment.application.service;

import com.jung.reservation.booking.framework.web.dto.BookingRequest;
import com.jung.reservation.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class PaymentValidatorTest {

    @Autowired
    private PaymentValidator paymentValidator;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Test
    @DisplayName("복합 결제 - 신용카드 + 포인트 허용")
    void validateCombination_cardAndPoint_ok() {
        List<BookingRequest.PaymentMethodRequest> methods = List.of(
                new BookingRequest.PaymentMethodRequest("CREDIT_CARD", 80000L),
                new BookingRequest.PaymentMethodRequest("Y_POINT", 20000L)
        );

        assertThatCode(() -> paymentValidator.validatePaymentCombination(methods))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("복합 결제 - Y페이 + 포인트 허용")
    void validateCombination_ypayAndPoint_ok() {
        List<BookingRequest.PaymentMethodRequest> methods = List.of(
                new BookingRequest.PaymentMethodRequest("Y_PAY", 80000L),
                new BookingRequest.PaymentMethodRequest("Y_POINT", 20000L)
        );

        assertThatCode(() -> paymentValidator.validatePaymentCombination(methods))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("복합 결제 - 포인트 단독 허용")
    void validateCombination_pointOnly_ok() {
        List<BookingRequest.PaymentMethodRequest> methods = List.of(
                new BookingRequest.PaymentMethodRequest("Y_POINT", 99000L)
        );

        assertThatCode(() -> paymentValidator.validatePaymentCombination(methods))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("복합 결제 - 신용카드 + Y페이 혼용 불가")
    void validateCombination_cardAndYpay_fail() {
        List<BookingRequest.PaymentMethodRequest> methods = List.of(
                new BookingRequest.PaymentMethodRequest("CREDIT_CARD", 50000L),
                new BookingRequest.PaymentMethodRequest("Y_PAY", 50000L)
        );

        assertThatThrownBy(() -> paymentValidator.validatePaymentCombination(methods))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("금액 검증 - Redis 캐시와 일치")
    void validateAmount_redis_match() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
        redisTemplate.opsForValue().set("checkout:ORD-VALID-001", "99000");

        assertThatCode(() -> paymentValidator.validateAmount("ORD-VALID-001", 99000L, 1L, null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("금액 검증 - Redis 캐시와 불일치")
    void validateAmount_redis_mismatch() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
        redisTemplate.opsForValue().set("checkout:ORD-VALID-002", "99000");

        assertThatThrownBy(() -> paymentValidator.validateAmount("ORD-VALID-002", 50000L, 1L, null))
                .isInstanceOf(BusinessException.class);
    }
}
