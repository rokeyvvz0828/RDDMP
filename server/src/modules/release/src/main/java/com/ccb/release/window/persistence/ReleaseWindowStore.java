package com.ccb.release.window.persistence;

import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.release.window.model.ReleaseWindow;
import com.ccb.release.window.model.WindowFieldChange;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

@Repository
public class ReleaseWindowStore {
    private final ReleaseWindowMapper mapper;
    public ReleaseWindowStore(ReleaseWindowMapper mapper) { this.mapper = mapper; }
    public PageResult<ReleaseWindow> findPage(long tenantId, String projectId, String keyword, PageQuery page) { Map<String,Object> params=p("tenantId",tenantId,"projectId",blank(projectId),"keyword",blank(keyword),"size",page.size(),"offset",(page.page()-1)*page.size()); Long total=mapper.count(params); return new PageResult<>(mapper.page(params),total == null ? 0 : total,page.page(),page.size()); }
    public Optional<ReleaseWindow> findById(long id,long tenantId) { return Optional.ofNullable(mapper.find(p("id",id,"tenantId",tenantId))); }
    public Optional<ReleaseWindow> findByIdForUpdate(long id,long tenantId) { return Optional.ofNullable(mapper.findForUpdate(p("id",id,"tenantId",tenantId))); }
    public OptionalLong findTenantId(long id) { Long tenant=mapper.tenantId(p("id",id)); return tenant == null ? OptionalLong.empty() : OptionalLong.of(tenant); }
    public void lockProjectWindows(long tenantId,String projectId) { mapper.lockProjectWindows(p("tenantId",tenantId,"projectId",projectId)); }
    public boolean hasOverlap(long tenantId,String projectId,LocalDateTime start,LocalDateTime end,Long excludedId) { Long count=mapper.overlapCount(p("tenantId",tenantId,"projectId",projectId,"start",start,"end",end,"excludedId",excludedId)); return count != null && count > 0; }
    public int nextMonthlySequence(long tenantId,String monthPrefix) { String code=mapper.latestCode(p("tenantId",tenantId,"monthPrefix",monthPrefix)); if(code == null) return 1; int separator=code.lastIndexOf('-'); if(separator<0||separator==code.length()-1)return 1; try{return Integer.parseInt(code.substring(separator+1))+1;}catch(NumberFormatException ignored){return 1;} }
    public void insert(ReleaseWindow window) { mapper.insert(windowParams(window)); }
    public boolean update(ReleaseWindow window,long expectedVersion) { Map<String,Object> values=windowParams(window);values.put("expectedVersion",expectedVersion);return mapper.update(values)==1; }
    public void appendChanges(long tenantId,long windowId,List<WindowFieldChange> changes,String reason,long operatorId,long idSeed) { long id=idSeed; for(WindowFieldChange change:changes) mapper.appendChange(p("id",id++,"tenantId",tenantId,"windowId",windowId,"fieldName",change.fieldName(),"oldValue",change.oldValue(),"newValue",change.newValue(),"reason",reason,"operatorId",operatorId)); }
    private static Map<String,Object> windowParams(ReleaseWindow w) { return p("id",w.id(),"tenantId",w.tenantId(),"windowCode",w.windowCode(),"windowName",w.windowName(),"projectId",w.projectId(),"projectCode",w.projectCode(),"projectName",w.projectName(),"declarationStart",w.declarationStart(),"declarationEnd",w.declarationEnd(),"productionStart",w.productionStart(),"productionEnd",w.productionEnd(),"regularEnabled",w.regularEnabled(),"description",w.description(),"createdBy",w.createdBy(),"updatedBy",w.updatedBy()); }
    private static Map<String,Object> p(Object... values) { Map<String,Object> result=new HashMap<>();for(int i=0;i<values.length;i+=2)result.put((String)values[i],values[i+1]);return result; }
    private static String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
