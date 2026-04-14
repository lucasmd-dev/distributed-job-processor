package com.lucas.jobprocessor.api.dto;

import com.lucas.jobprocessor.domain.model.JobStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record JobResponse(
        UUID id,
        String type,
        String payload,
        JobStatus status,
        String idempotencyKey,
        int runNumber,
        int attempt,
        int maxAttempts,
        OffsetDateTime scheduledAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<JobExecutionResponse> executions
) {}
