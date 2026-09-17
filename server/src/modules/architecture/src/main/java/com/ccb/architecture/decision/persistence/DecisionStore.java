package com.ccb.architecture.decision.persistence;

import com.ccb.architecture.decision.model.DecisionModels.ActionItem;
import com.ccb.architecture.decision.model.DecisionModels.ActionItemInput;
import com.ccb.architecture.decision.model.DecisionModels.Conclusion;
import com.ccb.architecture.decision.model.DecisionModels.ConclusionEffectiveStatus;
import com.ccb.architecture.decision.model.DecisionModels.DecisionMatter;
import com.ccb.architecture.decision.model.DecisionModels.FirstHandlingOutcome;
import com.ccb.architecture.decision.model.DecisionModels.MaterialRecord;
import com.ccb.architecture.decision.model.DecisionModels.MatterQuery;
import com.ccb.architecture.decision.model.DecisionModels.PublicationIntent;
import com.ccb.architecture.decision.model.DecisionModels.ReviewMethod;
import com.ccb.architecture.decision.model.DecisionModels.ReviewRecord;
import com.ccb.architecture.decision.model.DecisionModels.Supersession;
import com.ccb.architecture.decision.model.DecisionModels.WorkflowReceiptStart;
import com.ccb.architecture.decision.model.DecisionModels.WorkflowReceiptStatus;
import com.ccb.architecture.decision.model.DecisionModels.WorkflowRound;
import com.ccb.architecture.decision.model.DecisionModels.WorkflowRoundStatus;
import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Service-facing decision persistence facade backed by the MyBatis repository contract. */
@Component
public class DecisionStore {
    private final DecisionRepository repository;

    public DecisionStore(DecisionRepository repository) { this.repository = repository; }

    public PageResult<DecisionMatter> pageMatters(long tenantId, PageQuery page, MatterQuery query) { return repository.pageMatters(tenantId, page, query); }
    public Optional<DecisionMatter> findMatter(long tenantId, long id) { return repository.findMatter(tenantId, id); }
    public Optional<DecisionMatter> lockMatter(long tenantId, long id, long expectedRowVersion) { return repository.lockMatter(tenantId, id, expectedRowVersion); }
    public Optional<DecisionMatter> lockMatter(long tenantId, long id) { return repository.lockMatter(tenantId, id); }
    public int allocateMatterOrdinal(long tenantId, int year) { return repository.allocateMatterOrdinal(tenantId, year); }
    public long createMatter(DecisionMatter matter) { return repository.createMatter(matter); }
    public void updateMatter(long tenantId, long id, long expectedRowVersion, String title, String problem, long operatorId) { repository.updateMatter(tenantId, id, expectedRowVersion, title, problem, operatorId); }
    public void applyFirstHandling(long tenantId, long id, long expectedRowVersion, FirstHandlingOutcome outcome, String comment, ReviewMethod reviewMode, long handlerId, String handlerName) { repository.applyFirstHandling(tenantId, id, expectedRowVersion, outcome, comment, reviewMode, handlerId, handlerName); }
    public void resubmit(long tenantId, long id, long expectedRowVersion, LocalDateTime receivedAt, LocalDate deadline, long submitterId, String submitterName) { repository.resubmit(tenantId, id, expectedRowVersion, receivedAt, deadline, submitterId, submitterName); }
    public void setMatterType(long tenantId, long id, long expectedRowVersion, String typeCode, long operatorId) { repository.setMatterType(tenantId, id, expectedRowVersion, typeCode, operatorId); }
    public int touchPublicationPreparation(long tenantId, long id, long expectedRowVersion, long operatorId) { return repository.touchPublicationPreparation(tenantId, id, expectedRowVersion, operatorId); }
    public long addMaterial(MaterialRecord record) { return repository.addMaterial(record); }
    public List<MaterialRecord> listMaterials(long tenantId, long matterId) { return repository.listMaterials(tenantId, matterId); }
    public long insertReview(ReviewRecord review) { return repository.insertReview(review); }
    public int nextReviewNo(long tenantId, long matterId) { return repository.nextReviewNo(tenantId, matterId); }
    public Optional<ReviewRecord> findReview(long tenantId, long matterId, long reviewId) { return repository.findReview(tenantId, matterId, reviewId); }
    public List<ReviewRecord> listReviews(long tenantId, long matterId) { return repository.listReviews(tenantId, matterId); }
    public void updateReview(ReviewRecord review) { repository.updateReview(review); }
    public void replaceParticipants(long tenantId, long reviewId, List<Long> userIds, Map<Long, String> displayNames) { repository.replaceParticipants(tenantId, reviewId, userIds, displayNames); }
    public List<Long> listParticipantIds(long tenantId, long reviewId) { return repository.listParticipantIds(tenantId, reviewId); }
    public List<Map<String, Object>> listParticipants(long tenantId, long reviewId) { return repository.listParticipants(tenantId, reviewId); }
    public void replaceActionItems(long tenantId, long reviewId, List<ActionItemInput> inputs, long operatorId) { repository.replaceActionItems(tenantId, reviewId, inputs, operatorId); }
    public List<ActionItem> listActionItems(long tenantId, long reviewId) { return repository.listActionItems(tenantId, reviewId); }
    public Optional<ActionItem> findActionItem(long tenantId, long reviewId, long actionItemId) { return repository.findActionItem(tenantId, reviewId, actionItemId); }
    public void completeActionItem(long tenantId, long reviewId, long actionItemId, long operatorId) { repository.completeActionItem(tenantId, reviewId, actionItemId, operatorId); }
    public void upsertPublicationIntent(PublicationIntent intent) { repository.upsertPublicationIntent(intent); }
    public Optional<PublicationIntent> findPublicationIntent(long tenantId, long matterId) { return repository.findPublicationIntent(tenantId, matterId); }
    public Optional<Conclusion> findConclusion(long tenantId, long matterId) { return repository.findConclusion(tenantId, matterId); }
    public Optional<Conclusion> findConclusionById(long tenantId, long conclusionId) { return repository.findConclusionById(tenantId, conclusionId); }
    public PageResult<Conclusion> pageConclusions(long tenantId, PageQuery page, String effectiveStatus) { return repository.pageConclusions(tenantId, page, effectiveStatus); }
    public ConclusionEffectiveStatus conclusionEffectiveStatus(long tenantId, long conclusionId) { return repository.conclusionEffectiveStatus(tenantId, conclusionId); }
    public List<Supersession> listSupersedes(long tenantId, long conclusionId) { return repository.listSupersedes(tenantId, conclusionId); }
    public List<Supersession> listSupersededBy(long tenantId, long conclusionId) { return repository.listSupersededBy(tenantId, conclusionId); }
    public long insertConclusion(Conclusion conclusion) { return repository.insertConclusion(conclusion); }
    public void insertSupersession(Supersession supersession) { repository.insertSupersession(supersession); }
    public void markMatterPublished(long tenantId, long matterId, long expectedRowVersion) { repository.markMatterPublished(tenantId, matterId, expectedRowVersion); }
    public void insertPendingWorkflowRound(WorkflowRound round) { repository.insertPendingWorkflowRound(round); }
    public boolean bindWorkflowRoundStarted(long tenantId, long matterId, int roundNo, long definitionId, int definitionVersion, long instanceId, String digest, LocalDateTime startedAt) { return repository.bindWorkflowRoundStarted(tenantId, matterId, roundNo, definitionId, definitionVersion, instanceId, digest, startedAt); }
    public boolean compareAndSetMatterWorkflowContext(long tenantId, long matterId, int currentRound, long currentRowVersion, int nextRound, long definitionId, int definitionVersion, long instanceId, String digest, long operatorId) { return repository.compareAndSetMatterWorkflowContext(tenantId, matterId, currentRound, currentRowVersion, nextRound, definitionId, definitionVersion, instanceId, digest, operatorId); }
    public Optional<WorkflowRound> lockWorkflowRoundByInstance(long tenantId, long instanceId) { return repository.lockWorkflowRoundByInstance(tenantId, instanceId); }
    public boolean isLatestWorkflowRound(long tenantId, long matterId, int roundNo) { return repository.isLatestWorkflowRound(tenantId, matterId, roundNo); }
    public boolean completeStartedWorkflowRound(long tenantId, long matterId, int roundNo, WorkflowRoundStatus nextStatus, LocalDateTime endedAt) { return repository.completeStartedWorkflowRound(tenantId, matterId, roundNo, nextStatus, endedAt); }
    public boolean beginReceipt(WorkflowReceiptStart receipt) { return repository.beginReceipt(receipt); }
    public boolean completeReceipt(long tenantId, String eventId, String subscriberKey, WorkflowReceiptStatus status, String detail) { return repository.completeReceipt(tenantId, eventId, subscriberKey, status, detail); }
}
