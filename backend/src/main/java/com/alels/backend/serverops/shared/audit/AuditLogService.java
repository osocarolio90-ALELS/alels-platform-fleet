package com.alels.backend.serverops.shared.audit;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {
    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Async("auditTaskExecutor")
    public void logServerOpsRead(Long userId, Long companyId, String path, String method, int status, String ipAddress) {
        String metadata = "{\"method\":\"" + escape(method) + "\",\"path\":\"" + escape(path) + "\",\"status\":" + status + "}";
        auditLogRepository.insert(userId, companyId, "SERVEROPS_READ", "SERVER_OPERATIONS", path, ipAddress, metadata);
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
