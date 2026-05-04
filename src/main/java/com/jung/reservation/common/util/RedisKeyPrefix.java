package com.jung.reservation.common.util;

public class RedisKeyPrefix {

    private RedisKeyPrefix() {}

    public static final String RATE_LIMIT = "rate_limit:";
    public static final String SALE_START = "sale_start:promotion:";
    public static final String IDEMPOTENCY = "idempotency:booking:";
    public static final String STOCK = "stock:promotionRoomType:";
    public static final String CHECKOUT = "checkout:";
}
