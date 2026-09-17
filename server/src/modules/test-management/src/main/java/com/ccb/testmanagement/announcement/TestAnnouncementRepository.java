package com.ccb.testmanagement.announcement;

import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Map;

@Repository
class TestAnnouncementRepository {
    private final TestAnnouncementMapper mapper;
    TestAnnouncementRepository(TestAnnouncementMapper mapper) { this.mapper = mapper; }
    List<Map<String,Object>> projects(Map<String,Object> p){return mapper.projects(p);} List<Map<String,Object>> pinned(Map<String,Object> p){return mapper.pinned(p);} List<Map<String,Object>> newest(Map<String,Object> p){return mapper.newest(p);}
    long count(Map<String,Object> p){Long value=mapper.count(p);return value==null?0:value;} List<Map<String,Object>> page(Map<String,Object> p){return mapper.page(p);} Map<String,Object> find(Map<String,Object> p){return mapper.find(p);}
    void insert(Map<String,Object> p){mapper.insert(p);} void update(Map<String,Object> p){mapper.update(p);} void pin(Map<String,Object> p){mapper.pin(p);} List<Long> attachmentIds(Map<String,Object> p){return mapper.attachmentIds(p);}
    void deleteAttachment(Map<String,Object> p){mapper.deleteAttachment(p);} void deleteAttachments(Map<String,Object> p){mapper.deleteAttachments(p);} void deleteAnnouncement(Map<String,Object> p){mapper.deleteAnnouncement(p);}
    long attachmentCount(Map<String,Object> p){Long value=mapper.attachmentCount(p);return value==null?0:value;} void insertAttachment(Map<String,Object> p){mapper.insertAttachment(p);} List<Map<String,Object>> attachments(Map<String,Object> p){return mapper.attachments(p);}
    boolean projectExists(Map<String,Object> p){Long value=mapper.projectCount(p);return value!=null&&value>0;} void insertAudit(Map<String,Object> p){mapper.insertAudit(p);}
}
