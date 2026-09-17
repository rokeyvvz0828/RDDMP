package com.ccb.datamigration.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

@Repository
public class ReleaseDrillRepository {
    private final ReleaseDrillMapper mapper;

    public ReleaseDrillRepository(ReleaseDrillMapper mapper) {
        this.mapper = mapper;
    }

    public long count(long tenantId, long projectId, String granularity, String materialTypeCode, String systemCode, String keyword) {
        Long count = mapper.count(tenantId, projectId, granularity, materialTypeCode, systemCode, keyword);
        return count == null ? 0L : count;
    }

    public List<Map<String, Object>> page(long tenantId, long projectId, String granularity, String materialTypeCode,
                                           String systemCode, String keyword, int limit, long offset) {
        return mapper.page(tenantId, projectId, granularity, materialTypeCode, systemCode, keyword, limit, offset);
    }

    public Map<String, Object> require(long tenantId, long id) {
        return requireOne(mapper.find(tenantId, id), "投产及演练资料不存在");
    }

    public void insert(Map<String, Object> values) { mapper.insert(values); }
    public int update(Map<String, Object> values) { return mapper.update(values); }
    public int softDelete(long tenantId, long id, long deletedBy) { return mapper.softDelete(tenantId, id, deletedBy); }
    public List<Long> mainAttachmentIds(long tenantId, long id) { return mapper.mainAttachmentIds(tenantId, id); }

    public long recycleCount(long tenantId, long projectId, String keyword) {
        Long count = mapper.recycleCount(tenantId, projectId, keyword);
        return count == null ? 0L : count;
    }

    public List<Map<String, Object>> recyclePage(long tenantId, long projectId, String keyword, int limit) {
        return mapper.recyclePage(tenantId, projectId, keyword, limit);
    }

    public Map<String, Object> requireDeleted(long tenantId, long id) {
        return requireOne(mapper.findDeleted(tenantId, id), "投产及演练资料不存在于回收站");
    }

    public int restore(long tenantId, long id) { return mapper.restore(tenantId, id); }
    public int purge(long tenantId, long id) { return mapper.purge(tenantId, id); }
    public boolean enabledComponent(long tenantId, long projectId, String systemCode) {
        Integer count = mapper.enabledComponentCount(tenantId, projectId, systemCode);
        return count != null && count > 0;
    }

    public Map<String, Object> requireRaw(long tenantId, long id) {
        return requireOne(mapper.findRaw(tenantId, id), "投产及演练资料不存在");
    }

    public long requireDeletedProject(long tenantId, long id) {
        List<Long> projectIds = mapper.deletedProjectIds(tenantId, id);
        if (projectIds.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "投产及演练资料不存在于回收站");
        return projectIds.get(0);
    }

    public void audit(long tenantId, long actorId, long projectId, String operation, long id) {
        mapper.insertAudit(tenantId, actorId, projectId, operation, id);
    }

    private Map<String, Object> requireOne(List<Map<String, Object>> rows, String message) {
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, message);
        return rows.get(0);
    }
}
