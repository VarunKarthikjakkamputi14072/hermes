package com.hermes.orderapi.metrics;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Streams live queue metrics to the browser over Server-Sent Events instead of
 * making the frontend poll. A single background thread samples
 * {@link OrderMetricsService} every {@value #PUSH_INTERVAL_MS}ms and fans the
 * snapshot out to every open connection — so one DB read serves all clients and
 * the rate stays correct (one caller of {@code tick()}).
 */
@RestController
@RequestMapping("/api/metrics")
public class MetricsController {

    private static final Logger log = LoggerFactory.getLogger(MetricsController.class);
    private static final long PUSH_INTERVAL_MS = 500;

    private final OrderMetricsService service;
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final Map<SseEmitter, String> skuByEmitter = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "metrics-sse-push");
                t.setDaemon(true);
                return t;
            });

    public MetricsController(OrderMetricsService service) {
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

    /**
     * Opens an SSE connection. Pass {@code ?sku=...} to also receive that
     * product's remaining stock on every frame.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam(required = false) String sku) {
        SseEmitter emitter = new SseEmitter(0L); // never time out; we keep it warm
        skuByEmitter.put(emitter, sku == null ? "" : sku);
        emitter.onCompletion(() -> remove(emitter));
        emitter.onTimeout(() -> remove(emitter));
        emitter.onError(e -> remove(emitter));
        emitters.add(emitter);

        service.resetBaseline();
        // paint immediately instead of waiting up to PUSH_INTERVAL_MS
        send(emitter, snapshot(service.peek(), skuByEmitter.get(emitter)));
        return emitter;
    }

    private void broadcast() {
        if (emitters.isEmpty()) {
            return;
        }
        // Nothing may escape this method: scheduleAtFixedRate silently cancels all
        // future runs if a single execution throws, which would kill the stream
        // for every client.
        try {
            OrderMetricsService.Metrics base = service.tick();
            for (SseEmitter emitter : emitters) {
                send(emitter, snapshot(base, skuByEmitter.get(emitter)));
            }
        } catch (Exception e) {
            log.warn("metrics broadcast cycle failed: {}", e.toString());
        }
    }

    private MetricsSnapshot snapshot(OrderMetricsService.Metrics m, String sku) {
        boolean hasSku = sku != null && !sku.isBlank();
        Integer stock = hasSku ? service.stockFor(sku) : null;
        return new MetricsSnapshot(
                Instant.now().toEpochMilli(),
                m.pending(), m.fulfilled(), m.rejected(), m.total(), m.acceptedPerSec(),
                hasSku ? sku : null, stock);
    }

    private void send(SseEmitter emitter, MetricsSnapshot snapshot) {
        try {
            emitter.send(SseEmitter.event().name("metrics").data(snapshot));
        } catch (Exception e) {
            // Client went away mid-write. Depending on the container this surfaces
            // as IOException, IllegalStateException, AsyncRequestNotUsableException,
            // etc. — catch them all and drop the emitter; the onError/onCompletion
            // callbacks handle final cleanup. The scheduler keeps streaming.
            remove(emitter);
        }
    }

    private void remove(SseEmitter emitter) {
        emitters.remove(emitter);
        skuByEmitter.remove(emitter);
    }
}
