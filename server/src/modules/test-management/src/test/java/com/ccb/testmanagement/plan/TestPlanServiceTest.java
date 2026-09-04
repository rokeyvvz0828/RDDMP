package com.ccb.testmanagement.plan;

import com.ccb.attachment.integration.AttachmentGateway;
import com.ccb.attachment.integration.AttachmentItem;
import com.ccb.common.exception.BusinessException;
import com.ccb.security.model.AuthUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestPlanServiceTest {
    @Mock JdbcTemplate jdbc;
    @Mock AttachmentGateway attachments;
    private final AuthUser operator = new AuthUser(1, 1, "admin", "", "管理员", 1, true);

    @Test
    void rejectsUnknownDomainBeforeReadingProjectOrAttachmentData() {
        TestPlanService service = new TestPlanService(jdbc, attachments);

        assertThrows(BusinessException.class, () -> service.tree("unsafe-domain", 1, operator));

        verifyNoInteractions(jdbc, attachments);
    }

    @Test
    void rejectsDuplicateSpecialNodeInSameProject() {
        TestPlanService service = new TestPlanService(jdbc, attachments);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L);

        assertThrows(BusinessException.class, () -> service.createSpecial("application-assembly", 1, Map.of("node_name", "批量交易专项"), operator));
    }

    @Test
    void rejectsLegacyDocumentFormatBeforeAnyPlanOrVersionWrite() {
        TestPlanService service = new TestPlanService(jdbc, attachments);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L);
        when(attachments.get(91L, operator)).thenReturn(new AttachmentItem(
                91L, "测试方案.doc", "application/msword", 1024L, "doc", "TEMP",
                null, null, null, operator.id(), null));

        assertThrows(BusinessException.class, () -> service.upload("application-assembly", 1, null,
                Map.of("attachment_id", 91L, "plan_name", "核心方案", "version_note", "首次上传", "node_type", "PROJECT"), operator));

        verify(attachments).get(91L, operator);
    }
}
