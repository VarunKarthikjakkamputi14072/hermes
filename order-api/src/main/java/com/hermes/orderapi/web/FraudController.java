package com.hermes.orderapi.web;

import com.hermes.common.domain.RiskDecision;
import com.hermes.common.repository.FraudFlagRepository;
import com.hermes.orderapi.web.dto.FraudFlagResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/fraud")
public class FraudController {

    private final FraudFlagRepository flags;

    public FraudController(FraudFlagRepository flags) {
        this.flags = flags;
    }

    /** Most recent risk flags, newest first — drives the AI fraud-watch panel. */
    @GetMapping("/flags")
    public List<FraudFlagResponse> flags(@RequestParam(defaultValue = "20") int limit) {
        return flags.findAllByOrderByCreatedAtDesc(PageRequest.of(0, Math.min(limit, 100)))
                .stream().map(FraudFlagResponse::from).toList();
    }

    @GetMapping("/stats")
    public Map<String, Long> stats() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (RiskDecision d : RiskDecision.values()) {
            counts.put(d.name(), flags.countByDecision(d));
        }
        return counts;
    }
}
