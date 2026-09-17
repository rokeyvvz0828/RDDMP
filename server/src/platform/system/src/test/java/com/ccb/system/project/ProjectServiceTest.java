package com.ccb.system.project;

import com.ccb.attachment.model.AttachmentPort;
import com.ccb.common.audit.OperationAuditContext;
import com.ccb.common.exception.BusinessException;
import com.ccb.infrastructure.storage.MinioStorageService;
import com.ccb.security.model.AuthUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {
    private static final long PROJECT_ID = 9001L;

    @Mock private MinioStorageService storage;
    @Mock private AttachmentPort attachmentPort;
    @Mock private ProjectMemberMapper projectMemberMapper;
    @Mock private ProjectRepository projectRepository;

    private final AuthUser member = new AuthUser(7L, 1L, "member", "", "Member", 1L, true);
    private final AuthUser admin = new AuthUser(1L, 1L, "admin", "", "Admin", 1L, true);

    @AfterEach
    void clearRequestState() {
        SecurityContextHolder.clearContext();
        OperationAuditContext.clear();
    }

    @Test
    void createsProjectWithCreationTypeAndInitializesDefaults() {
        asSuperAdmin();
        when(projectRepository.activeUserCount(admin.id(), admin.tenantId())).thenReturn(1L);
        when(projectRepository.roleCount(anyLong(), anyLong(), anyLong())).thenReturn(1L);
        ProjectService service = spy(service());
        doReturn(Map.of("id", PROJECT_ID)).when(service).detail(anyLong(), eq(admin));

        Map<String, Object> result = service.create(Map.of("project_code", "TEST-070", "project_name", "Creation type",
                "creation_type", "CONTINUATION"), admin);

        assertEquals(PROJECT_ID, result.get("id"));
        ArgumentCaptor<Map<String, Object>> project = mapCaptor();
        verify(projectRepository).createProject(project.capture());
        assertEquals("CONTINUATION", project.getValue().get("creationType"));
        assertEquals(admin.tenantId(), project.getValue().get("tenantId"));
        verify(projectRepository, org.mockito.Mockito.times(7)).createProjectStage(any());
        verify(projectRepository).createProjectManagerRole(anyLong(), eq(admin.tenantId()), anyLong());
        verify(projectRepository).createMember(any());
        verify(projectRepository).createAudit(anyLong(), eq(admin.tenantId()), eq(admin.id()), eq("project:create"), any());
    }

    @Test
    void rejectsInvalidCreationTypeBeforeWriting() {
        asSuperAdmin();

        assertThrows(BusinessException.class, () -> service().create(Map.of("project_code", "TEST-070",
                "project_name", "Creation type", "creation_type", "INVALID"), admin));
        assertThrows(BusinessException.class, () -> service().update(PROJECT_ID, Map.of("creation_type", "INVALID"), admin));

        verify(projectRepository, never()).createProject(any());
        verify(projectRepository, never()).updateProject(any());
    }

    @Test
    void updatesOnlyProvidedCreationTypeAndAuditsChange() {
        asSuperAdmin();
        accessibleProject();
        when(projectRepository.project(PROJECT_ID, admin.tenantId())).thenReturn(new HashMap<>(Map.of("id", PROJECT_ID)));
        ProjectService service = spy(service());
        doReturn(Map.of("id", PROJECT_ID)).when(service).detail(PROJECT_ID, admin);

        service.update(PROJECT_ID, Map.of("project_name", "Changed", "creation_type", "CONTINUATION"), admin);

        ArgumentCaptor<Map<String, Object>> values = mapCaptor();
        verify(projectRepository).updateProject(values.capture());
        assertEquals("CONTINUATION", values.getValue().get("creationType"));
        assertEquals("Changed", values.getValue().get("projectName"));
        verify(projectRepository).createAudit(anyLong(), eq(admin.tenantId()), eq(admin.id()),
                eq("project:update"), eq(String.valueOf(PROJECT_ID)));
    }

    @Test
    void rejectsProjectActionWhenMemberPermissionIsMissing() {
        when(projectRepository.superAdminCount(member.id(), member.tenantId())).thenReturn(0L);
        when(projectRepository.projectActionCount(PROJECT_ID, member.tenantId(), member.id(),
                "project:project:list", "read")).thenReturn(0L);

        assertThrows(BusinessException.class, () -> service().detail(PROJECT_ID, member));

        verify(projectRepository, never()).projectCount(anyLong(), anyLong());
    }

    @Test
    void workbenchDecoratesProjectsUsingBatchRepositoryQueries() {
        asSuperAdmin();
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(new HashMap<>(Map.of("id", 9001L, "owner_id", 11L)));
        rows.add(new HashMap<>(Map.of("id", 9002L, "owner_id", 12L)));
        when(projectRepository.workbench(admin.tenantId(), null)).thenReturn(rows);
        when(projectRepository.userDisplayNames(admin.tenantId(), List.of(11L, 12L))).thenReturn(List.of(
                Map.of("id", 11L, "display_name", "Alice"), Map.of("id", 12L, "display_name", "Bob")));
        when(projectRepository.projectStatistics(admin.tenantId(), List.of(9001L, 9002L))).thenReturn(List.of(
                Map.of("id", 9001L, "member_count", 2L, "plan_count", 5L, "completed_plan_count", 3L, "plan_progress", 66.666),
                Map.of("id", 9002L, "member_count", 0L, "plan_count", 0L, "completed_plan_count", 0L)));

        List<Map<String, Object>> result = service().workbench(admin);

        assertEquals("Alice", result.get(0).get("owner_name"));
        assertEquals(2, result.get(0).get("member_count"));
        assertEquals(66.67, ((Number) result.get(0).get("plan_progress")).doubleValue(), 0.001);
        assertEquals("Bob", result.get(1).get("owner_name"));
        assertEquals(0.0, ((Number) result.get(1).get("plan_progress")).doubleValue());
    }

    @Test
    void membersKeepRoleAssignmentsAndDecorateAvatarUrls() {
        asSuperAdmin();
        accessibleProject();
        when(projectMemberMapper.selectMembers(PROJECT_ID, admin.tenantId())).thenReturn(List.of(
                Map.of("id", 101L, "user_id", 11L, "display_name", "Alice", "avatar_object_key", "avatars/alice"),
                Map.of("id", 102L, "user_id", 12L, "display_name", "Bob", "avatar_object_key", "avatars/bob")));
        when(projectMemberMapper.selectRolesByMemberIds(List.of(101L, 102L), admin.tenantId())).thenReturn(List.of(
                Map.of("member_id", 101L, "id", 201L, "role_code", "OWNER"),
                Map.of("member_id", 102L, "id", 202L, "role_code", "MEMBER")));
        when(storage.presignedUrl("avatars/alice")).thenReturn("https://files/alice");
        when(storage.presignedUrl("avatars/bob")).thenReturn("https://files/bob");

        List<Map<String, Object>> result = service().members(PROJECT_ID, admin);

        assertEquals("https://files/alice", result.get(0).get("avatar_url"));
        assertEquals("OWNER", ((Map<?, ?>) ((List<?>) result.get(0).get("roles")).get(0)).get("role_code"));
        assertEquals("MEMBER", ((Map<?, ?>) ((List<?>) result.get(1).get("roles")).get(0)).get("role_code"));
    }

    @Test
    void deletesProjectAttachmentsAndDependentsThenWritesAudit() {
        asSuperAdmin();
        accessibleProject();
        when(projectRepository.deleteProject(PROJECT_ID, admin.tenantId())).thenReturn(1);

        service().delete(PROJECT_ID, admin);

        verify(attachmentPort).enqueueBusinessDeletion("PROJECT", PROJECT_ID, admin.tenantId());
        verify(projectRepository).deleteProjectDependents(PROJECT_ID, admin.tenantId());
        verify(projectRepository).createAudit(anyLong(), eq(admin.tenantId()), eq(admin.id()),
                eq("project:delete"), eq(String.valueOf(PROJECT_ID)));
    }

    @Test
    void deletesAttachmentThroughPortAndWritesAudit() {
        asSuperAdmin();
        accessibleProject();

        service().deleteAttachment(PROJECT_ID, 4001L, admin);

        verify(attachmentPort).delete(4001L, "PROJECT", PROJECT_ID, admin.tenantId());
        verify(projectRepository).createAudit(anyLong(), eq(admin.tenantId()), eq(admin.id()),
                eq("project:attachment:delete"), eq("4001"));
    }

    private ProjectService service() {
        return new ProjectService(storage, attachmentPort, projectMemberMapper, projectRepository);
    }

    private void asSuperAdmin() {
        when(projectRepository.superAdminCount(admin.id(), admin.tenantId())).thenReturn(1L);
    }

    private void accessibleProject() {
        when(projectRepository.projectCount(PROJECT_ID, admin.tenantId())).thenReturn(1L);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArgumentCaptor<Map<String, Object>> mapCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(Map.class);
    }
}
