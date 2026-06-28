package com.hermes.worker.fraud;

import com.hermes.common.domain.FraudFlag;
import com.hermes.common.repository.FraudFlagRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Generates the analyst narrative for pending fraud flags — off the settlement
 * hot path, so the (1–2s) LLM latency never blocks Kafka throughput. A single
 * daemon thread polls for flags whose narrative hasn't been written yet, asks
 * NIM to explain the deterministic decision, and falls back to a template if NIM
 * is disabled or unavailable. The model explains; it never changes the decision.
 */
@Component
public class FraudNarrator {

    private static final Logger log = LoggerFactory.getLogger(FraudNarrator.class);
    private static final int BATCH = 5;
    private static final String SYSTEM =
            "You are a payments fraud analyst. In 2-3 sentences, explain the risk and recommend "
            + "whether to allow, review, or hold the charge. Be specific and concise, and use only "
            + "the signals provided. Do not invent details.";

    private final FraudFlagRepository flags;
    private final NimClient nim;
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "fraud-narrator");
                t.setDaemon(true);
                return t;
            });

    public FraudNarrator(FraudFlagRepository flags, NimClient nim) {
        this.flags = flags;
        this.nim = nim;
    }

    @PostConstruct
    void start() {
        scheduler.scheduleWithFixedDelay(this::run, 2, 2, TimeUnit.SECONDS);
        log.info("Fraud narrator started (NIM {})", nim.enabled() ? "live" : "disabled — using fallback");
    }

    @PreDestroy
    void stop() {
        scheduler.shutdownNow();
    }

    private void run() {
        try {
            List<FraudFlag> pending =
                    flags.findByNarrativePendingTrueOrderByCreatedAtAsc(PageRequest.of(0, BATCH));
            for (FraudFlag flag : pending) {
                flag.attachNarrative(narrate(flag));
                flags.save(flag);
            }
        } catch (Exception e) {
            log.warn("fraud narrator cycle failed: {}", e.toString());
        }
    }

    private String narrate(FraudFlag flag) {
        String user = String.format(
                "Account %s, charge %s. Deterministic decision: %s (risk score %d/100). Signals: %s.",
                flag.getAccountId(), usd(flag.getAmountCents()), flag.getDecision(),
                flag.getRiskScore(), flag.getReasons());
        String ai = nim.chat(SYSTEM, user);
        return (ai != null && !ai.isBlank()) ? ai.trim() : fallback(flag);
    }

    /** Deterministic explanation used when NIM is disabled or unavailable. */
    private String fallback(FraudFlag flag) {
        String action = switch (flag.getDecision()) {
            case HOLD -> "hold the charge and require step-up verification";
            case REVIEW -> "route to manual review before releasing funds";
            default -> "allow";
        };
        return String.format("Risk %s (score %d/100) on account %s for %s. Signals: %s. Recommended action: %s.",
                flag.getDecision(), flag.getRiskScore(), flag.getAccountId(),
                usd(flag.getAmountCents()), flag.getReasons(), action);
    }

    private static String usd(long cents) {
        return String.format("$%,.2f", cents / 100.0);
    }
}
