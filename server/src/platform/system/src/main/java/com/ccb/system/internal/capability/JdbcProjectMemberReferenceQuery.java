package com.ccb.system.internal.capability;

import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.ProjectMemberReference;
import com.ccb.system.capability.ProjectMemberReferenceQuery;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class JdbcProjectMemberReferenceQuery implements ProjectMemberReferenceQuery {
    private final SystemCapabilityRepository repository;

    public JdbcProjectMemberReferenceQuery(SystemCapabilityRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<ProjectMemberReference> findActiveMembers(AuthUser actor, long projectId) {
        Objects.requireNonNull(actor, "actor 不能为空");
        if (projectId <= 0) return List.of();
        return repository.activeProjectMembers(Map.of("tenantId", actor.tenantId(), "projectId", projectId)).stream().map(this::reference).toList();
    }

    @Override
    public Optional<ProjectMemberReference> findActiveMember(AuthUser actor, long projectId, long projectMemberId) {
        Objects.requireNonNull(actor, "actor 不能为空");
        if (projectId <= 0 || projectMemberId <= 0) return Optional.empty();
        Map<String, Object> row = repository.activeProjectMember(Map.of("memberId", projectMemberId, "tenantId", actor.tenantId(), "projectId", projectId));
        return Optional.ofNullable(row).map(this::reference);
    }

    private ProjectMemberReference reference(Map<String, Object> row) { return new ProjectMemberReference(((Number) row.get("id")).longValue(), ((Number) row.get("user_id")).longValue(), String.valueOf(row.get("display_name")), String.valueOf(row.get("username"))); }
}
