package com.jung.reservation.booking.application.outputport;

public interface IdempotencyOutputPort {
    boolean isDuplicate(String orderId);
}
