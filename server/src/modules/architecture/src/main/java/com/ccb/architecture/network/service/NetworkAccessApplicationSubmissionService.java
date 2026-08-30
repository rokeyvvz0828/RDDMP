package com.ccb.architecture.network.service;

import com.ccb.architecture.network.model.NetworkAccessModels.ApplicationStatus;
import com.ccb.architecture.network.model.NetworkAccessModels.NetworkAccessApplication;
import com.ccb.architecture.network.model.NetworkAccessModels.WorkflowRound;
import com.ccb.architecture.network.model.NetworkAccessModels.WorkflowRoundStatus;
import com.ccb.architecture.network.persistence.NetworkAccessStore;
import com.ccb.architecture.network.service.NetworkAccessService.CancellationPreparation;
import com.ccb.architecture.network.service.NetworkAccessService.SubmissionPreparation;
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

/** 网络访问申请与平台工作流公开契约之间的事务协调器。 */
@Service
public class NetworkAccessApplicationSubmissionService {
    public static final String WORKFLOW_DEFINITION_CODE = "architecture.network-access-application";
    public static final String MODULE_CODE = "architecture";
    public static final String MODULE_NAME = "架构管理";
    public static final String BUSINESS_TYPE = "architecture_network_access_application";
    private static final String DETAIL_PATH_PREFIX = "/architecture/network-access";

    private final NetworkAccessService access;
    private final NetworkAccessStore store;
    private final WorkflowBusinessGateway workflowGateway;
    private final LongSupplier idSupplier;
    private final Clock clock;

    @Autowired
    public NetworkAccessApplicationSubmissionService(NetworkAccessService access,
                                                     NetworkAccessStore store,
                                                     WorkflowBusinessGateway workflowGateway) {
        this(access, store, workflowGateway,
                () -> System.currentTimeMillis() * 1_000 + ThreadLocalRandom.current().nextInt(1_000),
                Clock.systemUTC());
    }

    NetworkAccessApplicationSubmissionService(NetworkAccessService access,
                                              NetworkAccessStore store,
                                              WorkflowBusinessGateway workflowGateway,
                                              LongSupplier idSupplier,
                                              Clock clock) {
        this.access = Objects.requireNonNull(access, "网络访问服务不能为空");
        this.store = Objects.requireNonNull(store, "网络访问存储不能为空");
        this.workflowGateway = Objects.requireNonNull(workflowGateway, "工作流网关不能为空");
        this.idSupplier = Objects.requireNonNull(idSupplier, "标识生成器不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    public NetworkAccessApplication submit(AuthUser actor, long applicationId, long expectedRowVersion) {
        access.coordinateSubmission(actor, applicationId, expectedRowVersion,
                preparation -> startWorkflow(actor, preparation));
        return store.findApplication(actor.tenantId(), applicationId)
                .orElseThrow(() -> conflict("网络访问申请不存在"));
    }

    public NetworkAccessApplication cancel(AuthUser actor, long applicationId, long expectedRowVersion) {
        NetworkAccessApplication current = store.findApplication(actor.tenantId(), applicationId)
                .orElseThrow(() -> conflict("网络访问申请不存在"));
        if (current.status() != ApplicationStatus.IN_REVIEW) {
            return access.cancelApplication(actor, applicationId, expectedRowVersion);
        }
        access.coordinateCancellation(actor, applicationId, expectedRowVersion,
                preparation -> terminateWorkflow(actor, preparation));
        return store.findApplication(actor.tenantId(), applicationId)
                .orElseThrow(() -> conflict("网络访问申请不存在"));
    }

    private void startWorkflow(AuthUser actor, SubmissionPreparation preparation) {
        NetworkAccessApplication prepared = store.lockApplication(actor.tenantId(), preparation.applicationId())
                .orElseThrow(() -> conflict("提交准备后的网络访问申请不存在"));
        if (prepared.status() != ApplicationStatus.IN_REVIEW
                || prepared.currentBusinessRound() != preparation.nextRound() - 1) {
            throw conflict("网络访问申请提交状态或业务轮次已变化");
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
            throw conflict("网络访问申请审批轮次启动状态已变化");
        }
        if (!store.compareAndSetApplicationWorkflowContext(prepared.tenantId(), prepared.id(),
                prepared.currentBusinessRound(), prepared.rowVersion(), preparation.nextRound(),
                result.definitionId(), result.definitionVersion(), result.instanceId(),
                preparation.digest(), actor.id())) {
            throw conflict("网络访问申请工作流上下文已被其他操作更新");
        }
    }

    private void terminateWorkflow(AuthUser actor, CancellationPreparation preparation) {
        WorkflowProgress progress = workflowGateway.progress(preparation.workflowInstanceId(), actor);
        if (progress == null || !"RUNNING".equals(progress.status())) {
            throw conflict("审批流程已结束（状态 " + (progress == null ? "未知" : progress.status())
                    + "），不能取消；请等待平台生命周期事件重试或由运维处置");
        }
        workflowGateway.terminate(new WorkflowTerminateCommand(
                preparation.workflowInstanceId(), BUSINESS_TYPE, String.valueOf(preparation.applicationId()),
                preparation.businessRound(), "申请人取消网络访问申请"), actor);
    }

    private WorkflowBusinessContext context(NetworkAccessApplication application,
                                            SubmissionPreparation preparation) {
        return new WorkflowBusinessContext(
                MODULE_CODE,
                MODULE_NAME,
                BUSINESS_TYPE,
                String.valueOf(application.id()),
                "网络访问申请 " + application.applicationNo(),
                preparation.nextRound(),
                null,
                null,
                DETAIL_PATH_PREFIX,
                preparation.digest());
    }

    private Map<String, Object> workflowVariables(NetworkAccessApplication application) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("applicationId", application.id());
        variables.put("applicationNo", application.applicationNo());
        variables.put("actionType", application.actionType().name());
        variables.put("applicantId", application.applicantId());
        variables.put("protocol", application.protocol().name());
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
            throw conflict("审批流程启动结果与网络访问申请上下文不一致");
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
