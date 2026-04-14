package com.lucas.jobprocessor.messaging.publisher;

import com.lucas.jobprocessor.messaging.config.RabbitMQConfig;
import com.lucas.jobprocessor.messaging.dto.JobMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class JobPublisher {

    private static final Logger log = LoggerFactory.getLogger(JobPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public JobPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(JobMessage message) {
        log.debug("Publishing job to queue: jobId={}, type={}", message.jobId(), message.type());
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KEY, message);
    }
}
