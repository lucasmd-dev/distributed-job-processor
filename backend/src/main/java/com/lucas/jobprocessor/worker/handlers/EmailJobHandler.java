package com.lucas.jobprocessor.worker.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lucas.jobprocessor.messaging.dto.JobMessage;
import com.lucas.jobprocessor.worker.JobHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class EmailJobHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(EmailJobHandler.class);
    private final ObjectMapper objectMapper;

    public EmailJobHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String handle(JobMessage message) {
        log.info("Sending email for job: jobId={}", message.jobId());
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("Email sent successfully: jobId={}", message.jobId());
        try {
            JsonNode payload = objectMapper.readTree(message.payload());
            String recipient = payload.path("to").asText("unknown");
            return objectMapper.writeValueAsString(java.util.Map.of(
                    "status", "SENT",
                    "recipient", recipient
            ));
        } catch (Exception ex) {
            return "{\"status\":\"SENT\"}";
        }
    }

    @Override
    public String getType() {
        return "EMAIL_SEND";
    }
}
