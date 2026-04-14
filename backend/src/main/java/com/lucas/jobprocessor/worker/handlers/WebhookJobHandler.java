package com.lucas.jobprocessor.worker.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lucas.jobprocessor.messaging.dto.JobMessage;
import com.lucas.jobprocessor.worker.JobHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class WebhookJobHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(WebhookJobHandler.class);
    private final ObjectMapper objectMapper;

    public WebhookJobHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String handle(JobMessage message) {
        log.info("Dispatching webhook for job: jobId={}", message.jobId());

        JsonNode payload;
        try {
            payload = objectMapper.readTree(message.payload());
            Thread.sleep(payload.path("delayMs").asInt(300));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Webhook dispatch interrupted", e);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid webhook payload", ex);
        }

        if (payload.path("forceFailure").asBoolean(false)) {
            throw new RuntimeException(payload.path("failureMessage").asText("Webhook endpoint returned 500"));
        }

        log.info("Webhook dispatched successfully: jobId={}", message.jobId());
        try {
            return objectMapper.writeValueAsString(java.util.Map.of(
                    "status", "DELIVERED",
                    "target", payload.path("url").asText("unknown")
            ));
        } catch (Exception ex) {
            return "{\"status\":\"DELIVERED\"}";
        }
    }

    @Override
    public String getType() {
        return "WEBHOOK_DISPATCH";
    }
}
