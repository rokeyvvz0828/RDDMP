package com.ccb.datamigration.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ccb.attachment.integration.AttachmentGateway;
import com.ccb.attachment.integration.AttachmentItem;
import com.ccb.common.exception.BusinessException;
import com.ccb.security.model.AuthUser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 治理测试：验证通用文件资产链路已彻底下线（REQ-20260911-070）。
 * ContentFileAssetService 仅保留 BUSINESS_TYPE/resolveAttachment/replaceMainFile；
 * 通用 CRUD/回收站方法与 MANAGED_TYPES 不得残留，DEPENDENCY/PARAMETER 由专属服务承接。
 */
class ContentFileAssetDocCodeGovernanceTest {
    private static final AuthUser USER = new AuthUser(7L, 1L, "developer", "", "研发人员", 11L, true);

    @Test
    void serviceKeepsOnlyReusedAttachmentCapabilitiesWithThreeArgConstructor() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AttachmentGateway gateway = mock(AttachmentGateway.class);
        ContentFileAssetService service = new ContentFileAssetService(
                jdbc, gateway, mock(ContentAttachmentService.class));

        assertEquals("DATA_MIGRATION_ASSET", ContentFileAssetService.BUSINESS_TYPE);
    }

    @Test
    void resolveAttachmentRejectsNonTemporaryAttachments() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AttachmentGateway gateway = mock(AttachmentGateway.class);
        AttachmentItem committed = new AttachmentItem(101L, "a.pdf", "application/pdf", 8L, "pdf",
                "COMMITTED", null, null, null, USER.id(),
                LocalDateTime.of(2026, 9, 11, 9, 0));
        when(gateway.get(101L, USER)).thenReturn(committed);
        ContentFileAssetService service = new ContentFileAssetService(
                jdbc, gateway, mock(ContentAttachmentService.class));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.resolveAttachment(101L, USER));
        assertTrue(ex.getMessage().contains("临时附件"));
    }

    @Test
    void genericCrudAndRecycleBinMethodsAreRemovedFromSource() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/ccb/datamigration/service/ContentFileAssetService.java"));
        assertTrue(source.contains("BUSINESS_TYPE"));
        assertTrue(source.contains("resolveAttachment"));
        assertTrue(source.contains("replaceMainFile"));
        assertFalse(source.contains("MANAGED_TYPES"));
        List<String> removedSignatures = List.of(
                "PageResult<Map<String, Object>> list(",
                "Map<String, Object> create(",
                "Map<String, Object> replace(",
                "void delete(",
                "void restore(",
                "void purge(",
                "long downloadAttachmentId(",
                "long countDeleted(",
                "List<Map<String, Object>> listDeletedPage(",
                "Map<String, Object> findDeletedDetail(");
        for (String signature : removedSignatures) {
            assertFalse(source.contains(signature), "通用方法不应残留：" + signature);
        }
    }
}
