package com.lucas.jobprocessor.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.FutureOrPresent;

import java.time.OffsetDateTime;

public record CreateJobRequest(
        @NotBlank String type,
        @NotNull @Size(max = 512_000) String payload,
        String idempotencyKey,
        @FutureOrPresent OffsetDateTime scheduledAt,
        @Min(1) @Max(10) Short maxAttempts
) {}
