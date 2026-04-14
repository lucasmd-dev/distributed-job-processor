package com.lucas.jobprocessor.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lucas.jobprocessor.domain.service.JobMetricsService;
import com.lucas.jobprocessor.messaging.config.RabbitMQConfig;
import com.lucas.jobprocessor.messaging.dto.JobMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.messaging", name = "consumer-enabled", havingValue = "true", matchIfMissing = true)
public class DlqConsumer {

    private static final Logger log = LoggerFactory.getLogger(DlqConsumer.class);

    private final ObjectMapper objectMapper;
    private final JobMetricsService jobMetricsService;

    public DlqConsumer(ObjectMapper objectMapper, JobMetricsService jobMetricsService) {
        this.objectMapper = objectMapper;
        this.jobMetricsService = jobMetricsService;
    }

    @RabbitListener(queues = RabbitMQConfig.DLQ)
    public void consume(Message message) {
        try {
            JobMessage jobMessage = objectMapper.readValue(message.getBody(), JobMessage.class);
            jobMetricsService.incrementDlq(jobMessage.type());
            log.error("Job reached DLQ: jobId={}", jobMessage.jobId());
        } catch (Exception e) {
            jobMetricsService.incrementDlq("UNKNOWN");
            log.error("Job reached DLQ: failed to deserialize message, raw body={}", new String(message.getBody()));
        }
    }
}
