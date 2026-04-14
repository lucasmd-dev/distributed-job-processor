package com.lucas.jobprocessor.domain.repository;

import com.lucas.jobprocessor.domain.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
}
