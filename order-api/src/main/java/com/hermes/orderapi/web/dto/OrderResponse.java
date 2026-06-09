package com.hermes.orderapi.web.dto;

import com.hermes.common.domain.OrderEntity;
import com.hermes.common.domain.OrderStatus;

import java.time.Instant;
import java.util.UUID;

public record OrderResponse(
        UUID orderId,
        String customerId,
        String sku,
        int quantity,
        OrderStatus status,
        String failureReason,
        Instant createdAt,
        Instant updatedAt
) {
    public static OrderResponse from(OrderEntity order) {
        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getSku(),
                order.getQuantity(),
                order.getStatus(),
                order.getFailureReason(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
