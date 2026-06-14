package com.hermes.worker.kafka;

import com.hermes.common.event.IngestRequestedEvent;
import com.hermes.common.event.Topics;
import com.hermes.worker.service.IngestionResult;
import com.hermes.worker.service.IngestionService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class IngestConsumer {

    private static final Logger log = LoggerFactory.getLogger(IngestConsumer.class);

    private final IngestionService ingestionService;
    private final MeterRegistry meterRegistry;
    private final Map<IngestionResult, Counter> counters = new ConcurrentHashMap<>();

    public IngestConsumer(IngestionService ingestionService, MeterRegistry meterRegistry) {
        this.ingestionService = ingestionService;
        this.meterRegistry = meterRegistry;
    }

    @KafkaListener(
            topics = Topics.INGEST_REQUESTED,
            groupId = Topics.INGEST_GROUP,
            concurrency = "${hermes.worker.ingest-concurrency:3}"
    )
    public void onIngestRequested(IngestRequestedEvent event) {
        IngestionResult result = ingestionService.ingest(event);
        record(result);
        if (log.isDebugEnabled()) {
            log.debug("Ingestion job {} -> {}", event.jobId(), result);
        }
    }

    private void record(IngestionResult result) {
        counters.computeIfAbsent(result, r ->
                Counter.builder("hermes.ingestion.processed")
                        .description("Ingestion jobs processed by the ingestion worker")
                        .tag("result", r.name().toLowerCase())
                        .register(meterRegistry)
        ).increment();
    }
}
