package com.ccb.architecture.standard.service;

import com.ccb.architecture.standard.model.StandardModels.DocumentStatus;
import com.ccb.architecture.standard.model.StandardModels.StandardCommand;
import com.ccb.architecture.standard.model.StandardModels.StandardDocument;
import com.ccb.architecture.standard.persistence.StandardStore;
import com.ccb.architecture.web.ArchitectureNotFoundException;
import com.ccb.attachment.integration.AttachmentGateway;
import com.ccb.attachment.model.AttachmentPort;
import com.ccb.common.exception.BusinessException;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.SystemParameterReference;
import com.ccb.system.capability.SystemReferenceQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArchitectureStandardServiceTest {
    private static final AuthUser VIEWER = new AuthUser(1L, 1L, "viewer", "", "查看人", 0L, true);

    @Mock
    private StandardStore store;
    @Mock
    private SystemReferenceQuery referenceQuery;
    @Mock
    private AttachmentPort attachmentPort;
    @Mock
    private AttachmentGateway attachmentGateway;

    private ArchitectureStandardService service;

    @BeforeEach
    void setUp() {
        service = new ArchitectureStandardService(store, referenceQuery, attachmentPort, attachmentGateway);
    }

    @Test
    void 创建文档校验类别来自平台参数并写入草稿() {
        when(referenceQuery.activeParameters(VIEWER, "ARCH_STANDARD_CATEGORY"))
                .thenReturn(List.of(new SystemParameterReference("DEPLOYMENT_SPEC", "部署规范")));
        when(store.findDocument(eq(1L), anyLong()))
                .thenReturn(Optional.empty(), Optional.of(draftDocument(1000L, "部署规范 v1")));

        StandardDocument created = service.create(VIEWER,
                new StandardCommand("部署规范 v1", "deployment_spec", "摘要", "正文"));

        assertThat(created.status()).isEqualTo(DocumentStatus.DRAFT);
        assertThat(created.title()).isEqualTo("部署规范 v1");
        verify(store).createDocument(anyLong(), anyLong(), any(), anyLong(), any());
    }

    @Test
    void 类别不在平台参数中时拒绝创建() {
        when(referenceQuery.activeParameters(VIEWER, "ARCH_STANDARD_CATEGORY")).thenReturn(List.of());

        assertThatThrownBy(() -> service.create(VIEWER,
                new StandardCommand("标题", "UNKNOWN_CATEGORY", null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("类别不存在或已停用");
        verify(store, never()).createDocument(anyLong(), anyLong(), any(), anyLong(), any());
    }

    @Test
    void 已下线文档不能编辑() {
        when(referenceQuery.activeParameters(VIEWER, "ARCH_STANDARD_CATEGORY"))
                .thenReturn(List.of(new SystemParameterReference("DEPLOYMENT_SPEC", "部署规范")));
        when(store.findDocument(1L, 101L)).thenReturn(Optional.of(document(101L, DocumentStatus.OFFLINE)));

        assertThatThrownBy(() -> service.update(VIEWER, 101L, 1L,
                new StandardCommand("新标题", "DEPLOYMENT_SPEC", null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已下线文档不能编辑");
    }

    @Test
    void 发布追加版本快照且重复发布被拒绝() {
        when(store.findDocument(1L, 101L)).thenReturn(Optional.of(document(101L, DocumentStatus.PUBLISHED)));

        assertThatThrownBy(() -> service.publish(VIEWER, 101L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文档已发布，无需重复发布");
    }

    @Test
    void 只有已发布文档可以下线() {
        when(store.findDocument(1L, 101L)).thenReturn(Optional.of(document(101L, DocumentStatus.DRAFT)));

        assertThatThrownBy(() -> service.offline(VIEWER, 101L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只有已发布文档可以下线");
    }

    @Test
    void 只有草稿可以删除且发布文档删除被拒绝() {
        when(store.findDocument(1L, 101L)).thenReturn(Optional.of(document(101L, DocumentStatus.PUBLISHED)));

        assertThatThrownBy(() -> service.delete(VIEWER, 101L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只有从未发布的草稿可以删除");
    }

    @Test
    void 详情不存在返回404语义() {
        when(store.findDocument(1L, 42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detail(VIEWER, 42L))
                .isInstanceOf(ArchitectureNotFoundException.class);
    }

    @Test
    void 未发布草稿没有版本历史() {
        when(store.findDocument(1L, 101L)).thenReturn(Optional.of(document(101L, DocumentStatus.DRAFT)));

        assertThat(service.versions(VIEWER, 101L)).isEmpty();
        verify(store, never()).listVersions(anyLong(), anyLong());
    }

    @Test
    void 已发布文档返回版本历史() {
        when(store.findDocument(1L, 101L)).thenReturn(Optional.of(document(101L, DocumentStatus.PUBLISHED)));
        when(store.listVersions(1L, 101L)).thenReturn(List.of());

        assertThat(service.versions(VIEWER, 101L)).isEmpty();
        verify(store).listVersions(1L, 101L);
    }

    @Test
    void 查询状态只接受受控取值() {
        assertThatThrownBy(() -> service.list(VIEWER, null,
                new com.ccb.architecture.standard.model.StandardModels.StandardQuery(null, null, "BROKEN")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("状态仅支持 DRAFT、PUBLISHED 或 OFFLINE");
    }

    private StandardDocument document(long id, DocumentStatus status) {
        return document(id, status, "网络规划规范");
    }

    private StandardDocument draftDocument(long id, String title) {
        return document(id, DocumentStatus.DRAFT, title);
    }

    private StandardDocument document(long id, DocumentStatus status, String title) {
        LocalDateTime now = LocalDateTime.of(2026, 8, 23, 10, 0);
        return new StandardDocument(id, 1L, title, "NETWORK_PLANNING", "摘要", "正文",
                status, status == DocumentStatus.DRAFT ? 0 : 2, now, 1L, "测试员", 1L,
                1L, "测试员", now, now);
    }
}
