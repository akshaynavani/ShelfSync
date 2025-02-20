package com.shelfsync.billing.dto;

import com.shelfsync.billing.OrderStatus;
import com.shelfsync.billing.SalesOrder;

import java.math.BigDecimal;
import java.time.Instant;

/** Lightweight list view; does not touch the lazily-loaded lines. */
public record OrderSummary(String orderNumber, OrderStatus status, String customerName, String currency,
                           BigDecimal total, Instant createdAt) {

    public static OrderSummary from(SalesOrder order) {
        return new OrderSummary(order.getOrderNumber(), order.getStatus(), order.getCustomerName(),
                order.getCurrency(), order.getTotal(), order.getCreatedAt());
    }
}
