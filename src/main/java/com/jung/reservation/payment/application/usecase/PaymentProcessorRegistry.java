package com.jung.reservation.payment.application.usecase;

import com.jung.reservation.payment.domain.model.enumeration.PaymentType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class PaymentProcessorRegistry {

    private final Map<PaymentType, PaymentProcessor> processors;

    public PaymentProcessorRegistry(List<PaymentProcessor> processorList) {
        this.processors = processorList.stream()
                .collect(Collectors.toMap(PaymentProcessor::getType, Function.identity()));
    }

    public PaymentProcessor getProcessor(PaymentType type) {
        PaymentProcessor processor = processors.get(type);
        if (processor == null) {
            throw new IllegalArgumentException("지원하지 않는 결제 수단입니다: " + type);
        }
        return processor;
    }
}
