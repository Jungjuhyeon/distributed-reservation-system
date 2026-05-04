package com.jung.reservation.payment.domain.model;

import com.jung.reservation.booking.domain.model.Booking;
import com.jung.reservation.common.entity.BaseEntity;
import com.jung.reservation.payment.domain.model.enumeration.PaymentStatus;
import com.jung.reservation.payment.domain.model.enumeration.PaymentType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentType paymentType;

    @Column(nullable = false)
    private Long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    private String pgPaymentKey; // PG 결제키 (외부 결제 시)

    private Payment(Booking booking, PaymentType paymentType, Long amount, PaymentStatus status) {
        this.booking = booking;
        this.paymentType = paymentType;
        this.amount = amount;
        this.status = status;
    }

    public static Payment create(Booking booking, PaymentType paymentType, Long amount) {
        return new Payment(booking, paymentType, amount, PaymentStatus.SUCCESS);
    }

    public void success(String pgPaymentKey) {
        this.status = PaymentStatus.SUCCESS;
        this.pgPaymentKey = pgPaymentKey;
    }

    public void fail() {
        this.status = PaymentStatus.FAILED;
    }

    public void cancel() {
        this.status = PaymentStatus.CANCELLED;
    }
}
