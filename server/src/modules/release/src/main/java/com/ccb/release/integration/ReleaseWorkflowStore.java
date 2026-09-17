package com.ccb.release.integration;

import com.ccb.release.application.model.ReleaseApplicationModels.Application;
import com.ccb.release.application.model.ReleaseApplicationModels.Characteristic;
import com.ccb.release.application.model.ReleaseApplicationModels.Status;
import com.ccb.release.application.model.ReleaseApplicationModels.VersionType;
import com.ccb.workflow.integration.WorkflowLifecycleEvent;
import com.ccb.workflow.integration.WorkflowStartResult;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Repository
public class ReleaseWorkflowStore {
    private final ReleaseWorkflowMapper mapper;
    public ReleaseWorkflowStore(ReleaseWorkflowMapper mapper) { this.mapper = mapper; }
    public int nextRoundNo(long tenantId, long applicationId) { Integer value = mapper.nextRoundNo(p("tenantId", tenantId, "applicationId", applicationId)); return value == null ? 1 : value; }
    public long insertStartingRound(Application application, int roundNo, String workflowCode, String digest) { long id = nextId(); mapper.insertStartingRound(p("id", id, "tenantId", application.tenantId(), "applicationId", application.id(), "roundNo", roundNo, "workflowCode", workflowCode, "digest", digest)); return id; }
    public boolean completeWorkflowStart(long roundId, long tenantId, WorkflowStartResult result) { return mapper.completeWorkflowStart(p("roundId", roundId, "tenantId", tenantId, "definitionId", result.definitionId(), "definitionVersion", result.definitionVersion(), "instanceId", result.instanceId())) == 1; }
    public boolean transitionApplicationToReview(Application application, long expectedVersion, VersionType versionType, Characteristic characteristic, String workflowCode, long operatorId) { return mapper.transitionApplicationToReview(app(application, "expectedVersion", expectedVersion, "versionType", versionType.name(), "characteristic", characteristic.name(), "workflowCode", workflowCode, "operatorId", operatorId)) == 1; }
    public void insertAttachment(long tenantId, long applicationId, long attachmentId, String category, String fileName, long applicationRevision) { mapper.insertAttachment(p("id", nextId(), "tenantId", tenantId, "applicationId", applicationId, "attachmentId", attachmentId, "category", category, "fileName", fileName, "applicationRevision", applicationRevision)); }
    public List<AttachmentSnapshot> findActiveAttachments(long tenantId, long applicationId) { return mapper.activeAttachments(p("tenantId", tenantId, "applicationId", applicationId)); }
    public boolean retireAttachment(long tenantId, long applicationId, long attachmentId) { return mapper.retireAttachment(p("tenantId", tenantId, "applicationId", applicationId, "attachmentId", attachmentId)) == 1; }
    public boolean bumpEditableApplicationVersion(Application application, long expectedVersion, long operatorId) { return mapper.bumpEditableApplicationVersion(app(application, "expectedVersion", expectedVersion, "operatorId", operatorId)) == 1; }
    public Optional<RoundSnapshot> findLatestRound(long tenantId, long applicationId) { return Optional.ofNullable(mapper.latestRound(p("tenantId", tenantId, "applicationId", applicationId))); }
    public Optional<RoundSnapshot> findLatestRoundForUpdate(long tenantId, long applicationId) { return Optional.ofNullable(mapper.latestRoundForUpdate(p("tenantId", tenantId, "applicationId", applicationId))); }
    public Optional<RoundSnapshot> findRoundByInstanceForUpdate(long tenantId, long instanceId) { return Optional.ofNullable(mapper.roundByInstanceForUpdate(p("tenantId", tenantId, "instanceId", instanceId))); }
    public boolean isLatestRound(long tenantId, long applicationId, int roundNo) { Integer latest = mapper.latestRoundNo(p("tenantId", tenantId, "applicationId", applicationId)); return latest != null && latest == roundNo; }
    public boolean markWithdrawalRequested(long tenantId, long roundId) { return mapper.markWithdrawalRequested(p("tenantId", tenantId, "roundId", roundId)) == 1; }
    public boolean markCancelRequested(long tenantId, long roundId) { return mapper.markCancelRequested(p("tenantId", tenantId, "roundId", roundId)) == 1; }
    public boolean completeRound(long tenantId, long roundId, String expectedStatus, String targetStatus, LocalDateTime completedAt) { return mapper.completeRound(p("tenantId", tenantId, "roundId", roundId, "expectedStatus", expectedStatus, "targetStatus", targetStatus, "completedAt", completedAt)) == 1; }
    public boolean markApproved(Application application, long assignedWindowId, LocalDateTime approvedAt, long operatorId) { return mapper.markApproved(app(application, "windowId", assignedWindowId, "approvedAt", approvedAt, "operatorId", operatorId)) == 1; }
    public boolean markReturned(Application application, long operatorId) { return transitionApplication(application, Status.RETURNED, operatorId); }
    public boolean markWithdrawn(Application application, long operatorId) { return transitionApplication(application, Status.WITHDRAWN, operatorId); }
    public boolean markCancelled(Application application, long operatorId) { return transitionApplication(application, Status.CANCELLED, operatorId); }
    private boolean transitionApplication(Application application, Status target, long operatorId) { return mapper.transitionApplication(app(application, "targetStatus", target.name(), "operatorId", operatorId)) == 1; }
    public Optional<Long> findReceivingWindow(long tenantId, String projectId, LocalDateTime at) { Map<String,Object> params = p("tenantId", tenantId, "projectId", projectId, "at", at); Long started = mapper.startedWindowCount(params); if (started == null || started == 0) return Optional.empty(); Long current = mapper.currentWindow(params); return current != null ? Optional.of(current) : Optional.ofNullable(mapper.futureWindow(params)); }
    public boolean beginReceipt(WorkflowLifecycleEvent event, long applicationId, String consumerKey) { return mapper.beginReceipt(p("id", nextId(), "tenantId", event.tenantId(), "eventId", event.eventId(), "instanceId", event.instanceId(), "applicationId", applicationId, "roundNo", event.context().businessRound(), "eventType", event.eventType().name(), "consumerKey", consumerKey, "occurredAt", event.occurredAt())) == 1; }
    public void completeReceipt(long tenantId, String eventId, String consumerKey, String status) { mapper.completeReceipt(p("tenantId", tenantId, "eventId", eventId, "consumerKey", consumerKey, "status", status)); }
    private static Map<String,Object> app(Application a, Object... extra) { Map<String,Object> values = p("id", a.id(), "tenantId", a.tenantId(), "status", a.status().name(), "rowVersion", a.rowVersion()); for (int i=0;i<extra.length;i+=2) values.put((String)extra[i], extra[i+1]); return values; }
    private static Map<String,Object> p(Object... values) { Map<String,Object> result = new HashMap<>(); for (int i=0;i<values.length;i+=2) result.put((String)values[i], values[i+1]); return result; }
    private long nextId() { return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000); }
    public record RoundSnapshot(long id,long tenantId,long applicationId,int roundNo,String workflowCode,Long workflowDefinitionId,Integer workflowDefinitionVersion,Long workflowInstanceId,String roundStatus,String dataDigest,LocalDateTime submittedAt,LocalDateTime completedAt) {}
    public record AttachmentSnapshot(long attachmentId,String category,String fileName,long applicationRevision) {}
}
