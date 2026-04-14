package com.lucas.jobprocessor.domain.service;

import com.lucas.jobprocessor.domain.model.Job;
import com.lucas.jobprocessor.domain.model.JobStatus;
import com.lucas.jobprocessor.domain.repository.JobRepository;
import com.lucas.jobprocessor.messaging.dto.JobMessage;
import com.lucas.jobprocessor.messaging.publisher.JobPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "app.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JobSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(JobSchedulerService.class);

    private final JobRepository jobRepository;
    private final JobPublisher jobPublisher;
    private final AuditLogService auditLogService;

    public JobSchedulerService(JobRepository jobRepository, JobPublisher jobPublisher, AuditLogService auditLogService) {
        this.jobRepository = jobRepository;
        this.jobPublisher = jobPublisher;
        this.auditLogService = auditLogService;
    }

    @Scheduled(fixedDelay = 10000)
    @Transactional
    public void dispatchScheduledJobs() {
        OffsetDateTime now = OffsetDateTime.now();
        List<Job> due = jobRepository.findByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(JobStatus.SCHEDULED, now);

        if (due.isEmpty()) {
            return;
        }

        log.info("Dispatching {} scheduled jobs", due.size());

        for (Job job : due) {
            if (jobRepository.transitionStatus(job.getId(), JobStatus.SCHEDULED, JobStatus.PENDING, now) != 1) {
                continue;
            }

            try {
                JobMessage message = new JobMessage(
                        job.getId(),
                        job.getRunNumber(),
                        job.getType(),
                        job.getPayload(),
                        job.getAttempt() == 0 ? 1 : job.getAttempt(),
                        job.getMaxAttempts()
                );
                jobPublisher.publish(message);
                auditLogService.record(job.getId(), "JOB_DISPATCHED", "SCHEDULER", Map.of(
                        "runNumber", job.getRunNumber(),
                        "attempt", message.attempt()
                ));
                log.info("Scheduled job dispatched: jobId={}", job.getId());
            } catch (RuntimeException ex) {
                jobRepository.transitionStatus(job.getId(), JobStatus.PENDING, JobStatus.SCHEDULED, OffsetDateTime.now());
                log.error("Failed to dispatch scheduled job: jobId={}", job.getId(), ex);
            }
        }
    }
}
