package com.ccb.architecture.change.persistence;

import com.ccb.architecture.change.model.SubsystemChangeModels.ChangeApplication;
import com.ccb.architecture.change.model.SubsystemChangeModels.ChangeHistoryEvent;
import com.ccb.architecture.change.model.SubsystemChangeModels.PhysicalDraft;
import com.ccb.architecture.change.model.SubsystemChangeModels.PhysicalPublishedState;
import com.ccb.architecture.change.model.SubsystemChangeModels.PhysicalReplacement;
import com.ccb.architecture.change.model.SubsystemChangeModels.TargetLock;
import com.ccb.architecture.change.model.SubsystemChangeModels.ValueReservation;
import com.ccb.architecture.change.model.SubsystemChangeModels.WorkflowReceipt;
import com.ccb.architecture.change.model.SubsystemChangeModels.WorkflowRound;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;

/** SQL ownership boundary for subsystem change persistence. */
@Mapper
interface SubsystemChangeMapper {
    int insertApplication(Map<String, Object> p); ChangeApplication findApplication(Map<String, Object> p);
    List<ChangeApplication> listApplications(Map<String, Object> p); ChangeApplication lockApplication(Map<String, Object> p);
    int compareAndSetApplicationStatus(Map<String, Object> p); int compareAndSetApplicationReason(Map<String, Object> p);
    int compareAndSetApplicationWorkflowContext(Map<String, Object> p); int compareAndSetCancellationRequested(Map<String, Object> p);
    int deletePhysicalDrafts(Map<String, Object> p); int insertPhysicalDraft(Map<String, Object> p);
    List<PhysicalDraft> findPhysicalDrafts(Map<String, Object> p); int insertHistory(Map<String, Object> p);
    List<ChangeHistoryEvent> listHistory(Map<String, Object> p); int insertPendingWorkflowRound(Map<String, Object> p);
    WorkflowRound findWorkflowRound(Map<String, Object> p); WorkflowRound lockWorkflowRound(Map<String, Object> p);
    WorkflowRound lockWorkflowRoundByInstance(Map<String, Object> p); Long countLatestWorkflowRound(Map<String, Object> p);
    int bindWorkflowRoundStarted(Map<String, Object> p); int completeStartedWorkflowRound(Map<String, Object> p);
    int beginReceipt(Map<String, Object> p); int completeReceipt(Map<String, Object> p); WorkflowReceipt findReceipt(Map<String, Object> p);
    int insertTargetLock(Map<String, Object> p); TargetLock findTargetLock(Map<String, Object> p); int deleteTargetLock(Map<String, Object> p);
    int insertValueReservation(Map<String, Object> p); ValueReservation findValueReservation(Map<String, Object> p);
    int deleteValueReservations(Map<String, Object> p); int insertPhysicalReplacement(Map<String, Object> p);
    PhysicalReplacement findPhysicalReplacementByApplication(Map<String, Object> p); Long countPhysicalByCode(Map<String, Object> p);
    Long countPhysicalByName(Map<String, Object> p); Long countPhysicalByEnglishName(Map<String, Object> p);
    PhysicalPublishedState findPhysical(Map<String, Object> p); PhysicalPublishedState lockPhysical(Map<String, Object> p);
    int insertPhysicalPublished(Map<String, Object> p); int updatePhysicalPublishedFields(Map<String, Object> p);
    int updatePhysicalPublishedStatus(Map<String, Object> p);
}
