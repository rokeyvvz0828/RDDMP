package com.ccb.architecture.decision.integration;

import com.ccb.architecture.decision.model.DecisionModels.Conclusion;
import com.ccb.architecture.decision.model.DecisionModels.DecisionMatter;
import com.ccb.architecture.decision.model.DecisionModels.MatterStatus;
import com.ccb.architecture.decision.model.DecisionModels.PublicationIntent;
import com.ccb.architecture.decision.model.DecisionModels.ReviewRecord;
import com.ccb.architecture.decision.model.DecisionModels.Supersession;
import com.ccb.architecture.decision.model.DecisionModels.SupersessionTarget;
import com.ccb.architecture.decision.model.DecisionModels.WorkflowReceiptStart;
import com.ccb.architecture.decision.model.DecisionModels.WorkflowReceiptStatus;
import com.ccb.architecture.decision.model.DecisionModels.WorkflowRound;
import com.ccb.architecture.decision.model.DecisionModels.WorkflowRoundStatus;
import com.ccb.architecture.decision.persistence.DecisionStore;
import com.ccb.architecture.decision.service.ArchitectureDecisionService;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.workflow.integration.WorkflowBusinessContext;
import com.ccb.workflow.integration.WorkflowLifecycleConsumer;
import com.ccb.workflow.integration.WorkflowLifecycleEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;

/**
 * 架构决策结论发布的可信工作流生命周期消费者。
 *
 * <p>事件只携带流程身份与摘要；批准发布始终重新读取发布准备意图和评审记录。
 * 结论行、替代关系与事项完成状态在同一事务写入，发布失败时回执一并回滚，
 * 从而允许平台安全重试。已发布结论不存在任何修改路径。</p>
 */
@Component
public class ArchitectureDecisionWorkflowLifecycleConsumer implements WorkflowLifecycleConsumer {
    public static final String SUBSCRIBER_KEY = ArchitectureDecisionService.SUBSCRIBER_KEY;

    private final DecisionStore store;
    private final LongSupplier idSupplier;

    @Autowired
    public ArchitectureDecisionWorkflowLifecycleConsumer(DecisionStore store) {
        this(store, () -> System.currentTimeMillis() * 1_000 + ThreadLocalRandom.current().nextInt(1_000));
    }

    ArchitectureDecisionWorkflowLifecycleConsumer(DecisionStore store, LongSupplier idSupplier) {
        this.store = Objects.requireNonNull(store, "决策存储不能为空");
        this.idSupplier = Objects.requireNonNull(idSupplier, "标识生成器不能为空");
    }

    @Override
    public String subscriberKey() {
        return SUBSCRIBER_KEY;
    }

    @Override
    public boolean supports(String businessType) {
        return ArchitectureDecisionService.BUSINESS_TYPE.equals(businessType);
    }

    @Override
    @Transactional
    public void consume(WorkflowLifecycleEvent event) {
        long matterId = validateAndMatterId(event);
        DecisionMatter matter = store.lockMatter(event.tenantId(), matterId)
                .orElseThrow(() -> conflict("工作流事件关联的架构决策事项不存在"));
        if (!store.beginReceipt(new WorkflowReceiptStart(nextId(), event.tenantId(), event.eventId(),
                SUBSCRIBER_KEY, matterId, event.context().businessRound(), event.instanceId(),
                event.eventType().name()))) {
            return;
        }

        WorkflowRound round = store.lockWorkflowRoundByInstance(event.tenantId(), event.instanceId())
                .orElseThrow(() -> conflict("工作流事件关联的发布轮次不存在"));
        if (!matches(matter, round, event)
                || !store.isLatestWorkflowRound(event.tenantId(), matter.id(), round.roundNo())) {
            ignored(event, "事件不匹配当前实例、轮次或摘要");
            return;
        }

        switch (event.eventType()) {
            case STARTED -> consumeStarted(event, matter, round);
            case APPROVED -> consumeApproved(event, matter, round);
            case RETURNED -> consumePublicationRefused(event, matter, round, WorkflowRoundStatus.RETURNED);
            case REJECTED -> consumePublicationRefused(event, matter, round, WorkflowRoundStatus.REJECTED);
            case TERMINATED -> consumeTerminated(event, matter, round);
        }
    }

    private void consumeStarted(WorkflowLifecycleEvent event, DecisionMatter matter, WorkflowRound round) {
        if (!isActivePublication(matter, round)) {
            ignored(event, "STARTED 事件对应的事项或轮次已变化");
            return;
        }
        processed(event, "已确认结论发布轮次启动");
    }

    private void consumeApproved(WorkflowLifecycleEvent event, DecisionMatter matter, WorkflowRound round) {
        if (!isActivePublication(matter, round)) {
            ignored(event, "APPROVED 事件对应的事项或轮次已变化");
            return;
        }
        publishConclusion(event, matter, round);
        processed(event, "已发布正式决策结论并完成事项");
    }

    private void consumePublicationRefused(WorkflowLifecycleEvent event, DecisionMatter matter,
                                           WorkflowRound round, WorkflowRoundStatus roundStatus) {
        if (!isActivePublication(matter, round)) {
            ignored(event, event.eventType() + " 事件对应的事项或轮次已变化");
            return;
        }
        store.completeStartedWorkflowRound(event.tenantId(), matter.id(), round.roundNo(),
                roundStatus, event.occurredAt());
        processed(event, "结论发布被退回或拒绝，事项保持评审中，发布准备保留可调整");
    }

    private void consumeTerminated(WorkflowLifecycleEvent event, DecisionMatter matter, WorkflowRound round) {
        if (!isActivePublication(matter, round)) {
            ignored(event, "TERMINATED 事件对应的事项或轮次已变化");
            return;
        }
        store.completeStartedWorkflowRound(event.tenantId(), matter.id(), round.roundNo(),
                WorkflowRoundStatus.TERMINATED, event.occurredAt());
        processed(event, "结论发布流程被终止，事项保持评审中");
    }

    private void publishConclusion(WorkflowLifecycleEvent event, DecisionMatter matter, WorkflowRound round) {
        PublicationIntent intent = store.findPublicationIntent(event.tenantId(), matter.id())
                .orElseThrow(() -> conflict("发布准备意图不存在，不能发布结论"));
        ReviewRecord review = store.findReview(event.tenantId(), matter.id(), intent.reviewId())
                .orElseThrow(() -> conflict("结论来源评审记录不存在"));
        if (review.conclusionContent() == null || review.conclusionContent().isBlank()) {
            throw conflict("评审记录缺少正式结论，不能发布");
        }
        if (matter.typeCode() == null || matter.typeCode().isBlank()) {
            throw conflict("事项类型未确定，不能发布结论");
        }
        if (store.findConclusion(event.tenantId(), matter.id()).isPresent()) {
            throw conflict("事项已存在已发布结论");
        }
        for (SupersessionTarget target : intent.targets()) {
            store.findConclusionById(event.tenantId(), target.conclusionId())
                    .orElseThrow(() -> conflict("替代目标结论不存在：" + target.conclusionId()));
        }
        long conclusionId = nextId();
        store.insertConclusion(new Conclusion(conclusionId, event.tenantId(), matter.id(), review.id(),
                review.conclusionContent(), review.conclusionRationale(), event.occurredAt(),
                event.operatorId(), operatorName(event), event.occurredAt()));
        long supersessionId = conclusionId * 10;
        for (SupersessionTarget target : intent.targets()) {
            store.insertSupersession(new Supersession(supersessionId++, event.tenantId(), conclusionId,
                    target.conclusionId(), target.kind(), event.occurredAt()));
        }
        store.markMatterPublished(event.tenantId(), matter.id(), matter.rowVersion());
        store.completeStartedWorkflowRound(event.tenantId(), matter.id(), round.roundNo(),
                WorkflowRoundStatus.APPROVED, event.occurredAt());
    }

    private boolean isActivePublication(DecisionMatter matter, WorkflowRound round) {
        return matter.status() == MatterStatus.IN_REVIEW
                && round.status() == WorkflowRoundStatus.STARTED;
    }

    private boolean matches(DecisionMatter matter, WorkflowRound round, WorkflowLifecycleEvent event) {
        WorkflowBusinessContext context = event.context();
        return matter.id() == round.matterId()
                && matter.tenantId() == event.tenantId()
                && matter.currentBusinessRound() == context.businessRound()
                && Objects.equals(matter.currentWorkflowDefinitionId(), round.workflowDefinitionId())
                && Objects.equals(matter.currentWorkflowVersionId(), round.workflowVersionId())
                && Objects.equals(matter.currentWorkflowInstanceId(), event.instanceId())
                && Objects.equals(round.workflowInstanceId(), event.instanceId())
                && round.roundNo() == context.businessRound()
                && equalsDigest(matter.currentPayloadDigest(), context.dataDigest())
                && equalsDigest(round.payloadDigest(), context.dataDigest())
                && String.valueOf(matter.id()).equals(context.businessKey());
    }

    private long validateAndMatterId(WorkflowLifecycleEvent event) {
        if (event == null || event.context() == null || event.eventType() == null || event.occurredAt() == null
                || event.eventId() == null || event.eventId().isBlank() || event.tenantId() <= 0
                || event.instanceId() <= 0 || event.operatorId() <= 0
                || !supports(event.context().businessType())
                || !ArchitectureDecisionService.MODULE_CODE.equals(event.context().moduleCode())
                || event.context().businessRound() <= 0
                || event.context().dataDigest() == null
                || event.context().dataDigest().length() != 64) {
            throw conflict("工作流生命周期事件无效");
        }
        try {
            long matterId = Long.parseLong(event.context().businessKey());
            if (matterId <= 0) {
                throw new NumberFormatException("非正数");
            }
            return matterId;
        } catch (NumberFormatException exception) {
            throw conflict("工作流事件业务键不是有效的架构决策事项编号");
        }
    }

    private boolean equalsDigest(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private String operatorName(WorkflowLifecycleEvent event) {
        // 事件不携带展示姓名；发布人姓名快照在发布准备时已记录，这里回退为工作流操作者。
        return "工作流操作者 " + event.operatorId();
    }

    private void processed(WorkflowLifecycleEvent event, String detail) {
        if (!store.completeReceipt(event.tenantId(), event.eventId(), SUBSCRIBER_KEY,
                WorkflowReceiptStatus.PROCESSED, detail)) {
            throw conflict("工作流事件回执状态已变化");
        }
    }

    private void ignored(WorkflowLifecycleEvent event, String detail) {
        if (!store.completeReceipt(event.tenantId(), event.eventId(), SUBSCRIBER_KEY,
                WorkflowReceiptStatus.IGNORED, detail)) {
            throw conflict("工作流事件回执状态已变化");
        }
    }

    private long nextId() {
        long value = idSupplier.getAsLong();
        if (value <= 0) {
            throw new IllegalStateException("决策回执标识生成器返回无效值");
        }
        return value;
    }

    private BusinessException conflict(String message) {
        return new BusinessException(ErrorCode.CONFLICT, message);
    }
}
