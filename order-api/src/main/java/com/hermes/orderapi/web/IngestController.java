package com.hermes.orderapi.web;

import com.hermes.common.domain.IngestionJob;
import com.hermes.common.domain.IngestionStatus;
import com.hermes.common.event.IngestRequestedEvent;
import com.hermes.common.repository.IngestionJobRepository;
import com.hermes.orderapi.kafka.IngestProducer;
import com.hermes.orderapi.web.dto.CreateIngestRequest;
import com.hermes.orderapi.web.dto.IngestionJobResponse;
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

/**
 * Accepts documents for asynchronous ingestion. Mirrors {@link OrderController}:
 * the document is persisted PENDING and handed to Kafka, then the API returns 202
 * immediately so a 5,000-page upload never blocks the request thread — the
 * embedding happens in the background on the ingestion workers.
 */
@RestController
@RequestMapping("/api/ingest")
public class IngestController {

    private final IngestionJobRepository jobRepository;
    private final IngestProducer ingestProducer;

    public IngestController(IngestionJobRepository jobRepository, IngestProducer ingestProducer) {
        this.jobRepository = jobRepository;
        this.ingestProducer = ingestProducer;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<IngestionJobResponse> requestIngest(@Valid @RequestBody CreateIngestRequest request) {
        IngestionJob job = new IngestionJob(
                UUID.randomUUID(),
                request.docId(),
                request.source(),
                request.chunkCount()
        );
        jobRepository.save(job);

        ingestProducer.publish(new IngestRequestedEvent(
                job.getId(),
                job.getDocId(),
                job.getSource(),
                job.getTotalChunks(),
                Instant.now()
        ));

        return ResponseEntity.accepted().body(IngestionJobResponse.from(job));
    }

    @GetMapping("/{id}")
    public IngestionJobResponse getJob(@PathVariable UUID id) {
        return jobRepository.findById(id)
                .map(IngestionJobResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ingestion job not found"));
    }

    /** Convenience endpoint backing the dashboard / smoke tests. */
    @GetMapping("/stats")
    public Map<IngestionStatus, Long> stats() {
        Map<IngestionStatus, Long> counts = new EnumMap<>(IngestionStatus.class);
        for (IngestionStatus status : IngestionStatus.values()) {
            counts.put(status, jobRepository.countByStatus(status));
        }
        return counts;
    }
}
