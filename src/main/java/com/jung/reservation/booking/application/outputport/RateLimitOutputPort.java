package com.jung.reservation.booking.application.outputport;

public interface RateLimitOutputPort {
    boolean isAllowed(Long userId);
}
