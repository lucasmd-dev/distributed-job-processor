package com.lucas.jobprocessor.domain.service;

import com.lucas.jobprocessor.api.dto.CreateJobRequest;
import com.lucas.jobprocessor.api.dto.JobExecutionResponse;
import com.lucas.jobprocessor.api.dto.JobResponse;
import com.lucas.jobprocessor.api.dto.JobStatsResponse;
import com.lucas.jobprocessor.api.exception.DuplicateJobException;
import com.lucas.jobprocessor.api.exception.InvalidJobRequestException;
import com.lucas.jobprocessor.api.exception.InvalidStatusTransitionException;
import com.lucas.jobprocessor.api.exception.JobNotFoundException;
import com.lucas.jobprocessor.domain.model.Job;
import com.lucas.jobprocessor.domain.model.JobStatus;
import com.lucas.jobprocessor.domain.repository.JobExecutionRepository;
import com.lucas.jobprocessor.domain.repository.JobRepository;
import com.lucas.jobprocessor.messaging.dto.JobMessage;
import com.lucas.jobprocessor.messaging.publisher.JobPublisher;
import com.lucas.jobprocessor.worker.JobHandlerRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class JobService {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    private final JobRepository jobRepository;
    private final JobExecutionRepository jobExecutionRepository;
    private final IdempotencyService idempotencyService;
    private final JobPublisher jobPublisher;
    private final JobHandlerRouter jobHandlerRouter;
    private final JsonPayloadService jsonPayloadService;
    private final AuditLogService auditLogService;
    private final JobMetricsService jobMetricsService;
    private final short defaultMaxAttempts;

    public JobService(
            JobRepository jobRepository,
            JobExecutionRepository jobExecutionRepository,
            IdempotencyService idempotencyService,
            JobPublisher jobPublisher,
            JobHandlerRouter jobHandlerRouter,
            JsonPayloadService jsonPayloadService,
            AuditLogService auditLogService,
            JobMetricsService jobMetricsService,
            @Value("${app.job.max-attempts:3}") short defaultMaxAttempts) {
        this.jobRepository = jobRepository;
        this.jobExecutionRepository = jobExecutionRepository;
        this.idempotencyService = idempotencyService;
        this.jobPublisher = jobPublisher;
        this.jobHandlerRouter = jobHandlerRouter;
        this.jsonPayloadService = jsonPayloadService;
        this.auditLogService = auditLogService;
        this.jobMetricsService = jobMetricsService;
        this.defaultMaxAttempts = defaultMaxAttempts;
    }

    @Transactional
    public JobResponse create(CreateJobRequest request) {
        validateCreateRequest(request);

        String canonicalPayload = jsonPayloadService.canonicalize(request.payload());
        String idempotencyKey = StringUtils.hasText(request.idempotencyKey())
                ? request.idempotencyKey().trim()
                : sha256(request.type() + "|" + canonicalPayload);

        ensureNotDuplicate(idempotencyKey);

        Job job = new Job();
        job.setType(request.type());
        job.setPayload(canonicalPayload);
        job.setIdempotencyKey(idempotencyKey);
        job.setMaxAttempts(request.maxAttempts() != null ? request.maxAttempts() : defaultMaxAttempts);

        boolean isScheduled = request.scheduledAt() != null;
        if (isScheduled) {
            job.setScheduledAt(request.scheduledAt());
            job.setStatus(JobStatus.SCHEDULED);
        }

        try {
            job = jobRepository.saveAndFlush(job);
        } catch (DataIntegrityViolationException ex) {
            throw duplicateFromDatabase(idempotencyKey);
        }

        idempotencyService.save(idempotencyKey, job.getId().toString());
        auditLogService.record(job.getId(), "JOB_CREATED", "API", Map.of(
                "type", job.getType(),
                "runNumber", job.getRunNumber(),
                "scheduled", isScheduled
        ));
        jobMetricsService.incrementCreated(job.getType());

        if (!isScheduled) {
            JobMessage message = toMessage(job);
            jobPublisher.publish(message);
            log.info("Job published to queue: jobId={}, type={}", job.getId(), job.getType());
            auditLogService.record(job.getId(), "JOB_PUBLISHED", "API", Map.of(
                    "attempt", message.attempt(),
                    "runNumber", message.runNumber()
            ));
        } else {
            log.info("Job scheduled: jobId={}, scheduledAt={}", job.getId(), job.getScheduledAt());
            auditLogService.record(job.getId(), "JOB_SCHEDULED", "API", Map.of(
                    "scheduledAt", job.getScheduledAt().toString(),
                    "runNumber", job.getRunNumber()
            ));
        }

        return toResponse(job, List.of());
    }

    @Transactional(readOnly = true)
    public Page<JobResponse> list(JobStatus status, String type, Pageable pageable) {
        Page<Job> page;

        if (status != null && type != null) {
            page = jobRepository.findAllByStatusAndTypeOrderByCreatedAtDesc(status, type, pageable);
        } else if (status != null) {
            page = jobRepository.findAllByStatusOrderByCreatedAtDesc(status, pageable);
        } else if (type != null) {
            page = jobRepository.findAllByTypeOrderByCreatedAtDesc(type, pageable);
        } else {
            page = jobRepository.findAllByOrderByCreatedAtDesc(pageable);
        }

        return page.map(job -> toResponse(job, List.of()));
    }

    @Transactional(readOnly = true)
    public JobResponse findById(UUID id) {
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> new JobNotFoundException("Job not found: " + id));
        List<JobExecutionResponse> executions = jobExecutionRepository.findByJobIdOrderByRunNumberAscAttemptAsc(id)
                .stream()
                .map(this::toExecutionResponse)
                .toList();
        return toResponse(job, executions);
    }

    @Transactional
    public JobResponse cancel(UUID id) {
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> new JobNotFoundException("Job not found: " + id));

        if (!job.getStatus().canTransitionTo(JobStatus.CANCELLED)) {
            throw new InvalidStatusTransitionException(
                    "Cannot cancel job in status %s".formatted(job.getStatus()));
        }

        job.setStatus(JobStatus.CANCELLED);
        job = jobRepository.save(job);
        log.info("Job cancelled: jobId={}", id);
        auditLogService.record(job.getId(), "JOB_CANCELLED", "API", Map.of(
                "runNumber", job.getRunNumber(),
                "attempt", job.getAttempt()
        ));

        return toResponse(job, List.of());
    }

    @Transactional
    public JobResponse retry(UUID id) {
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> new JobNotFoundException("Job not found: " + id));

        if (!job.getStatus().canTransitionTo(JobStatus.PENDING) && job.getStatus() != JobStatus.CANCELLED) {
            throw new InvalidStatusTransitionException(
                    "Cannot retry job in status %s".formatted(job.getStatus()));
        }

        job.setRunNumber((short) (job.getRunNumber() + 1));
        job.setAttempt((short) 0);
        job.setStatus(JobStatus.PENDING);
        job.setScheduledAt(null);
        job = jobRepository.save(job);

        idempotencyService.clearProcessed(job.getId());

        JobMessage message = toMessage(job);
        jobPublisher.publish(message);
        log.info("Job retried and republished: jobId={}", id);
        auditLogService.record(job.getId(), "JOB_RETRIED", "API", Map.of(
                "runNumber", job.getRunNumber(),
                "attempt", message.attempt()
        ));

        return toResponse(job, List.of());
    }

    @Transactional(readOnly = true)
    public JobStatsResponse stats() {
        long pending = jobRepository.countByStatus(JobStatus.PENDING);
        long scheduled = jobRepository.countByStatus(JobStatus.SCHEDULED);
        long running = jobRepository.countByStatus(JobStatus.RUNNING);
        long success = jobRepository.countByStatus(JobStatus.SUCCESS);
        long failed = jobRepository.countByStatus(JobStatus.FAILED);
        long dead = jobRepository.countByStatus(JobStatus.DEAD);
        long cancelled = jobRepository.countByStatus(JobStatus.CANCELLED);
        long total = pending + running + success + failed + dead + cancelled + scheduled;

        return new JobStatsResponse(total, pending, scheduled, running, success, failed, dead, cancelled);
    }

    private JobMessage toMessage(Job job) {
        return new JobMessage(
                job.getId(),
                job.getRunNumber(),
                job.getType(),
                job.getPayload(),
                job.getAttempt() == 0 ? 1 : job.getAttempt(),
                job.getMaxAttempts()
        );
    }

    private JobResponse toResponse(Job job, List<JobExecutionResponse> executions) {
        return new JobResponse(
                job.getId(),
                job.getType(),
                job.getPayload(),
                job.getStatus(),
                job.getIdempotencyKey(),
                job.getRunNumber(),
                job.getAttempt(),
                job.getMaxAttempts(),
                job.getScheduledAt(),
                job.getCreatedAt(),
                job.getUpdatedAt(),
                executions
        );
    }

    private JobExecutionResponse toExecutionResponse(com.lucas.jobprocessor.domain.model.JobExecution exec) {
        return new JobExecutionResponse(
                exec.getId(),
                exec.getRunNumber(),
                exec.getAttempt(),
                exec.getStatus(),
                exec.getWorkerId(),
                exec.getStartedAt(),
                exec.getFinishedAt(),
                exec.getOutput(),
                exec.getErrorMessage()
        );
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private void validateCreateRequest(CreateJobRequest request) {
        if (!jobHandlerRouter.supportsType(request.type())) {
            throw new InvalidJobRequestException(
                    "Unsupported job type '%s'. Supported types: %s".formatted(
                            request.type(),
                            String.join(", ", jobHandlerRouter.supportedTypes())
                    )
            );
        }

        if (request.scheduledAt() != null && request.scheduledAt().isBefore(OffsetDateTime.now())) {
            throw new InvalidJobRequestException("scheduledAt cannot be in the past");
        }
    }

    private void ensureNotDuplicate(String idempotencyKey) {
        if (!idempotencyService.exists(idempotencyKey)) {
            return;
        }

        String existingJobId = jobRepository.findByIdempotencyKey(idempotencyKey)
                .map(Job::getId)
                .map(UUID::toString)
                .or(() -> idempotencyService.getJobId(idempotencyKey))
                .orElse("unknown");

        throw new DuplicateJobException("Job already exists with idempotency key '%s', jobId=%s"
                .formatted(idempotencyKey, existingJobId));
    }

    private DuplicateJobException duplicateFromDatabase(String idempotencyKey) {
        String existingJobId = jobRepository.findByIdempotencyKey(idempotencyKey)
                .map(Job::getId)
                .map(UUID::toString)
                .orElse("unknown");

        return new DuplicateJobException("Job already exists with idempotency key '%s', jobId=%s"
                .formatted(idempotencyKey, existingJobId));
    }
}
