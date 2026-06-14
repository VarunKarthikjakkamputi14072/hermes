package com.hermes.orderapi.web;

import com.hermes.common.repository.IngestionJobRepository;
import com.hermes.orderapi.web.dto.IngestionProgress;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Streams a single ingestion job's progress to the browser over Server-Sent
 * Events, so the upload UI shows the bar fill live instead of polling. A single
 * background thread samples each open job every {@value #PUSH_INTERVAL_MS}ms and
 * closes the connection once the job reaches a terminal state. Same defensive
 * shape as the order metrics stream: nothing may escape the scheduled task, or
 * {@code scheduleAtFixedRate} would cancel all future runs and freeze every
 * client's bar.
 */
@RestController
@RequestMapping("/api/ingest")
public class IngestionStreamController {

    private static final Logger log = LoggerFactory.getLogger(IngestionStreamController.class);
    private static final long PUSH_INTERVAL_MS = 400;

    private final IngestionJobRepository jobs;
    private final Map<SseEmitter, UUID> jobByEmitter = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "ingest-sse-push");
                t.setDaemon(true);
                return t;
            });

    public IngestionStreamController(IngestionJobRepository jobs) {
        this.jobs = jobs;
    }

    @PostConstruct
    void start() {
        scheduler.scheduleAtFixedRate(this::broadcast, PUSH_INTERVAL_MS, PUSH_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void stop() {
        scheduler.shutdownNow();
        jobByEmitter.keySet().forEach(SseEmitter::complete);
    }

    @GetMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable UUID id) {
        SseEmitter emitter = new SseEmitter(0L); // no timeout; closed on terminal state
        emitter.onCompletion(() -> jobByEmitter.remove(emitter));
        emitter.onTimeout(() -> jobByEmitter.remove(emitter));
        emitter.onError(e -> jobByEmitter.remove(emitter));
        jobByEmitter.put(emitter, id);
        // paint immediately instead of waiting up to PUSH_INTERVAL_MS
        push(emitter, id);
        return emitter;
    }

    private void broadcast() {
        if (jobByEmitter.isEmpty()) {
            return;
        }
        try {
            jobByEmitter.forEach(this::push);
        } catch (Exception e) {
            log.warn("ingest progress broadcast cycle failed: {}", e.toString());
        }
    }

    private void push(SseEmitter emitter, UUID jobId) {
        IngestionProgress progress = jobs.findById(jobId)
                .map(IngestionProgress::from)
                .orElse(null);
        if (progress == null) {
            return; // job not visible yet; next tick will retry
        }
        try {
            emitter.send(SseEmitter.event().name("progress").data(progress));
            if (progress.isTerminal()) {
                emitter.complete();
                jobByEmitter.remove(emitter);
            }
        } catch (Exception e) {
            // Client went away mid-write (surfaces as IOException /
            // IllegalStateException / AsyncRequestNotUsableException depending on
            // the container). Drop the emitter; the scheduler keeps streaming.
            jobByEmitter.remove(emitter);
        }
    }
}
