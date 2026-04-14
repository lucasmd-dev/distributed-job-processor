package com.lucas.jobprocessor.worker.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lucas.jobprocessor.messaging.dto.JobMessage;
import com.lucas.jobprocessor.worker.JobHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ReportJobHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(ReportJobHandler.class);
    private final ObjectMapper objectMapper;

    public ReportJobHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String handle(JobMessage message) {
        log.info("Generating report for job: jobId={}", message.jobId());
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("Report generated successfully: jobId={}", message.jobId());
        try {
            return objectMapper.writeValueAsString(java.util.Map.of(
                    "status", "GENERATED",
                    "jobId", message.jobId()
            ));
        } catch (Exception ex) {
            return "{\"status\":\"GENERATED\"}";
        }
    }

    @Override
    public String getType() {
        return "REPORT_GENERATE";
    }
}
