package com.hermes.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;

/**
 * A sellable SKU with a finite amount of stock.
 *
 * The {@link Version} column gives us optimistic locking for free; the worker
 * additionally takes a pessimistic row lock when decrementing stock so that two
 * concurrent consumers can never oversell the same item.
 */
@Entity
@Table(name = "products")
public class Product {

    @Id
    @Column(name = "sku", nullable = false, updatable = false)
    private String sku;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "category")
    private String category;

    @Column(name = "price", nullable = false)
    private BigDecimal price;

    @Column(name = "stock_available", nullable = false)
    private int stockAvailable;

    @Version
    @Column(name = "version")
    private long version;

    protected Product() {
        // for JPA
    }

    public Product(String sku, String name, String category, BigDecimal price, int stockAvailable) {
        this.sku = sku;
        this.name = name;
        this.category = category;
        this.price = price;
        this.stockAvailable = stockAvailable;
    }

    public boolean canFulfil(int quantity) {
        return stockAvailable >= quantity;
    }

    public void deduct(int quantity) {
        if (!canFulfil(quantity)) {
            throw new IllegalStateException("Insufficient stock for " + sku);
        }
        this.stockAvailable -= quantity;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public int getStockAvailable() {
        return stockAvailable;
    }

    public long getVersion() {
        return version;
    }
}
