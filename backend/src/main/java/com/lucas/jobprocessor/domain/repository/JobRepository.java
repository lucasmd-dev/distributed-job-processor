package com.lucas.jobprocessor.domain.repository;

import com.lucas.jobprocessor.domain.model.Job;
import com.lucas.jobprocessor.domain.model.JobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobRepository extends JpaRepository<Job, UUID> {

    Page<Job> findAllByStatusOrderByCreatedAtDesc(JobStatus status, Pageable pageable);

    Page<Job> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<Job> findAllByTypeOrderByCreatedAtDesc(String type, Pageable pageable);

    Page<Job> findAllByStatusAndTypeOrderByCreatedAtDesc(JobStatus status, String type, Pageable pageable);

    Optional<Job> findByIdempotencyKey(String idempotencyKey);

    long countByStatus(JobStatus status);

    List<Job> findByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(JobStatus status, OffsetDateTime now);

    @Modifying
    @Query("""
            update Job job
            set job.status = :nextStatus,
                job.updatedAt = :updatedAt
            where job.id = :jobId
              and job.status = :expectedStatus
            """)
    int transitionStatus(
            @Param("jobId") UUID jobId,
            @Param("expectedStatus") JobStatus expectedStatus,
            @Param("nextStatus") JobStatus nextStatus,
            @Param("updatedAt") OffsetDateTime updatedAt
    );
}
