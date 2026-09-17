package com.ccb.datamigration.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Map;

@Repository
public class TargetTableRepository {
    private final TargetTableMapper mapper;
    public TargetTableRepository(TargetTableMapper mapper) { this.mapper = mapper; }
    public int countFields(long tenantId, String category, long projectId, Map<String, Object> p) { Integer n=mapper.countFields(tenantId,category,projectId,p); return n==null?0:n; }
    public List<Map<String,Object>> pageFields(long tenantId,String category,long projectId,Map<String,Object> p,long limit,long offset){return mapper.pageFields(tenantId,category,projectId,p,limit,offset);}
    public Map<String,Object> requireTable(long tenantId,long tableCode,String category){List<Map<String,Object>> rows=mapper.detail(tenantId,tableCode,category);if(rows.isEmpty())throw new BusinessException(ErrorCode.BAD_REQUEST,"目标表不存在");return rows.get(0);}
    public List<Map<String,Object>> fields(long tenantId,long tableCode){return mapper.fields(tenantId,tableCode);}
    public void insertTable(Map<String,Object> p){mapper.insertTable(p);} public void updateTable(Map<String,Object> p){mapper.updateTable(p);}
    public void deleteTable(long tenantId,long tableCode,long updatedBy){mapper.deleteTableFields(tenantId,tableCode);mapper.deleteTable(tenantId,tableCode,updatedBy);}
    public Map<String,Object> requireField(long tenantId,long fieldCode,String category){List<Map<String,Object>> rows=mapper.fieldContext(tenantId,fieldCode,category);if(rows.isEmpty())throw new BusinessException(ErrorCode.BAD_REQUEST,"字段不存在");return rows.get(0);}
    public void insertField(Map<String,Object> p){mapper.insertField(p);} public void updateField(Map<String,Object> p){mapper.updateField(p);}
    public Map<String,Object> field(long tenantId,long fieldCode){return mapper.field(tenantId,fieldCode);}
    public void deleteField(long tenantId,long fieldCode,long updatedBy){mapper.deleteField(tenantId,fieldCode,updatedBy);}
    public boolean hasActiveFields(long tenantId,long tableCode){Integer n=mapper.activeFieldCount(tenantId,tableCode);return n!=null&&n>0;}
    public void cascadeDeleteTable(long tenantId,long tableCode,String category,long updatedBy){mapper.cascadeDeleteTable(tenantId,tableCode,category,updatedBy);}
    public List<Map<String,Object>> projectByCode(long tenantId,String code){return mapper.projectByCode(tenantId,code);} public List<Map<String,Object>> projectById(long tenantId,long id){return mapper.projectById(tenantId,id);}
    public boolean enabledComponentExists(long tenantId,long projectId,String systemCode){Integer n=mapper.enabledComponentCount(tenantId,projectId,systemCode);return n!=null&&n>0;}
    public boolean tableNameEnExists(long tenantId,long projectId,String systemCode,String name,long exclude){Integer n=mapper.tableNameEnCount(tenantId,projectId,systemCode,name,exclude);return n!=null&&n>0;} public boolean tableNameCnExists(long tenantId,long projectId,String systemCode,String name,long exclude){Integer n=mapper.tableNameCnCount(tenantId,projectId,systemCode,name,exclude);return n!=null&&n>0;}
    public boolean fieldNameEnExists(long tenantId,long tableCode,String name,long exclude){Integer n=mapper.fieldNameEnCount(tenantId,tableCode,name,exclude);return n!=null&&n>0;} public boolean fieldNameCnExists(long tenantId,long tableCode,String name,long exclude){Integer n=mapper.fieldNameCnCount(tenantId,tableCode,name,exclude);return n!=null&&n>0;}
    public List<Map<String,Object>> exportAll(long tenantId,String category,long projectId,Map<String,Object> p){return mapper.exportAll(tenantId,category,projectId,p);} public List<Map<String,Object>> exportSelected(long tenantId,String category,long projectId,List<Long> ids){return mapper.exportSelected(tenantId,category,projectId,ids);}
    public void audit(long tenantId,long actorId,long projectId,String op,long entityId){mapper.insertAudit(tenantId,actorId,projectId,op,entityId);}
}
