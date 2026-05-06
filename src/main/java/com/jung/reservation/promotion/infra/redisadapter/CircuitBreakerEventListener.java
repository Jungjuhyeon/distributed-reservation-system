package com.jung.reservation.promotion.infra.redisadapter;

import com.jung.reservation.promotion.application.service.StockSyncService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CircuitBreakerEventListener {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final StockSyncService stockSyncService;

    @PostConstruct
    public void registerListener() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("redisCircuitBreaker");

        circuitBreaker.getEventPublisher()
                .onStateTransition(event -> {
                    if (event.getStateTransition() == CircuitBreaker.StateTransition.HALF_OPEN_TO_CLOSED) {
                        log.info("[Circuit Breaker] HALF_OPEN → CLOSED: Redis 복구 감지, 재고 동기화 시작");
                        stockSyncService.syncActiveStockToRedis();
                    }
                });
    }
}
