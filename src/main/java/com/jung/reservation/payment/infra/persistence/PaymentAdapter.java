package com.jung.reservation.payment.infra.persistence;

import com.jung.reservation.payment.application.outputport.PaymentOutputPort;
import com.jung.reservation.payment.domain.model.Payment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PaymentAdapter implements PaymentOutputPort {

    private final PaymentJpaRepository paymentJpaRepository;

    @Override
    public Payment save(Payment payment) {
        return paymentJpaRepository.save(payment);
    }
}
