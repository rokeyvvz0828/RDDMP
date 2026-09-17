package com.ccb.architecture.decision.persistence;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

/** MyBatis contract for architecture decision persistence. */
@Mapper
public interface DecisionMapper {
    Long countMatters(Map<String, Object> params);
    List<Map<String, Object>> pageMatters(Map<String, Object> params);
    Map<String, Object> findMatter(Map<String, Object> params);
    Map<String, Object> lockMatter(Map<String, Object> params);
    Map<String, Object> lockMatterVersion(Map<String, Object> params);
    int initializeMatterSequence(Map<String, Object> params);
    Integer lockMatterOrdinal(Map<String, Object> params);
    int advanceMatterOrdinal(Map<String, Object> params);
    int insertMatter(Map<String, Object> params);
    int updateMatter(Map<String, Object> params);
    int applyFirstHandling(Map<String, Object> params);
    int resubmit(Map<String, Object> params);
    int setMatterType(Map<String, Object> params);
    int touchPublicationPreparation(Map<String, Object> params);
    int insertMaterial(Map<String, Object> params);
    List<Map<String, Object>> listMaterials(Map<String, Object> params);
    int insertReview(Map<String, Object> params);
    Integer maxReviewNo(Map<String, Object> params);
    Map<String, Object> findReview(Map<String, Object> params);
    List<Map<String, Object>> listReviews(Map<String, Object> params);
    int updateReview(Map<String, Object> params);
    int deleteParticipants(Map<String, Object> params);
    int insertParticipant(Map<String, Object> params);
    List<Long> listParticipantIds(Map<String, Object> params);
    List<Map<String, Object>> listParticipants(Map<String, Object> params);
    List<Map<String, Object>> listActionItems(Map<String, Object> params);
    int updateActionItem(Map<String, Object> params);
    int insertActionItem(Map<String, Object> params);
    int deleteActionItem(Map<String, Object> params);
    Map<String, Object> findActionItem(Map<String, Object> params);
    int completeActionItem(Map<String, Object> params);
    int upsertPublicationIntent(Map<String, Object> params);
    Map<String, Object> findPublicationIntent(Map<String, Object> params);
    Map<String, Object> findConclusion(Map<String, Object> params);
    Map<String, Object> findConclusionById(Map<String, Object> params);
    Long countConclusions(Map<String, Object> params);
    List<Map<String, Object>> pageConclusions(Map<String, Object> params);
    List<String> supersessionKinds(Map<String, Object> params);
    List<Map<String, Object>> listSupersedes(Map<String, Object> params);
    List<Map<String, Object>> listSupersededBy(Map<String, Object> params);
    int insertConclusion(Map<String, Object> params);
    int insertSupersession(Map<String, Object> params);
    int markMatterPublished(Map<String, Object> params);
    int insertPendingWorkflowRound(Map<String, Object> params);
    int bindWorkflowRoundStarted(Map<String, Object> params);
    int compareAndSetMatterWorkflowContext(Map<String, Object> params);
    Map<String, Object> lockWorkflowRoundByInstance(Map<String, Object> params);
    Integer maxWorkflowRoundNo(Map<String, Object> params);
    int completeStartedWorkflowRound(Map<String, Object> params);
    int beginReceipt(Map<String, Object> params);
    int completeReceipt(Map<String, Object> params);
}
