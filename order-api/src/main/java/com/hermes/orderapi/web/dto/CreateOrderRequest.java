package com.hermes.orderapi.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateOrderRequest(
        @NotBlank String customerId,
        @NotBlank String sku,
        @Min(1) @Max(1000) int quantity
) {
}
