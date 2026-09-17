package com.ccb.datamigration.service;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface ContentAttachmentMapper {
    List<Map<String, Object>> listActive(@Param("tenantId") long tenantId, @Param("businessType") String businessType, @Param("businessId") long businessId);
    List<Map<String, Object>> listDeleted(@Param("tenantId") long tenantId, @Param("businessType") String businessType, @Param("businessId") long businessId);
    List<Long> activeAttachmentIds(@Param("tenantId") long tenantId, @Param("businessType") String businessType, @Param("businessId") long businessId);
    List<Long> deletedAttachmentIds(@Param("tenantId") long tenantId, @Param("businessType") String businessType, @Param("businessId") long businessId);
    int updateSort(@Param("sortOrder") int sortOrder, @Param("businessId") long businessId, @Param("attachmentId") long attachmentId, @Param("tenantId") long tenantId, @Param("businessType") String businessType);
    int restoreAttachment(@Param("fileName") String fileName, @Param("sortOrder") int sortOrder, @Param("businessId") long businessId, @Param("attachmentId") long attachmentId, @Param("tenantId") long tenantId, @Param("businessType") String businessType);
    int insert(@Param("id") long id, @Param("tenantId") long tenantId, @Param("businessType") String businessType, @Param("businessId") long businessId, @Param("attachmentId") long attachmentId, @Param("fileName") String fileName, @Param("sortOrder") int sortOrder, @Param("createdBy") long createdBy);
    int softDeleteAttachment(@Param("deletedBy") long deletedBy, @Param("tenantId") long tenantId, @Param("businessType") String businessType, @Param("businessId") long businessId, @Param("attachmentId") long attachmentId);
    int restoreById(@Param("id") long id, @Param("tenantId") long tenantId);
    int purgeById(@Param("id") long id, @Param("tenantId") long tenantId);
    List<Long> attachmentIds(@Param("tenantId") long tenantId, @Param("businessType") String businessType, @Param("businessId") long businessId);
    int deleteAll(@Param("tenantId") long tenantId, @Param("businessType") String businessType, @Param("businessId") long businessId);
    List<Map<String, Object>> softDeletedRows(@Param("tenantId") long tenantId, @Param("ids") List<Long> ids);
    List<Long> softDeletedBusinessIds(@Param("id") long id, @Param("tenantId") long tenantId);
}
