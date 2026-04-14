package com.lucas.jobprocessor.domain.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lucas.jobprocessor.domain.model.AuditLog;
import com.lucas.jobprocessor.domain.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditLogService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    public void record(UUID jobId, String event, String actor, Map<String, Object> details) {
        AuditLog auditLog = new AuditLog();
        auditLog.setJobId(jobId);
        auditLog.setEvent(event);
        auditLog.setActor(actor);
        auditLog.setDetails(serialize(details));
        auditLogRepository.save(auditLog);
    }

    private String serialize(Map<String, Object> details) {
        if (details == null || details.isEmpty()) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize audit details", ex);
            return null;
        }
    }
}
