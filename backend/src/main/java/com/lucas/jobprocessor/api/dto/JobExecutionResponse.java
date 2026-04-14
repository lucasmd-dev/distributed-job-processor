package com.lucas.jobprocessor.api.dto;

import com.lucas.jobprocessor.domain.model.JobStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record JobExecutionResponse(
        UUID id,
        int runNumber,
        int attempt,
        JobStatus status,
        String workerId,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        String output,
        String errorMessage
) {}
