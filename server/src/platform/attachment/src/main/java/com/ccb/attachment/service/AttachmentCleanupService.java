package com.ccb.attachment.service;

import com.ccb.infrastructure.storage.MinioStorageService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class AttachmentCleanupService {
    private final AttachmentPersistenceRepository repository;
    private final MinioStorageService storage;

    public AttachmentCleanupService(AttachmentPersistenceRepository repository, MinioStorageService storage) {
        this.repository = repository;
        this.storage = storage;
    }

    @Scheduled(fixedDelayString = "${ccb.attachment.cleanup-delay-ms:3600000}")
    public void scheduledCleanup() {
        cleanupBatch(100);
    }

    public int cleanupBatch(int limit) {
        int bounded = Math.max(1, Math.min(limit, 500));
        List<Map<String, Object>> rows = repository.cleanupCandidates(bounded);
        for (Map<String, Object> row : rows) cleanup(row);
        return rows.size();
    }

    private void cleanup(Map<String, Object> row) {
        long id = ((Number) row.get("id")).longValue();
        long tenantId = ((Number) row.get("tenant_id")).longValue();
        if ("TEMP".equals(row.get("status"))) {
            int changed = repository.expireTemporaryFile(Map.of("id", id, "tenantId", tenantId));
            if (changed != 1) return;
        }
        try {
            storage.delete(String.valueOf(row.get("object_key")));
            repository.markFileCleanupDone(Map.of("id", id, "tenantId", tenantId));
        } catch (RuntimeException exception) {
            String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            repository.markFileCleanupRetry(Map.of("id", id, "tenantId", tenantId,
                    "error", message.substring(0, Math.min(message.length(), 1000))));
        }
    }
}
