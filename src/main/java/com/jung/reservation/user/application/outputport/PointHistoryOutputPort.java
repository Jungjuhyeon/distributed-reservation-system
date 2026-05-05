package com.jung.reservation.user.application.outputport;

import com.jung.reservation.user.domain.model.PointHistory;

public interface PointHistoryOutputPort {
    PointHistory save(PointHistory pointHistory);
}
