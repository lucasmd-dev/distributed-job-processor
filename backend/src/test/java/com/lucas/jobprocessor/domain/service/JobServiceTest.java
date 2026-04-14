package com.lucas.jobprocessor.domain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lucas.jobprocessor.api.dto.CreateJobRequest;
import com.lucas.jobprocessor.api.exception.DuplicateJobException;
import com.lucas.jobprocessor.api.exception.InvalidJobRequestException;
import com.lucas.jobprocessor.domain.model.Job;
import com.lucas.jobprocessor.domain.model.JobStatus;
import com.lucas.jobprocessor.domain.repository.JobExecutionRepository;
import com.lucas.jobprocessor.domain.repository.JobRepository;
import com.lucas.jobprocessor.messaging.dto.JobMessage;
import com.lucas.jobprocessor.messaging.publisher.JobPublisher;
import com.lucas.jobprocessor.worker.JobHandlerRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock
    private JobRepository jobRepository;
    @Mock
    private JobExecutionRepository jobExecutionRepository;
    @Mock
    private IdempotencyService idempotencyService;
    @Mock
    private JobPublisher jobPublisher;
    @Mock
    private JobHandlerRouter jobHandlerRouter;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private JobMetricsService jobMetricsService;

    private JobService jobService;

    @BeforeEach
    void setUp() {
        jobService = new JobService(
                jobRepository,
                jobExecutionRepository,
                idempotencyService,
                jobPublisher,
                jobHandlerRouter,
                new JsonPayloadService(new ObjectMapper()),
                auditLogService,
                jobMetricsService,
                (short) 3
        );
    }

    @Test
    void shouldCreateJobWithCanonicalPayloadAndInitialDispatchAttempt() {
        when(jobHandlerRouter.supportsType("EMAIL_SEND")).thenReturn(true);
        when(jobRepository.saveAndFlush(any(Job.class))).thenAnswer(invocation -> {
            Job job = invocation.getArgument(0);
            job.setId(UUID.randomUUID());
            return job;
        });

        jobService.create(new CreateJobRequest("EMAIL_SEND", "{\"b\":1,\"a\":2}", null, null, null));

        ArgumentCaptor<JobMessage> messageCaptor = ArgumentCaptor.forClass(JobMessage.class);
        verify(jobPublisher).publish(messageCaptor.capture());

        JobMessage published = messageCaptor.getValue();
        assertEquals(1, published.runNumber());
        assertEquals(1, published.attempt());
        assertEquals("{\"a\":2,\"b\":1}", published.payload());
    }

    @Test
    void shouldRejectUnsupportedJobType() {
        when(jobHandlerRouter.supportsType("UNKNOWN")).thenReturn(false);
        when(jobHandlerRouter.supportedTypes()).thenReturn(Set.of("EMAIL_SEND"));

        assertThrows(InvalidJobRequestException.class, () ->
                jobService.create(new CreateJobRequest("UNKNOWN", "{}", null, null, null)));
    }

    @Test
    void shouldConvertDatabaseDuplicateIntoConflict() {
        Job existingJob = new Job();
        existingJob.setId(UUID.randomUUID());
        existingJob.setIdempotencyKey("dup-key");

        when(jobHandlerRouter.supportsType("EMAIL_SEND")).thenReturn(true);
        when(idempotencyService.exists("dup-key")).thenReturn(false);
        when(jobRepository.saveAndFlush(any(Job.class))).thenThrow(new DataIntegrityViolationException("duplicate"));
        when(jobRepository.findByIdempotencyKey("dup-key")).thenReturn(Optional.of(existingJob));

        assertThrows(DuplicateJobException.class, () ->
                jobService.create(new CreateJobRequest("EMAIL_SEND", "{}", "dup-key", null, null)));
    }

    @Test
    void shouldAdvanceRunNumberOnManualRetry() {
        UUID jobId = UUID.randomUUID();
        Job job = new Job();
        job.setId(jobId);
        job.setType("WEBHOOK_DISPATCH");
        job.setPayload("{\"forceFailure\":true}");
        job.setStatus(JobStatus.DEAD);
        job.setRunNumber((short) 1);
        job.setAttempt((short) 2);
        job.setMaxAttempts((short) 2);

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

        jobService.retry(jobId);

        ArgumentCaptor<JobMessage> messageCaptor = ArgumentCaptor.forClass(JobMessage.class);
        verify(jobPublisher).publish(messageCaptor.capture());

        JobMessage published = messageCaptor.getValue();
        assertEquals(2, published.runNumber());
        assertEquals(1, published.attempt());
        assertEquals(0, job.getAttempt());
    }
}
