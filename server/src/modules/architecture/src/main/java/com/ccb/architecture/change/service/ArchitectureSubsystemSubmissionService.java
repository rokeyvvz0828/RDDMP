package com.ccb.architecture.change.service;

import com.ccb.architecture.change.model.SubsystemChangeModels.ApplicationStatus;
import com.ccb.architecture.change.model.SubsystemChangeModels.ChangeApplication;
import com.ccb.architecture.change.model.SubsystemChangeModels.WorkflowRound;
import com.ccb.architecture.change.model.SubsystemChangeModels.WorkflowRoundStatus;
import com.ccb.architecture.change.persistence.SubsystemChangeStore;
import com.ccb.architecture.change.service.SubsystemChangeService.AccessScope;
import com.ccb.architecture.change.service.SubsystemChangeService.ApplicationDetail;
import com.ccb.architecture.change.service.SubsystemChangeService.CancellationPreparation;
import com.ccb.architecture.change.service.SubsystemChangeService.SubmissionPreparation;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.workflow.integration.WorkflowBusinessContext;
import com.ccb.workflow.integration.WorkflowBusinessGateway;
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

/**
 * 架构子系统工单与平台工作流公开契约之间的事务协调器。
 *
 * <p>提交准备和流程启动、轮次绑定必须共享 {@link SubsystemChangeService} 开启的事务；
 * 任一平台结果校验或持久化失败都会让编号、锁、快照和 IN_REVIEW 状态一起回滚。</p>
 */
@Service
public class ArchitectureSubsystemSubmissionService {
    public static final String WORKFLOW_DEFINITION_CODE = "architecture.subsystem.change";
    public static final String MODULE_CODE = "architecture";
    public static final String MODULE_NAME = "架构管理";
    public static final String BUSINESS_TYPE = "architecture_subsystem_change";
    private static final String DETAIL_PATH_PREFIX = "/architecture/subsystem-change-applications/";

    private final SubsystemChangeService changes;
    private final SubsystemChangeStore store;
    private final WorkflowBusinessGateway workflowGateway;
    private final LongSupplier idSupplier;
    private final Clock clock;

    @Autowired
    public ArchitectureSubsystemSubmissionService(SubsystemChangeService changes,
                                                  SubsystemChangeStore store,
                                                  WorkflowBusinessGateway workflowGateway) {
        this(changes, store, workflowGateway,
                () -> System.currentTimeMillis() * 1_000 + ThreadLocalRandom.current().nextInt(1_000),
                Clock.systemUTC());
    }

    ArchitectureSubsystemSubmissionService(SubsystemChangeService changes,
                                           SubsystemChangeStore store,
                                           WorkflowBusinessGateway workflowGateway,
                                           LongSupplier idSupplier,
                                           Clock clock) {
        this.changes = Objects.requireNonNull(changes, "工单服务不能为空");
        this.store = Objects.requireNonNull(store, "工单存储不能为空");
        this.workflowGateway = Objects.requireNonNull(workflowGateway, "工作流网关不能为空");
        this.idSupplier = Objects.requireNonNull(idSupplier, "标识生成器不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    /** 申请人本人提交；manage 用户发起自己的申请时仍走同一工作流。 */
    public ApplicationDetail submit(AuthUser actor, long applicationId, long expectedRowVersion) {
        changes.coordinateSubmission(actor, AccessScope.OWN, applicationId, expectedRowVersion,
                preparation -> startWorkflow(actor, preparation));
        return changes.detail(actor, AccessScope.OWN, applicationId);
    }

    /**
     * 草稿/退回同步取消；审批中先登记取消请求并调用 terminate，等待当前轮次 TERMINATED 事件终态化。
     */
    public ApplicationDetail cancel(AuthUser actor, long applicationId, long expectedRowVersion) {
        ApplicationDetail current = changes.detail(actor, AccessScope.OWN, applicationId);
        if (current.application().status() != ApplicationStatus.IN_REVIEW) {
            return changes.cancel(actor, AccessScope.OWN, applicationId, expectedRowVersion);
        }
        changes.coordinateCancellation(actor, applicationId, expectedRowVersion,
                preparation -> terminateWorkflow(actor, preparation));
        return changes.detail(actor, AccessScope.OWN, applicationId);
    }

    private void startWorkflow(AuthUser actor, SubmissionPreparation preparation) {
        ChangeApplication prepared = store.lockApplication(actor.tenantId(), preparation.applicationId())
                .orElseThrow(() -> conflict("提交准备后的工单不存在"));
        if (prepared.status() != ApplicationStatus.IN_REVIEW
                || prepared.currentBusinessRound() != preparation.nextRound() - 1) {
            throw conflict("工单提交状态或业务轮次已变化");
        }

        long roundId = nextId();
        store.insertPendingWorkflowRound(new WorkflowRound(roundId, prepared.tenantId(), prepared.id(),
                preparation.nextRound(), null, null, null, null, WorkflowRoundStatus.PENDING,
                null, null, null, null));
        WorkflowBusinessContext context = context(prepared, preparation);
        WorkflowStartResult result = workflowGateway.startByCode(new WorkflowStartCommand(
                WORKFLOW_DEFINITION_CODE, context, workflowVariables(prepared, preparation)), actor);
        validateWorkflowResult(result, context);

        LocalDateTime startedAt = LocalDateTime.now(clock);
        if (!store.bindWorkflowRoundStarted(prepared.tenantId(), prepared.id(), preparation.nextRound(),
                result.definitionId(), result.definitionVersion(), result.instanceId(),
                preparation.digest(), startedAt)) {
            throw conflict("审批轮次启动状态已变化");
        }
        if (!store.compareAndSetApplicationWorkflowContext(prepared.tenantId(), prepared.id(),
                prepared.currentBusinessRound(), prepared.rowVersion(), preparation.nextRound(),
                result.definitionId(), result.definitionVersion(), result.instanceId(),
                preparation.digest(), actor.id())) {
            throw conflict("工单工作流上下文已被其他操作更新");
        }
    }

    private void terminateWorkflow(AuthUser actor, CancellationPreparation preparation) {
        workflowGateway.terminate(new WorkflowTerminateCommand(
                preparation.workflowInstanceId(), BUSINESS_TYPE, String.valueOf(preparation.applicationId()),
                preparation.businessRound(), "申请人取消架构子系统变更工单"), actor);
    }

    private WorkflowBusinessContext context(ChangeApplication application, SubmissionPreparation preparation) {
        return new WorkflowBusinessContext(
                MODULE_CODE,
                MODULE_NAME,
                BUSINESS_TYPE,
                String.valueOf(application.id()),
                "架构子系统变更申请 " + application.id(),
                preparation.nextRound(),
                null,
                null,
                DETAIL_PATH_PREFIX + application.id(),
                preparation.digest());
    }

    private Map<String, Object> workflowVariables(ChangeApplication application,
                                                  SubmissionPreparation preparation) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("applicationId", application.id());
        variables.put("targetKind", application.targetKind().name());
        variables.put("actionType", application.actionType().name());
        variables.put("applicantId", application.applicantId());
        variables.put("reservedNumbers", preparation.reservedNumbers().stream()
                .map(SubsystemChangeService.ReservedNumber::code).toList());
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
            throw conflict("审批流程启动结果与架构子系统工单上下文不一致");
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
