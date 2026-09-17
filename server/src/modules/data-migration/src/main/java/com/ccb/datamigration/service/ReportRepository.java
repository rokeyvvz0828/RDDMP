package com.ccb.datamigration.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Map;

@Repository
public class ReportRepository {
    private final ReportMapper mapper;
    public ReportRepository(ReportMapper mapper) { this.mapper = mapper; }
    public long count(long tenantId,long projectId,boolean deleted,String period,String keyword){Long n=mapper.count(tenantId,projectId,deleted,period,keyword);return n==null?0:n;}
    public List<Map<String,Object>> page(long tenantId,long projectId,boolean deleted,String period,String keyword,int limit,long offset){return mapper.page(tenantId,projectId,deleted,period,keyword,limit,offset);}
    public Map<String,Object> require(long tenantId,long id,boolean deleted){List<Map<String,Object>> rows=mapper.find(tenantId,id,deleted);if(rows.isEmpty())throw new BusinessException(ErrorCode.BAD_REQUEST,"汇报材料不存在");return rows.get(0);}
    public void insert(Map<String,Object> p){mapper.insert(p);} public void update(Map<String,Object> p){mapper.update(p);}
    public int softDelete(long tenantId,long id,long deletedBy){return mapper.softDelete(tenantId,id,deletedBy);} public int restore(long tenantId,long id){return mapper.restore(tenantId,id);} public int purge(long tenantId,long id){return mapper.purge(tenantId,id);}
    public List<Map<String,Object>> projectOptions(long tenantId){return mapper.projectOptions(tenantId);} public void audit(long tenantId,long actorId,long projectId,String operation,long id){mapper.insertAudit(tenantId,actorId,projectId,operation,id);}
}
