package com.lucas.jobprocessor.messaging.dto;

import java.util.UUID;

public record JobMessage(
        UUID jobId,
        int runNumber,
        String type,
        String payload,
        int attempt,
        int maxAttempts
) {}
