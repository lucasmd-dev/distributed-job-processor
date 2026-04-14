package com.lucas.jobprocessor.domain.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
    private static final String IDEMPOTENCY_PREFIX = "idempotency:";
    private static final String JOB_RESULT_PREFIX = "job:result:";

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;

    public IdempotencyService(
            StringRedisTemplate redisTemplate,
            @org.springframework.beans.factory.annotation.Value("${app.job.idempotency-ttl-hours:24}") long ttlHours) {
        this.redisTemplate = redisTemplate;
        this.ttl = Duration.ofHours(ttlHours);
    }

    public boolean exists(String key) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(IDEMPOTENCY_PREFIX + key));
        } catch (RuntimeException ex) {
            log.warn("Failed to read idempotency key from Redis: key={}", key, ex);
            return false;
        }
    }

    public void save(String key, String jobId) {
        try {
            redisTemplate.opsForValue().set(IDEMPOTENCY_PREFIX + key, jobId, ttl);
        } catch (RuntimeException ex) {
            log.warn("Failed to save idempotency key in Redis: key={}, jobId={}", key, jobId, ex);
        }
    }

    public Optional<String> getJobId(String key) {
        try {
            return Optional.ofNullable(redisTemplate.opsForValue().get(IDEMPOTENCY_PREFIX + key));
        } catch (RuntimeException ex) {
            log.warn("Failed to resolve jobId from Redis for key={}", key, ex);
            return Optional.empty();
        }
    }

    public void markAsProcessed(UUID jobId) {
        try {
            redisTemplate.opsForValue().set(JOB_RESULT_PREFIX + jobId, "SUCCESS", ttl);
        } catch (RuntimeException ex) {
            log.warn("Failed to mark job as processed in Redis: jobId={}", jobId, ex);
        }
    }

    public boolean alreadyProcessed(UUID jobId) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(JOB_RESULT_PREFIX + jobId));
        } catch (RuntimeException ex) {
            log.warn("Failed to read processed marker from Redis: jobId={}", jobId, ex);
            return false;
        }
    }

    public void clearProcessed(UUID jobId) {
        try {
            redisTemplate.delete(JOB_RESULT_PREFIX + jobId);
        } catch (RuntimeException ex) {
            log.warn("Failed to clear processed marker from Redis: jobId={}", jobId, ex);
        }
    }
}
