package com.ccb.datamigration.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

@Repository
public class ProgramRepository {
    private final ProgramMapper mapper;
    public ProgramRepository(ProgramMapper mapper) { this.mapper = mapper; }
    public long count(long t,long p,String type,String system,String keyword){Long n=mapper.count(t,p,type,system,keyword);return n==null?0:n;}
    public List<Map<String,Object>> page(long t,long p,String type,String system,String keyword,int limit,long offset){return mapper.page(t,p,type,system,keyword,limit,offset);}
    public Map<String,Object> require(long t,long id){return required(mapper.find(t,id),"迁移程序不存在");}
    public void insert(Map<String,Object> p){mapper.insert(p);} public int update(Map<String,Object> p){return mapper.update(p);} public int softDelete(long t,long id,long by){return mapper.softDelete(t,id,by);}
    public List<Long> attachmentIds(long t,long id){return mapper.attachmentIds(t,id);} public long recycleCount(long t,long p,String keyword){Long n=mapper.recycleCount(t,p,keyword);return n==null?0:n;}
    public List<Map<String,Object>> recyclePage(long t,long p,String keyword,int limit){return mapper.recyclePage(t,p,keyword,limit);} public Map<String,Object> requireDeleted(long t,long id){return required(mapper.findDeleted(t,id),"迁移程序不存在于回收站");}
    public int restore(long t,long id){return mapper.restore(t,id);} public int purge(long t,long id){return mapper.purge(t,id);} public boolean enabledComponent(long t,long p,String system){Integer n=mapper.enabledComponentCount(t,p,system);return n!=null&&n>0;}
    public Map<String,Object> requireRaw(long t,long id){return required(mapper.findRaw(t,id),"迁移程序不存在");} public long requireDeletedProject(long t,long id){List<Long> ids=mapper.deletedProjectIds(t,id);if(ids.isEmpty())throw new BusinessException(ErrorCode.BAD_REQUEST,"迁移程序不存在于回收站");return ids.get(0);}
    public void audit(long t,long actor,long project,String operation,long id){mapper.insertAudit(t,actor,project,operation,id);} private Map<String,Object> required(List<Map<String,Object>> rows,String message){if(rows.isEmpty())throw new BusinessException(ErrorCode.BAD_REQUEST,message);return rows.get(0);}
}
