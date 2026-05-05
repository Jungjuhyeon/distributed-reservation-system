package com.jung.reservation.user.infra.persistence;

import com.jung.reservation.user.application.outputport.PointHistoryOutputPort;
import com.jung.reservation.user.domain.model.PointHistory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PointHistoryAdapter implements PointHistoryOutputPort {

    private final PointHistoryJpaRepository pointHistoryJpaRepository;

    @Override
    public PointHistory save(PointHistory pointHistory) {
        return pointHistoryJpaRepository.save(pointHistory);
    }
}
