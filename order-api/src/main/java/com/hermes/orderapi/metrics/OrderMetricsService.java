package com.hermes.orderapi.metrics;

import com.hermes.common.domain.OrderStatus;
import com.hermes.common.domain.Product;
import com.hermes.common.repository.OrderRepository;
import com.hermes.common.repository.ProductRepository;
import org.springframework.stereotype.Service;

/**
 * Computes the live snapshot that backs the SSE stream. Throughput is derived
 * from the change in total order count between ticks, so it needs no extra
 * counter wired into the request path — every accepted order is already a row.
 *
 * {@link #tick()} is the single source of truth for the rate and must be called
 * by exactly one caller (the push scheduler) so the delta stays correct.
 */
@Service
public class OrderMetricsService {

    private final OrderRepository orders;
    private final ProductRepository products;

    private long lastTotal;
    private long lastNanos = System.nanoTime();

    public OrderMetricsService(OrderRepository orders, ProductRepository products) {
        this.orders = orders;
        this.products = products;
    }

    /** Wire format pushed to the browser (rate excluded from {@link #peek()}). */
    public record Metrics(long pending, long fulfilled, long rejected, long total, double acceptedPerSec) {
    }

    /**
     * Reset the throughput baseline — call when a client connects so the first
     * reading isn't an artificial spike spanning an idle gap.
     */
    public synchronized void resetBaseline() {
        this.lastTotal = orders.count();
        this.lastNanos = System.nanoTime();
    }

    /** Advance one tick: read counts and compute orders/sec from the delta. */
    public synchronized Metrics tick() {
        long pending = orders.countByStatus(OrderStatus.PENDING);
        long fulfilled = orders.countByStatus(OrderStatus.FULFILLED);
        long rejected = orders.countByStatus(OrderStatus.REJECTED);
        long total = pending + fulfilled + rejected;

        long now = System.nanoTime();
        double seconds = (now - lastNanos) / 1_000_000_000.0;
        double rate = seconds > 0 ? Math.max(0, total - lastTotal) / seconds : 0;

        lastTotal = total;
        lastNanos = now;
        return new Metrics(pending, fulfilled, rejected, total, rate);
    }

    /** Counts only, without advancing the rate baseline — for the connect event. */
    public synchronized Metrics peek() {
        long pending = orders.countByStatus(OrderStatus.PENDING);
        long fulfilled = orders.countByStatus(OrderStatus.FULFILLED);
        long rejected = orders.countByStatus(OrderStatus.REJECTED);
        return new Metrics(pending, fulfilled, rejected, pending + fulfilled + rejected, 0);
    }

    /** Remaining stock for one SKU, so the storefront's count updates live too. */
    public Integer stockFor(String sku) {
        if (sku == null || sku.isBlank()) {
            return null;
        }
        return products.findById(sku).map(Product::getStockAvailable).orElse(null);
    }
}
