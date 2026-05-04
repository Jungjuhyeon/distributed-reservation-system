package com.jung.reservation.payment.infra.persistence;

import com.jung.reservation.payment.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentJpaRepository extends JpaRepository<Payment, Long> {
}
