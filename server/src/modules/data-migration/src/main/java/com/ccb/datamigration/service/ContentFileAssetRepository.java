package com.ccb.datamigration.service;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

@Repository
public class ContentFileAssetRepository {
    private final ContentFileAssetMapper mapper;
    public ContentFileAssetRepository(ContentFileAssetMapper mapper) { this.mapper = mapper; }
    public long count(String table,String type,long tenant,long project,String system,String keyword){Long n=mapper.count(table,type,tenant,project,system,keyword);return n==null?0:n;}
    public List<Map<String,Object>> page(String table,String type,long tenant,long project,String system,String keyword,int limit,long offset){return mapper.page(table,type,tenant,project,system,keyword,limit,offset);}
    public void insert(String table,Map<String,Object> p){mapper.insert(table,p);} public Map<String,Object> active(String table,long tenant,long id){return first(mapper.findActive(table,tenant,id));} public int update(String table,Map<String,Object> p){return mapper.update(table,p);} public int softDelete(String table,long tenant,long id,long actor){return mapper.softDelete(table,tenant,id,actor);} public int restore(String table,long tenant,long id,long actor){return mapper.restore(table,tenant,id,actor);} public int purge(String table,long tenant,long id){return mapper.purge(table,tenant,id);}
    public List<Long> mainAttachmentIds(String table,String type,long tenant,long id){return mapper.mainAttachmentIds(table,type,tenant,id);} public long deletedCount(String table,long tenant,long project,String keyword){Long n=mapper.deletedCount(table,tenant,project,keyword);return n==null?0:n;} public List<Map<String,Object>> deletedPage(String table,String type,long tenant,long project,String keyword,int limit){return mapper.deletedPage(table,type,tenant,project,keyword,limit);} public Map<String,Object> deleted(String table,String type,long tenant,long id){return first(mapper.findDeleted(table,type,tenant,id));} public Map<String,Object> any(String table,long tenant,long id){return first(mapper.findAny(table,tenant,id));} public List<Long> deletedProjectIds(String table,long tenant,long id){return mapper.deletedProjectIds(table,tenant,id);} public boolean enabledComponent(long tenant,long project,String system){Integer n=mapper.enabledComponentCount(tenant,project,system);return n!=null&&n>0;} public List<Long> currentMainAttachmentIds(long tenant,String type,long id){return mapper.currentMainAttachmentIds(tenant,type,id);} public void audit(long tenant,long actor,long project,String operation,long id){mapper.insertAudit(tenant,actor,project,operation,id);}
    private Map<String,Object> first(List<Map<String,Object>> rows){return rows.isEmpty()?null:rows.get(0);}
}
