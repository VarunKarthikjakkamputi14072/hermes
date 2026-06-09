package com.hermes.worker.service;

import com.hermes.common.domain.OrderEntity;
import com.hermes.common.domain.Product;
import com.hermes.common.event.OrderPlacedEvent;
import com.hermes.common.repository.OrderRepository;
import com.hermes.common.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * The transactional heart of the engine. Each call runs in a single DB
 * transaction: the product row is locked, stock is checked and decremented, and
 * the order status is updated atomically. If anything throws, the whole unit of
 * work rolls back and Kafka will redeliver the message.
 */
@Service
public class FulfillmentService {

    private static final Logger log = LoggerFactory.getLogger(FulfillmentService.class);

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    public FulfillmentService(OrderRepository orderRepository, ProductRepository productRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
    }

    @Transactional
    public FulfillmentResult fulfil(OrderPlacedEvent event) {
        OrderEntity order = orderRepository.findById(event.orderId()).orElse(null);
        if (order == null) {
            // The API publishes inside its own DB transaction, so a fast worker can
            // consume the event before that commit is visible (a dual-write race).
            // Throwing here lets the error handler retry after a back-off, by which
            // point the row has committed. If it never appears, it lands on the DLT.
            // A transactional outbox would remove this race entirely (see README).
            throw new OrderNotYetVisibleException(event.orderId());
        }

        // Idempotency: redelivery of an already-processed order is a no-op. This
        // makes at-least-once delivery safe.
        if (!order.isPending()) {
            return FulfillmentResult.SKIPPED_DUPLICATE;
        }

        Optional<Product> maybeProduct = productRepository.findBySkuForUpdate(event.sku());
        if (maybeProduct.isEmpty()) {
            order.markRejected("UNKNOWN_PRODUCT");
            orderRepository.save(order);
            return FulfillmentResult.REJECTED_UNKNOWN_PRODUCT;
        }

        Product product = maybeProduct.get();
        if (!product.canFulfil(event.quantity())) {
            order.markRejected("OUT_OF_STOCK");
            orderRepository.save(order);
            return FulfillmentResult.REJECTED_OUT_OF_STOCK;
        }

        product.deduct(event.quantity());
        order.markFulfilled();
        // both entities are managed → flushed on commit
        return FulfillmentResult.FULFILLED;
    }
}
