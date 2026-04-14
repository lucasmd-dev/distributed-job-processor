package com.lucas.jobprocessor.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lucas.jobprocessor.domain.model.Job;
import com.lucas.jobprocessor.domain.model.JobExecution;
import com.lucas.jobprocessor.domain.model.JobStatus;
import com.lucas.jobprocessor.domain.repository.JobExecutionRepository;
import com.lucas.jobprocessor.domain.repository.JobRepository;
import com.lucas.jobprocessor.domain.service.AuditLogService;
import com.lucas.jobprocessor.domain.service.IdempotencyService;
import com.lucas.jobprocessor.domain.service.JobMetricsService;
import com.lucas.jobprocessor.messaging.config.RabbitMQConfig;
import com.lucas.jobprocessor.messaging.dto.JobMessage;
import com.lucas.jobprocessor.worker.JobHandlerRouter;
import com.lucas.jobprocessor.worker.RetryManager;
import com.rabbitmq.client.Channel;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetAddress;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "app.messaging", name = "consumer-enabled", havingValue = "true", matchIfMissing = true)
public class JobConsumer {

    private static final Logger log = LoggerFactory.getLogger(JobConsumer.class);
    private static final Duration JOB_STATE_SYNC_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration JOB_STATE_SYNC_POLL_INTERVAL = Duration.ofMillis(50);

    private final ObjectMapper objectMapper;
    private final JobRepository jobRepository;
    private final JobExecutionRepository jobExecutionRepository;
    private final IdempotencyService idempotencyService;
    private final JobHandlerRouter jobHandlerRouter;
    private final RetryManager retryManager;
    private final AuditLogService auditLogService;
    private final JobMetricsService jobMetricsService;

    private String workerId;

    public JobConsumer(
            ObjectMapper objectMapper,
            JobRepository jobRepository,
            JobExecutionRepository jobExecutionRepository,
            IdempotencyService idempotencyService,
            JobHandlerRouter jobHandlerRouter,
            RetryManager retryManager,
            AuditLogService auditLogService,
            JobMetricsService jobMetricsService) {
        this.objectMapper = objectMapper;
        this.jobRepository = jobRepository;
        this.jobExecutionRepository = jobExecutionRepository;
        this.idempotencyService = idempotencyService;
        this.jobHandlerRouter = jobHandlerRouter;
        this.retryManager = retryManager;
        this.auditLogService = auditLogService;
        this.jobMetricsService = jobMetricsService;
    }

    @PostConstruct
    void init() {
        try {
            String hostname = InetAddress.getLocalHost().getHostName();
            long pid = ProcessHandle.current().pid();
            this.workerId = hostname + "-" + pid;
        } catch (Exception e) {
            this.workerId = "worker-" + ProcessHandle.current().pid();
        }
        log.info("Job consumer initialized with workerId={}", workerId);
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE, containerFactory = "rabbitListenerContainerFactory")
    public void consume(Message message, Channel channel) throws IOException {
        JobMessage jobMessage = null;
        JobExecution execution = null;
        Timer.Sample executionSample = null;
        try {
            jobMessage = objectMapper.readValue(message.getBody(), JobMessage.class);

            MDC.put("jobId", jobMessage.jobId().toString());
            MDC.put("attempt", String.valueOf(jobMessage.attempt()));
            MDC.put("workerId", workerId);

            if (idempotencyService.alreadyProcessed(jobMessage.jobId())) {
                log.info("Job already processed, skipping: jobId={}", jobMessage.jobId());
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
                return;
            }

            Optional<Job> visibleJob = findVisibleJob(jobMessage);
            if (visibleJob.isEmpty()) {
                log.warn("Job state not visible yet, requeueing message: jobId={}, messageRunNumber={}",
                        jobMessage.jobId(), jobMessage.runNumber());
                channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, true);
                return;
            }

            Job job = visibleJob.get();

            if (jobMessage.runNumber() < job.getRunNumber()) {
                log.info("Stale job message received, skipping: jobId={}, messageRunNumber={}, currentRunNumber={}",
                        job.getId(), jobMessage.runNumber(), job.getRunNumber());
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
                return;
            }

            if (jobMessage.attempt() < job.getAttempt()
                    || (jobMessage.attempt() == job.getAttempt()
                    && (job.getStatus() == JobStatus.FAILED || job.getStatus() == JobStatus.DEAD))) {
                log.info("Stale job attempt received, skipping: jobId={}, messageAttempt={}, currentAttempt={}, status={}",
                        job.getId(), jobMessage.attempt(), job.getAttempt(), job.getStatus());
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
                return;
            }

            if (job.getStatus() == JobStatus.SUCCESS || job.getStatus() == JobStatus.CANCELLED) {
                log.info("Job already in terminal state, skipping: jobId={}, status={}", job.getId(), job.getStatus());
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
                return;
            }

            job.setStatus(JobStatus.RUNNING);
            job.setAttempt((short) jobMessage.attempt());
            jobRepository.save(job);

            execution = jobExecutionRepository.findByJobIdAndRunNumberAndAttempt(
                            jobMessage.jobId(),
                            (short) jobMessage.runNumber(),
                            (short) jobMessage.attempt())
                    .orElseGet(JobExecution::new);

            execution.setJob(job);
            execution.setRunNumber((short) jobMessage.runNumber());
            execution.setAttempt((short) jobMessage.attempt());
            execution.setStatus(JobStatus.RUNNING);
            execution.setWorkerId(workerId);
            execution.setStartedAt(execution.getStartedAt() != null ? execution.getStartedAt() : OffsetDateTime.now());
            execution.setFinishedAt(null);
            execution.setOutput(null);
            execution.setErrorMessage(null);
            execution.setStackTrace(null);
            execution = jobExecutionRepository.save(execution);
            executionSample = jobMetricsService.startExecution();

            String output = jobHandlerRouter.handle(jobMessage);

            execution.setStatus(JobStatus.SUCCESS);
            execution.setFinishedAt(OffsetDateTime.now());
            execution.setOutput(output);
            jobExecutionRepository.save(execution);

            job.setStatus(JobStatus.SUCCESS);
            jobRepository.save(job);

            idempotencyService.markAsProcessed(jobMessage.jobId());
            auditLogService.record(job.getId(), "JOB_SUCCESS", "WORKER", Map.of(
                    "runNumber", job.getRunNumber(),
                    "attempt", job.getAttempt(),
                    "workerId", workerId
            ));
            jobMetricsService.recordExecution(job.getType(), JobStatus.SUCCESS, executionSample);
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);

            log.info("Job processed successfully: jobId={}", jobMessage.jobId());

        } catch (Exception ex) {
            if (jobMessage != null) {
                log.error("Job processing failed: jobId={}, error={}", jobMessage.jobId(), ex.getMessage());
                try {
                    Job job = jobRepository.findById(jobMessage.jobId()).orElse(null);
                    if (job != null) {
                        JobStatus finalStatus = retryManager.handle(job, execution, ex, channel, message, workerId);
                        if (executionSample != null) {
                            jobMetricsService.recordExecution(job.getType(), finalStatus, executionSample);
                        }
                    } else {
                        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
                    }
                } catch (Exception retryEx) {
                    log.error("Failed to handle retry for jobId={}", jobMessage.jobId(), retryEx);
                    channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, true);
                }
            } else {
                log.error("Failed to deserialize job message", ex);
                jobMetricsService.incrementDlq("UNKNOWN");
                channel.basicReject(message.getMessageProperties().getDeliveryTag(), false);
            }
        } finally {
            MDC.clear();
        }
    }

    private Optional<Job> findVisibleJob(JobMessage jobMessage) {
        long deadline = System.nanoTime() + JOB_STATE_SYNC_TIMEOUT.toNanos();

        while (true) {
            Optional<Job> job = jobRepository.findById(jobMessage.jobId());
            if (job.isPresent() && job.get().getRunNumber() >= jobMessage.runNumber()) {
                return job;
            }

            if (System.nanoTime() >= deadline) {
                return Optional.empty();
            }

            try {
                Thread.sleep(JOB_STATE_SYNC_POLL_INTERVAL.toMillis());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
        }
    }
}
