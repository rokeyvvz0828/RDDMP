package com.ccb.architecture.service;

import com.ccb.architecture.model.DeliveryUnitModels.DeliveryUnit;
import com.ccb.architecture.model.DeliveryUnitModels.DeliveryUnitCommand;
import com.ccb.architecture.model.DeliveryUnitModels.DeliveryUnitQuery;
import com.ccb.architecture.model.DeliveryUnitModels.DeploymentUnitRef;
import com.ccb.architecture.persistence.DeliveryUnitNumberCapacityExceededException;
import com.ccb.architecture.persistence.DeliveryUnitStore;
import com.ccb.architecture.persistence.DeliveryUnitStore.PhysicalSubsystemProjection;
import com.ccb.architecture.service.DeploymentUnitService.RelatedDeploymentUnitView;
import com.ccb.architecture.web.ArchitectureNotFoundException;
import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.ProjectAccess;
import com.ccb.system.capability.SystemOperationAudit;
import com.ccb.system.capability.SystemOperationAuditCommand;
import com.ccb.system.capability.SystemReferenceQuery;
import com.ccb.system.capability.SystemUserReference;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;

/**
 * 交付单元维护：名称与归属物理子系统固定、编号创建时分配、与部署单元的无方向关联。
 *
 * <p>归属物理子系统创建后不可变更；关联双方必须属于同一物理子系统且均为启用状态，
 * 该不变量同时由数据库复合外键与本节校验保证。</p>
 */
@Service
public class DeliveryUnitService {
    public static final String RESOURCE_PATH = "/api/architecture/delivery-units";

    private static final String CREATE_OPERATION = "ARCHITECTURE_DELIVERY_UNIT_CREATE";
    private static final String UPDATE_OPERATION = "ARCHITECTURE_DELIVERY_UNIT_UPDATE";
    private static final String RELATE_OPERATION = "ARCHITECTURE_DELIVERY_UNIT_RELATE";
    private static final String DEACTIVATE_OPERATION = "ARCHITECTURE_DELIVERY_UNIT_DEACTIVATE";
    private static final String REACTIVATE_OPERATION = "ARCHITECTURE_DELIVERY_UNIT_REACTIVATE";
    private static final String DELETE_OPERATION = "ARCHITECTURE_DELIVERY_UNIT_DELETE";
    private static final String DEPLOYMENT_UNIT_RELATE_OPERATION =
            "ARCHITECTURE_DEPLOYMENT_UNIT_RELATE_DELIVERY_UNIT";
    private static final String DEPLOYMENT_UNIT_RESOURCE_PATH = "/api/architecture/deployment-units";
    private static final String ACTIVE = "ACTIVE";
    private static final String INACTIVE = "INACTIVE";

    private final DeliveryUnitStore store;
    private final DeploymentUnitService deploymentUnitService;
    private final SystemReferenceQuery referenceQuery;
    private final SystemOperationAudit operationAudit;
    private final TransactionTemplate transactions;
    private final LongSupplier identifiers;

    @org.springframework.beans.factory.annotation.Autowired
    public DeliveryUnitService(DeliveryUnitStore store,
                               DeploymentUnitService deploymentUnitService,
                               SystemReferenceQuery referenceQuery,
                               SystemOperationAudit operationAudit,
                               TransactionTemplate transactions) {
        this(store, deploymentUnitService, referenceQuery, operationAudit, transactions,
                () -> System.currentTimeMillis() * 1_000 + ThreadLocalRandom.current().nextInt(1_000));
    }

    DeliveryUnitService(DeliveryUnitStore store,
                        DeploymentUnitService deploymentUnitService,
                        SystemReferenceQuery referenceQuery,
                        SystemOperationAudit operationAudit,
                        TransactionTemplate transactions,
                        LongSupplier identifiers) {
        this.store = store;
        this.deploymentUnitService = deploymentUnitService;
        this.referenceQuery = referenceQuery;
        this.operationAudit = operationAudit;
        this.transactions = transactions;
        this.identifiers = identifiers;
    }

    // ---------- 查询 ----------

    public PageResult<DeliveryUnitView> list(AuthUser actor, ProjectAccess project, PageQuery page,
                                             DeliveryUnitQuery query) {
        requireActor(actor);
        requireProject(project);
        PageResult<DeliveryUnit> result = store.pageUnits(actor.tenantId(), project.id(), page,
                normalizeQuery(query));
        Map<Long, PhysicalSubsystemProjection> physicals = new HashMap<>();
        Map<Long, Optional<SystemUserReference>> users = new HashMap<>();
        List<DeliveryUnitView> records = result.records().stream()
                .map(item -> toView(actor, project, item, physicals, users))
                .toList();
        return new PageResult<>(records, result.total(), result.page(), result.size());
    }

    public DeliveryUnitView detail(AuthUser actor, ProjectAccess project, long id) {
        requireActor(actor);
        requireProject(project);
        requirePositiveId(id);
        return toView(actor, project, requireUnit(actor, project, id), new HashMap<>(), new HashMap<>());
    }

    /** 交付单元关联选择用的部署单元候选：限定物理子系统、启用状态并支持关键字分页。 */
    public PageResult<RelatedDeploymentUnitView> deploymentUnitOptions(AuthUser actor, ProjectAccess project,
                                                                      Long physicalSubsystemId, String keyword,
                                                                      Long excludeId, PageQuery page) {
        requireActor(actor);
        requireProject(project);
        return deploymentUnitService.options(actor, project, keyword, excludeId, physicalSubsystemId, page);
    }

    /** 部署单元侧只读反查：某部署单元关联的交付单元。 */
    public List<RelatedDeliveryUnitView> relatedDeliveryUnits(AuthUser actor, ProjectAccess project,
                                                             long deploymentUnitId) {
        requireActor(actor);
        requireProject(project);
        requirePositiveId(deploymentUnitId);
        requireDeploymentUnit(actor, project, deploymentUnitId);
        return store.findRelatedDeliveryUnits(actor.tenantId(), project.id(), deploymentUnitId).stream()
                .map(unit -> new RelatedDeliveryUnitView(unit.id(), unit.code(), unit.name(), unit.status()))
                .toList();
    }

    /** 部署单元侧关联候选：同物理子系统下启用且未删除的交付单元。 */
    public PageResult<RelatedDeliveryUnitView> deliveryUnitOptionsForDeploymentUnit(AuthUser actor,
                                                                                   ProjectAccess project,
                                                                                   long deploymentUnitId,
                                                                                   String keyword, PageQuery page) {
        requireActor(actor);
        requireProject(project);
        requirePositiveId(deploymentUnitId);
        DeploymentUnitRef deploymentUnit = requireDeploymentUnit(actor, project, deploymentUnitId);
        PageResult<DeliveryUnit> result = store.searchActiveOptions(actor.tenantId(), project.id(),
                deploymentUnit.physicalSubsystemId(), keyword, page);
        List<RelatedDeliveryUnitView> records = result.records().stream()
                .map(unit -> new RelatedDeliveryUnitView(unit.id(), unit.code(), unit.name(), unit.status()))
                .toList();
        return new PageResult<>(records, result.total(), result.page(), result.size());
    }

    /**
     * 从部署单元侧覆盖式更新关联集合；与交付单元侧写同一张关系表。
     *
     * <p>按用户确认的方案 A，本入口不发布部署单元新版本，也不写关系变更历史，
     * 只更新关联表并写审计。</p>
     */
    public List<RelatedDeliveryUnitView> replaceDeploymentUnitDeliveryUnits(AuthUser actor, ProjectAccess project,
                                                                          long deploymentUnitId,
                                                                          List<Long> deliveryUnitIds,
                                                                          String traceId) {
        requireActor(actor);
        requireProject(project);
        requirePositiveId(deploymentUnitId);
        Set<Long> desired = normalizeRelationIds(deliveryUnitIds);
        try {
            transactions.executeWithoutResult(status -> {
                DeploymentUnitRef deploymentUnit = requireDeploymentUnit(actor, project, deploymentUnitId);
                if (!ACTIVE.equals(deploymentUnit.status())) {
                    throw conflict("已停用或已作废部署单元不能调整关联，请先重新启用");
                }
                validateDeliveryUnitTargets(actor.tenantId(), project.id(), deploymentUnit.physicalSubsystemId(),
                        desired);
                store.replaceDeploymentUnitsFromDeploymentSide(actor.tenantId(), project.id(),
                        deploymentUnit.physicalSubsystemId(), deploymentUnitId, desired, actor.id());
            });
        } catch (RuntimeException exception) {
            throw recordFailure(actor, DEPLOYMENT_UNIT_RELATE_OPERATION, "PUT",
                    DEPLOYMENT_UNIT_RESOURCE_PATH + "/" + deploymentUnitId + "/delivery-units", exception, traceId);
        }
        operationAudit.recordSuccess(auditCommand(actor, DEPLOYMENT_UNIT_RELATE_OPERATION, "PUT",
                DEPLOYMENT_UNIT_RESOURCE_PATH + "/" + deploymentUnitId + "/delivery-units", null, traceId));
        return relatedDeliveryUnits(actor, project, deploymentUnitId);
    }

    // ---------- 写操作 ----------

    public DeliveryUnitView create(AuthUser actor, ProjectAccess project, DeliveryUnitCommand command,
                                   String traceId) {
        requireActor(actor);
        requireProject(project);
        PreparedCommand prepared = prepare(actor, project, command, null);
        long unitId;
        try {
            unitId = transactions.execute(status -> createInTransaction(actor, project, prepared));
        } catch (DuplicateKeyException exception) {
            throw recordFailure(actor, CREATE_OPERATION, "POST",
                    conflict("同一物理子系统下已存在同名交付单元"), traceId);
        } catch (RuntimeException exception) {
            throw recordFailure(actor, CREATE_OPERATION, "POST", exception, traceId);
        }
        DeliveryUnit created = requireUnit(actor, project, unitId);
        operationAudit.recordSuccess(auditCommand(actor, CREATE_OPERATION, "POST", RESOURCE_PATH, null, traceId));
        return toView(actor, project, created, new HashMap<>(), new HashMap<>());
    }

    /** 更新名称、描述与备注；归属物理子系统不可变更，rowVersion 冲突时拒绝。 */
    public DeliveryUnitView update(AuthUser actor, ProjectAccess project, long id, DeliveryUnitCommand command,
                                   String traceId) {
        requireActor(actor);
        requireProject(project);
        requirePositiveId(id);
        PreparedCommand prepared = prepare(actor, project, command, id);
        try {
            transactions.executeWithoutResult(status -> {
                requireUnit(actor, project, id);
                if (store.unitNameExists(actor.tenantId(), project.id(), prepared.physicalSubsystemId(),
                        prepared.name(), id)) {
                    throw conflict("同一物理子系统下已存在同名交付单元");
                }
                int updated = store.updateUnitContent(actor.tenantId(), project.id(), id, prepared.rowVersion(),
                        prepared.name(), prepared.description(), prepared.remark(), actor.id());
                if (updated != 1) {
                    throw conflict("交付单元已被其他操作修改，请刷新后重试");
                }
            });
        } catch (DuplicateKeyException exception) {
            throw recordFailure(actor, UPDATE_OPERATION, "PUT",
                    conflict("同一物理子系统下已存在同名交付单元"), traceId);
        } catch (RuntimeException exception) {
            throw recordFailure(actor, UPDATE_OPERATION, "PUT", exception, traceId);
        }
        operationAudit.recordSuccess(auditCommand(actor, UPDATE_OPERATION, "PUT", RESOURCE_PATH + "/" + id, null,
                traceId));
        return detail(actor, project, id);
    }

    /** 覆盖式更新交付单元与部署单元的关联集合。 */
    public DeliveryUnitView replaceDeploymentUnits(AuthUser actor, ProjectAccess project, long id,
                                                   List<Long> deploymentUnitIds, String traceId) {
        requireActor(actor);
        requireProject(project);
        requirePositiveId(id);
        Set<Long> desired = normalizeRelationIds(deploymentUnitIds);
        try {
            transactions.executeWithoutResult(status -> {
                DeliveryUnit unit = requireUnit(actor, project, id);
                if (INACTIVE.equals(unit.status())) {
                    throw conflict("已停用交付单元不能调整关联，请先重新启用");
                }
                validateRelationTargets(actor.tenantId(), project.id(), unit.physicalSubsystemId(), desired);
                store.replaceDeploymentUnits(actor.tenantId(), project.id(), unit.physicalSubsystemId(), id,
                        desired, actor.id());
            });
        } catch (RuntimeException exception) {
            throw recordFailure(actor, RELATE_OPERATION, "PUT", exception, traceId);
        }
        operationAudit.recordSuccess(auditCommand(actor, RELATE_OPERATION, "PUT",
                RESOURCE_PATH + "/" + id + "/deployment-units", null, traceId));
        return detail(actor, project, id);
    }

    public DeliveryUnitView deactivate(AuthUser actor, ProjectAccess project, long id, String traceId) {
        return transition(actor, project, id, ACTIVE, INACTIVE, DEACTIVATE_OPERATION, traceId);
    }

    public DeliveryUnitView reactivate(AuthUser actor, ProjectAccess project, long id, String traceId) {
        return transition(actor, project, id, INACTIVE, ACTIVE, REACTIVATE_OPERATION, traceId);
    }

    /** 软删除交付单元并清理其关联；部署单元主记录不受影响。 */
    public void delete(AuthUser actor, ProjectAccess project, long id, String traceId) {
        requireActor(actor);
        requireProject(project);
        requirePositiveId(id);
        try {
            transactions.executeWithoutResult(status -> {
                requireUnit(actor, project, id);
                store.softDelete(actor.tenantId(), project.id(), id, actor.id());
            });
        } catch (RuntimeException exception) {
            throw recordFailure(actor, DELETE_OPERATION, "DELETE", exception, traceId);
        }
        operationAudit.recordSuccess(auditCommand(actor, DELETE_OPERATION, "DELETE", RESOURCE_PATH + "/" + id, null,
                traceId));
    }

    // ---------- 内部实现 ----------

    private DeliveryUnitView transition(AuthUser actor, ProjectAccess project, long id, String from, String to,
                                        String operation, String traceId) {
        requireActor(actor);
        requireProject(project);
        requirePositiveId(id);
        try {
            transactions.executeWithoutResult(status -> {
                DeliveryUnit unit = requireUnit(actor, project, id);
                if (!from.equals(unit.status())) {
                    throw conflict("交付单元当前状态不允许该操作（当前 " + unit.status() + "）");
                }
                if (store.updateUnitStatus(actor.tenantId(), project.id(), id, from, to, actor.id()) != 1) {
                    throw conflict("交付单元状态已被其他操作修改，请刷新后重试");
                }
            });
        } catch (RuntimeException exception) {
            throw recordFailure(actor, operation, "POST", exception, traceId);
        }
        operationAudit.recordSuccess(auditCommand(actor, operation, "POST", RESOURCE_PATH + "/" + id, null, traceId));
        return detail(actor, project, id);
    }

    private long createInTransaction(AuthUser actor, ProjectAccess project, PreparedCommand prepared) {
        long tenantId = actor.tenantId();
        PhysicalSubsystemProjection physical = store.findPhysical(tenantId, project.id(),
                        prepared.physicalSubsystemId())
                .orElseThrow(() -> badRequest("物理子系统不存在或不属于当前项目"));
        if (physical.deleted()) {
            throw badRequest("物理子系统已删除，不能在其下创建交付单元");
        }
        if (!ACTIVE.equals(physical.status())) {
            throw badRequest("物理子系统当前状态不允许创建交付单元（状态 " + physical.status() + "）");
        }
        if (store.unitNameExists(tenantId, project.id(), prepared.physicalSubsystemId(), prepared.name(), null)) {
            throw conflict("同一物理子系统下已存在同名交付单元");
        }
        long unitId = nextId();
        validateRelationTargets(tenantId, project.id(), prepared.physicalSubsystemId(),
                prepared.relatedDeploymentUnitIds());
        String code;
        try {
            code = store.allocateNumber(tenantId, project.id(), prepared.physicalSubsystemId(), physical.code());
        } catch (DeliveryUnitNumberCapacityExceededException exception) {
            throw conflict(exception.getMessage());
        }
        store.insertUnit(unitId, tenantId, project.id(), code, prepared.physicalSubsystemId(), prepared.name(),
                prepared.description(), prepared.remark(), actor.id());
        store.replaceDeploymentUnits(tenantId, project.id(), prepared.physicalSubsystemId(), unitId,
                prepared.relatedDeploymentUnitIds(), actor.id());
        return unitId;
    }

    private PreparedCommand prepare(AuthUser actor, ProjectAccess project, DeliveryUnitCommand command, Long targetId) {
        if (command == null) {
            throw badRequest("请求内容不能为空");
        }
        DeliveryUnit existing = targetId == null ? null : requireUnit(actor, project, targetId);
        if (existing != null) {
            if (command.physicalSubsystemId() != null && command.physicalSubsystemId() > 0
                    && !command.physicalSubsystemId().equals(existing.physicalSubsystemId())) {
                throw badRequest("归属物理子系统不可变更");
            }
        } else if (command.physicalSubsystemId() == null || command.physicalSubsystemId() <= 0) {
            throw badRequest("请选择归属物理子系统");
        }
        String name = required(command.name(), "交付单元名称", 2, 200);
        String description = optional(command.description(), "描述", 2000);
        String remark = optional(command.remark(), "备注", 1000);
        Set<Long> related = normalizeRelationIds(command.relatedDeploymentUnitIds());
        Long physicalSubsystemId = existing == null ? command.physicalSubsystemId() : existing.physicalSubsystemId();
        Long rowVersion = existing == null ? null : requiredRowVersion(command.rowVersion());
        return new PreparedCommand(physicalSubsystemId, name, description, remark, related, rowVersion);
    }

    private void validateRelationTargets(long tenantId, long projectId, long physicalSubsystemId,
                                         Set<Long> deploymentUnitIds) {
        if (deploymentUnitIds.isEmpty()) {
            return;
        }
        List<DeploymentUnitRef> targets = store.findDeploymentUnitsByIds(tenantId, projectId, deploymentUnitIds);
        if (targets.size() != deploymentUnitIds.size()) {
            throw conflict("关联的部署单元不存在或不属于当前项目");
        }
        for (DeploymentUnitRef target : targets) {
            if (target.deleted() || !ACTIVE.equals(target.status())) {
                throw conflict("只能关联启用状态的部署单元：" + target.name());
            }
            if (target.physicalSubsystemId() != physicalSubsystemId) {
                throw conflict("只能关联同一物理子系统下的部署单元：" + target.name());
            }
        }
    }

    private void validateDeliveryUnitTargets(long tenantId, long projectId, long physicalSubsystemId,
                                             Set<Long> deliveryUnitIds) {
        if (deliveryUnitIds.isEmpty()) {
            return;
        }
        List<DeliveryUnit> targets = store.findDeliveryUnitsByIds(tenantId, projectId, deliveryUnitIds);
        if (targets.size() != deliveryUnitIds.size()) {
            throw conflict("关联的交付单元不存在或已删除");
        }
        for (DeliveryUnit target : targets) {
            if (!ACTIVE.equals(target.status())) {
                throw conflict("只能关联启用状态的交付单元：" + target.name());
            }
            if (target.physicalSubsystemId() != physicalSubsystemId) {
                throw conflict("只能关联同一物理子系统下的交付单元：" + target.name());
            }
        }
    }

    private DeploymentUnitRef requireDeploymentUnit(AuthUser actor, ProjectAccess project, long deploymentUnitId) {
        return store.findDeploymentUnitsByIds(actor.tenantId(), project.id(), List.of(deploymentUnitId)).stream()
                .findFirst()
                .orElseThrow(() -> new ArchitectureNotFoundException("部署单元不存在：" + deploymentUnitId));
    }

    private Set<Long> normalizeRelationIds(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        Set<Long> normalized = new LinkedHashSet<>();
        for (Long value : values) {
            if (value == null || value <= 0) {
                throw badRequest("关联的部署单元标识无效");
            }
            normalized.add(value);
        }
        return normalized;
    }

    private DeliveryUnit requireUnit(AuthUser actor, ProjectAccess project, long id) {
        return store.findUnit(actor.tenantId(), project.id(), id).orElseThrow(() -> notFound(id));
    }

    private DeliveryUnitView toView(AuthUser actor, ProjectAccess project, DeliveryUnit item,
                                    Map<Long, PhysicalSubsystemProjection> physicals,
                                    Map<Long, Optional<SystemUserReference>> users) {
        PhysicalSubsystemProjection physical = physicals.computeIfAbsent(item.physicalSubsystemId(),
                key -> store.findPhysical(actor.tenantId(), project.id(), key).orElse(null));
        String physicalName = physical == null ? null : physical.name();
        List<RelatedDeploymentUnitView> relatedUnits = store.findRelatedDeploymentUnits(actor.tenantId(),
                        project.id(), item.id()).stream()
                .map(target -> new RelatedDeploymentUnitView(target.id(), target.code(), target.name(),
                        target.kind(), target.physicalSubsystemId(), physicalName, target.status()))
                .toList();
        SystemUserReference creator = userReference(actor, item.createdBy(), users);
        SystemUserReference updater = userReference(actor, item.updatedBy(), users);
        return new DeliveryUnitView(item.id(), item.code(), item.physicalSubsystemId(),
                physical == null ? null : physical.code(), physicalName,
                physical == null ? null : physical.status(), item.name(), item.status(), relatedUnits,
                item.description(), item.remark(), item.createdBy(),
                creator == null ? null : creator.displayName(), item.updatedBy(),
                updater == null ? null : updater.displayName(), item.createdAt(), item.updatedAt(),
                item.rowVersion());
    }

    private SystemUserReference userReference(AuthUser actor, Long userId,
                                              Map<Long, Optional<SystemUserReference>> cache) {
        if (userId == null) {
            return null;
        }
        return cache.computeIfAbsent(userId, key -> {
            Optional<SystemUserReference> reference = referenceQuery.findUser(actor, key, false);
            return reference == null ? Optional.empty() : reference;
        }).orElse(null);
    }

    private DeliveryUnitQuery normalizeQuery(DeliveryUnitQuery query) {
        if (query == null) {
            return DeliveryUnitQuery.empty();
        }
        String status = optional(query.status(), "状态", 16);
        return new DeliveryUnitQuery(optional(query.name(), "交付单元名称", 200),
                query.physicalSubsystemId(), status == null ? null : status.toUpperCase(Locale.ROOT));
    }

    private SystemOperationAuditCommand auditCommand(AuthUser actor, String operationCode, String method,
                                                     String path, String error, String traceId) {
        return new SystemOperationAuditCommand(actor, operationCode, method, path, error, traceId);
    }

    private RuntimeException recordFailure(AuthUser actor, String operationCode, String method,
                                           RuntimeException original, String traceId) {
        return recordFailure(actor, operationCode, method, RESOURCE_PATH, original, traceId);
    }

    private RuntimeException recordFailure(AuthUser actor, String operationCode, String method, String path,
                                           RuntimeException original, String traceId) {
        try {
            operationAudit.recordFailure(auditCommand(actor, operationCode, method, path,
                    safeErrorMessage(original), traceId));
        } catch (RuntimeException auditFailure) {
            logFailure(operationCode, auditFailure);
        }
        return original;
    }

    private String safeErrorMessage(RuntimeException exception) {
        if (exception instanceof BusinessException || exception instanceof ArchitectureNotFoundException
                || exception instanceof DeliveryUnitNumberCapacityExceededException) {
            return exception.getMessage();
        }
        return "交付单元操作失败";
    }

    private void logFailure(String operationCode, RuntimeException auditFailure) {
        org.slf4j.LoggerFactory.getLogger(DeliveryUnitService.class)
                .error("交付单元操作审计写入失败，operationCode={}", operationCode, auditFailure);
    }

    private void requireActor(AuthUser actor) {
        if (actor == null || actor.id() <= 0 || actor.tenantId() <= 0) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "需要有效的认证用户和租户");
        }
    }

    private void requireProject(ProjectAccess project) {
        if (project == null || project.id() <= 0) {
            throw badRequest("请选择项目后重试");
        }
    }

    private void requirePositiveId(long id) {
        if (id <= 0) {
            throw badRequest("交付单元标识无效");
        }
    }

    private long requiredRowVersion(Long value) {
        if (value == null || value < 0) {
            throw badRequest("数据版本不能为空");
        }
        return value;
    }

    private String required(String value, String label, int min, int max) {
        String normalized = optional(value, label, max);
        if (normalized == null || normalized.length() < min) {
            throw badRequest(label + "长度必须为 " + min + "—" + max + " 个字符");
        }
        return normalized;
    }

    private String optional(String value, String label, int max) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > max) {
            throw badRequest(label + "最长 " + max + " 个字符");
        }
        return normalized;
    }

    private ArchitectureNotFoundException notFound(long id) {
        return new ArchitectureNotFoundException("交付单元不存在：" + id);
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(ErrorCode.BAD_REQUEST, message);
    }

    private BusinessException conflict(String message) {
        return new BusinessException(ErrorCode.CONFLICT, message);
    }

    private long nextId() {
        return identifiers.getAsLong();
    }

    private record PreparedCommand(Long physicalSubsystemId, String name, String description, String remark,
                                   Set<Long> relatedDeploymentUnitIds, Long rowVersion) {
    }

    /** 交付单元视图。 */
    public record DeliveryUnitView(
            long id,
            String code,
            long physicalSubsystemId,
            String physicalSubsystemCode,
            String physicalSubsystemName,
            String physicalSubsystemStatus,
            String name,
            String status,
            List<RelatedDeploymentUnitView> relatedDeploymentUnits,
            String description,
            String remark,
            long createdBy,
            String createdByDisplayName,
            long updatedBy,
            String updatedByDisplayName,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            long rowVersion) {
    }

    /** 部署单元侧反查到的交付单元只读引用。 */
    public record RelatedDeliveryUnitView(
            long id,
            String code,
            String name,
            String status) {
    }
}
