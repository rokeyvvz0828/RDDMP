package com.ccb.architecture.service;

import com.ccb.architecture.model.PhysicalSubsystem;
import com.ccb.architecture.model.PhysicalSubsystemCommand;
import com.ccb.architecture.model.PhysicalSubsystemQuery;
import com.ccb.architecture.repository.ArchitectureSubsystemRepository;
import com.ccb.architecture.web.ArchitectureNotFoundException;
import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.SystemOperationAudit;
import com.ccb.system.capability.SystemOperationAuditCommand;
import com.ccb.system.capability.SystemParameterReference;
import com.ccb.system.capability.SystemReferenceQuery;
import com.ccb.system.capability.SystemUserReference;
import com.ccb.system.org.OrgTreeNode;
import com.ccb.system.org.OrganizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.regex.Pattern;

@Service
public class PhysicalSubsystemService {
    static final String RUNTIME_CATEGORY = "ARCH_RUNTIME";
    static final String SYSTEM_LEVEL_CATEGORY = "ARCH_SYSTEM_LEVEL";
    static final String DEVELOPMENT_FRAMEWORK_CATEGORY = "ARCH_DEVELOPMENT_FRAMEWORK";
    static final String DEPLOYMENT_PLATFORM_CATEGORY = "ARCH_DEPLOYMENT_PLATFORM";
    static final String DISASTER_RECOVERY_MODE_CATEGORY = "ARCH_DISASTER_RECOVERY_MODE";
    static final String BUSINESS_COMPONENT_CATEGORY = ArchitectureOptionsService.BUSINESS_COMPONENT_CATEGORY;

    private static final Logger log = LoggerFactory.getLogger(PhysicalSubsystemService.class);
    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Z0-9_-]{2,32}");
    private static final String RESOURCE_PATH = "/api/architecture/physical-subsystems";
    private static final String CREATE_OPERATION = "ARCHITECTURE_PHYSICAL_CREATE";
    private static final String UPDATE_OPERATION = "ARCHITECTURE_PHYSICAL_UPDATE";
    private static final String DELETE_OPERATION = "ARCHITECTURE_PHYSICAL_DELETE";
    private static final Set<String> PUBLISHED_STATUSES = Set.of("ACTIVE", "OFFLINE", "VOIDED");
    static final String WORK_ORDER_REQUIRED_MESSAGE =
            "ARCHITECTURE_WORK_ORDER_REQUIRED：请通过架构子系统变更工单发起申请";

    private final ArchitectureSubsystemRepository repository;
    private final OrganizationService organizationService;
    private final SystemReferenceQuery referenceQuery;
    private final SystemOperationAudit operationAudit;
    private final TransactionTemplate transactions;

    public PhysicalSubsystemService(ArchitectureSubsystemRepository repository,
                                    OrganizationService organizationService,
                                    SystemReferenceQuery referenceQuery,
                                    SystemOperationAudit operationAudit,
                                    TransactionTemplate transactions) {
        this.repository = repository;
        this.organizationService = organizationService;
        this.referenceQuery = referenceQuery;
        this.operationAudit = operationAudit;
        this.transactions = transactions;
    }

    public PageResult<PhysicalSubsystemView> list(AuthUser actor, PageQuery page, PhysicalSubsystemQuery query) {
        requireActor(actor);
        PhysicalSubsystemQuery normalized = normalizeQuery(actor, query);
        validateQueryIds(normalized);
        PageResult<PhysicalSubsystem> result = repository.pagePhysical(actor.tenantId(), page, normalized);
        ProjectionContext context = projectionContext(actor);
        List<PhysicalSubsystemView> records = result.records().stream()
                .map(item -> toView(actor, item, context))
                .toList();
        return new PageResult<>(records, result.total(), result.page(), result.size());
    }

    public PhysicalSubsystemView detail(AuthUser actor, long id) {
        requireActor(actor);
        requirePositiveId(id);
        PhysicalSubsystem subsystem = repository.findPhysical(actor.tenantId(), id)
                .orElseThrow(() -> notFound(id));
        return toView(actor, subsystem, projectionContext(actor));
    }

    public PhysicalSubsystemView create(AuthUser actor, PhysicalSubsystemCommand command, String traceId) {
        requireActor(actor);
        throw workOrderRequired();
    }

    public PhysicalSubsystemView update(AuthUser actor, long id, PhysicalSubsystemCommand command, String traceId) {
        requireActor(actor);
        throw workOrderRequired();
    }

    public void delete(AuthUser actor, long id, String traceId) {
        requireActor(actor);
        throw workOrderRequired();
    }

    private PreparedCommand prepare(AuthUser actor, PhysicalSubsystemCommand command) {
        if (command == null) {
            throw badRequest("请求内容不能为空");
        }
        String code = required(command.code(), "系统编号", 2, 32).toUpperCase(Locale.ROOT);
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw badRequest("系统编号只能包含字母、数字、连字符和下划线，长度为 2—32 位");
        }
        String shortName = required(command.shortName(), "系统简称", 2, 100);
        String name = required(command.name(), "系统名称", 2, 200);
        String logicalSubsystemName = optional(command.logicalSubsystemName(), "逻辑子系统", 200);
        String businessComponentCode = validateParameter(actor, BUSINESS_COMPONENT_CATEGORY,
                command.businessComponentCode(), "业务组件编号");
        long responsibleTeamOrgId = requiredId(command.responsibleTeamOrgId(), "负责团队");
        OrgTreeNode responsibleTeam = requireActiveOrganization(actor, responsibleTeamOrgId, "负责团队");
        Long ownerUserId = validateOptionalUser(actor, command.ownerUserId(), "系统负责人");
        String runtimeCode = validateParameter(actor, RUNTIME_CATEGORY, command.runtimeCode(), "系统运行时间");
        String systemLevelCode = validateParameter(actor, SYSTEM_LEVEL_CATEGORY, command.systemLevelCode(), "系统级别");
        String developmentFrameworkCode = validateParameter(actor, DEVELOPMENT_FRAMEWORK_CATEGORY,
                command.developmentFrameworkCode(), "开发平台框架");
        String deploymentPlatform = validateParameter(actor, DEPLOYMENT_PLATFORM_CATEGORY,
                command.deploymentPlatform(), "部署平台");
        String disasterRecoveryMode = validateParameter(actor, DISASTER_RECOVERY_MODE_CATEGORY,
                command.disasterRecoveryMode(), "灾备模式");
        PhysicalSubsystemCommand normalized = new PhysicalSubsystemCommand(code, shortName, name,
                logicalSubsystemName, businessComponentCode,
                optional(command.businessGroupName(), "所属事业群", 100),
                deploymentPlatform, disasterRecoveryMode,
                responsibleTeamOrgId, runtimeCode, systemLevelCode, developmentFrameworkCode,
                ownerUserId, optional(command.description(), "系统描述", 2000),
                optional(command.remark(), "备注", 1000));
        return new PreparedCommand(normalized, responsibleTeam.orgName());
    }

    private PhysicalSubsystemView toView(AuthUser actor, PhysicalSubsystem item, ProjectionContext context) {
        OrgTreeNode currentTeam = context.organizations().get(item.responsibleTeamOrgId());
        boolean responsibleTeamValid = currentTeam != null && currentTeam.status() == 1;
        String responsibleTeamDisplayName = responsibleTeamValid
                ? currentTeam.orgName() : item.responsibleTeamNameSnapshot();
        SystemUserReference owner = userReference(actor, item.ownerUserId(), context.users());
        SystemUserReference creator = userReference(actor, item.createdBy(), context.users());
        return new PhysicalSubsystemView(item.id(), item.code(), item.shortName(), item.name(),
                item.logicalSubsystemName(), item.businessComponentCode(),
                item.businessGroupName(), item.deploymentPlatform(), item.disasterRecoveryMode(),
                item.responsibleTeamOrgId(), responsibleTeamDisplayName,
                responsibleTeamValid, item.runtimeCode(), item.systemLevelCode(), item.developmentFrameworkCode(),
                item.ownerUserId(), owner == null ? null : owner.displayName(),
                item.description(), item.remark(), item.createdBy(), creator == null ? null : creator.displayName(),
                item.updatedBy(), item.createdAt(), item.updatedAt(), item.englishName(),
                item.status(), item.rowVersion());
    }

    private SystemUserReference userReference(AuthUser actor, Long userId, Map<Long, Optional<SystemUserReference>> cache) {
        if (userId == null) {
            return null;
        }
        return cache.computeIfAbsent(userId, key -> {
            Optional<SystemUserReference> reference = referenceQuery.findUser(actor, key, false);
            return reference == null ? Optional.empty() : reference;
        }).orElse(null);
    }

    private ProjectionContext projectionContext(AuthUser actor) {
        return new ProjectionContext(organizationMap(actor), new HashMap<>());
    }

    private Map<Long, OrgTreeNode> organizationMap(AuthUser actor) {
        Map<Long, OrgTreeNode> result = new LinkedHashMap<>();
        Deque<OrgTreeNode> pending = new ArrayDeque<>(organizationService.tree(actor));
        while (!pending.isEmpty()) {
            OrgTreeNode node = pending.removeFirst();
            result.put(node.id(), node);
            if (node.children() != null) {
                pending.addAll(node.children());
            }
        }
        return result;
    }

    private OrgTreeNode requireActiveOrganization(AuthUser actor, long organizationId, String label) {
        OrgTreeNode organization = organizationMap(actor).get(organizationId);
        if (organization == null) {
            throw badRequest(label + "不存在或不属于当前租户");
        }
        if (organization.status() != 1) {
            throw badRequest(label + "已停用，请重新选择");
        }
        return organization;
    }

    private Long validateOptionalUser(AuthUser actor, Long userId, String label) {
        if (userId == null) {
            return null;
        }
        if (userId <= 0 || referenceQuery.findUser(actor, userId, true).isEmpty()) {
            throw badRequest(label + "不存在、已停用或不属于当前租户");
        }
        return userId;
    }

    private String validateParameter(AuthUser actor, String categoryCode, String input, String label) {
        String normalized = normalizeOptional(input);
        if (normalized == null) {
            return null;
        }
        List<SystemParameterReference> parameters = referenceQuery.activeParameters(actor, categoryCode);
        String expected = normalized;
        return parameters.stream()
                .map(SystemParameterReference::code)
                .filter(code -> code != null && code.trim().equalsIgnoreCase(expected))
                .map(String::trim)
                .findFirst()
                .orElseThrow(() -> badRequest(label + "参数无效或已停用"));
    }

    private void ensureUnique(long tenantId, PhysicalSubsystemCommand command, Long excludeId) {
        if (repository.physicalCodeExists(tenantId, command.code(), excludeId)) {
            throw conflict("系统编号已存在，删除后的编号也不能复用");
        }
        if (repository.physicalNameExists(tenantId, command.name(), excludeId)) {
            throw conflict("系统名称已存在，删除后的名称也不能复用");
        }
    }

    private <T> T executeWrite(AuthUser actor, String operationCode, String method, String path,
                               String traceId, Supplier<T> action) {
        try {
            return action.get();
        } catch (DuplicateKeyException exception) {
            BusinessException conflict = conflict("系统编号或名称已存在，删除后的值也不能复用");
            recordFailure(actor, operationCode, method, path, conflict, traceId);
            throw conflict;
        } catch (RuntimeException exception) {
            recordFailure(actor, operationCode, method, path, exception, traceId);
            throw exception;
        }
    }

    private void recordFailure(AuthUser actor, String operationCode, String method, String path,
                               RuntimeException original, String traceId) {
        try {
            operationAudit.recordFailure(auditCommand(actor, operationCode, method, path,
                    safeErrorMessage(original), traceId));
        } catch (RuntimeException auditFailure) {
            log.error("物理子系统失败审计写入失败，operationCode={}", operationCode, auditFailure);
        }
    }

    private SystemOperationAuditCommand auditCommand(AuthUser actor, String operationCode, String method,
                                                      String path, String error, String traceId) {
        return new SystemOperationAuditCommand(actor, operationCode, method, path, error, traceId);
    }

    private String safeErrorMessage(RuntimeException exception) {
        if (exception instanceof BusinessException || exception instanceof ArchitectureNotFoundException) {
            return exception.getMessage();
        }
        return "物理子系统操作失败";
    }

    private PhysicalSubsystemQuery normalizeQuery(AuthUser actor, PhysicalSubsystemQuery query) {
        if (query == null) {
            return PhysicalSubsystemQuery.empty();
        }
        return new PhysicalSubsystemQuery(normalizeOptional(query.code()), normalizeOptional(query.shortName()),
                normalizeOptional(query.name()), normalizeOptional(query.logicalSubsystemName()),
                validateParameter(actor, BUSINESS_COMPONENT_CATEGORY, query.businessComponentCode(), "业务组件编号"),
                normalizeOptional(query.businessGroupName()), query.responsibleTeamOrgId(),
                normalizeStatus(query.status()));
    }

    private String normalizeStatus(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            return null;
        }
        String status = normalized.toUpperCase(Locale.ROOT);
        if (!PUBLISHED_STATUSES.contains(status)) {
            throw badRequest("发布状态仅支持 ACTIVE、OFFLINE 或 VOIDED");
        }
        return status;
    }

    private void validateQueryIds(PhysicalSubsystemQuery query) {
        if (query.responsibleTeamOrgId() != null && query.responsibleTeamOrgId() <= 0) {
            throw badRequest("负责团队编号无效");
        }
    }

    private void requireActor(AuthUser actor) {
        if (actor == null || actor.id() <= 0 || actor.tenantId() <= 0) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "需要有效的认证用户和租户");
        }
    }

    private void requirePositiveId(long id) {
        if (id <= 0) {
            throw badRequest("物理子系统编号无效");
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
        return new ArchitectureNotFoundException("物理子系统不存在或已删除：" + id);
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(ErrorCode.BAD_REQUEST, message);
    }

    private BusinessException conflict(String message) {
        return new BusinessException(ErrorCode.CONFLICT, message);
    }

    private BusinessException workOrderRequired() {
        return conflict(WORK_ORDER_REQUIRED_MESSAGE);
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000);
    }

    private record PreparedCommand(PhysicalSubsystemCommand command, String responsibleTeamNameSnapshot) {
    }

    private record ProjectionContext(Map<Long, OrgTreeNode> organizations,
                                     Map<Long, Optional<SystemUserReference>> users) {
    }

    public record PhysicalSubsystemView(
            long id,
            String code,
            String shortName,
            String name,
            String logicalSubsystemName,
            String businessComponentCode,
            String businessGroupName,
            String deploymentPlatform,
            String disasterRecoveryMode,
            long responsibleTeamOrgId,
            String responsibleTeamDisplayName,
            boolean responsibleTeamValid,
            String runtimeCode,
            String systemLevelCode,
            String developmentFrameworkCode,
            Long ownerUserId,
            String ownerDisplayName,
            String description,
            String remark,
            long createdBy,
            String createdByDisplayName,
            long updatedBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            String englishName,
            String status,
            long rowVersion) {
    }
}
