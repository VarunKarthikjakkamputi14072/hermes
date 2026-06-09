package com.hermes.worker.service;

import com.hermes.common.domain.OrderEntity;
import com.hermes.common.domain.OrderStatus;
import com.hermes.common.domain.Product;
import com.hermes.common.event.OrderPlacedEvent;
import com.hermes.common.repository.OrderRepository;
import com.hermes.common.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@EntityScan("com.hermes.common.domain")
@EnableJpaRepositories("com.hermes.common.repository")
@Import(FulfillmentService.class)
class FulfillmentServiceTest {

    @Autowired
    OrderRepository orderRepository;
    @Autowired
    ProductRepository productRepository;
    @Autowired
    FulfillmentService service;

    @BeforeEach
    void seed() {
        productRepository.save(new Product("SKU-1", "Widget", "tools", new BigDecimal("9.99"), 10));
    }

    private OrderPlacedEvent persistOrder(String sku, int qty) {
        OrderEntity order = new OrderEntity(UUID.randomUUID(), "cust-1", sku, qty);
        orderRepository.save(order);
        return new OrderPlacedEvent(order.getId(), "cust-1", sku, qty, Instant.now());
    }

    @Test
    void fulfilsWhenStockAvailable() {
        OrderPlacedEvent event = persistOrder("SKU-1", 3);

        FulfillmentResult result = service.fulfil(event);

        assertThat(result).isEqualTo(FulfillmentResult.FULFILLED);
        assertThat(orderRepository.findById(event.orderId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.FULFILLED);
        assertThat(productRepository.findById("SKU-1").orElseThrow().getStockAvailable())
                .isEqualTo(7);
    }

    @Test
    void rejectsWhenOutOfStock() {
        OrderPlacedEvent event = persistOrder("SKU-1", 50);

        FulfillmentResult result = service.fulfil(event);

        assertThat(result).isEqualTo(FulfillmentResult.REJECTED_OUT_OF_STOCK);
        assertThat(orderRepository.findById(event.orderId()).orElseThrow().getFailureReason())
                .isEqualTo("OUT_OF_STOCK");
        assertThat(productRepository.findById("SKU-1").orElseThrow().getStockAvailable())
                .isEqualTo(10);
    }

    @Test
    void rejectsUnknownProduct() {
        OrderPlacedEvent event = persistOrder("SKU-NOPE", 1);

        assertThat(service.fulfil(event)).isEqualTo(FulfillmentResult.REJECTED_UNKNOWN_PRODUCT);
    }

    @Test
    void isIdempotentOnRedelivery() {
        OrderPlacedEvent event = persistOrder("SKU-1", 2);

        assertThat(service.fulfil(event)).isEqualTo(FulfillmentResult.FULFILLED);
        // redelivery of the same message must not deduct stock twice
        assertThat(service.fulfil(event)).isEqualTo(FulfillmentResult.SKIPPED_DUPLICATE);
        assertThat(productRepository.findById("SKU-1").orElseThrow().getStockAvailable())
                .isEqualTo(8);
    }
}
