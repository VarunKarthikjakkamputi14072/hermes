package com.hermes.orderapi.web;

import com.hermes.common.domain.OrderEntity;
import com.hermes.common.domain.OrderStatus;
import com.hermes.common.event.OrderPlacedEvent;
import com.hermes.common.repository.OrderRepository;
import com.hermes.orderapi.kafka.OrderProducer;
import com.hermes.orderapi.web.dto.CreateOrderRequest;
import com.hermes.orderapi.web.dto.OrderResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderRepository orderRepository;
    private final OrderProducer orderProducer;

    public OrderController(OrderRepository orderRepository, OrderProducer orderProducer) {
        this.orderRepository = orderRepository;
        this.orderProducer = orderProducer;
    }

    /**
     * Accepts an order, persists it as PENDING, then hands it to Kafka for
     * asynchronous fulfilment. Returns 202 Accepted — the API never blocks on
     * inventory, which is what lets it absorb load spikes.
     */
    @PostMapping
    @Transactional
    public ResponseEntity<OrderResponse> placeOrder(@Valid @RequestBody CreateOrderRequest request) {
        OrderEntity order = new OrderEntity(
                UUID.randomUUID(),
                request.customerId(),
                request.sku(),
                request.quantity()
        );
        orderRepository.save(order);

        orderProducer.publish(new OrderPlacedEvent(
                order.getId(),
                order.getCustomerId(),
                order.getSku(),
                order.getQuantity(),
                Instant.now()
        ));

        return ResponseEntity.accepted().body(OrderResponse.from(order));
    }

    @GetMapping("/{id}")
    public OrderResponse getOrder(@PathVariable UUID id) {
        return orderRepository.findById(id)
                .map(OrderResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found"));
    }

    /** Convenience endpoint backing the dashboard / smoke tests. */
    @GetMapping("/stats")
    public Map<OrderStatus, Long> stats() {
        Map<OrderStatus, Long> counts = new EnumMap<>(OrderStatus.class);
        for (OrderStatus status : OrderStatus.values()) {
            counts.put(status, orderRepository.countByStatus(status));
        }
        return counts;
    }
}
