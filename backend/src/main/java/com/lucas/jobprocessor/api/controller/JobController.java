package com.lucas.jobprocessor.api.controller;

import com.lucas.jobprocessor.api.dto.CreateJobRequest;
import com.lucas.jobprocessor.api.dto.JobExecutionResponse;
import com.lucas.jobprocessor.api.dto.JobResponse;
import com.lucas.jobprocessor.api.dto.JobStatsResponse;
import com.lucas.jobprocessor.domain.model.JobStatus;
import com.lucas.jobprocessor.domain.repository.JobExecutionRepository;
import com.lucas.jobprocessor.domain.service.JobService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {

    private final JobService jobService;
    private final JobExecutionRepository jobExecutionRepository;

    public JobController(JobService jobService, JobExecutionRepository jobExecutionRepository) {
        this.jobService = jobService;
        this.jobExecutionRepository = jobExecutionRepository;
    }

    @PostMapping
    public ResponseEntity<JobResponse> create(@Valid @RequestBody CreateJobRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(jobService.create(request));
    }

    @GetMapping
    public ResponseEntity<Page<JobResponse>> list(
            @RequestParam(required = false) JobStatus status,
            @RequestParam(required = false) String type,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(jobService.list(status, type, pageable));
    }

    @GetMapping("/stats")
    public ResponseEntity<JobStatsResponse> stats() {
        return ResponseEntity.ok(jobService.stats());
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(jobService.findById(id));
    }

    @GetMapping("/{id}/executions")
    public ResponseEntity<List<JobExecutionResponse>> executions(@PathVariable UUID id) {
        jobService.findById(id);
        List<JobExecutionResponse> result = jobExecutionRepository.findByJobIdOrderByRunNumberAscAttemptAsc(id)
                .stream()
                .map(exec -> new JobExecutionResponse(
                        exec.getId(),
                        exec.getRunNumber(),
                        exec.getAttempt(),
                        exec.getStatus(),
                        exec.getWorkerId(),
                        exec.getStartedAt(),
                        exec.getFinishedAt(),
                        exec.getOutput(),
                        exec.getErrorMessage()
                ))
                .toList();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<JobResponse> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(jobService.cancel(id));
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<JobResponse> retry(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(jobService.retry(id));
    }
}
