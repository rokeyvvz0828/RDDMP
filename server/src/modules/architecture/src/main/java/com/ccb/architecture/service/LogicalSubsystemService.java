package com.ccb.architecture.service;

import com.ccb.architecture.model.LogicalSubsystem;
import com.ccb.architecture.model.LogicalSubsystemCommand;
import com.ccb.architecture.model.LogicalSubsystemLock;
import com.ccb.architecture.model.LogicalSubsystemQuery;
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
import com.ccb.system.org.OrgTreeNode;
import com.ccb.system.org.OrganizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.regex.Pattern;

@Service
public class LogicalSubsystemService {
    static final String DEPLOYMENT_PLATFORM_CATEGORY = "ARCH_DEPLOYMENT_PLATFORM";
    static final String SYSTEM_TYPE_CATEGORY = "ARCH_SYSTEM_TYPE";
    static final String SYSTEM_OWNERSHIP_CATEGORY = "ARCH_SYSTEM_OWNERSHIP";

    private static final Logger log = LoggerFactory.getLogger(LogicalSubsystemService.class);
    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Z0-9_-]{2,32}");
    private static final String RESOURCE_PATH = "/api/architecture/logical-subsystems";
    private static final String CREATE_OPERATION = "ARCHITECTURE_LOGICAL_CREATE";
    private static final String UPDATE_OPERATION = "ARCHITECTURE_LOGICAL_UPDATE";
    private static final String DELETE_OPERATION = "ARCHITECTURE_LOGICAL_DELETE";

    private final ArchitectureSubsystemRepository repository;
    private final OrganizationService organizationService;
    private final SystemReferenceQuery referenceQuery;
    private final SystemOperationAudit operationAudit;
    private final TransactionTemplate transactions;

    public LogicalSubsystemService(ArchitectureSubsystemRepository repository,
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

    public PageResult<LogicalSubsystem> list(AuthUser actor, PageQuery page, LogicalSubsystemQuery query) {
        requireActor(actor);
        LogicalSubsystemQuery normalized = normalizeQuery(query);
        if (normalized.businessOrgId() != null && normalized.businessOrgId() <= 0) {
            throw badRequest("事业群编号无效");
        }
        return repository.pageLogical(actor.tenantId(), page, normalized);
    }

    public LogicalSubsystem detail(AuthUser actor, long id) {
        requireActor(actor);
        requirePositiveId(id);
        return repository.findLogical(actor.tenantId(), id)
                .orElseThrow(() -> notFound(id));
    }

    public LogicalSubsystem create(AuthUser actor, LogicalSubsystemCommand command, String traceId) {
        requireActor(actor);
        return executeWrite(actor, CREATE_OPERATION, "POST", RESOURCE_PATH, traceId, () -> {
            LogicalSubsystemCommand normalized = normalizeAndValidate(actor, command);
            ensureUnique(actor.tenantId(), normalized, null);
            long id = nextId();
            repository.insertLogical(id, actor.tenantId(), normalized, actor.id());
            operationAudit.recordSuccess(auditCommand(actor, CREATE_OPERATION, "POST", RESOURCE_PATH, null, traceId));
            return repository.findLogical(actor.tenantId(), id)
                    .orElseThrow(() -> new IllegalStateException("逻辑子系统创建后无法读取"));
        });
    }

    public LogicalSubsystem update(AuthUser actor, long id, LogicalSubsystemCommand command, String traceId) {
        requireActor(actor);
        String path = RESOURCE_PATH + "/" + id;
        return executeWrite(actor, UPDATE_OPERATION, "PUT", path, traceId, () -> {
            requirePositiveId(id);
            if (repository.findLogical(actor.tenantId(), id).isEmpty()) {
                throw notFound(id);
            }
            LogicalSubsystemCommand normalized = normalizeAndValidate(actor, command);
            ensureUnique(actor.tenantId(), normalized, id);
            if (repository.updateLogical(actor.tenantId(), id, normalized, actor.id()) == 0) {
                throw notFound(id);
            }
            operationAudit.recordSuccess(auditCommand(actor, UPDATE_OPERATION, "PUT", path, null, traceId));
            return repository.findLogical(actor.tenantId(), id)
                    .orElseThrow(() -> notFound(id));
        });
    }

    public void delete(AuthUser actor, long id, String traceId) {
        requireActor(actor);
        String path = RESOURCE_PATH + "/" + id;
        executeWrite(actor, DELETE_OPERATION, "DELETE", path, traceId, () -> {
            requirePositiveId(id);
            LogicalSubsystemLock locked = repository.lockLogical(actor.tenantId(), id)
                    .filter(record -> !record.deleted())
                    .orElseThrow(() -> notFound(id));
            if (repository.countActivePhysicalByLogical(actor.tenantId(), locked.id()) > 0) {
                throw conflict("逻辑子系统仍被物理子系统引用，不能删除");
            }
            if (repository.softDeleteLogical(actor.tenantId(), locked.id(), actor.id()) == 0) {
                throw notFound(id);
            }
            operationAudit.recordSuccess(auditCommand(actor, DELETE_OPERATION, "DELETE", path, null, traceId));
            return null;
        });
    }

    private LogicalSubsystemCommand normalizeAndValidate(AuthUser actor, LogicalSubsystemCommand command) {
        if (command == null) {
            throw badRequest("请求内容不能为空");
        }
        String code = required(command.code(), "系统编号", 2, 32).toUpperCase(Locale.ROOT);
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw badRequest("系统编号只能包含字母、数字、连字符和下划线，长度为 2—32 位");
        }
        String shortName = required(command.shortName(), "系统简称", 2, 100);
        String name = required(command.name(), "系统名称", 2, 200);
        long businessOrgId = requiredId(command.businessOrgId(), "事业群");
        long contactUserId = requiredId(command.contactUserId(), "联系人");
        requireActiveOrganization(actor, businessOrgId, "事业群");
        if (referenceQuery.findUser(actor, contactUserId, true).isEmpty()) {
            throw badRequest("联系人不存在、已停用或不属于当前租户");
        }
        String deploymentPlatformCode = validateParameter(actor, DEPLOYMENT_PLATFORM_CATEGORY,
                command.deploymentPlatformCode(), "部署平台");
        String systemTypeCode = validateParameter(actor, SYSTEM_TYPE_CATEGORY,
                command.systemTypeCode(), "系统类型");
        String systemOwnershipCode = validateParameter(actor, SYSTEM_OWNERSHIP_CATEGORY,
                command.systemOwnershipCode(), "系统归属");
        return new LogicalSubsystemCommand(code, shortName, name, businessOrgId,
                deploymentPlatformCode, systemTypeCode, systemOwnershipCode, contactUserId,
                optional(command.description(), "系统描述", 2000), optional(command.remark(), "备注", 1000));
    }

    private void requireActiveOrganization(AuthUser actor, long organizationId, String label) {
        Deque<OrgTreeNode> pending = new ArrayDeque<>(organizationService.tree(actor));
        while (!pending.isEmpty()) {
            OrgTreeNode node = pending.removeFirst();
            if (node.id() == organizationId) {
                if (node.status() != 1) {
                    throw badRequest(label + "已停用");
                }
                return;
            }
            if (node.children() != null) {
                pending.addAll(node.children());
            }
        }
        throw badRequest(label + "不存在或不属于当前租户");
    }

    private String validateParameter(AuthUser actor, String categoryCode, String input, String label) {
        String normalized = normalizeOptional(input);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        List<SystemParameterReference> parameters = referenceQuery.activeParameters(actor, categoryCode);
        String expected = normalized;
        boolean valid = parameters.stream().anyMatch(item -> item.code() != null
                && item.code().trim().equalsIgnoreCase(expected));
        if (!valid) {
            throw badRequest(label + "参数无效或已停用");
        }
        return normalized;
    }

    private void ensureUnique(long tenantId, LogicalSubsystemCommand command, Long excludeId) {
        if (repository.logicalCodeExists(tenantId, command.code(), excludeId)) {
            throw conflict("系统编号已存在，删除后的编号也不能复用");
        }
        if (repository.logicalNameExists(tenantId, command.name(), excludeId)) {
            throw conflict("系统名称已存在，删除后的名称也不能复用");
        }
    }

    private <T> T executeWrite(AuthUser actor, String operationCode, String method, String path,
                               String traceId, Supplier<T> action) {
        try {
            return transactions.execute(status -> action.get());
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
            log.error("逻辑子系统失败审计写入失败，operationCode={}", operationCode, auditFailure);
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
        return "逻辑子系统操作失败";
    }

    private LogicalSubsystemQuery normalizeQuery(LogicalSubsystemQuery query) {
        if (query == null) {
            return LogicalSubsystemQuery.empty();
        }
        return new LogicalSubsystemQuery(normalizeOptional(query.code()), normalizeOptional(query.shortName()),
                normalizeOptional(query.name()), query.businessOrgId());
    }

    private void requireActor(AuthUser actor) {
        if (actor == null || actor.id() <= 0 || actor.tenantId() <= 0) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "需要有效的认证用户和租户");
        }
    }

    private void requirePositiveId(long id) {
        if (id <= 0) {
            throw badRequest("逻辑子系统编号无效");
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
        return new ArchitectureNotFoundException("逻辑子系统不存在或已删除：" + id);
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(ErrorCode.BAD_REQUEST, message);
    }

    private BusinessException conflict(String message) {
        return new BusinessException(ErrorCode.CONFLICT, message);
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000);
    }
}
