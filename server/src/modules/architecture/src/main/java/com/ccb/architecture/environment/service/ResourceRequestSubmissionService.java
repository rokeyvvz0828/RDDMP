package com.ccb.architecture.environment.service;

import com.ccb.architecture.environment.model.EnvironmentResourceModels.RequestStatus;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.ResourceRequest;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.WorkflowRound;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.WorkflowRoundStatus;
import com.ccb.architecture.environment.persistence.EnvironmentResourceStore;
import com.ccb.architecture.environment.service.EnvironmentResourceService.AccessScope;
import com.ccb.architecture.environment.service.EnvironmentResourceService.CancellationPreparation;
import com.ccb.architecture.environment.service.EnvironmentResourceService.ResourceRequestDetail;
import com.ccb.architecture.environment.service.EnvironmentResourceService.SubmissionPreparation;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.workflow.integration.WorkflowBusinessContext;
import com.ccb.workflow.integration.WorkflowBusinessGateway;
import com.ccb.workflow.integration.WorkflowProgress;
import com.ccb.workflow.integration.WorkflowStartCommand;
import com.ccb.workflow.integration.WorkflowStartResult;
import com.ccb.workflow.integration.WorkflowTerminateCommand;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;

/** 资源申请与平台工作流公开契约之间的事务协调器（REQ-20260824-052）。 */
@Service
public class ResourceRequestSubmissionService {
    public static final String WORKFLOW_DEFINITION_CODE = "architecture.resource-request";
    public static final String MODULE_CODE = "architecture";
    public static final String MODULE_NAME = "架构管理";
    public static final String BUSINESS_TYPE = "architecture_resource_request";
    private static final String DETAIL_PATH_PREFIX = "/architecture/resource-requests/";

    private final EnvironmentResourceService changes;
    private final EnvironmentResourceStore store;
    private final WorkflowBusinessGateway workflowGateway;
    private final LongSupplier idSupplier;
    private final Clock clock;

    @Autowired
    public ResourceRequestSubmissionService(EnvironmentResourceService changes,
                                            EnvironmentResourceStore store,
                                            WorkflowBusinessGateway workflowGateway) {
        this(changes, store, workflowGateway,
                () -> System.currentTimeMillis() * 1_000 + ThreadLocalRandom.current().nextInt(1_000),
                Clock.systemUTC());
    }

    ResourceRequestSubmissionService(EnvironmentResourceService changes,
                                     EnvironmentResourceStore store,
                                     WorkflowBusinessGateway workflowGateway,
                                     LongSupplier idSupplier,
                                     Clock clock) {
        this.changes = Objects.requireNonNull(changes, "环境资源服务不能为空");
        this.store = Objects.requireNonNull(store, "环境资源存储不能为空");
        this.workflowGateway = Objects.requireNonNull(workflowGateway, "工作流网关不能为空");
        this.idSupplier = Objects.requireNonNull(idSupplier, "标识生成器不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    public ResourceRequestDetail submit(AuthUser actor, long requestId, long expectedRowVersion) {
        changes.coordinateSubmission(actor, requestId, expectedRowVersion,
                preparation -> startWorkflow(actor, preparation));
        return changes.detailRequest(actor, AccessScope.OWN, requestId);
    }

    public ResourceRequestDetail cancel(AuthUser actor, long requestId, long expectedRowVersion) {
        ResourceRequestDetail current = changes.detailRequest(actor, AccessScope.OWN, requestId);
        if (current.request().status() != RequestStatus.IN_REVIEW) {
            return changes.cancel(actor, AccessScope.OWN, requestId, expectedRowVersion);
        }
        changes.coordinateCancellation(actor, requestId, expectedRowVersion,
                preparation -> terminateWorkflow(actor, preparation));
        return changes.detailRequest(actor, AccessScope.OWN, requestId);
    }

    private void startWorkflow(AuthUser actor, SubmissionPreparation preparation) {
        ResourceRequest prepared = store.lockRequest(actor.tenantId(), preparation.requestId())
                .orElseThrow(() -> conflict("提交准备后的资源申请不存在"));
        if (prepared.status() != RequestStatus.IN_REVIEW
                || prepared.currentBusinessRound() != preparation.nextRound() - 1) {
            throw conflict("资源申请提交状态或业务轮次已变化");
        }

        long roundId = nextId();
        store.insertPendingWorkflowRound(new WorkflowRound(roundId, prepared.tenantId(), prepared.id(),
                preparation.nextRound(), null, null, null, null, WorkflowRoundStatus.PENDING,
                null, null, null, null));
        WorkflowBusinessContext context = context(prepared, preparation);
        WorkflowStartResult result = workflowGateway.startByCode(new WorkflowStartCommand(
                WORKFLOW_DEFINITION_CODE, context, workflowVariables(prepared)), actor);
        validateWorkflowResult(result, context);

        LocalDateTime startedAt = LocalDateTime.now(clock);
        if (!store.bindWorkflowRoundStarted(prepared.tenantId(), prepared.id(), preparation.nextRound(),
                result.definitionId(), result.definitionVersion(), result.instanceId(),
                preparation.digest(), startedAt)) {
            throw conflict("资源申请审批轮次启动状态已变化");
        }
        if (!store.compareAndSetWorkflowContext(prepared.tenantId(), prepared.id(),
                prepared.currentBusinessRound(), prepared.rowVersion(), preparation.nextRound(),
                result.definitionId(), result.definitionVersion(), result.instanceId(),
                preparation.digest(), actor.id())) {
            throw conflict("资源申请工作流上下文已被其他操作更新");
        }
    }

    private void terminateWorkflow(AuthUser actor, CancellationPreparation preparation) {
        WorkflowProgress progress = workflowGateway.progress(preparation.workflowInstanceId(), actor);
        if (progress == null || !"RUNNING".equals(progress.status())) {
            throw conflict("审批流程已结束（状态 " + (progress == null ? "未知" : progress.status())
                    + "），不能取消；请等待平台生命周期事件重试或由运维处置");
        }
        workflowGateway.terminate(new WorkflowTerminateCommand(
                preparation.workflowInstanceId(), BUSINESS_TYPE, String.valueOf(preparation.requestId()),
                preparation.businessRound(), "申请人取消资源申请"), actor);
    }

    private WorkflowBusinessContext context(ResourceRequest request, SubmissionPreparation preparation) {
        return new WorkflowBusinessContext(
                MODULE_CODE,
                MODULE_NAME,
                BUSINESS_TYPE,
                String.valueOf(request.id()),
                "资源申请 " + request.requestNo(),
                preparation.nextRound(),
                null,
                null,
                DETAIL_PATH_PREFIX + request.id(),
                preparation.digest());
    }

    private Map<String, Object> workflowVariables(ResourceRequest request) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("resourceRequestId", request.id());
        variables.put("requestNo", request.requestNo());
        variables.put("physicalSubsystemId", request.physicalSubsystemId());
        variables.put("environmentId", request.environmentId());
        variables.put("requestType", request.requestType().name());
        variables.put("applicantId", request.applicantId());
        return Map.copyOf(variables);
    }

    private void validateWorkflowResult(WorkflowStartResult result, WorkflowBusinessContext expected) {
        if (result == null || result.instanceId() <= 0 || result.definitionId() <= 0
                || result.definitionVersion() <= 0 || result.context() == null
                || !Objects.equals(result.context().moduleCode(), expected.moduleCode())
                || !Objects.equals(result.context().businessType(), expected.businessType())
                || !Objects.equals(result.context().businessKey(), expected.businessKey())
                || result.context().businessRound() != expected.businessRound()
                || !Objects.equals(result.context().dataDigest(), expected.dataDigest())) {
            throw conflict("审批流程启动结果与资源申请上下文不一致");
        }
    }

    private long nextId() {
        long value = idSupplier.getAsLong();
        if (value <= 0) {
            throw new IllegalStateException("工作流轮次标识生成器返回无效值");
        }
        return value;
    }

    private BusinessException conflict(String message) {
        return new BusinessException(ErrorCode.CONFLICT, message);
    }
}
