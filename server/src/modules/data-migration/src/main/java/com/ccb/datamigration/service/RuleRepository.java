package com.ccb.datamigration.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

@Repository
public class RuleRepository {
    private final RuleMapper mapper;
    public RuleRepository(RuleMapper mapper) { this.mapper = mapper; }
    public long count(Map<String, Object> p) { Long value = mapper.count(p); return value == null ? 0L : value; }
    public List<Map<String, Object>> list(Map<String, Object> p) { return mapper.list(p); }
    public Map<String, Object> require(long tenantId, long id) { return required(mapper.find(tenantId, id), "迁移检核规则不存在"); }
    public Map<String, Object> requireDeleted(long tenantId, long id) { return required(mapper.findDeleted(tenantId, id), "迁移检核规则不存在于回收站"); }
    public Map<String, Object> requireRaw(long tenantId, long id) { return required(mapper.findRaw(tenantId, id), "迁移检核规则不存在"); }
    public void insert(Map<String, Object> p) { mapper.insert(p); }
    public int update(Map<String, Object> p) { return mapper.update(p); }
    public int softDelete(long tenantId, long id, long actorId) { return mapper.softDelete(tenantId, id, actorId); }
    public int restore(long tenantId, long id) { return mapper.restore(tenantId, id); }
    public int purge(long tenantId, long id) { return mapper.purge(tenantId, id); }
    public boolean enabledComponent(long tenantId, long projectId, String systemCode) { Integer value = mapper.enabledComponentCount(tenantId, projectId, systemCode); return value != null && value > 0; }
    public boolean ruleCodeExists(long tenantId, String ruleCode, Long excludeId) { Integer value = mapper.ruleCodeCount(tenantId, ruleCode, excludeId); return value != null && value > 0; }
    public long requireDeletedProject(long tenantId, long id) { List<Long> ids = mapper.deletedProjectIds(tenantId, id); if (ids.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "迁移检核规则不存在于回收站"); return ids.get(0); }
    public void audit(long tenantId, long actorId, long projectId, String operation, long entityId) { mapper.insertAudit(tenantId, actorId, projectId, operation, entityId); }
    private Map<String, Object> required(List<Map<String, Object>> rows, String message) { if (rows.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, message); return rows.get(0); }
}
