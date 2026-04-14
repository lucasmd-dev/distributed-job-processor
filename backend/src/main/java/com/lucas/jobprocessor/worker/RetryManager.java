package com.lucas.jobprocessor.worker;

import com.lucas.jobprocessor.domain.model.Job;
import com.lucas.jobprocessor.domain.model.JobExecution;
import com.lucas.jobprocessor.domain.model.JobStatus;
import com.lucas.jobprocessor.domain.repository.JobExecutionRepository;
import com.lucas.jobprocessor.domain.repository.JobRepository;
import com.lucas.jobprocessor.domain.service.AuditLogService;
import com.lucas.jobprocessor.messaging.config.RabbitMQConfig;
import com.lucas.jobprocessor.messaging.dto.JobMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.OffsetDateTime;
import java.util.Map;

@Component
public class RetryManager {

    private static final Logger log = LoggerFactory.getLogger(RetryManager.class);

    private final JobRepository jobRepository;
    private final JobExecutionRepository jobExecutionRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final AuditLogService auditLogService;
    private final int retryBackoffBaseSeconds;

    public RetryManager(
            JobRepository jobRepository,
            JobExecutionRepository jobExecutionRepository,
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            AuditLogService auditLogService,
            @Value("${app.job.retry-backoff-base-seconds:5}") int retryBackoffBaseSeconds) {
        this.jobRepository = jobRepository;
        this.jobExecutionRepository = jobExecutionRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.auditLogService = auditLogService;
        this.retryBackoffBaseSeconds = retryBackoffBaseSeconds;
    }

    @Transactional
    public JobStatus handle(
            Job job,
            JobExecution execution,
            Exception ex,
            Channel channel,
            Message message,
            String workerId) throws IOException {
        String errorMessage = ex.getMessage();
        String stackTrace = getStackTrace(ex);
        OffsetDateTime now = OffsetDateTime.now();

        if (job.getAttempt() < job.getMaxAttempts()) {
            long delayMs = (long) Math.pow(retryBackoffBaseSeconds, job.getAttempt()) * 1000;

            job.setStatus(JobStatus.FAILED);
            jobRepository.save(job);

            if (execution == null) {
                execution = new JobExecution();
                execution.setJob(job);
                execution.setRunNumber(job.getRunNumber());
                execution.setAttempt(job.getAttempt());
                execution.setStartedAt(now);
            }

            execution.setStatus(JobStatus.FAILED);
            execution.setWorkerId(workerId);
            execution.setFinishedAt(now);
            execution.setOutput(null);
            execution.setErrorMessage(errorMessage);
            execution.setStackTrace(stackTrace);
            jobExecutionRepository.save(execution);

            JobMessage retryMessage = new JobMessage(
                    job.getId(),
                    job.getRunNumber(),
                    job.getType(),
                    job.getPayload(),
                    job.getAttempt() + 1,
                    job.getMaxAttempts()
            );

            byte[] body = objectMapper.writeValueAsBytes(retryMessage);
            MessageProperties props = new MessageProperties();
            props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            props.setExpiration(String.valueOf(delayMs));

            org.springframework.amqp.core.Message amqpMessage = new org.springframework.amqp.core.Message(body, props);
            rabbitTemplate.send(RabbitMQConfig.RETRY_EXCHANGE, "retry", amqpMessage);
            auditLogService.record(job.getId(), "JOB_RETRY_SCHEDULED", "WORKER", Map.of(
                    "runNumber", job.getRunNumber(),
                    "currentAttempt", job.getAttempt(),
                    "nextAttempt", retryMessage.attempt(),
                    "delayMs", delayMs
            ));

            log.warn("Job scheduled for retry: jobId={}, attempt={}, delayMs={}", job.getId(), job.getAttempt(), delayMs);
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
            return JobStatus.FAILED;
        } else {
            job.setStatus(JobStatus.DEAD);
            jobRepository.save(job);

            if (execution == null) {
                execution = new JobExecution();
                execution.setJob(job);
                execution.setRunNumber(job.getRunNumber());
                execution.setAttempt(job.getAttempt());
                execution.setStartedAt(now);
            }

            execution.setStatus(JobStatus.DEAD);
            execution.setWorkerId(workerId);
            execution.setFinishedAt(now);
            execution.setOutput(null);
            execution.setErrorMessage(errorMessage);
            execution.setStackTrace(stackTrace);
            jobExecutionRepository.save(execution);

            byte[] body = objectMapper.writeValueAsBytes(
                    new JobMessage(
                            job.getId(),
                            job.getRunNumber(),
                            job.getType(),
                            job.getPayload(),
                            job.getAttempt(),
                            job.getMaxAttempts()
                    )
            );
            MessageProperties props = new MessageProperties();
            props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            org.springframework.amqp.core.Message amqpMessage = new org.springframework.amqp.core.Message(body, props);
            rabbitTemplate.send(RabbitMQConfig.DLX, "", amqpMessage);
            auditLogService.record(job.getId(), "JOB_MOVED_TO_DLQ", "WORKER", Map.of(
                    "runNumber", job.getRunNumber(),
                    "attempt", job.getAttempt()
            ));

            log.error("Job moved to DLQ: jobId={}, attempts={}", job.getId(), job.getAttempt());
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
            return JobStatus.DEAD;
        }
    }

    private String getStackTrace(Exception ex) {
        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
