package com.jung.reservation.payment.framework.web.dto;

import com.jung.reservation.payment.application.outputport.PgPaymentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PgWebhookRequest {

    @NotBlank
    private String paymentKey; // PG 측 결제 고유 식별자 (= pgTransactionId)

    @NotBlank
    private String orderId;

    @NotNull
    private PgPaymentStatus status;

    @NotNull
    private Long totalAmount;
}
