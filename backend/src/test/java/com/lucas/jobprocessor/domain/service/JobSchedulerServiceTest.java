package com.lucas.jobprocessor.domain.service;

import com.lucas.jobprocessor.domain.model.Job;
import com.lucas.jobprocessor.domain.model.JobStatus;
import com.lucas.jobprocessor.domain.repository.JobRepository;
import com.lucas.jobprocessor.messaging.publisher.JobPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobSchedulerServiceTest {

    @Mock
    private JobRepository jobRepository;
    @Mock
    private JobPublisher jobPublisher;
    @Mock
    private AuditLogService auditLogService;

    @Test
    void shouldPublishOnlyClaimedScheduledJobs() {
        Job claimedJob = new Job();
        claimedJob.setId(UUID.randomUUID());
        claimedJob.setType("REPORT_GENERATE");
        claimedJob.setPayload("{}");
        claimedJob.setStatus(JobStatus.SCHEDULED);
        claimedJob.setRunNumber((short) 1);
        claimedJob.setAttempt((short) 0);
        claimedJob.setMaxAttempts((short) 3);
        claimedJob.setScheduledAt(OffsetDateTime.now().minusSeconds(5));

        Job skippedJob = new Job();
        skippedJob.setId(UUID.randomUUID());
        skippedJob.setType("EMAIL_SEND");
        skippedJob.setPayload("{}");
        skippedJob.setStatus(JobStatus.SCHEDULED);
        skippedJob.setRunNumber((short) 1);
        skippedJob.setAttempt((short) 0);
        skippedJob.setMaxAttempts((short) 3);
        skippedJob.setScheduledAt(OffsetDateTime.now().minusSeconds(5));

        when(jobRepository.findByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(eq(JobStatus.SCHEDULED), any()))
                .thenReturn(List.of(claimedJob, skippedJob));
        when(jobRepository.transitionStatus(eq(claimedJob.getId()), eq(JobStatus.SCHEDULED), eq(JobStatus.PENDING), any()))
                .thenReturn(1);
        when(jobRepository.transitionStatus(eq(skippedJob.getId()), eq(JobStatus.SCHEDULED), eq(JobStatus.PENDING), any()))
                .thenReturn(0);

        JobSchedulerService service = new JobSchedulerService(jobRepository, jobPublisher, auditLogService);

        service.dispatchScheduledJobs();

        verify(jobPublisher).publish(any());
        verify(auditLogService).record(eq(claimedJob.getId()), eq("JOB_DISPATCHED"), eq("SCHEDULER"), any());
        verify(auditLogService, never()).record(eq(skippedJob.getId()), eq("JOB_DISPATCHED"), eq("SCHEDULER"), any());
    }
}
