package com.hermes.orderapi.web;

import com.hermes.common.domain.Payment;
import com.hermes.common.domain.PaymentStatus;
import com.hermes.common.event.PaymentRequestedEvent;
import com.hermes.common.repository.PaymentRepository;
import com.hermes.orderapi.kafka.PaymentProducer;
import com.hermes.orderapi.metrics.PaymentMetrics;
import com.hermes.orderapi.web.dto.CreatePaymentRequest;
import com.hermes.orderapi.web.dto.PaymentResponse;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentRepository paymentRepository;
    private final PaymentProducer paymentProducer;
    private final PaymentMetrics paymentMetrics;

    public PaymentController(PaymentRepository paymentRepository,
                            PaymentProducer paymentProducer,
                            PaymentMetrics paymentMetrics) {
        this.paymentRepository = paymentRepository;
        this.paymentProducer = paymentProducer;
        this.paymentMetrics = paymentMetrics;
    }

    /**
     * Accepts a charge and hands it to Kafka for settlement, returning 202.
     *
     * Idempotency: the request carries a client-supplied key. If a payment with
     * that key already exists we return it unchanged — no second charge, no
     * second event. The UNIQUE constraint on the column closes the concurrency
     * window: if two requests with the same key race past the initial lookup,
     * exactly one INSERT wins and the other catches the violation and returns
     * the winner. This is *not* a method-level @Transactional on purpose, so the
     * failed insert's own transaction rolls back cleanly and the follow-up read
     * runs in a fresh one.
     */
    @PostMapping
    public ResponseEntity<PaymentResponse> charge(@Valid @RequestBody CreatePaymentRequest request) {
        Payment existing = paymentRepository.findByIdempotencyKey(request.idempotencyKey()).orElse(null);
        if (existing != null) {
            paymentMetrics.recordDuplicate();
            return ResponseEntity.ok(PaymentResponse.deduplicated(existing));
        }

        Payment payment = new Payment(
                UUID.randomUUID(),
                request.idempotencyKey(),
                request.accountId(),
                request.amountCents()
        );
        try {
            paymentRepository.saveAndFlush(payment); // flush now so the unique constraint fires here
        } catch (DataIntegrityViolationException race) {
            // A concurrent request with the same key inserted first — dedupe to it.
            paymentMetrics.recordDuplicate();
            Payment winner = paymentRepository.findByIdempotencyKey(request.idempotencyKey())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "duplicate key"));
            return ResponseEntity.ok(PaymentResponse.deduplicated(winner));
        }

        paymentProducer.publish(new PaymentRequestedEvent(
                payment.getId(),
                payment.getIdempotencyKey(),
                payment.getAccountId(),
                payment.getAmountCents(),
                Instant.now()
        ));
        return ResponseEntity.accepted().body(PaymentResponse.accepted(payment));
    }

    @GetMapping("/{id}")
    public PaymentResponse getPayment(@PathVariable UUID id) {
        return paymentRepository.findById(id)
                .map(PaymentResponse::accepted)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "payment not found"));
    }

    /** Counts by status + the running dedupe total — backs the dashboard / smoke tests. */
    @GetMapping("/stats")
    public Map<String, Long> stats() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (PaymentStatus status : PaymentStatus.values()) {
            counts.put(status.name(), paymentRepository.countByStatus(status));
        }
        counts.put("DUPLICATES_BLOCKED", paymentMetrics.getDuplicatesBlocked());
        return counts;
    }
}
