package com.jung.reservation.common.exception;

import com.jung.reservation.promotion.application.outputport.StockOutputPort;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class CircuitBreakerFallbackTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Test
    @DisplayName("Redis Circuit Breaker OPEN 시 503 응답")
    void circuitBreakerOpen_returns503() throws Exception {
        // Circuit Breaker 강제 OPEN
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("redisCircuitBreaker");
        circuitBreaker.transitionToOpenState();

        String requestBody = """
                {
                    "orderId": "ORD-20260505-CB-TEST",
                    "userId": 1,
                    "roomTypeId": 1,
                    "promotionRoomTypeId": 1,
                    "totalAmount": 99000,
                    "checkInDate": "2026-05-10",
                    "checkOutDate": "2026-05-12",
                    "pgTransactionId": null,
                    "paymentMethods": [{"type": "Y_POINT", "amount": 99000}]
                }
                """;

        mockMvc.perform(post("/api/booking")
                        .header("X-Idempotency-Key", "ORD-20260505-CB-TEST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("5030"))
                .andExpect(jsonPath("$.message").value("서비스가 일시적으로 불가합니다. 잠시 후 다시 시도해주세요."));

        // Circuit Breaker 원복
        circuitBreaker.transitionToClosedState();
    }
}
