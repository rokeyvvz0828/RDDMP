package com.ccb.attachment.service;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class AttachmentPersistenceRepository {
    private final AttachmentPersistenceMapper mapper;

    public AttachmentPersistenceRepository(AttachmentPersistenceMapper mapper) { this.mapper = mapper; }
    public List<Map<String, Object>> cleanupCandidates(int limit) { return mapper.cleanupCandidates(limit); }
    public int expireTemporaryFile(Map<String, Object> params) { return mapper.expireTemporaryFile(params); }
    public int markFileCleanupDone(Map<String, Object> params) { return mapper.markFileCleanupDone(params); }
    public int markFileCleanupRetry(Map<String, Object> params) { return mapper.markFileCleanupRetry(params); }
    public int insertTemporaryFile(Map<String, Object> params) { return mapper.insertTemporaryFile(params); }
    public Map<String, Object> attachmentFile(Map<String, Object> params) { return mapper.attachmentFile(params); }
    public int bindTemporaryFile(Map<String, Object> params) { return mapper.bindTemporaryFile(params); }
    public int markAttachmentFileDeleted(Map<String, Object> params) { return mapper.markAttachmentFileDeleted(params); }
    public int insertAttachmentOperation(Map<String, Object> params) { return mapper.insertAttachmentOperation(params); }
    public List<Map<String, Object>> projectCleanupCandidates(int limit) { return mapper.projectCleanupCandidates(limit); }
    public int claimProjectCleanup(long id) { return mapper.claimProjectCleanup(id); }
    public int markProjectCleanupDone(long id) { return mapper.markProjectCleanupDone(id); }
    public int markProjectCleanupRetry(Map<String, Object> params) { return mapper.markProjectCleanupRetry(params); }
    public int insertProjectAttachment(Map<String, Object> params) { return mapper.insertProjectAttachment(params); }
    public List<Map<String, Object>> attachmentCategories(Map<String, Object> params) { return mapper.attachmentCategories(params); }
    public Integer countAttachmentCategoryByName(Map<String, Object> params) { return mapper.countAttachmentCategoryByName(params); }
    public Integer maxAttachmentCategorySort(Map<String, Object> params) { return mapper.maxAttachmentCategorySort(params); }
    public int insertAttachmentCategory(Map<String, Object> params) { return mapper.insertAttachmentCategory(params); }
    public int updateProjectAttachmentCategory(Map<String, Object> params) { return mapper.updateProjectAttachmentCategory(params); }
    public Long countProjectAttachments(Map<String, Object> params) { return mapper.countProjectAttachments(params); }
    public List<Map<String, Object>> projectAttachments(Map<String, Object> params) { return mapper.projectAttachments(params); }
    public Map<String, Object> projectAttachment(Map<String, Object> params) { return mapper.projectAttachment(params); }
    public int countAttachmentCategory(Map<String, Object> params) { return mapper.countAttachmentCategory(params); }
    public int deleteProjectAttachment(Map<String, Object> params) { return mapper.deleteProjectAttachment(params); }
    public int enqueueProjectAttachmentDeletion(Map<String, Object> params) { return mapper.enqueueProjectAttachmentDeletion(params); }
    public int deleteProjectAttachments(Map<String, Object> params) { return mapper.deleteProjectAttachments(params); }
}
