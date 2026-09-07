package com.ccb.architecture.plan.service;

import com.ccb.architecture.plan.model.PlanModels.*;
import com.ccb.architecture.plan.persistence.PlanStore;
import com.ccb.architecture.service.SubsystemParticipationService;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.ProjectMemberReferenceQuery;
import com.ccb.system.capability.SystemReferenceQuery;
import com.ccb.system.capability.ProjectAccess;
import org.springframework.stereotype.Service;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;

/** 分工快照与实时资格的统一边界；管理权不构成执行资格。 */
@Service
public class PlanParticipationService {
    private final PlanStore store;
    private final SubsystemParticipationService systems;
    private final ProjectMemberReferenceQuery members;
    private final SystemReferenceQuery users;

    public PlanParticipationService(PlanStore store, SubsystemParticipationService systems,
            ProjectMemberReferenceQuery members, SystemReferenceQuery users) {
        this.store = store; this.systems = systems; this.members = members; this.users = users;
    }

    public record Assignment(Long ownerUserId, List<Long> participantUserIds,
                             List<SubsystemParticipationService.Candidate> candidates) {}

    public Long systemId(AuthUser actor, long projectId, TargetType type, Long targetId) {
        if (targetId == null || targetId < 0) return null;
        return type == TargetType.DEPLOYMENT_UNIT
                ? store.unitSystemId(actor.tenantId(), projectId, targetId)
                    .orElseThrow(() -> denied("部署单元不存在或不属于当前项目")) : targetId;
    }

    public Assignment defaults(AuthUser actor, long projectId, TargetType type, Long targetId, Long commonOwner) {
        Long systemId = systemId(actor, projectId, type, targetId);
        List<SubsystemParticipationService.Candidate> candidates;
        Long owner;
        if (systemId != null) {
            var view = systems.assignmentScope(actor, new ProjectAccess(projectId, "", ""), systemId);
            owner = view.ownerUserId();
            candidates = view.participants().stream()
                    .filter(c -> view.effectiveParticipantUserIds().contains(c.userId())).toList();
            return new Assignment(owner, view.effectiveParticipantUserIds(), candidates);
        }
        candidates = members.findActiveMembers(actor, projectId).stream()
                .flatMap(m -> users.findUser(actor, m.userId(), true).stream())
                .map(u -> new SubsystemParticipationService.Candidate(u.id(),
                        u.displayName() == null || u.displayName().isBlank() ? u.username() : u.displayName())).toList();
        // 默认分派不能超出执行资格，也不能把没有姓名选项的用户 ID 填入选择器。
        owner = candidates.stream().anyMatch(c -> Objects.equals(c.userId(), commonOwner)) ? commonOwner : null;
        return new Assignment(owner, owner == null ? List.of() : List.of(owner), candidates);
    }

    public Assignment validate(AuthUser actor, long projectId, TargetType type, Long targetId,
                               Long owner, List<Long> participants) {
        Assignment defaults = defaults(actor, projectId, type, targetId, owner);
        Set<Long> eligible = new HashSet<>();
        defaults.candidates().forEach(c -> eligible.add(c.userId()));
        LinkedHashSet<Long> ids = new LinkedHashSet<>(participants == null ? List.of() : participants);
        if (owner == null || owner <= 0) throw denied("任务负责人必填");
        ids.add(owner);
        if (!eligible.containsAll(ids)) throw denied("负责人和参与人必须是有效项目成员及系统参与人员");
        return new Assignment(owner, List.copyOf(ids), defaults.candidates());
    }

    /** 请求内查询上下文；不跨请求缓存资格，写操作仍重新校验并持锁。 */
    public ReadScope readScope(AuthUser actor, long projectId, long planId) {
        return new ReadScope(actor, projectId, planId);
    }

    public final class ReadScope {
        private final AuthUser actor;
        private final long projectId;
        private final boolean manager;
        private final Map<Long, List<Long>> participants;
        private final Map<String, Set<Long>> eligible = new HashMap<>();
        private final Map<Long, String> names = new HashMap<>();
        private ReadScope(AuthUser actor, long projectId, long planId) {
            this.actor = actor; this.projectId = projectId;
            this.manager = PlanParticipationService.this.manager(actor, projectId, planId);
            this.participants = store.findPlanParticipants(actor.tenantId(), projectId, planId);
        }
        public boolean manager() { return manager; }
        public List<Long> participants(long taskId) { return participants.getOrDefault(taskId, List.of()); }
        private Set<Long> eligible(TargetType type, Long targetId) {
            String key = type + ":" + targetId;
            return eligible.computeIfAbsent(key, ignored -> {
                try {
                    return defaults(actor, projectId, type, targetId, null).candidates().stream()
                            .map(SubsystemParticipationService.Candidate::userId)
                            .collect(java.util.stream.Collectors.toSet());
                } catch (BusinessException ex) {
                    if (ex.code() == ErrorCode.FORBIDDEN) return Set.of();
                    throw ex;
                }
            });
        }
        public boolean related(Task task) {
            return actor.enabled() && (actor.id() == task.ownerUserId() || participants(task.id()).contains(actor.id()))
                    && eligible(task.targetType(), task.targetId()).contains(actor.id());
        }
        public boolean attention(TargetType type, Long targetId, long owner, List<Long> assigned) {
            return manager && (!eligible(type, targetId).contains(owner) || !eligible(type, targetId).containsAll(assigned));
        }
        public String name(long id) { return names.computeIfAbsent(id, key -> userName(actor, key)); }
    }

    public boolean related(AuthUser actor, long projectId, Task task) {
        if (actor.id() != task.ownerUserId()
                && !store.findParticipantUserIds(actor.tenantId(), projectId, task.id()).contains(actor.id())) return false;
        return eligible(actor, projectId, task);
    }

    public boolean assignmentNeedsAttention(AuthUser actor, long projectId, TargetType type, Long targetId,
                                             long owner, List<Long> participants) {
        try {
            var eligible = defaults(actor, projectId, type, targetId, owner).candidates().stream()
                    .map(SubsystemParticipationService.Candidate::userId).collect(java.util.stream.Collectors.toSet());
            return !eligible.contains(owner) || !eligible.containsAll(participants);
        } catch (BusinessException ex) {
            if (ex.code() == ErrorCode.FORBIDDEN) return true;
            throw ex;
        }
    }

    public boolean eligible(AuthUser actor, long projectId, Task task) {
        if (!actor.enabled()) return false;
        try {
            return defaults(actor, projectId, task.targetType(), task.targetId(), task.ownerUserId())
                    .candidates().stream().anyMatch(c -> c.userId() == actor.id());
        } catch (BusinessException ex) {
            // 已删除或失效的系统保留管理可见性，但不得继续执行历史任务。
            if (ex.code() == ErrorCode.FORBIDDEN) return false;
            throw ex;
        }
    }

    public boolean activeMember(AuthUser actor, long projectId) {
        return actor.enabled() && users.findUser(actor, actor.id(), true).isPresent()
                && members.findActiveMembers(actor, projectId).stream().anyMatch(m -> m.userId() == actor.id());
    }

    public boolean hasAuthority(AuthUser actor, String authority) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return actor.enabled() && auth != null && auth.isAuthenticated()
                && auth.getPrincipal() instanceof AuthUser user && user.id() == actor.id()
                && user.tenantId() == actor.tenantId()
                && auth.getAuthorities().stream().anyMatch(a -> authority.equals(a.getAuthority()));
    }

    public boolean workOrderVisible(AuthUser actor, long projectId, WorkOrderType type, long id) {
        var applicant = store.workOrderApplicant(actor.tenantId(), projectId, type, id);
        if (applicant.isEmpty()) return false;
        if (hasAuthority(actor, type == WorkOrderType.RESOURCE_REQUEST
                ? "architecture:resource-request:manage" : "architecture:network-work-order:manage")
                || type == WorkOrderType.RESOURCE_REQUEST && administrator(actor)) return true;
        if (applicant.get() != actor.id()) return false;
        if (type != WorkOrderType.RESOURCE_REQUEST) return true;
        var refs = store.resourceRequestRefs(actor.tenantId(), projectId, List.of(id));
        if (refs.isEmpty()) return false;
        try {
            systems.requireSystemParticipant(actor, new ProjectAccess(projectId, "", ""), refs.get(0)[2]);
            return true;
        } catch (BusinessException ex) {
            if (ex.code() == ErrorCode.FORBIDDEN) return false;
            throw ex;
        }
    }

    public String userName(AuthUser actor, long userId) {
        return users.findUser(actor, userId, false).map(u -> u.displayName()).orElse("用户#" + userId);
    }

    public boolean administrator(AuthUser actor) {
        return hasAuthority(actor, "architecture:manage");
    }

    public boolean manager(AuthUser actor, long projectId, long planId) {
        return administrator(actor) || store.findPlan(actor.tenantId(), projectId, planId)
                .map(p -> p.planOwnerUserId() == actor.id()).orElse(false);
    }

    public boolean visible(AuthUser actor, long projectId, Task task) {
        return manager(actor, projectId, task.planId()) || related(actor, projectId, task);
    }

    public void requireVisible(AuthUser actor, long projectId, Task task) {
        if (!visible(actor, projectId, task)) throw denied("无权访问该任务");
    }

    public void requireExecutor(AuthUser actor, long projectId, Task task) {
        Long systemId = systemId(actor, projectId, task.targetType(), task.targetId());
        if (systemId != null) systems.lockAndRequireSystemParticipant(actor,
                new ProjectAccess(projectId, "", ""), systemId);
        Task current = store.lockTask(actor.tenantId(), projectId, task.id())
                .orElseThrow(() -> denied("任务不存在"));
        if (!current.equals(task)) {
            throw new BusinessException(ErrorCode.CONFLICT, "任务已更新，请刷新后重试");
        }
        if (!related(actor, projectId, current)) throw denied("仅当前有效任务负责人或参与人可以执行，管理者不能代执行");
    }

    /** 仅向仍具备查看资格的快照成员发送任务明细，不向退出人员广播。 */
    public List<Long> recipients(AuthUser actor, long projectId, Task task) {
        var assigned = new LinkedHashSet<>(store.findParticipantUserIds(actor.tenantId(), projectId, task.id()));
        assigned.add(task.ownerUserId());
        try {
            return defaults(actor, projectId, task.targetType(), task.targetId(), task.ownerUserId())
                    .candidates().stream().map(SubsystemParticipationService.Candidate::userId)
                    .filter(assigned::contains).distinct().toList();
        } catch (BusinessException ex) {
            if (ex.code() == ErrorCode.FORBIDDEN) return List.of();
            throw ex;
        }
    }

    private static BusinessException denied(String message) { return new BusinessException(ErrorCode.FORBIDDEN, message); }
}
