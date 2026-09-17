package com.ccb.attachment.service;

import com.ccb.infrastructure.storage.MinioStorageService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** Clears project attachment objects after the deletion transaction has committed. */
@Service
public class ProjectAttachmentCleanupOutboxService {
    private static final int MAX_BATCH_SIZE = 100;
    private final AttachmentPersistenceRepository repository;
    private final MinioStorageService storage;

    public ProjectAttachmentCleanupOutboxService(AttachmentPersistenceRepository repository, MinioStorageService storage) {
        this.repository = repository;
        this.storage = storage;
    }

    @Scheduled(fixedDelayString = "${ccb.attachment.project-cleanup-delay-ms:60000}")
    public void scheduledCleanup() {
        cleanupBatch(MAX_BATCH_SIZE);
    }

    public int cleanupBatch(int limit) {
        int size = Math.max(1, Math.min(limit, MAX_BATCH_SIZE));
        List<Map<String, Object>> candidates = repository.projectCleanupCandidates(size);
        int processed = 0;
        for (Map<String, Object> row : candidates) {
            long id = ((Number) row.get("id")).longValue();
            if (repository.claimProjectCleanup(id) != 1) continue;
            processed++;
            clean(id, String.valueOf(row.get("object_key")));
        }
        return processed;
    }

    private void clean(long id, String objectKey) {
        try {
            storage.delete(objectKey);
            repository.markProjectCleanupDone(id);
        } catch (RuntimeException exception) {
            String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            repository.markProjectCleanupRetry(Map.of("id", id,
                    "error", message.substring(0, Math.min(message.length(), 1000))));
        }
    }
}
