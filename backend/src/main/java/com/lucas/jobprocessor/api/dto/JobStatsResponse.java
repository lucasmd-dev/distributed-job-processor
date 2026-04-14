package com.lucas.jobprocessor.api.dto;

public record JobStatsResponse(
        long total,
        long pending,
        long scheduled,
        long running,
        long success,
        long failed,
        long dead,
        long cancelled
) {}
