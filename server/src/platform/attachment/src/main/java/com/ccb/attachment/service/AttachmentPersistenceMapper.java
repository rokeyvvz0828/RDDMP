package com.ccb.attachment.service;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface AttachmentPersistenceMapper {
    List<Map<String, Object>> cleanupCandidates(int limit);
    int expireTemporaryFile(Map<String, Object> params);
    int markFileCleanupDone(Map<String, Object> params);
    int markFileCleanupRetry(Map<String, Object> params);
    int insertTemporaryFile(Map<String, Object> params);
    Map<String, Object> attachmentFile(Map<String, Object> params);
    int bindTemporaryFile(Map<String, Object> params);
    int markAttachmentFileDeleted(Map<String, Object> params);
    int insertAttachmentOperation(Map<String, Object> params);
    List<Map<String, Object>> projectCleanupCandidates(int limit);
    int claimProjectCleanup(long id);
    int markProjectCleanupDone(long id);
    int markProjectCleanupRetry(Map<String, Object> params);
    int insertProjectAttachment(Map<String, Object> params);
    List<Map<String, Object>> attachmentCategories(Map<String, Object> params);
    Integer countAttachmentCategoryByName(Map<String, Object> params);
    Integer maxAttachmentCategorySort(Map<String, Object> params);
    int insertAttachmentCategory(Map<String, Object> params);
    int updateProjectAttachmentCategory(Map<String, Object> params);
    Long countProjectAttachments(Map<String, Object> params);
    List<Map<String, Object>> projectAttachments(Map<String, Object> params);
    Map<String, Object> projectAttachment(Map<String, Object> params);
    int countAttachmentCategory(Map<String, Object> params);
    int deleteProjectAttachment(Map<String, Object> params);
    int enqueueProjectAttachmentDeletion(Map<String, Object> params);
    int deleteProjectAttachments(Map<String, Object> params);
}
