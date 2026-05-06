package com.jung.reservation.promotion.application.service;

import com.jung.reservation.promotion.application.outputport.StockOutputPort;
import com.jung.reservation.promotion.domain.model.PromotionRoomType;
import com.jung.reservation.promotion.infra.persistence.PromotionRoomTypeJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockSyncService {

    private final PromotionRoomTypeJpaRepository promotionRoomTypeJpaRepository;
    private final StockOutputPort stockOutputPort;

    /**
     * 활성 프로모션의 DB stock → Redis stock 동기화
     * Redis 복구(Circuit Breaker CLOSED 전환) 시 호출
     */
    public void syncActiveStockToRedis() {
        List<PromotionRoomType> activeList = promotionRoomTypeJpaRepository
                .findActivePromotionRoomTypes(LocalDateTime.now());

        for (PromotionRoomType prt : activeList) {
            try {
                stockOutputPort.setStock(prt.getId(), prt.getStock());
                log.info("[재고 동기화] promotionRoomTypeId: {}, stock: {}", prt.getId(), prt.getStock());
            } catch (Exception e) {
                log.error("[재고 동기화 실패] promotionRoomTypeId: {}", prt.getId(), e);
            }
        }
    }
}
