package com.ccb.architecture.service;

import com.ccb.architecture.model.DeploymentUnitModels.DeploymentUnit;
import com.ccb.architecture.model.DeploymentUnitModels.DeploymentUnitCommand;
import com.ccb.architecture.model.DeploymentUnitModels.DeploymentUnitKind;
import com.ccb.architecture.model.DeploymentUnitModels.DeploymentUnitQuery;
import com.ccb.architecture.model.DeploymentUnitModels.DeploymentUnitStatus;
import com.ccb.architecture.model.DeploymentUnitModels.DeploymentUnitVersion;
import com.ccb.architecture.network.service.NetworkAccessService;
import com.ccb.architecture.network.service.NetworkAccessService.ZoneRef;
import com.ccb.architecture.persistence.DeploymentUnitNumberCapacityExceededException;
import com.ccb.architecture.persistence.DeploymentUnitStore;
import com.ccb.architecture.persistence.DeploymentUnitStore.PhysicalSubsystemRef;
import com.ccb.architecture.persistence.DeploymentUnitStore.RelatedDeploymentUnitRow;
import com.ccb.architecture.web.ArchitectureNotFoundException;
import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
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
import java.util.regex.Pattern;

/**
 * 部署单元生命周期：创建即发布版本 1；更新即发布新版本；停用/启用/作废状态机。
 * 已发布部署单元的编号与物理归属不可变更；作废必须通过引用守卫 fail-closed 检查。
 */
@Service
public class DeploymentUnitService {
    public static final String RESOURCE_PATH = "/api/architecture/deployment-units";

    private static final String CREATE_OPERATION = "ARCHITECTURE_DEPLOYMENT_UNIT_CREATE";
    private static final String UPDATE_OPERATION = "ARCHITECTURE_DEPLOYMENT_UNIT_UPDATE";
    private static final String DEACTIVATE_OPERATION = "ARCHITECTURE_DEPLOYMENT_UNIT_DEACTIVATE";
    private static final String REACTIVATE_OPERATION = "ARCHITECTURE_DEPLOYMENT_UNIT_REACTIVATE";
    private static final String VOID_OPERATION = "ARCHITECTURE_DEPLOYMENT_UNIT_VOID";
    private static final Set<String> PUBLISHED_STATUSES = Set.of(
            DeploymentUnitStatus.ACTIVE.name(), DeploymentUnitStatus.INACTIVE.name(),
            DeploymentUnitStatus.VOIDED.name());
    private static final Pattern NAME_PATTERN = Pattern.compile("^[A-Z0-9]+_[A-Z0-9]{1,8}$");
    private static final Map<String, DeploymentUnitKind> STANDARD_SUFFIX_KIND = Map.of(
            "AP", DeploymentUnitKind.APPLICATION,
            "DB", DeploymentUnitKind.DATABASE,
            "WB", DeploymentUnitKind.WEB);

    private final DeploymentUnitStore store;
    private final DeploymentUnitReferenceGuard referenceGuard;
    private final SystemReferenceQuery referenceQuery;
    private final SystemOperationAudit operationAudit;
    private final TransactionTemplate transactions;
    private final LongSupplier identifiers;
    private final NetworkAccessService networkAccessService;

    @org.springframework.beans.factory.annotation.Autowired
    public DeploymentUnitService(DeploymentUnitStore store,
                                 DeploymentUnitReferenceGuard referenceGuard,
                                 SystemReferenceQuery referenceQuery,
                                 SystemOperationAudit operationAudit,
                                 TransactionTemplate transactions,
                                 NetworkAccessService networkAccessService) {
        this(store, referenceGuard, referenceQuery, operationAudit, transactions,
                () -> System.currentTimeMillis() * 1_000 + ThreadLocalRandom.current().nextInt(1_000),
                networkAccessService);
    }

    DeploymentUnitService(DeploymentUnitStore store,
                          DeploymentUnitReferenceGuard referenceGuard,
                          SystemReferenceQuery referenceQuery,
                          SystemOperationAudit operationAudit,
                          TransactionTemplate transactions,
                          LongSupplier identifiers) {
        this(store, referenceGuard, referenceQuery, operationAudit, transactions, identifiers, null);
    }

    DeploymentUnitService(DeploymentUnitStore store,
                          DeploymentUnitReferenceGuard referenceGuard,
                          SystemReferenceQuery referenceQuery,
                          SystemOperationAudit operationAudit,
                          TransactionTemplate transactions,
                          LongSupplier identifiers,
                          NetworkAccessService networkAccessService) {
        this.store = store;
        this.referenceGuard = referenceGuard;
        this.referenceQuery = referenceQuery;
        this.operationAudit = operationAudit;
        this.transactions = transactions;
        this.identifiers = identifiers;
        this.networkAccessService = networkAccessService;
    }

    // ---------- 查询 ----------

    public PageResult<DeploymentUnitView> list(AuthUser actor, PageQuery page, DeploymentUnitQuery query) {
        requireActor(actor);
        PageResult<DeploymentUnit> result = store.pageUnits(actor.tenantId(), page, normalizeQuery(query));
        Map<Long, PhysicalSubsystemRef> physicals = new HashMap<>();
        Map<Long, Optional<SystemUserReference>> users = new HashMap<>();
        List<DeploymentUnitView> records = result.records().stream()
                .map(item -> toView(actor, item, physicals, users))
                .toList();
        return new PageResult<>(records, result.total(), result.page(), result.size());
    }

    public DeploymentUnitView detail(AuthUser actor, long id) {
        requireActor(actor);
        requirePositiveId(id);
        DeploymentUnit unit = store.findUnit(actor.tenantId(), id).orElseThrow(() -> notFound(id));
        return toView(actor, unit, new HashMap<>(), new HashMap<>());
    }

    public List<DeploymentUnitVersionView> versions(AuthUser actor, long id) {
        requireActor(actor);
        requirePositiveId(id);
        if (store.findUnit(actor.tenantId(), id).isEmpty()) {
            throw notFound(id);
        }
        Map<Long, Optional<SystemUserReference>> users = new HashMap<>();
        return store.findVersions(actor.tenantId(), id).stream()
                .map(version -> new DeploymentUnitVersionView(
                        version.versionNo(), version.name(), version.kind(),
                        version.defaultNetworkZoneId(), version.defaultNetworkZoneName(),
                        version.description(), version.remark(),
                        version.publishedBy(), displayName(actor, version.publishedBy(), users),
                        version.publishedAt()))
                .toList();
    }

    public PageResult<RelatedDeploymentUnitView> options(AuthUser actor, String keyword, Long excludeId,
                                                          PageQuery page) {
        requireActor(actor);
        if (excludeId != null) {
            requirePositiveId(excludeId);
        }
        PageResult<DeploymentUnit> result = store.searchActiveOptions(actor.tenantId(), keyword, excludeId, page);
        Map<Long, PhysicalSubsystemRef> physicals = new HashMap<>();
        List<RelatedDeploymentUnitView> records = result.records().stream()
                .map(unit -> {
                    PhysicalSubsystemRef physical = physicals.computeIfAbsent(unit.physicalSubsystemId(),
                            key -> store.findPhysical(actor.tenantId(), key).orElse(null));
                    return new RelatedDeploymentUnitView(unit.id(), unit.code(), unit.name(), unit.kind(),
                            unit.physicalSubsystemId(), physical == null ? null : physical.name(), unit.status());
                })
                .toList();
        return new PageResult<>(records, result.total(), result.page(), result.size());
    }

    // ---------- 写操作 ----------

    public DeploymentUnitView create(AuthUser actor, DeploymentUnitCommand command, String traceId) {
        requireActor(actor);
        PreparedCommand prepared = prepare(actor, command, null);
        long unitId;
        try {
            unitId = transactions.execute(status -> publishInitial(actor, prepared.physicalSubsystemId(),
                    prepared.name(), prepared.kind(), prepared.description(), prepared.relatedDeploymentUnitIds(),
                    prepared.defaultNetworkZoneId(), prepared.defaultNetworkZoneName(), prepared.remark()));
        } catch (DuplicateKeyException exception) {
            throw recordFailure(actor, CREATE_OPERATION, "POST", conflict("部署单元名称已被占用，停用或作废后也不可复用"), traceId);
        } catch (RuntimeException exception) {
            throw recordFailure(actor, CREATE_OPERATION, "POST", exception, traceId);
        }
        DeploymentUnit unit = store.findUnit(actor.tenantId(), unitId).orElseThrow(() -> notFound(unitId));
        operationAudit.recordSuccess(auditCommand(actor, CREATE_OPERATION, "POST", RESOURCE_PATH, null, traceId));
        return toView(actor, unit, new HashMap<>(), new HashMap<>());
    }

    /**
     * 更新 ACTIVE 部署单元展示内容并发布新版本；乐观锁 rowVersion 冲突时拒绝。
     * 编号与物理归属不在更新命令中，天然不可变更。
     */
    public DeploymentUnitView update(AuthUser actor, long id, DeploymentUnitCommand command, String traceId) {
        requireActor(actor);
        requirePositiveId(id);
        PreparedCommand prepared = prepare(actor, command, id);
        try {
            transactions.executeWithoutResult(status -> {
                DeploymentUnit locked = store.lockUnit(actor.tenantId(), id)
                        .orElseThrow(() -> notFound(id));
                if (locked.status().equals(DeploymentUnitStatus.INACTIVE.name())) {
                    throw conflict("已停用部署单元不能直接修改，请先重新启用");
                }
                if (locked.status().equals(DeploymentUnitStatus.VOIDED.name())) {
                    throw conflict("已作废部署单元不可修改");
                }
                if (store.unitNameExists(actor.tenantId(), prepared.name(), id)) {
                    throw conflict("部署单元名称已被占用，停用或作废后也不可复用");
                }
                validateRelationTargets(actor.tenantId(), id, prepared.relatedDeploymentUnitIds());
                int updated = hasDefaultNetworkZone(prepared)
                        ? store.updateUnitContent(actor.tenantId(), id, prepared.rowVersion(),
                        prepared.name(), prepared.kind(),
                        prepared.defaultNetworkZoneId(), prepared.defaultNetworkZoneName(), prepared.description(),
                        prepared.remark(), actor.id())
                        : store.updateUnitContent(actor.tenantId(), id, prepared.rowVersion(),
                        prepared.name(), prepared.kind(), prepared.description(),
                        prepared.remark(), actor.id());
                if (updated != 1) {
                    throw conflict("部署单元已被其他操作修改，请刷新后重试");
                }
                int nextVersion = locked.currentVersion() + 1;
                if (hasDefaultNetworkZone(prepared)) {
                    store.insertVersion(nextId(), actor.tenantId(), id, nextVersion, prepared.name(),
                            prepared.kind(), prepared.defaultNetworkZoneId(), prepared.defaultNetworkZoneName(),
                            prepared.description(), prepared.remark(), actor.id());
                } else {
                    store.insertVersion(nextId(), actor.tenantId(), id, nextVersion, prepared.name(),
                            prepared.kind(), prepared.description(), prepared.remark(), actor.id());
                }
                store.updateUnitCurrentVersion(actor.tenantId(), id, nextVersion, actor.id());
                store.replaceRelations(actor.tenantId(), id, prepared.relatedDeploymentUnitIds(),
                        actor.id(), nextVersion);
            });
        } catch (DuplicateKeyException exception) {
            throw recordFailure(actor, UPDATE_OPERATION, "PUT", conflict("部署单元名称已被占用，停用或作废后也不可复用"), traceId);
        } catch (RuntimeException exception) {
            throw recordFailure(actor, UPDATE_OPERATION, "PUT", exception, traceId);
        }
        DeploymentUnit unit = store.findUnit(actor.tenantId(), id).orElseThrow(() -> notFound(id));
        operationAudit.recordSuccess(auditCommand(actor, UPDATE_OPERATION, "PUT", RESOURCE_PATH + "/" + id, null, traceId));
        return toView(actor, unit, new HashMap<>(), new HashMap<>());
    }

    public DeploymentUnitView deactivate(AuthUser actor, long id, String traceId) {
        requireActor(actor);
        requirePositiveId(id);
        transition(actor, id, DeploymentUnitStatus.ACTIVE, DeploymentUnitStatus.INACTIVE, false,
                DEACTIVATE_OPERATION, "POST", traceId);
        return detail(actor, id);
    }

    public DeploymentUnitView reactivate(AuthUser actor, long id, String traceId) {
        requireActor(actor);
        requirePositiveId(id);
        transition(actor, id, DeploymentUnitStatus.INACTIVE, DeploymentUnitStatus.ACTIVE, false,
                REACTIVATE_OPERATION, "POST", traceId);
        return detail(actor, id);
    }

    public DeploymentUnitView voidUnit(AuthUser actor, long id, String traceId) {
        requireActor(actor);
        requirePositiveId(id);
        transition(actor, id, null, DeploymentUnitStatus.VOIDED, true, VOID_OPERATION, "POST", traceId);
        return detail(actor, id);
    }

    private void transition(AuthUser actor, long id, DeploymentUnitStatus from, DeploymentUnitStatus to,
                            boolean referenceCheck, String operation, String method, String traceId) {
        try {
            transactions.executeWithoutResult(status -> {
                DeploymentUnit locked = store.lockUnit(actor.tenantId(), id)
                        .orElseThrow(() -> notFound(id));
                if (from != null && !locked.status().equals(from.name())) {
                    throw conflict("部署单元当前状态不允许该操作（当前 " + locked.status() + "）");
                }
                if (referenceCheck) {
                    if (!locked.status().equals(DeploymentUnitStatus.ACTIVE.name())
                            && !locked.status().equals(DeploymentUnitStatus.INACTIVE.name())) {
                        throw conflict("已作废部署单元不可重复作废");
                    }
                    if (store.hasRelations(actor.tenantId(), id)) {
                        throw conflict("部署单元仍存在关联，请先解除关联后再作废");
                    }
                    referenceGuard.requireClear(
                            new com.ccb.architecture.integration.DeploymentUnitReferenceCheckRequest(
                                    actor.tenantId(), id));
                }
                int updated = store.updateUnitStatus(actor.tenantId(), id, locked.status(), to.name(), actor.id());
                if (updated != 1) {
                    throw conflict("部署单元状态已被其他操作修改，请刷新后重试");
                }
            });
        } catch (RuntimeException exception) {
            throw recordFailure(actor, operation, method, exception, traceId);
        }
        operationAudit.recordSuccess(auditCommand(actor, operation, method, RESOURCE_PATH + "/" + id, null, traceId));
    }

    // ---------- 导入复用 ----------

    /**
     * 在调用方事务内创建并发布版本 1（分配编号）。所有校验先于编号分配，
     * 避免失败行消耗序号。导入服务在事务内调用本方法。
     */
    long publishInitial(AuthUser actor, long physicalSubsystemId, String name,
                        String kind, String description, Set<Long> relatedDeploymentUnitIds,
                        Long defaultNetworkZoneId,
                        String defaultNetworkZoneName, String remark) {
        long tenantId = actor.tenantId();
        PhysicalSubsystemRef physical = store.findPhysical(tenantId, physicalSubsystemId)
                .orElseThrow(() -> badRequest("物理子系统不存在或不属于当前租户"));
        if (physical.deleted()) {
            throw badRequest("物理子系统已删除，不能在其下创建部署单元");
        }
        if (!"ACTIVE".equals(physical.status())) {
            throw badRequest("物理子系统当前状态不允许创建部署单元（状态 " + physical.status() + "）");
        }
        if (store.unitNameExists(tenantId, name, null)) {
            throw conflict("部署单元名称已被占用，停用或作废后也不可复用");
        }
        long unitId = nextId();
        validateRelationTargets(tenantId, unitId, relatedDeploymentUnitIds);
        String code;
        try {
            code = store.allocateNumber(tenantId, physicalSubsystemId, physical.code());
        } catch (DeploymentUnitNumberCapacityExceededException exception) {
            throw conflict(exception.getMessage());
        }
        if (hasDefaultNetworkZone(defaultNetworkZoneId, defaultNetworkZoneName)) {
            store.insertUnit(unitId, tenantId, code, physicalSubsystemId, name,
                    kind, defaultNetworkZoneId,
                    defaultNetworkZoneName, description, remark, actor.id());
            store.insertVersion(nextId(), tenantId, unitId, 1, name,
                    kind, defaultNetworkZoneId,
                    defaultNetworkZoneName, description, remark, actor.id());
        } else {
            store.insertUnit(unitId, tenantId, code, physicalSubsystemId, name,
                    kind, description, remark, actor.id());
            store.insertVersion(nextId(), tenantId, unitId, 1, name,
                    kind, description, remark, actor.id());
        }
        store.replaceRelations(tenantId, unitId, relatedDeploymentUnitIds, actor.id(), 1);
        return unitId;
    }

    long publishInitial(AuthUser actor, long physicalSubsystemId, String name,
                        String kind, String description, String remark) {
        return publishInitial(actor, physicalSubsystemId, name, kind, description,
                Set.of(), null, null, remark);
    }

    // ---------- 校验与投影 ----------

    private PreparedCommand prepare(AuthUser actor, DeploymentUnitCommand command, Long targetId) {
        if (command == null) {
            throw badRequest("请求内容不能为空");
        }
        if (command.kind() == null || command.kind().isBlank()) {
            throw badRequest("部署单元类型不能为空");
        }
        DeploymentUnitKind kind;
        try {
            kind = DeploymentUnitKind.valueOf(command.kind().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw badRequest("部署单元类型仅支持 应用（APPLICATION）、数据库（DATABASE）、Web（WEB）");
        }
        String name = required(command.name(), "部署单元名称", 4, 200).toUpperCase(Locale.ROOT);
        if (!NAME_PATTERN.matcher(name).matches()) {
            throw badRequest("部署单元名称格式必须为主名称_后缀，主名称只允许大写字母和数字，后缀为1—8位大写字母或数字");
        }
        String suffix = name.substring(name.lastIndexOf('_') + 1);
        DeploymentUnitKind standardKind = STANDARD_SUFFIX_KIND.get(suffix);
        if (standardKind != null && standardKind != kind) {
            throw badRequest("标准后缀_" + suffix + "与部署单元类型" + kind.name() + "不一致");
        }
        Set<Long> relatedDeploymentUnitIds = normalizeRelationIds(command.relatedDeploymentUnitIds(), targetId);
        String description = optional(command.description(), "描述", 2000);
        String remark = optional(command.remark(), "备注", 1000);
        ZoneRef zone = validateDefaultNetworkZone(actor, command.defaultNetworkZoneId());
        if (targetId == null) {
            long physicalSubsystemId = requiredId(command.physicalSubsystemId(), "所属物理子系统");
            PhysicalSubsystemRef physical = store.findPhysical(actor.tenantId(), physicalSubsystemId)
                    .orElseThrow(() -> badRequest("物理子系统不存在或不属于当前租户"));
            if (physical.deleted()) {
                throw badRequest("物理子系统已删除，不能在其下创建部署单元");
            }
            if (!physical.status().equals("ACTIVE")) {
                throw badRequest("只能选择已发布的物理子系统创建部署单元（当前状态 " + physical.status() + "）");
            }
            return new PreparedCommand(physicalSubsystemId, name, kind.name(), relatedDeploymentUnitIds,
                    zone == null ? null : zone.id(),
                    zone == null ? null : zone.name(), description, remark, null);
        }
        return new PreparedCommand(null, name, kind.name(), relatedDeploymentUnitIds,
                zone == null ? null : zone.id(), zone == null ? null : zone.name(),
                description, remark,
                requiredRowVersion(command.rowVersion()));
    }

    private Set<Long> normalizeRelationIds(List<Long> values, Long targetId) {
        Set<Long> ids = new LinkedHashSet<>();
        if (values == null) {
            return ids;
        }
        for (Long value : values) {
            if (value == null || value <= 0) {
                throw badRequest("关联部署单元编号必须为正整数");
            }
            if (targetId != null && value.equals(targetId)) {
                throw badRequest("部署单元不能关联自身");
            }
            ids.add(value);
        }
        return ids;
    }

    private void validateRelationTargets(long tenantId, long sourceUnitId, Set<Long> targetIds) {
        if (targetIds == null || targetIds.isEmpty()) {
            return;
        }
        if (targetIds.contains(sourceUnitId)) {
            throw badRequest("部署单元不能关联自身");
        }
        List<DeploymentUnit> lockedTargets = store.lockActiveUnits(tenantId, targetIds.stream().sorted().toList());
        if (lockedTargets.size() != targetIds.size()) {
            throw badRequest("关联部署单元不存在、不属于当前租户或已非 ACTIVE 状态");
        }
    }

    private ZoneRef validateDefaultNetworkZone(AuthUser actor, Long zoneId) {
        if (zoneId == null) {
            return null;
        }
        if (networkAccessService == null) {
            return new ZoneRef(zoneId, null, null);
        }
        return networkAccessService.requireActiveLeafZone(actor.tenantId(), zoneId, "部署单元默认网络分区");
    }

    /** 乐观锁版本允许从 0 开始；负数视为无效。 */
    private long requiredRowVersion(Long value) {
        if (value == null || value < 0) {
            throw badRequest("数据版本不能为空");
        }
        return value;
    }

    private DeploymentUnitView toView(AuthUser actor, DeploymentUnit item,
                                      Map<Long, PhysicalSubsystemRef> physicals,
                                      Map<Long, Optional<SystemUserReference>> users) {
        PhysicalSubsystemRef physical = physicals.computeIfAbsent(item.physicalSubsystemId(),
                key -> store.findPhysical(actor.tenantId(), key).orElse(null));
        SystemUserReference creator = userReference(actor, item.createdBy(), users);
        SystemUserReference updater = userReference(actor, item.updatedBy(), users);
        List<RelatedDeploymentUnitView> relatedUnits = store.findRelatedUnits(actor.tenantId(), item.id()).stream()
                .map(this::toRelatedView)
                .toList();
        return new DeploymentUnitView(item.id(), item.code(), item.physicalSubsystemId(),
                physical == null ? null : physical.code(), physical == null ? null : physical.name(),
                physical == null ? null : physical.status(), item.name(), item.kind(), relatedUnits, item.status(),
                item.defaultNetworkZoneId(), item.defaultNetworkZoneName(),
                item.currentVersion(), item.description(), item.remark(), item.createdBy(),
                creator == null ? null : creator.displayName(), item.updatedBy(),
                updater == null ? null : updater.displayName(), item.createdAt(), item.updatedAt(),
                item.rowVersion());
    }

    private RelatedDeploymentUnitView toRelatedView(RelatedDeploymentUnitRow row) {
        return new RelatedDeploymentUnitView(row.id(), row.code(), row.name(), row.kind(),
                row.physicalSubsystemId(), row.physicalSubsystemName(), row.status());
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

    private String displayName(AuthUser actor, long userId, Map<Long, Optional<SystemUserReference>> cache) {
        SystemUserReference user = userReference(actor, userId, cache);
        return user == null ? "用户 #" + userId : user.displayName();
    }

    private DeploymentUnitQuery normalizeQuery(DeploymentUnitQuery query) {
        if (query == null) {
            return DeploymentUnitQuery.empty();
        }
        String kind = normalizeOptional(query.kind());
        String status = normalizeOptional(query.status());
        if (kind != null) {
            try {
                DeploymentUnitKind.valueOf(kind.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw badRequest("部署单元类型仅支持 APPLICATION、DATABASE 或 WEB");
            }
        }
        if (status != null && !PUBLISHED_STATUSES.contains(status.toUpperCase(Locale.ROOT))) {
            throw badRequest("发布状态仅支持 ACTIVE、INACTIVE 或 VOIDED");
        }
        return new DeploymentUnitQuery(normalizeOptional(query.code()), normalizeOptional(query.name()),
                query.physicalSubsystemId(),
                kind == null ? null : kind.toUpperCase(Locale.ROOT),
                status == null ? null : status.toUpperCase(Locale.ROOT));
    }

    private SystemOperationAuditCommand auditCommand(AuthUser actor, String operationCode, String method,
                                                     String path, String error, String traceId) {
        return new SystemOperationAuditCommand(actor, operationCode, method, path, error, traceId);
    }

    private RuntimeException recordFailure(AuthUser actor, String operationCode, String method,
                                           RuntimeException original, String traceId) {
        try {
            operationAudit.recordFailure(auditCommand(actor, operationCode, method, RESOURCE_PATH,
                    safeErrorMessage(original), traceId));
        } catch (RuntimeException auditFailure) {
            logFailure(operationCode, auditFailure);
        }
        return original;
    }

    private String safeErrorMessage(RuntimeException exception) {
        if (exception instanceof BusinessException || exception instanceof ArchitectureNotFoundException
                || exception instanceof DeploymentUnitNumberCapacityExceededException) {
            return exception.getMessage();
        }
        return "部署单元操作失败";
    }

    private void logFailure(String operationCode, RuntimeException auditFailure) {
        org.slf4j.LoggerFactory.getLogger(DeploymentUnitService.class)
                .error("部署单元操作审计写入失败，operationCode={}", operationCode, auditFailure);
    }

    private void requireActor(AuthUser actor) {
        if (actor == null || actor.id() <= 0 || actor.tenantId() <= 0) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "需要有效的认证用户和租户");
        }
    }

    private void requirePositiveId(long id) {
        if (id <= 0) {
            throw badRequest("部署单元编号无效");
        }
    }

    private long requiredId(Long value, String label) {
        if (value == null || value <= 0) {
            throw badRequest(label + "不能为空");
        }
        return value;
    }

    private String required(String value, String label, int min, int max) {
        String normalized = normalizeOptional(value);
        if (normalized == null || normalized.length() < min || normalized.length() > max) {
            throw badRequest(label + "长度必须为 " + min + "—" + max + " 个字符");
        }
        return normalized;
    }

    private String optional(String value, String label, int max) {
        String normalized = normalizeOptional(value);
        if (normalized != null && normalized.length() > max) {
            throw badRequest(label + "最长 " + max + " 个字符");
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private ArchitectureNotFoundException notFound(long id) {
        return new ArchitectureNotFoundException("部署单元不存在：" + id);
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

    private boolean hasDefaultNetworkZone(PreparedCommand prepared) {
        return hasDefaultNetworkZone(prepared.defaultNetworkZoneId(), prepared.defaultNetworkZoneName());
    }

    private boolean hasDefaultNetworkZone(Long zoneId, String zoneName) {
        return zoneId != null || zoneName != null;
    }

    private record PreparedCommand(Long physicalSubsystemId, String name, String kind,
                                   Set<Long> relatedDeploymentUnitIds,
                                   Long defaultNetworkZoneId, String defaultNetworkZoneName,
                                   String description, String remark, Long rowVersion) {
    }

    public record DeploymentUnitView(
            long id,
            String code,
            long physicalSubsystemId,
            String physicalSubsystemCode,
            String physicalSubsystemName,
            String physicalSubsystemStatus,
            String name,
            String kind,
            List<RelatedDeploymentUnitView> relatedDeploymentUnits,
            String status,
            Long defaultNetworkZoneId,
            String defaultNetworkZoneName,
            int currentVersion,
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

    public record DeploymentUnitVersionView(
            int versionNo,
            String name,
            String kind,
            Long defaultNetworkZoneId,
            String defaultNetworkZoneName,
            String description,
            String remark,
            long publishedBy,
            String publishedByDisplayName,
            LocalDateTime publishedAt) {
    }

    public record RelatedDeploymentUnitView(
            long id,
            String code,
            String name,
            String kind,
            long physicalSubsystemId,
            String physicalSubsystemName,
            String status) {
    }
}
