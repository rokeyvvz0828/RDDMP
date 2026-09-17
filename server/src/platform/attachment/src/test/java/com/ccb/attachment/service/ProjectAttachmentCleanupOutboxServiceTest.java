package com.ccb.attachment.service;

import com.ccb.infrastructure.storage.MinioStorageProperties;
import com.ccb.infrastructure.storage.MinioStorageService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectAttachmentCleanupOutboxServiceTest {
    @Test
    void marksClaimedEventDoneAfterStorageDelete() {
        StubJdbc jdbc = new StubJdbc();
        StubStorage storage = new StubStorage(false);
        assertEquals(1, new ProjectAttachmentCleanupOutboxService(jdbc, storage).cleanupBatch(10));
        assertEquals("DONE", jdbc.status);
        assertEquals("attachments/project/a", storage.deletedKey);
    }

    @Test
    void keepsFailedDeletionRetryable() {
        StubJdbc jdbc = new StubJdbc();
        new ProjectAttachmentCleanupOutboxService(jdbc, new StubStorage(true)).cleanupBatch(1);
        assertEquals("RETRY", jdbc.status);
        assertEquals(1, jdbc.attempts);
    }

    @Test
    void reclaimsExpiredProcessingLease() {
        StubJdbc jdbc = new StubJdbc("PROCESSING");
        StubStorage storage = new StubStorage(false);

        assertEquals(1, new ProjectAttachmentCleanupOutboxService(jdbc, storage).cleanupBatch(1));

        assertEquals("DONE", jdbc.status);
        assertEquals("attachments/project/a", storage.deletedKey);
    }

    private static final class StubJdbc extends AttachmentPersistenceRepository {
        private String status;
        private int attempts;

        private StubJdbc() { this("PENDING"); }
        private StubJdbc(String status) { super(null); this.status = status; }

        @Override public List<Map<String, Object>> projectCleanupCandidates(int limit) {
            return List.of(Map.of("id", 41L, "tenant_id", 1L, "object_key", "attachments/project/a"));
        }
        @Override public int claimProjectCleanup(long id) { status = "PROCESSING"; return 1; }
        @Override public int markProjectCleanupDone(long id) { status = "DONE"; return 1; }
        @Override public int markProjectCleanupRetry(Map<String, Object> params) { status = "RETRY"; attempts++; return 1; }
    }

    private static final class StubStorage extends MinioStorageService {
        private final boolean fail;
        private String deletedKey;
        private StubStorage(boolean fail) { super(properties()); this.fail = fail; }
        @Override public void delete(String objectKey) { deletedKey = objectKey; if (fail) throw new IllegalStateException("unavailable"); }
        private static MinioStorageProperties properties() {
            MinioStorageProperties value = new MinioStorageProperties();
            value.setEndpoint("http://127.0.0.1:9000"); value.setAccessKey("test"); value.setSecretKey("test"); value.setBucket("test"); value.setPresignedExpirySeconds(60);
            return value;
        }
    }
}
