package com.jung.reservation.payment.application.outputport;

import com.jung.reservation.payment.domain.model.Payment;

public interface PaymentOutputPort {
    Payment save(Payment payment);
}
