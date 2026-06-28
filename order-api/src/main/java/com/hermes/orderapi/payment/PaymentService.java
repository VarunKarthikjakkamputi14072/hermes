package com.hermes.orderapi.payment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermes.common.domain.OutboxEvent;
import com.hermes.common.domain.Payment;
import com.hermes.common.event.PaymentRequestedEvent;
import com.hermes.common.event.Topics;
import com.hermes.common.repository.OutboxRepository;
import com.hermes.common.repository.PaymentRepository;
import com.hermes.orderapi.web.dto.CreatePaymentRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Accepts a charge using the transactional outbox pattern: the Payment row and
 * the outbox event are written in ONE transaction, so they commit atomically.
 * There is no Kafka call here — Debezium tails the WAL and ships the outbox row.
 * That removes the dual-write problem (a crash after the DB commit can't lose the
 * event) and makes settlement exactly-once end to end.
 */
@Service
public class PaymentService {

    private final PaymentRepository payments;
    private final OutboxRepository outbox;
    private final ObjectMapper objectMapper;

    public PaymentService(PaymentRepository payments, OutboxRepository outbox, ObjectMapper objectMapper) {
        this.payments = payments;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    /**
     * Persists the payment + its outbox event atomically. The UNIQUE idempotency
     * key is flushed here, so a duplicate throws {@code DataIntegrityViolationException}
     * and the whole transaction (payment AND outbox) rolls back — leaving nothing
     * behind for the caller to dedupe against.
     */
    @Transactional
    public Payment acceptCharge(CreatePaymentRequest request) {
        Payment payment = new Payment(
                UUID.randomUUID(),
                request.idempotencyKey(),
                request.accountId(),
                request.amountCents());
        payments.saveAndFlush(payment); // unique constraint fires inside this transaction

        PaymentRequestedEvent event = new PaymentRequestedEvent(
                payment.getId(),
                payment.getIdempotencyKey(),
                payment.getAccountId(),
                payment.getAmountCents(),
                Instant.now());

        outbox.save(new OutboxEvent(
                UUID.randomUUID(),
                Topics.PAYMENTS_REQUESTED,   // → routed to this Kafka topic
                payment.getAccountId(),      // → Kafka message key
                "PaymentRequested",
                serialize(event)));          // → Kafka message value

        return payment;
    }

    private String serialize(PaymentRequestedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize payment event", e);
        }
    }
}
