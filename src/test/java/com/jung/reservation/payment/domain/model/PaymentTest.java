package com.jung.reservation.payment.domain.model;

import com.jung.reservation.accommodation.domain.model.Accommodation;
import com.jung.reservation.accommodation.domain.model.RoomType;
import com.jung.reservation.booking.domain.model.Booking;
import com.jung.reservation.payment.domain.model.enumeration.PaymentStatus;
import com.jung.reservation.payment.domain.model.enumeration.PaymentType;
import com.jung.reservation.user.domain.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentTest {

    private Booking createBooking() {
        User host = User.create("호스트", "010-0000-0000");
        Accommodation accommodation = Accommodation.create(host, "제주 호텔", "제주시 중앙로 1");
        RoomType roomType = RoomType.create(accommodation, "디럭스", 200000L, 2, 5,
                LocalTime.of(15, 0), LocalTime.of(11, 0));
        User user = User.create("정주현", "010-1234-5678");
        return Booking.create("ORD-TEST", user, roomType, null,
                LocalDate.of(2026, 5, 10), LocalDate.of(2026, 5, 12), 99000L);
    }

    @Test
    @DisplayName("Payment 생성 시 SUCCESS 상태")
    void create() {
        Booking booking = createBooking();
        Payment payment = Payment.create(booking, PaymentType.Y_POINT, 99000L);

        assertThat(payment.getBooking()).isEqualTo(booking);
        assertThat(payment.getPaymentType()).isEqualTo(PaymentType.Y_POINT);
        assertThat(payment.getAmount()).isEqualTo(99000L);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.getPgTransactionId()).isNull();
    }

    @Test
    @DisplayName("PG 결제 성공 시 pgTransactionId 저장")
    void success() {
        Booking booking = createBooking();
        Payment payment = Payment.create(booking, PaymentType.CREDIT_CARD, 79000L);
        payment.success("pg-txn-12345");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.getPgTransactionId()).isEqualTo("pg-txn-12345");
    }

    @Test
    @DisplayName("결제 실패")
    void fail() {
        Booking booking = createBooking();
        Payment payment = Payment.create(booking, PaymentType.CREDIT_CARD, 79000L);
        payment.fail();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    @DisplayName("결제 취소")
    void cancel() {
        Booking booking = createBooking();
        Payment payment = Payment.create(booking, PaymentType.CREDIT_CARD, 79000L);
        payment.cancel();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
    }
}
