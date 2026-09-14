package com.ccb.architecture.service;

import com.ccb.architecture.persistence.SubsystemParticipationStore;
import com.ccb.architecture.persistence.SubsystemParticipationStore.SystemScope;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.ProjectAccess;
import com.ccb.system.capability.ProjectMemberRemovalGuard;
import com.ccb.system.capability.ProjectMemberReferenceQuery;
import com.ccb.system.capability.SystemReferenceQuery;
import com.ccb.system.capability.SystemOperationAudit;
import com.ccb.system.capability.SystemOperationAuditCommand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 系统参与资格唯一入口：负责人隐含参与，项目退出及用户停用实时失权，无管理员执行豁免。 */
@Service
public class SubsystemParticipationService implements ProjectMemberRemovalGuard {
    public record ReplaceCommand(List<Long> participantUserIds, Long rowVersion, String reason) { }
    public record Candidate(long userId, String displayName) { }
    public record ParticipationView(Long ownerUserId, List<Long> explicitParticipantUserIds,
            List<Long> effectiveParticipantUserIds, long rowVersion, boolean canManage, List<Candidate> participants) { }

    private final SubsystemParticipationStore store;
    private final ProjectMemberReferenceQuery members;
    private final SystemReferenceQuery users;
    private final SystemOperationAudit audit;
    private final TransactionTemplate transactions;

    public SubsystemParticipationService(SubsystemParticipationStore store, ProjectMemberReferenceQuery members,
            SystemReferenceQuery users, SystemOperationAudit audit, TransactionTemplate transactions) {
        this.store = store;
        this.members = members;
        this.users = users;
        this.audit = audit;
        this.transactions = transactions;
    }

    public ParticipationView detail(AuthUser actor, ProjectAccess project, long systemId, boolean manager) {
        SystemScope system = system(actor, project, systemId, false);
        return view(actor, project, system, manager);
    }

    /** 分派事务使用与撤销相同的父锁，但不要求管理者本人参与系统。 */
    public ParticipationView assignmentScope(AuthUser actor, ProjectAccess project, long systemId) {
        return view(actor, project, system(actor, project, systemId,
                TransactionSynchronizationManager.isActualTransactionActive()
                        && !TransactionSynchronizationManager.isCurrentTransactionReadOnly()), false);
    }

    public List<Candidate> candidates(AuthUser actor, ProjectAccess project, long systemId, boolean manager) {
        SystemScope system = system(actor, project, systemId, false);
        requireMaintainer(actor, system, manager);
        return members.findActiveMembers(actor, project.id()).stream()
                .filter(m -> users.findUser(actor, m.userId(), true).isPresent())
                .map(m -> new Candidate(m.userId(), m.displayName())).toList();
    }

    public Set<Long> participatingSystemIds(AuthUser actor, ProjectAccess project) {
        requireContext(actor, project);
        if (!activeMember(actor, project)) return Set.of();
        return Set.copyOf(store.participatingSystemIds(actor.tenantId(), project.id(), actor.id()));
    }

    public boolean activeMember(AuthUser actor, ProjectAccess project) {
        return actor != null && actor.enabled() && users.findUser(actor, actor.id(), true).isPresent()
                && members.findActiveMembers(actor, project.id()).stream().anyMatch(m -> m.userId() == actor.id());
    }

    public void requireSystemParticipant(AuthUser actor, ProjectAccess project, long systemId) {
        if (!detail(actor, project, systemId, false).effectiveParticipantUserIds().contains(actor.id())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅有效系统参与人员可以执行该操作");
        }
    }

    /** 调用者须在同一事务持锁直至业务写入完成，不能在校验后另开事务。 */
    public void lockAndRequireSystemParticipant(AuthUser actor, ProjectAccess project, long systemId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("参与资格执行校验必须处于业务写入事务中");
        }
        SystemScope system = system(actor, project, systemId, true);
        if (!view(actor, project, system, false).effectiveParticipantUserIds().contains(actor.id())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅有效系统参与人员可以执行该操作");
        }
    }

    public long requireUnitSystem(AuthUser actor, ProjectAccess project, long unitId) {
        requireContext(actor, project);
        long systemId = store.findUnitSystem(actor.tenantId(), project.id(), unitId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "部署单元不存在或无权访问"));
        requireSystemParticipant(actor, project, systemId);
        return systemId;
    }

    public ParticipationView replace(AuthUser actor, ProjectAccess project, long systemId, ReplaceCommand command,
                                     boolean manager, String traceId) {
        requireContext(actor, project);
        String path = "/api/architecture/physical-subsystems/" + systemId + "/participants";
        try {
            ParticipationView result = transactions.execute(tx -> {
                SystemScope system = system(actor, project, systemId, true);
                requireMaintainer(actor, system, manager);
                if (command == null || command.rowVersion() == null || command.participantUserIds() == null
                        || command.participantUserIds().size() > 1000
                        || (command.reason() != null && command.reason().trim().length() > 500)) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "参与人员和版本不能为空，变更原因不能超过500字");
                }
                if (command.rowVersion() != system.rowVersion()) throw conflict();
                LinkedHashSet<Long> requested = new LinkedHashSet<>(command.participantUserIds());
                Set<Long> active = activeUsers(actor, project, requested);
                if (requested.stream().anyMatch(id -> id == null || id <= 0 || !active.contains(id))) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "参与人员必须是有效项目成员");
                }
                // 负责人参与来自系统事实，不能通过显式名单将负责人排除。
                requested.remove(system.ownerUserId());
                List<Long> before = store.findExplicit(actor.tenantId(), project.id(), systemId);
                for (long removed : before) {
                    if (!requested.contains(removed) && !Objects.equals(system.ownerUserId(), removed)
                            && store.hasPendingResponsibility(actor.tenantId(), project.id(), systemId, removed)) {
                        throw new BusinessException(ErrorCode.CONFLICT, "请先移交退出人员的未完成任务或未解决阻塞责任");
                    }
                }
                if (!store.advanceVersion(actor.tenantId(), project.id(), systemId, system.rowVersion(), actor.id())) {
                    throw conflict();
                }
                List<Long> after = new ArrayList<>(requested);
                store.replace(actor.tenantId(), project.id(), systemId, after, actor.id());
                store.recordChange(actor.tenantId(), project.id(), systemId, actor.id(), before, after,
                        command.reason() == null ? "" : command.reason().trim(), traceId);
                return view(actor, project, new SystemScope(systemId, system.ownerUserId(), system.rowVersion() + 1), manager);
            });
            audit.recordSuccess(new SystemOperationAuditCommand(actor, "architecture.subsystem.participants.update",
                    "PUT", path, null, traceId));
            return result;
        } catch (RuntimeException ex) {
            audit.recordFailure(new SystemOperationAuditCommand(actor, "architecture.subsystem.participants.update",
                    "PUT", path, ex.getMessage(), traceId));
            throw ex;
        }
    }

    /** 平台成员停用或删除时调用；包含未绑定系统的公共任务责任。 */
    @Override
    public void requireNoPendingTasks(long tenantId, long projectId, long userId) {
        if (store.hasProjectPendingResponsibility(tenantId, projectId, userId)) {
            throw new BusinessException(ErrorCode.CONFLICT, "该成员仍有搭建任务或未解决阻塞，请先移交责任");
        }
    }

    /** 在变更工单发布事务内校验旧负责人退出，父锁与人员撤销使用同一锁序。 */
    public void requireOwnerTransfer(long tenantId, long projectId, long systemId, Long nextOwnerUserId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("负责人移交校验必须在发布事务内执行");
        }
        SystemScope current = store.findSystem(tenantId, projectId, systemId, true)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONFLICT, "系统不存在或已失效"));
        Long previousOwner = current.ownerUserId();
        if (previousOwner == null || Objects.equals(previousOwner, nextOwnerUserId)) return;
        // 显式参与仍保留时并未退出系统，不要求无关的强制移交。
        if (!store.findExplicit(tenantId, projectId, systemId).contains(previousOwner)
                && store.hasPendingResponsibility(tenantId, projectId, systemId, previousOwner)) {
            throw new BusinessException(ErrorCode.CONFLICT, "请先移交原系统负责人的未完成任务或未解决阻塞责任");
        }
    }

    private ParticipationView view(AuthUser actor, ProjectAccess project, SystemScope system, boolean manager) {
        List<Long> explicit = store.findExplicit(actor.tenantId(), project.id(), system.id());
        LinkedHashSet<Long> effective = new LinkedHashSet<>();
        if (system.ownerUserId() != null) effective.add(system.ownerUserId());
        effective.addAll(explicit);
        List<Candidate> participants = effective.stream().map(id -> new Candidate(id,
                users.findUser(actor, id, false).map(u -> u.displayName()).orElse("已失效成员"))).toList();
        effective.retainAll(activeUsers(actor, project, effective));
        return new ParticipationView(system.ownerUserId(), List.copyOf(explicit), List.copyOf(effective),
                system.rowVersion(), manager || Objects.equals(system.ownerUserId(), actor.id()), participants);
    }

    private Set<Long> activeUsers(AuthUser actor, ProjectAccess project, Set<Long> candidates) {
        return members.findActiveMembers(actor, project.id()).stream()
                .map(m -> m.userId()).distinct().filter(candidates::contains).filter(id -> users.findUser(actor, id, true).isPresent())
                .collect(Collectors.toSet());
    }

    private SystemScope system(AuthUser actor, ProjectAccess project, long id, boolean lock) {
        requireContext(actor, project);
        if (id <= 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "系统标识不合法");
        return store.findSystem(actor.tenantId(), project.id(), id, lock)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "系统不存在或无权访问"));
    }

    private void requireContext(AuthUser actor, ProjectAccess project) {
        if (actor == null || !actor.enabled()) throw new BusinessException(ErrorCode.UNAUTHORIZED, "请重新登录");
        if (project == null || project.id() <= 0) throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问项目");
    }

    private void requireMaintainer(AuthUser actor, SystemScope system, boolean manager) {
        if (!manager && !Objects.equals(system.ownerUserId(), actor.id())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅系统负责人或授权管理者可以维护参与人员");
        }
    }

    private BusinessException conflict() {
        return new BusinessException(ErrorCode.CONFLICT, "系统已被修改，请刷新后重试");
    }
}
