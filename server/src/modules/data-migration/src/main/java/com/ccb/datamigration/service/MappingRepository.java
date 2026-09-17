package com.ccb.datamigration.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Repository;

@Repository
public class MappingRepository {
    private final MappingMapper mapper;
    public MappingRepository(MappingMapper mapper) { this.mapper = mapper; }
    public long count(long t, long p, String type, String system, String keyword) { Long n=mapper.count(t,p,type,system,keyword); return n==null?0:n; }
    public List<Map<String,Object>> page(long t,long p,String type,String system,String keyword,int limit,long offset){return mapper.page(t,p,type,system,keyword,limit,offset);}
    public Map<String,Object> require(long t,long id){return require(mapper.find(t,id),"迁移映射不存在");}
    public void insert(Map<String,Object> p){mapper.insert(p);} public int update(Map<String,Object> p){return mapper.update(p);} public int softDelete(long t,long id,long by){return mapper.softDelete(t,id,by);}
    public List<Long> attachmentIds(long t,long id){return mapper.attachmentIds(t,id);} public long recycleCount(long t,long p,String k){Long n=mapper.recycleCount(t,p,k);return n==null?0:n;}
    public List<Map<String,Object>> recyclePage(long t,long p,String k,int limit){return mapper.recyclePage(t,p,k,limit);} public Map<String,Object> requireDeleted(long t,long id){return require(mapper.findDeleted(t,id),"迁移映射不存在于回收站");}
    public int restore(long t,long id){return mapper.restore(t,id);} public int purge(long t,long id){return mapper.purge(t,id);} public boolean enabledComponent(long t,long p,String code){Integer n=mapper.enabledComponentCount(t,p,code);return n!=null&&n>0;}
    public Set<Long> boundAttachmentIds(long t,long id){return Set.copyOf(mapper.boundAttachmentIds(t,id));} public Map<String,Object> requireRaw(long t,long id){return require(mapper.findRaw(t,id),"迁移映射不存在");}
    public long requireDeletedProject(long t,long id){List<Long> rows=mapper.deletedProjectIds(t,id);if(rows.isEmpty())throw new BusinessException(ErrorCode.BAD_REQUEST,"迁移映射不存在于回收站");return rows.get(0);}
    public void audit(long t,long actor,long project,String op,long id){mapper.insertAudit(t,actor,project,op,id);} private Map<String,Object> require(List<Map<String,Object>> rows,String message){if(rows.isEmpty())throw new BusinessException(ErrorCode.BAD_REQUEST,message);return rows.get(0);}
}
