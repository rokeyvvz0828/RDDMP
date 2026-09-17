package com.ccb.system.capability;

import com.ccb.security.model.AuthUser;
import com.ccb.system.internal.capability.JdbcProjectMemberReferenceQuery;
import com.ccb.system.internal.capability.SystemCapabilityRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectMemberReferenceQueryTest {
    @Test
    void queriesOnlyActiveMembersInActorTenantAndProject() {
        SystemCapabilityRepository repository = mock(SystemCapabilityRepository.class);
        AuthUser actor = new AuthUser(7L, 1L, "operator", "", "操作员", 1L, true);
        when(repository.activeProjectMembers(Map.of("tenantId", 1L, "projectId", 9001L))).thenReturn(List.of(
                Map.of("id", 7001L, "user_id", 42L, "display_name", "项目成员", "username", "member")));

        List<ProjectMemberReference> result = new JdbcProjectMemberReferenceQuery(repository)
                .findActiveMembers(actor, 9001L);

        assertEquals(List.of(new ProjectMemberReference(7001L, 42L, "项目成员", "member")), result);
        verify(repository).activeProjectMembers(eq(Map.of("tenantId", 1L, "projectId", 9001L)));
    }

    @Test
    void returnsEmptyWithoutQueryForInvalidProjectOrMemberReference() {
        SystemCapabilityRepository repository = mock(SystemCapabilityRepository.class);
        JdbcProjectMemberReferenceQuery query = new JdbcProjectMemberReferenceQuery(repository);
        AuthUser actor = new AuthUser(7L, 1L, "operator", "", "操作员", 1L, true);

        assertEquals(List.of(), query.findActiveMembers(actor, 0));
        assertEquals(java.util.Optional.empty(), query.findActiveMember(actor, 9001L, 0));
    }
}
