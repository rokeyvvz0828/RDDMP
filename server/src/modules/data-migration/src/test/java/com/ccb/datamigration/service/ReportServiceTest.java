package com.ccb.datamigration.service;

import com.ccb.attachment.integration.AttachmentBindingCommand;
import com.ccb.attachment.integration.AttachmentGateway;
import com.ccb.attachment.integration.AttachmentItem;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.infrastructure.storage.MinioStorageProperties;
import com.ccb.infrastructure.storage.MinioStorageService;
import com.ccb.security.model.AuthUser;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportServiceTest {
    private static final AuthUser USER = new AuthUser(7L, 1L, "developer", "", "研发人员", 11L, true);

    @Test
    void batchUploadPersistsValidatedDigestForEachTemporaryAttachment() {
        List<String> events = new ArrayList<>();
        StubJdbcTemplate jdbc = new StubJdbcTemplate(events);
        StubAttachmentGateway attachments = new StubAttachmentGateway(events);
        ReportService service = service(jdbc, attachments);
        List<AttachmentItem> items = List.of(attachment(201L, "one.pdf"), attachment(202L, "two.pdf"));

        service.batchUpload(10L, "MONTHLY", items,
            List.of("900150983cd24fb0d6963f7d28e17f72", "f96b697d7cb7938d525a2f31aaf161d0"), USER);

        assertEquals(List.of("900150983cd24fb0d6963f7d28e17f72", "f96b697d7cb7938d525a2f31aaf161d0"),
            jdbc.insertedDigests);
        assertEquals(2, attachments.boundIds.size());
    }

    @Test
    void batchUploadRejectsMismatchedDigestListBeforeWriting() {
        List<String> events = new ArrayList<>();
        StubJdbcTemplate jdbc = new StubJdbcTemplate(events);
        StubAttachmentGateway attachments = new StubAttachmentGateway(events);
        ReportService service = service(jdbc, attachments);

        BusinessException error = assertThrows(BusinessException.class,
            () -> service.batchUpload(10L, "MONTHLY", List.of(attachment(201L, "one.pdf")), List.of(), USER));

        assertEquals(ErrorCode.BAD_REQUEST, error.code());
        assertTrue(jdbc.insertedDigests.isEmpty());
        assertTrue(attachments.boundIds.isEmpty());
    }

    @Test
    void replacementBindsNewAttachmentThenUpdatesRowThenRetiresOldBinding() {
        List<String> events = new ArrayList<>();
        StubJdbcTemplate jdbc = new StubJdbcTemplate(events);
        jdbc.putReport(50L, 10L, 101L, false);
        StubAttachmentGateway attachments = new StubAttachmentGateway(events);
        attachments.item = attachment(202L, "replacement.pdf");
        ReportService service = service(jdbc, attachments);

        service.update(50L, null, null, null, null, null, 202L,
            "900150983cd24fb0d6963f7d28e17f72", USER);

        assertOrdered(events, "bind:202", "db:file-update", "delete:101");
        assertEquals(202L, ((Number) jdbc.reports.get(50L).get("attachment_id")).longValue());
    }

    @Test
    void restoreDoesNotAuditWhenDeletedRowChangesConcurrently() {
        List<String> events = new ArrayList<>();
        StubJdbcTemplate jdbc = new StubJdbcTemplate(events);
        jdbc.putReport(50L, 10L, 101L, true);
        jdbc.restoreUpdateCount = 0;
        ReportService service = service(jdbc, new StubAttachmentGateway(events));

        BusinessException error = assertThrows(BusinessException.class, () -> service.restore(List.of(50L), USER));

        assertEquals(ErrorCode.CONFLICT, error.code());
        assertTrue(events.stream().noneMatch(event -> event.equals("audit:REPORT_RESTORE")));
    }

    @Test
    void restoreAuditsExactlyOnceAfterSuccessfulStateChange() {
        List<String> events = new ArrayList<>();
        StubJdbcTemplate jdbc = new StubJdbcTemplate(events);
        jdbc.putReport(50L, 10L, 101L, true);
        ReportService service = service(jdbc, new StubAttachmentGateway(events));

        service.restore(List.of(50L), USER);

        assertEquals(List.of("db:restore", "audit:REPORT_RESTORE"),
            events.stream().filter(event -> event.equals("db:restore") || event.startsWith("audit:")).toList());
    }

    private ReportService service(StubJdbcTemplate jdbc, AttachmentGateway attachments) {
        return new ReportService(jdbc, attachments, new MinioStorageService(storageProperties()),
            new DataMigrationPermissionService(jdbc));
    }

    private static AttachmentItem attachment(long id, String name) {
        return new AttachmentItem(id, name, "application/pdf", 8L, "pdf", "TEMP",
            null, null, null, USER.id(), LocalDateTime.of(2026, 8, 24, 9, 0));
    }

    private static MinioStorageProperties storageProperties() {
        MinioStorageProperties properties = new MinioStorageProperties();
        properties.setEndpoint("http://127.0.0.1:9000");
        properties.setAccessKey("local-test");
        properties.setSecretKey("local-test-secret");
        properties.setBucket("local-test");
        return properties;
    }

    private static void assertOrdered(List<String> events, String... expected) {
        int previous = -1;
        for (String event : expected) {
            int current = events.indexOf(event);
            assertTrue(current > previous, () -> "Expected ordered event " + event + " in " + events);
            previous = current;
        }
    }

    private static final class StubAttachmentGateway implements AttachmentGateway {
        private final List<String> events;
        private final List<Long> boundIds = new ArrayList<>();
        private AttachmentItem item;

        private StubAttachmentGateway(List<String> events) {
            this.events = events;
        }

        @Override
        public void bind(AttachmentBindingCommand command, AuthUser operator) {
            boundIds.add(command.attachmentId());
            events.add("bind:" + command.attachmentId());
        }

        @Override
        public AttachmentItem get(long attachmentId, AuthUser operator) {
            return item;
        }

        @Override
        public void deleteBound(long attachmentId, String businessType, String businessKey, AuthUser operator) {
            events.add("delete:" + attachmentId);
        }
    }

    private static final class StubJdbcTemplate extends JdbcTemplate {
        private final List<String> events;
        private final Map<Long, Map<String, Object>> reports = new LinkedHashMap<>();
        private final List<String> insertedDigests = new ArrayList<>();
        private int restoreUpdateCount = 1;

        private StubJdbcTemplate(List<String> events) {
            this.events = events;
        }

        private void putReport(long id, long projectId, long attachmentId, boolean deleted) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", id);
            row.put("project_id", projectId);
            row.put("asset_type", "REPORT");
            row.put("asset_code", "REPORT-" + id);
            row.put("asset_name", "Report");
            row.put("content_type", "application/pdf");
            row.put("file_size", 8L);
            row.put("attachment_id", attachmentId);
            row.put("checksum_md5", "old");
            row.put("report_period", "MONTHLY");
            row.put("report_date", null);
            row.put("keywords", "migration");
            row.put("owner_id", USER.id());
            row.put("deleted", deleted ? 1 : 0);
            reports.put(id, row);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (sql.contains("FROM pm_project")) return (T) Integer.valueOf(1);
            if (sql.contains("checksum_md5")) return (T) Integer.valueOf(0);
            if (sql.contains("FROM sys_user_role")) return (T) Integer.valueOf(1);
            throw new AssertionError("Unexpected queryForObject: " + sql);
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            if (!sql.contains("FROM dm_asset WHERE id")) return List.of();
            long id = ((Number) args[0]).longValue();
            boolean deleted = ((Number) args[2]).intValue() == 1;
            Map<String, Object> row = reports.get(id);
            if (row == null || (((Number) row.get("deleted")).intValue() == 1) != deleted) return List.of();
            return List.of(row);
        }

        @Override
        public int update(String sql, Object... args) {
            if (sql.startsWith("INSERT INTO dm_asset")) {
                long id = ((Number) args[0]).longValue();
                putReport(id, ((Number) args[2]).longValue(), ((Number) args[7]).longValue(), false);
                reports.get(id).put("checksum_md5", args[8]);
                insertedDigests.add(String.valueOf(args[8]));
                return 1;
            }
            if (sql.startsWith("UPDATE dm_asset SET attachment_id")) {
                long id = ((Number) args[5]).longValue();
                reports.get(id).put("attachment_id", args[0]);
                reports.get(id).put("checksum_md5", args[1]);
                events.add("db:file-update");
                return 1;
            }
            if (sql.startsWith("UPDATE dm_asset SET updated_by")) return 1;
            if (sql.startsWith("UPDATE dm_asset SET deleted = 0")) {
                events.add("db:restore");
                if (restoreUpdateCount == 1) reports.get(((Number) args[0]).longValue()).put("deleted", 0);
                return restoreUpdateCount;
            }
            if (sql.startsWith("INSERT INTO dm_operation_log")) {
                events.add("audit:" + args[2]);
                return 1;
            }
            throw new AssertionError("Unexpected update: " + sql);
        }
    }
}
