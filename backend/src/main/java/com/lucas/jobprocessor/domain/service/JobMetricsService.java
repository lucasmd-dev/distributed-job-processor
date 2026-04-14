package com.lucas.jobprocessor.domain.service;

import com.lucas.jobprocessor.domain.model.JobStatus;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

@Service
public class JobMetricsService {

    private final MeterRegistry meterRegistry;

    public JobMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void incrementCreated(String type) {
        meterRegistry.counter("jobs.created.total", "type", type).increment();
    }

    public Timer.Sample startExecution() {
        return Timer.start(meterRegistry);
    }

    public void recordExecution(String type, JobStatus status, Timer.Sample sample) {
        meterRegistry.counter(
                "jobs.executed.total",
                "type", type,
                "status", status.name()
        ).increment();

        sample.stop(meterRegistry.timer(
                "jobs.execution.duration",
                "type", type,
                "status", status.name()
        ));
    }

    public void incrementRetry(String type, int attempt) {
        meterRegistry.counter(
                "jobs.retry.total",
                "type", type,
                "attempt", String.valueOf(attempt)
        ).increment();
    }

    public void incrementDlq(String type) {
        meterRegistry.counter("jobs.dlq.total", "type", type).increment();
    }
}
