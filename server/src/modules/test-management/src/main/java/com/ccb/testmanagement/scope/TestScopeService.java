/*
 * 文件：server/src/modules/test-management/src/main/java/com/ccb/testmanagement/scope/TestScopeService.java
 * 说明：测试范围的服务、策略或接口实现。
 * 用途：承载模块边界内的查询、校验、事务、权限或文件处理职责。
 * 作者：hengguan
 */
package com.ccb.testmanagement.scope;

// 关键逻辑：所有读写以认证用户的租户、测试大类和项目为共同边界；写入由事务与审计保持一致性。

import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/** 测试范围领域：目录、范围、编号、回收与工作簿导入均受租户/大类/项目边界保护。 */
@Service
public class TestScopeService {
    private static final Set<String> DOMAINS = Set.of("application-assembly", "user-testing", "non-functional", "security");
    private static final Set<String> STATUS = Set.of("SUCCESS", "FAILED", "RUNNING", "INVALID", "UNEXECUTED");
    private static final AtomicLong IDS = new AtomicLong(System.currentTimeMillis() * 1000);
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public TestScopeService(JdbcTemplate jdbc, ObjectMapper objectMapper) { this.jdbc = jdbc; this.objectMapper = objectMapper; }

    public Map<String, Object> tree(String domain, long projectId, AuthUser user) {
        scope(domain, projectId, user);
        List<Map<String, Object>> systems = jdbc.queryForList("SELECT s.physical_subsystem_id AS id,p.code,p.short_name,COALESCE(NULLIF(p.short_name,''),REPLACE(p.name,'物理子系统','')) AS name FROM tm_test_participating_system s JOIN arch_physical_subsystem p ON p.id=s.physical_subsystem_id AND p.tenant_id=s.tenant_id AND p.deleted=0 WHERE s.tenant_id=? AND s.test_domain=? AND s.project_id=? AND s.enabled=1 AND s.deleted=0 ORDER BY p.code,p.id", user.tenantId(), domain, projectId);
        List<Map<String, Object>> directories = jdbc.queryForList("SELECT d.id,d.physical_subsystem_id,d.parent_id,d.directory_name,d.sort_no,(SELECT COUNT(*) FROM tm_test_scope x WHERE x.tenant_id=d.tenant_id AND x.directory_id=d.id AND x.deleted=0) AS scope_count FROM tm_test_scope_directory d WHERE d.tenant_id=? AND d.test_domain=? AND d.project_id=? AND d.deleted=0 ORDER BY d.physical_subsystem_id,d.sort_no,d.id", user.tenantId(), domain, projectId);
        return Map.of("systems", systems, "directories", directories);
    }

    public PageResult<Map<String, Object>> list(String domain, long projectId, Long systemId, Long directoryId, String keyword, Collection<String> statuses, Collection<String> functionTypes, Collection<String> changeStatuses, Collection<String> importances, Collection<String> accountingFlags, String coverage, boolean recycled, String sortBy, String sortOrder, PageQuery page, AuthUser user) {
        scope(domain, projectId, user);
        StringBuilder where = new StringBuilder(" WHERE s.tenant_id=? AND s.test_domain=? AND s.project_id=? AND s.deleted=?");
        List<Object> args = new ArrayList<>(List.of(user.tenantId(), domain, projectId, recycled ? 1 : 0));
        if (systemId != null) { requireSystem(domain, projectId, systemId, user.tenantId()); where.append(" AND s.physical_subsystem_id=?"); args.add(systemId); }
        if (directoryId != null) { directory(directoryId, domain, projectId, user.tenantId()); List<Long> ids = descendantDirectoryIds(directoryId, user.tenantId()); where.append(" AND s.directory_id IN (").append(placeholders(ids.size())).append(")"); args.addAll(ids); }
        String cleanKeyword = text(keyword, 100, "关键词");
        if (cleanKeyword != null) { String like = "%" + cleanKeyword + "%"; where.append(" AND (s.scope_code LIKE ? OR s.scope_name LIKE ? OR s.leaf_menu LIKE ?)"); args.add(like); args.add(like); args.add(like); }
        in(where, args, "s.function_type", functionTypes);
        in(where, args, "s.change_status", changeStatuses);
        in(where, args, "s.importance", importances);
        in(where, args, "s.accounting_flag", accountingFlags);
        String state = statusExpression();
        List<String> statusValues = validValues(statuses, STATUS, "状态");
        if (!statusValues.isEmpty()) { where.append(" AND ").append(state).append(" IN (").append(placeholders(statusValues.size())).append(")"); args.addAll(statusValues); }
        if ("COVERED".equalsIgnoreCase(coverage)) where.append(" AND ").append(caseCountExpression()).append(" > 0");
        if ("UNCOVERED".equalsIgnoreCase(coverage)) where.append(" AND ").append(caseCountExpression()).append(" = 0");
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM tm_test_scope s" + where, Long.class, args.toArray());
        String order = order(sortBy, sortOrder);
        List<Object> pageArgs = new ArrayList<>(args); pageArgs.add((page.page() - 1) * page.size()); pageArgs.add(page.size());
        String sql = "SELECT s.id,s.scope_code,s.scope_name,s.leaf_menu,s.physical_subsystem_id,p.code AS physical_system_code,COALESCE(NULLIF(p.short_name,''),REPLACE(p.name,'物理子系统','')) AS physical_system_name,s.directory_id,d.directory_name,s.function_type,s.change_status,s.importance,s.accounting_flag,s.invalidated,s.invalid_reason,s.deleted,s.deleted_at,s.created_by,creator.display_name AS created_by_name,s.created_at,s.updated_by,updater.display_name AS updated_by_name,s.updated_at," + caseCountExpression() + " AS case_count," + state + " AS status FROM tm_test_scope s JOIN arch_physical_subsystem p ON p.id=s.physical_subsystem_id AND p.tenant_id=s.tenant_id LEFT JOIN tm_test_scope_directory d ON d.id=s.directory_id AND d.tenant_id=s.tenant_id AND d.deleted=0 LEFT JOIN sys_user creator ON creator.id=s.created_by AND creator.tenant_id=s.tenant_id AND creator.deleted=0 LEFT JOIN sys_user updater ON updater.id=s.updated_by AND updater.tenant_id=s.tenant_id AND updater.deleted=0" + where + " ORDER BY " + order + " LIMIT ?,?";
        return new PageResult<>(jdbc.queryForList(sql, pageArgs.toArray()), total == null ? 0 : total, page.page(), page.size());
    }

    @Transactional
    public Map<String, Object> saveDirectory(String domain, long projectId, Long id, Map<String, Object> body, AuthUser user) {
        scope(domain, projectId, user);
        long systemId = positive(body.get("physical_subsystem_id"), "参测系统"); requireSystem(domain, projectId, systemId, user.tenantId());
        Long parentId = positiveOrNull(body.get("parent_id"));
        if (parentId != null) {
            Map<String, Object> parent = directory(parentId, domain, projectId, user.tenantId());
            if (number(parent.get("physical_subsystem_id")) != systemId) throw bad("父目录必须属于同一参测系统");
            if (id != null && (parentId.equals(id) || descendantDirectoryIds(id, user.tenantId()).contains(parentId))) throw bad("目录不能移动到自身或子目录下");
        }
        if (id == null && depth(parentId, user.tenantId()) >= 5) throw bad("目录最多五层");
        String name = required(body.get("directory_name"), "目录名称", 100); int sort = integer(body.get("sort_no"));
        Long exists = jdbc.queryForObject("SELECT COUNT(*) FROM tm_test_scope_directory WHERE tenant_id=? AND test_domain=? AND project_id=? AND physical_subsystem_id=? AND parent_id " + (parentId == null ? "IS NULL" : "=?") + " AND directory_name=? AND deleted=0" + (id == null ? "" : " AND id<>?"), Long.class, directoryArgs(user.tenantId(), domain, projectId, systemId, parentId, name, id));
        if (exists != null && exists > 0) throw conflict("同级目录名称已存在");
        long saved = id == null ? next() : id;
        if (id == null) jdbc.update("INSERT INTO tm_test_scope_directory(id,tenant_id,test_domain,project_id,physical_subsystem_id,parent_id,directory_name,sort_no,created_by,updated_by) VALUES(?,?,?,?,?,?,?,?,?,?)", saved,user.tenantId(),domain,projectId,systemId,parentId,name,sort,user.id(),user.id());
        else { directory(id, domain, projectId, user.tenantId()); jdbc.update("UPDATE tm_test_scope_directory SET physical_subsystem_id=?,parent_id=?,directory_name=?,sort_no=?,updated_by=? WHERE id=? AND tenant_id=? AND deleted=0", systemId,parentId,name,sort,user.id(),id,user.tenantId()); }
        audit(domain, projectId, "SCOPE_DIRECTORY", saved, id == null ? "CREATE" : "UPDATE", Map.of("directory_name", name, "parent_id", parentId == null ? 0 : parentId), user);
        return jdbc.queryForMap("SELECT id,physical_subsystem_id,parent_id,directory_name,sort_no,updated_at FROM tm_test_scope_directory WHERE id=? AND tenant_id=?", saved,user.tenantId());
    }

    @Transactional
    public void deleteDirectory(String domain, long projectId, long id, Long targetDirectoryId, AuthUser user) {
        Map<String,Object> row = directory(id, domain, projectId, user.tenantId());
        Long target = targetDirectoryId == null ? positiveOrNull(row.get("parent_id")) : targetDirectoryId;
        if (target != null) { Map<String,Object> targetRow = directory(target,domain,projectId,user.tenantId()); if (number(targetRow.get("physical_subsystem_id")) != number(row.get("physical_subsystem_id"))) throw bad("移交目录必须属于同一参测系统"); if (target == id) throw bad("移交目录不能是当前目录"); }
        Long children = jdbc.queryForObject("SELECT COUNT(*) FROM tm_test_scope_directory WHERE tenant_id=? AND parent_id=? AND deleted=0",Long.class,user.tenantId(),id);
        if (children != null && children > 0) throw conflict("目录仍有子目录，请先调整子目录");
        jdbc.update("UPDATE tm_test_scope SET directory_id=?,updated_by=? WHERE tenant_id=? AND directory_id=? AND deleted=0",target,user.id(),user.tenantId(),id);
        jdbc.update("UPDATE tm_test_scope_directory SET deleted=1,deleted_at=CURRENT_TIMESTAMP,updated_by=? WHERE id=? AND tenant_id=? AND deleted=0",user.id(),id,user.tenantId());
        audit(domain,projectId,"SCOPE_DIRECTORY",id,"DELETE",Map.of("target_directory_id",target == null ? 0 : target),user);
    }

    public Map<String,Object> previewCodeChange(String domain,long projectId,long id,String scopeCode,AuthUser user) {
        Map<String,Object> old = scopeRow(id,domain,projectId,user.tenantId()); String code = scopeCode(required(scopeCode,"序号",128), systemCode(number(old.get("physical_subsystem_id")), user.tenantId())); uniqueScopeCode(domain,projectId,code,id,user.tenantId());
        Long cases = jdbc.queryForObject("SELECT COUNT(*) FROM tm_test_case WHERE tenant_id=? AND scope_id=? AND deleted=0",Long.class,user.tenantId(),id);
        return Map.of("scope_id",id,"old_scope_code",old.get("scope_code"),"scope_code",code,"affected_case_count",cases == null ? 0 : cases,"confirmation_required",!code.equals(String.valueOf(old.get("scope_code"))));
    }

    @Transactional
    public Map<String, Object> saveScope(String domain, long projectId, Long id, Map<String, Object> body, AuthUser user) {
        scope(domain, projectId, user);
        long systemId = positive(body.get("physical_subsystem_id"), "参测系统"); requireSystem(domain,projectId,systemId,user.tenantId());
        Long directoryId = positive(body.get("directory_id"), "所属目录"); { Map<String,Object> d=directory(directoryId,domain,projectId,user.tenantId()); if(number(d.get("physical_subsystem_id"))!=systemId)throw bad("所属目录必须属于同一所属系统"); }
        String scopeName=required(body.get("scope_name"),"功能/用例/批处理名称",100); String requested=text(body.get("scope_code"),128,"序号"); String scopeCode = requested == null ? nextScopeCode(domain,projectId,systemId,user.tenantId()) : scopeCode(requested, systemCode(systemId, user.tenantId()));
        uniqueScopeCode(domain,projectId,scopeCode,id,user.tenantId());
        Map<String,Object> values=scopeValues(domain,projectId,body,user); long saved=id==null?next():id; int affected=0;
        if(id==null) jdbc.update("INSERT INTO tm_test_scope(id,tenant_id,test_domain,project_id,physical_subsystem_id,directory_id,scope_code,scope_name,leaf_menu,function_type,change_status,importance,accounting_flag,created_by,updated_by) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",saved,user.tenantId(),domain,projectId,systemId,directoryId,scopeCode,scopeName,values.get("leaf_menu"),values.get("function_type"),values.get("change_status"),values.get("importance"),values.get("accounting_flag"),user.id(),user.id());
        else {
            Map<String,Object> old=scopeRow(id,domain,projectId,user.tenantId());
            if(!scopeCode.equals(String.valueOf(old.get("scope_code"))) && !Boolean.TRUE.equals(body.get("confirm_code_sync"))) return previewCodeChange(domain,projectId,id,scopeCode,user);
            jdbc.update("UPDATE tm_test_scope SET physical_subsystem_id=?,directory_id=?,scope_code=?,scope_name=?,leaf_menu=?,function_type=?,change_status=?,importance=?,accounting_flag=?,updated_by=? WHERE id=? AND tenant_id=? AND deleted=0",systemId,directoryId,scopeCode,scopeName,values.get("leaf_menu"),values.get("function_type"),values.get("change_status"),values.get("importance"),values.get("accounting_flag"),user.id(),id,user.tenantId());
            if(!scopeCode.equals(String.valueOf(old.get("scope_code")))) affected=jdbc.update("UPDATE tm_test_case SET case_code=CONCAT(?, '-', LPAD(case_serial_no,4,'0')),updated_by=? WHERE tenant_id=? AND scope_id=? AND deleted=0",scopeCode,user.id(),user.tenantId(),id);
        }
        Map<String,Object> detail=new LinkedHashMap<>();detail.put("scope_code",scopeCode);detail.put("affected_case_count",affected);audit(domain,projectId,"SCOPE",saved,id==null?"CREATE":"UPDATE",detail,user);
        Map<String,Object> result=jdbc.queryForMap("SELECT id,physical_subsystem_id,directory_id,scope_code,scope_name,leaf_menu,function_type,change_status,importance,accounting_flag,invalidated,updated_at FROM tm_test_scope WHERE id=? AND tenant_id=?",saved,user.tenantId()); if(affected>0){result=new LinkedHashMap<>(result);result.put("affected_case_count",affected);}return result;
    }

    @Transactional public Map<String,Object> invalidate(String domain,long projectId,long id,boolean invalidated,String reason,AuthUser user){Map<String,Object> row=scopeRow(id,domain,projectId,user.tenantId());String clean=text(reason,500,"无效原因");if(invalidated&&clean==null)throw bad("请填写无效原因");jdbc.update("UPDATE tm_test_scope SET invalidated=?,invalidated_by=?,invalidated_at=?,invalid_reason=?,updated_by=? WHERE id=? AND tenant_id=?",invalidated?1:0,invalidated?user.id():null,invalidated?java.sql.Timestamp.valueOf(java.time.LocalDateTime.now()):null,invalidated?clean:null,user.id(),id,user.tenantId());audit(domain,projectId,"SCOPE",id,invalidated?"INVALIDATE":"REVOKE_INVALID",Map.of("scope_code",String.valueOf(row.get("scope_code")),"reason",clean == null ? "" : clean),user);return Map.of("id",id,"invalidated",invalidated);}

    @Transactional public Map<String,Object> deleteScope(String domain,long projectId,long id,AuthUser user){Map<String,Object> row=scopeRow(id,domain,projectId,user.tenantId());Long cases=jdbc.queryForObject("SELECT COUNT(*) FROM tm_test_case WHERE tenant_id=? AND scope_id=? AND deleted=0",Long.class,user.tenantId(),id);jdbc.update("UPDATE tm_test_scope SET deleted=1,deleted_at=CURRENT_TIMESTAMP,updated_by=? WHERE id=? AND tenant_id=? AND deleted=0",user.id(),id,user.tenantId());audit(domain,projectId,"SCOPE",id,"DELETE",Map.of("scope_code",row.get("scope_code"),"associated_case_count",cases == null ? 0 : cases),user);return Map.of("id",id,"associated_case_count",cases == null ? 0 : cases);}

    @Transactional public Map<String,Object> restoreScope(String domain,long projectId,long id,Map<String,Object> body,AuthUser user){scope(domain,projectId,user);List<Map<String,Object>> rows=jdbc.queryForList("SELECT id,scope_code,physical_subsystem_id FROM tm_test_scope WHERE id=? AND tenant_id=? AND test_domain=? AND project_id=? AND deleted=1",id,user.tenantId(),domain,projectId);if(rows.isEmpty())throw bad("回收站中不存在该测试范围");String old=String.valueOf(rows.get(0).get("scope_code"));String restoreCode=text(body.get("scope_code"),128,"序号");String target=restoreCode==null?old:scopeCode(restoreCode,systemCode(number(rows.get(0).get("physical_subsystem_id")),user.tenantId()));uniqueScopeCode(domain,projectId,target,id,user.tenantId());jdbc.update("UPDATE tm_test_scope SET deleted=0,deleted_at=NULL,scope_code=?,updated_by=? WHERE id=? AND tenant_id=?",target,user.id(),id,user.tenantId());if(!target.equals(old))jdbc.update("UPDATE tm_test_case SET case_code=CONCAT(?, '-', LPAD(case_serial_no,4,'0')),updated_by=? WHERE tenant_id=? AND scope_id=? AND deleted=0",target,user.id(),user.tenantId(),id);audit(domain,projectId,"SCOPE",id,"RESTORE",Map.of("old_scope_code",old,"scope_code",target),user);return Map.of("id",id,"scope_code",target);}

    public Map<String,Object> previewImport(String domain,long projectId,List<Map<String,Object>> rows,AuthUser user){return validateImport(domain,projectId,rows,user);}

    @Transactional public Map<String,Object> importScopes(String domain,long projectId,List<Map<String,Object>> rows,String duplicateAction,AuthUser user){Map<String,Object> validation=validateImport(domain,projectId,rows,user);if(!Boolean.TRUE.equals(validation.get("success")))return validation;@SuppressWarnings("unchecked") List<Map<String,Object>> prepared=(List<Map<String,Object>>)validation.get("rows");int created=0,updated=0,skipped=0;for(Map<String,Object> row:prepared){String existing=String.valueOf(row.getOrDefault("existing_id", ""));if(!existing.isBlank()&&!"null".equals(existing)){if("SKIP".equalsIgnoreCase(duplicateAction)){skipped++;continue;}Map<String,Object> copy=new LinkedHashMap<>(row);copy.put("directory_id",ensureDirectory(domain,projectId,number(copy.get("physical_subsystem_id")),String.valueOf(copy.get("directory_path")),user));copy.put("confirm_code_sync",true);saveScope(domain,projectId,Long.parseLong(existing),copy,user);updated++;}else{Map<String,Object> copy=new LinkedHashMap<>(row);copy.put("directory_id",ensureDirectory(domain,projectId,number(copy.get("physical_subsystem_id")),String.valueOf(copy.get("directory_path")),user));saveScope(domain,projectId,null,copy,user);created++;}}audit(domain,projectId,"SCOPE",0,"IMPORT",Map.of("created",created,"updated",updated,"skipped",skipped),user);Map<String,Object> result=new LinkedHashMap<>(validation);result.put("written",created+updated);result.put("created",created);result.put("updated",updated);result.put("skipped",skipped);result.remove("rows");return result;}

    public List<Map<String,Object>> exportRows(String domain,long projectId,Long systemId,Long directoryId,String keyword,Collection<String> statuses,Collection<String> functionTypes,Collection<String> changeStatuses,Collection<String> importances,Collection<String> accountingFlags,String coverage,AuthUser user){List<Map<String,Object>> result=new ArrayList<>();for(Map<String,Object> row:list(domain,projectId,systemId,directoryId,keyword,statuses,functionTypes,changeStatuses,importances,accountingFlags,coverage,false,"scope_code","ascending",new PageQuery(1,2000),user).records()){Map<String,Object> copy=new LinkedHashMap<>(row);Long id=positiveOrNull(copy.get("directory_id"));copy.put("directory_path",id==null?"":directoryPath(id,user.tenantId()));result.add(copy);}return result;}

    private Map<String,Object> validateImport(String domain,long projectId,List<Map<String,Object>> rows,AuthUser user){scope(domain,projectId,user);if(rows==null||rows.isEmpty())throw bad("导入文件没有有效数据行");List<Map<String,Object>> errors=new ArrayList<>();List<Map<String,Object>> prepared=new ArrayList<>();Set<String> fileCodes=new LinkedHashSet<>();Set<String> autoDirectories=new LinkedHashSet<>();int duplicate=0;for(Map<String,Object> source:rows){Map<String,Object> row=new LinkedHashMap<>(source);try{String systemRef=required(row.get("physical_system_code"),"所属系统",64);Long systemId=systemByReference(domain,projectId,systemRef,user.tenantId());if(systemId==null)throw bad("所属系统不存在或未启用："+systemRef);String scopeName=required(row.get("scope_name"),"功能/用例/批处理名称",100);String scopeCode=scopeCode(required(row.get("scope_code"),"序号",128),systemCode(systemId,user.tenantId()));if(!fileCodes.add(scopeCode))throw bad("文件内序号重复："+scopeCode);List<Map<String,Object>> exists=jdbc.queryForList("SELECT id FROM tm_test_scope WHERE tenant_id=? AND test_domain=? AND project_id=? AND scope_code=? AND deleted=0",user.tenantId(),domain,projectId,scopeCode);if(!exists.isEmpty()){row.put("existing_id",exists.get(0).get("id"));duplicate++;}row.put("scope_name",scopeName);row.put("scope_code",scopeCode);row.put("physical_subsystem_id",systemId);String path=required(row.get("directory_path"),"所属目录",500);row.put("directory_path",path);autoDirectories.add(systemId+":"+path);validateDictionary(domain,projectId,"func_type",row.get("function_type"),"功能类型",user);validateDictionary(domain,projectId,"change_status",row.get("change_status"),"变动状态",user);validateDictionary(domain,projectId,"importance",row.get("importance"),"业务重要程度",user);validateDictionary(domain,projectId,"accounting_flag",row.get("accounting_flag"),"是否核算相关",user);prepared.add(row);}catch(BusinessException e){errors.add(Map.of("row_number",row.getOrDefault("row_number",0),"message",e.getMessage()));}}Map<String,Object> result=new LinkedHashMap<>();result.put("total",rows.size());result.put("valid",prepared.size());result.put("failed",errors.size());result.put("duplicate",duplicate);result.put("directories",autoDirectories.size());result.put("success",errors.isEmpty());result.put("errors",errors);result.put("rows",prepared);return result;}

    private void validateDictionary(String domain,long projectId,String dictionaryCode,Object value,String label,AuthUser user){String target=required(value,label,64);Long n=jdbc.queryForObject("SELECT COUNT(*) FROM tm_test_dictionary d JOIN tm_test_dictionary_option o ON o.dictionary_id=d.id AND o.tenant_id=d.tenant_id AND o.deleted=0 AND o.enabled=1 WHERE d.tenant_id=? AND d.test_domain=? AND d.project_id=? AND d.dictionary_code=? AND d.deleted=0 AND d.enabled=1 AND (o.option_code=? OR o.option_name=?)",Long.class,user.tenantId(),domain,projectId,dictionaryCode,target,target);if(n==null||n==0)throw bad(label+"字典项无效："+target);}
    private Map<String,Object> scopeValues(String domain,long projectId,Map<String,Object>b,AuthUser user){Map<String,Object> v=new LinkedHashMap<>();v.put("leaf_menu",text(b.get("leaf_menu"),200,"末级菜单名称"));for(String[] item:List.of(new String[]{"function_type","func_type","功能类型"},new String[]{"change_status","change_status","变动状态"},new String[]{"importance","importance","业务重要程度"},new String[]{"accounting_flag","accounting_flag","是否核算相关"})){String value=required(b.get(item[0]),item[2],64);validateDictionary(domain,projectId,item[1],value,item[2],user);v.put(item[0],value);}return v;}
    private Long ensureDirectory(String domain,long projectId,long systemId,String path,AuthUser user){Long parent=null;for(String raw:path.replace('/','\\').split("\\\\")){String name=raw.trim();if(name.isEmpty())continue;List<Map<String,Object>> found=jdbc.queryForList("SELECT id FROM tm_test_scope_directory WHERE tenant_id=? AND test_domain=? AND project_id=? AND physical_subsystem_id=? AND parent_id "+(parent==null?"IS NULL":"=?")+" AND directory_name=? AND deleted=0",directoryArgs(user.tenantId(),domain,projectId,systemId,parent,name,null));if(found.isEmpty()){if(depth(parent,user.tenantId())>=5)throw bad("范围目录路径超过五层："+path);Map<String,Object> input=new LinkedHashMap<>();input.put("physical_subsystem_id",systemId);input.put("parent_id",parent);input.put("directory_name",name);input.put("sort_no",0);parent=number(saveDirectory(domain,projectId,null,input,user).get("id"));}else parent=number(found.get(0).get("id"));}if(parent==null)throw bad("范围目录路径不能为空");return parent;}
    private void scope(String domain,long projectId,AuthUser user){domain(domain);if(projectId<=0)throw bad("请选择项目");Long n=jdbc.queryForObject("SELECT COUNT(*) FROM pm_project WHERE id=? AND tenant_id=? AND deleted=0",Long.class,projectId,user.tenantId());if(n==null||n==0)throw bad("项目不存在或不属于当前租户");}
    private void domain(String domain){if(!DOMAINS.contains(domain))throw bad("测试大类无效");}
    private void requireSystem(String domain,long projectId,long systemId,long tenantId){Long n=jdbc.queryForObject("SELECT COUNT(*) FROM tm_test_participating_system WHERE tenant_id=? AND test_domain=? AND project_id=? AND physical_subsystem_id=? AND enabled=1 AND deleted=0",Long.class,tenantId,domain,projectId,systemId);if(n==null||n==0)throw bad("请选择已启用的参测系统");}
    private Long systemByReference(String domain,long projectId,String value,long tenant){List<Map<String,Object>> rows=jdbc.queryForList("SELECT p.id FROM tm_test_participating_system s JOIN arch_physical_subsystem p ON p.id=s.physical_subsystem_id AND p.tenant_id=s.tenant_id AND p.deleted=0 WHERE s.tenant_id=? AND s.test_domain=? AND s.project_id=? AND s.enabled=1 AND s.deleted=0 AND (p.code=? OR p.short_name=? OR p.name=?)",tenant,domain,projectId,value,value,value);return rows.isEmpty()?null:number(rows.get(0).get("id"));}
    private Map<String,Object> directory(long id,String domain,long projectId,long tenant){List<Map<String,Object>> rows=jdbc.queryForList("SELECT id,physical_subsystem_id,parent_id,directory_name FROM tm_test_scope_directory WHERE id=? AND tenant_id=? AND test_domain=? AND project_id=? AND deleted=0",id,tenant,domain,projectId);if(rows.isEmpty())throw bad("范围目录不存在或不属于当前项目");return rows.get(0);}
    private String directoryPath(long id,long tenant){List<String> names=new ArrayList<>();Long current=id;while(current!=null){List<Map<String,Object>> rows=jdbc.queryForList("SELECT parent_id,directory_name FROM tm_test_scope_directory WHERE id=? AND tenant_id=? AND deleted=0",current,tenant);if(rows.isEmpty())break;names.add(String.valueOf(rows.get(0).get("directory_name")));current=positiveOrNull(rows.get(0).get("parent_id"));}java.util.Collections.reverse(names);return String.join("\\",names);}
    private Map<String,Object> scopeRow(long id,String domain,long projectId,long tenant){List<Map<String,Object>> rows=jdbc.queryForList("SELECT id,scope_code,physical_subsystem_id,directory_id FROM tm_test_scope WHERE id=? AND tenant_id=? AND test_domain=? AND project_id=? AND deleted=0",id,tenant,domain,projectId);if(rows.isEmpty())throw bad("测试范围不存在或不属于当前项目");return rows.get(0);}
    private List<Long> descendantDirectoryIds(long root,long tenant){List<Long> result=new ArrayList<>(List.of(root));for(int index=0;index<result.size();index++){List<Long> children=jdbc.queryForList("SELECT id FROM tm_test_scope_directory WHERE tenant_id=? AND parent_id=? AND deleted=0",Long.class,tenant,result.get(index));result.addAll(children);}return result;}
    private int depth(Long parent,long tenant){int result=0;Long current=parent;while(current!=null){result++;if(result>5)return result;List<Map<String,Object>> rows=jdbc.queryForList("SELECT parent_id FROM tm_test_scope_directory WHERE id=? AND tenant_id=? AND deleted=0",current,tenant);current=rows.isEmpty()?null:positiveOrNull(rows.get(0).get("parent_id"));}return result;}
    private Object[] directoryArgs(long tenant,String domain,long project,long system,Long parent,String name,Long id){List<Object>a=new ArrayList<>(List.of(tenant,domain,project,system));if(parent!=null)a.add(parent);a.add(name);if(id!=null)a.add(id);return a.toArray();}
    private String nextScopeCode(String d,long p,long system,long tenant){String prefix=systemCode(system,tenant);Integer n=jdbc.queryForObject("SELECT COALESCE(MAX(CAST(SUBSTRING_INDEX(scope_code,'-',-1) AS UNSIGNED)),0)+1 FROM tm_test_scope WHERE tenant_id=? AND test_domain=? AND project_id=? AND physical_subsystem_id=?",Integer.class,tenant,d,p,system);return prefix+"-"+String.format(Locale.ROOT,"%04d",n==null?1:n);}
    private String systemCode(long system,long tenant){return jdbc.queryForObject("SELECT code FROM arch_physical_subsystem WHERE id=? AND tenant_id=? AND deleted=0",String.class,system,tenant);}
    private String scopeCode(String value,String systemCode){String code=required(value,"序号",128).toUpperCase(Locale.ROOT);if(!code.matches(java.util.regex.Pattern.quote(systemCode.toUpperCase(Locale.ROOT))+"-[0-9]{4,}"))throw bad("序号格式必须为“"+systemCode+"-流水号”");return code;}
    private void uniqueScopeCode(String d,long p,String code,Long id,long tenant){List<Object>a=new ArrayList<>(List.of(tenant,d,p,code));String sql="SELECT COUNT(*) FROM tm_test_scope WHERE tenant_id=? AND test_domain=? AND project_id=? AND scope_code=?"+(id==null?"":" AND id<>?");if(id!=null)a.add(id);Long n=jdbc.queryForObject(sql,Long.class,a.toArray());if(n!=null&&n>0)throw conflict("序号已存在");}
    private String statusExpression(){return "CASE WHEN s.invalidated=1 THEN 'INVALID' WHEN NOT EXISTS (SELECT 1 FROM tm_test_case c0 WHERE c0.tenant_id=s.tenant_id AND c0.scope_id=s.id AND c0.deleted=0 AND c0.invalidated=0) THEN 'INVALID' WHEN EXISTS (SELECT 1 FROM tm_test_case c1 JOIN tm_test_execution e1 ON e1.case_id=c1.id AND e1.tenant_id=c1.tenant_id AND e1.deleted=0 WHERE c1.tenant_id=s.tenant_id AND c1.scope_id=s.id AND c1.deleted=0 AND c1.invalidated=0 AND e1.execution_status='FAILED') THEN 'FAILED' WHEN EXISTS (SELECT 1 FROM tm_test_case c2 JOIN tm_test_execution e2 ON e2.case_id=c2.id AND e2.tenant_id=c2.tenant_id AND e2.deleted=0 WHERE c2.tenant_id=s.tenant_id AND c2.scope_id=s.id AND c2.deleted=0 AND c2.invalidated=0 AND e2.execution_status='RUNNING') THEN 'RUNNING' WHEN EXISTS (SELECT 1 FROM tm_test_case c3 WHERE c3.tenant_id=s.tenant_id AND c3.scope_id=s.id AND c3.deleted=0 AND c3.invalidated=0 AND NOT EXISTS (SELECT 1 FROM tm_test_execution e3 WHERE e3.tenant_id=c3.tenant_id AND e3.case_id=c3.id AND e3.deleted=0 AND e3.execution_status='SUCCESS')) THEN 'UNEXECUTED' ELSE 'SUCCESS' END";}
    private String caseCountExpression(){return "(SELECT COUNT(*) FROM tm_test_case c WHERE c.tenant_id=s.tenant_id AND c.scope_id=s.id AND c.deleted=0 AND c.invalidated=0)";}
    private String order(String field,String direction){Map<String,String> fields=Map.of("scope_code","s.scope_code","scope_name","s.scope_name","physical_system_name","p.name","directory_name","d.directory_name","case_count",caseCountExpression(),"updated_at","s.updated_at","created_at","s.created_at");return fields.getOrDefault(field == null ? "" : field,"s.updated_at")+("ascending".equalsIgnoreCase(direction)||"asc".equalsIgnoreCase(direction)?" ASC":" DESC")+",s.id DESC";}
    private void in(StringBuilder where,List<Object> args,String column,Collection<String> values){List<String> clean=validValues(values,null,column);if(!clean.isEmpty()){where.append(" AND ").append(column).append(" IN (").append(placeholders(clean.size())).append(")");args.addAll(clean);}}
    private List<String> validValues(Collection<String> raw,Set<String> allowed,String label){if(raw==null)return List.of();List<String> out=new ArrayList<>();for(String value:raw){String clean=text(value,64,label);if(clean!=null){if(allowed!=null&&!allowed.contains(clean.toUpperCase(Locale.ROOT)))throw bad(label+"无效："+clean);out.add(clean.toUpperCase(Locale.ROOT));}}return out;}
    private String placeholders(int size){return String.join(",", java.util.Collections.nCopies(size,"?"));}
    private void audit(String d,long p,String type,long id,String action,Map<String,Object> detail,AuthUser u){try{jdbc.update("INSERT INTO tm_test_scope_case_audit(id,tenant_id,test_domain,project_id,entity_type,entity_id,action_code,operator_id,detail_json) VALUES(?,?,?,?,?,?,?,?,?)",next(),u.tenantId(),d,p,type,id,action,u.id(),objectMapper.writeValueAsString(detail));}catch(JsonProcessingException e){throw new BusinessException(ErrorCode.INTERNAL_ERROR,"审计记录序列化失败");}}
    private long next(){return IDS.incrementAndGet();} private long positive(Object v,String f){Long n=positiveOrNull(v);if(n==null||n<=0)throw bad(f+"无效");return n;} private Long positiveOrNull(Object v){if(v==null||String.valueOf(v).isBlank()||"null".equals(String.valueOf(v)))return null;try{return Long.parseLong(String.valueOf(v));}catch(NumberFormatException e){throw bad("编号无效");}} private long number(Object v){return ((Number)v).longValue();} private int integer(Object v){if(v==null||String.valueOf(v).isBlank())return 0;try{return Integer.parseInt(String.valueOf(v));}catch(NumberFormatException e){throw bad("排序值无效");}}
    private String required(Object v,String f,int max){String s=text(v,max,f);if(s==null)throw bad(f+"不能为空");return s;} private String text(Object v,int max,String f){if(v==null)return null;String s=String.valueOf(v).trim();if(s.isEmpty()||"null".equals(s))return null;if(s.length()>max)throw bad(f+"不能超过"+max+"个字符");return s;} private String code(String v,String f,int max){String s=required(v,f,max);if(!s.matches("[A-Za-z0-9][A-Za-z0-9_-]*"))throw bad(f+"只能包含字母、数字、下划线或短横线");return s.toUpperCase(Locale.ROOT);} private BusinessException bad(String message){return new BusinessException(ErrorCode.BAD_REQUEST,message);}private BusinessException conflict(String message){return new BusinessException(ErrorCode.CONFLICT,message);}
}
