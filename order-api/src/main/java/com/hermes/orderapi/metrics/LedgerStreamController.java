package com.hermes.orderapi.metrics;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Streams live ledger metrics to the browser over Server-Sent Events. One shared
 * daemon thread samples {@link LedgerMetricsService} every {@value #PUSH_INTERVAL_MS}ms
 * and fans the snapshot out to every open connection. Same defensive shape as the
 * order metrics stream: nothing may escape the scheduled task, or
 * {@code scheduleAtFixedRate} would cancel all future runs and freeze the stream.
 */
@RestController
@RequestMapping("/api/payments")
public class LedgerStreamController {

    private static final Logger log = LoggerFactory.getLogger(LedgerStreamController.class);
    private static final long PUSH_INTERVAL_MS = 500;

    private final LedgerMetricsService service;
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "ledger-sse-push");
                t.setDaemon(true);
                return t;
            });

    public LedgerStreamController(LedgerMetricsService service) {
        this.service = service;
    }

    @PostConstruct
    void start() {
        scheduler.scheduleAtFixedRate(this::broadcast, PUSH_INTERVAL_MS, PUSH_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void stop() {
        scheduler.shutdownNow();
        emitters.forEach(SseEmitter::complete);
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        SseEmitter emitter = new SseEmitter(0L); // never time out; kept warm by the push
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        emitters.add(emitter);

        service.resetBaseline();
        send(emitter, service.peek()); // paint immediately
        return emitter;
    }

    private void broadcast() {
        if (emitters.isEmpty()) {
            return;
        }
        try {
            LedgerSnapshot snapshot = service.tick();
            for (SseEmitter emitter : emitters) {
                send(emitter, snapshot);
            }
        } catch (Exception e) {
            log.warn("ledger broadcast cycle failed: {}", e.toString());
        }
    }

    private void send(SseEmitter emitter, LedgerSnapshot snapshot) {
        try {
            emitter.send(SseEmitter.event().name("ledger").data(snapshot));
        } catch (Exception e) {
            emitters.remove(emitter);
        }
    }
}
