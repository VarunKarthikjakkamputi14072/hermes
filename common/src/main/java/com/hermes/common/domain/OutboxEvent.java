package com.hermes.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Transactional outbox row. The producing service writes this in the SAME
 * database transaction as the business entity (e.g. a Payment), so the event and
 * the state change commit atomically — no dual-write. Debezium then tails the
 * Postgres WAL and ships each committed row to Kafka via its outbox EventRouter,
 * which is why the column names are the ones the router expects by default
 * (aggregatetype, aggregateid, type, payload).
 */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Routed to the Kafka topic of this name (e.g. "payments.requested"). */
    @Column(name = "aggregatetype", nullable = false)
    private String aggregateType;

    /** Becomes the Kafka message key (e.g. the account id). */
    @Column(name = "aggregateid", nullable = false)
    private String aggregateId;

    /** Logical event type (e.g. "PaymentRequested"). */
    @Column(name = "type", nullable = false)
    private String type;

    /** The event itself, as JSON — becomes the Kafka message value. */
    @Column(name = "payload", nullable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OutboxEvent() {
        // for JPA
    }

    public OutboxEvent(UUID id, String aggregateType, String aggregateId, String type, String payload) {
        this.id = id;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.type = type;
        this.payload = payload;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getType() {
        return type;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
