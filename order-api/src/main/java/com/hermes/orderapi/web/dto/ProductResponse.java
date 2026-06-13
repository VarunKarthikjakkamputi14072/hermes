package com.hermes.orderapi.web.dto;

import com.hermes.common.domain.Product;

import java.math.BigDecimal;

/**
 * Read model for the storefront: a sellable SKU plus its live stock level.
 * Backs the dashboard's "remaining" counter.
 */
public record ProductResponse(
        String sku,
        String name,
        String category,
        BigDecimal price,
        int stockAvailable
) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getSku(),
                product.getName(),
                product.getCategory(),
                product.getPrice(),
                product.getStockAvailable()
        );
    }
}
