package com.ccb.testmanagement.announcement;

import org.apache.ibatis.annotations.Mapper;
import java.util.List;
import java.util.Map;

@Mapper
interface TestAnnouncementMapper {
    List<Map<String,Object>> projects(Map<String,Object> p); List<Map<String,Object>> pinned(Map<String,Object> p); List<Map<String,Object>> newest(Map<String,Object> p);
    Long count(Map<String,Object> p); List<Map<String,Object>> page(Map<String,Object> p); Map<String,Object> find(Map<String,Object> p);
    int insert(Map<String,Object> p); int update(Map<String,Object> p); int pin(Map<String,Object> p); List<Long> attachmentIds(Map<String,Object> p);
    int deleteAttachment(Map<String,Object> p); int deleteAttachments(Map<String,Object> p); int deleteAnnouncement(Map<String,Object> p); Long attachmentCount(Map<String,Object> p);
    int insertAttachment(Map<String,Object> p); List<Map<String,Object>> attachments(Map<String,Object> p); Long projectCount(Map<String,Object> p); int insertAudit(Map<String,Object> p);
}
