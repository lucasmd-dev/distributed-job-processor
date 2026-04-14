package com.lucas.jobprocessor.domain.repository;

import com.lucas.jobprocessor.domain.model.JobExecution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobExecutionRepository extends JpaRepository<JobExecution, UUID> {

    List<JobExecution> findByJobIdOrderByRunNumberAscAttemptAsc(UUID jobId);

    Optional<JobExecution> findByJobIdAndRunNumberAndAttempt(UUID jobId, short runNumber, short attempt);
}
