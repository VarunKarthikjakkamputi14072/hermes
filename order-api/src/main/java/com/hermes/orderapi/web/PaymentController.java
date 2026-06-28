package com.hermes.orderapi.web;

import com.hermes.common.domain.Payment;
import com.hermes.common.domain.PaymentStatus;
import com.hermes.common.repository.PaymentRepository;
import com.hermes.orderapi.metrics.PaymentMetrics;
import com.hermes.orderapi.payment.PaymentService;
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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final PaymentMetrics paymentMetrics;

    public PaymentController(PaymentRepository paymentRepository,
                            PaymentService paymentService,
                            PaymentMetrics paymentMetrics) {
        this.paymentRepository = paymentRepository;
        this.paymentService = paymentService;
        this.paymentMetrics = paymentMetrics;
    }

    /**
     * Accepts a charge and returns 202. The payment row and its Kafka event are
     * written atomically to the outbox by {@link PaymentService}; Debezium ships
     * the event — no direct Kafka call, so the API can't commit the charge and
     * then lose the event.
     *
     * Idempotency: the request carries a client-supplied key. If one already
     * exists we return it unchanged. The UNIQUE constraint closes the concurrency
     * window — if two requests with the same key race past the lookup, exactly
     * one INSERT wins and the other catches the violation (which rolls back its
     * whole transaction, payment + outbox) and returns the winner.
     */
    @PostMapping
    public ResponseEntity<PaymentResponse> charge(@Valid @RequestBody CreatePaymentRequest request) {
        Payment existing = paymentRepository.findByIdempotencyKey(request.idempotencyKey()).orElse(null);
        if (existing != null) {
            paymentMetrics.recordDuplicate();
            return ResponseEntity.ok(PaymentResponse.deduplicated(existing));
        }

        try {
            Payment payment = paymentService.acceptCharge(request);
            return ResponseEntity.accepted().body(PaymentResponse.accepted(payment));
        } catch (DataIntegrityViolationException race) {
            // A concurrent request with the same key committed first — dedupe to it.
            paymentMetrics.recordDuplicate();
            Payment winner = paymentRepository.findByIdempotencyKey(request.idempotencyKey())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "duplicate key"));
            return ResponseEntity.ok(PaymentResponse.deduplicated(winner));
        }
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
