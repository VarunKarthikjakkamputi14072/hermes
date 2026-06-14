package com.hermes.orderapi.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateIngestRequest(
        @NotBlank String docId,
        String source,
        @Min(0) @Max(1_000_000) int chunkCount
) {
}
