package com.ccb.architecture.persistence;

import com.ccb.architecture.model.DeploymentUnitModels.DeploymentUnit;
import com.ccb.architecture.model.DeploymentUnitModels.DeploymentUnitImportBatch;
import com.ccb.architecture.model.DeploymentUnitModels.DeploymentUnitImportItem;
import com.ccb.architecture.model.DeploymentUnitModels.DeploymentUnitQuery;
import com.ccb.architecture.model.DeploymentUnitModels.DeploymentUnitVersion;
import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Repository;

/** Deployment-unit domain facade. All persistence SQL is owned by DeploymentUnitMapper.xml. */
@Repository
public class DeploymentUnitStore {
    public static final int MAX_ORDINAL_PER_PHYSICAL = 999;
    public static final int MAX_IMPORT_ROWS = 5000;
    public record PhysicalSubsystemRef(long id,String code,String name,String status,boolean deleted) {}
    public record RelatedDeploymentUnitRow(long id,String code,String name,String kind,long physicalSubsystemId,String physicalSubsystemName,String status) {}
    private final DeploymentUnitRepository repository;
    public DeploymentUnitStore(DeploymentUnitRepository repository) { this.repository = repository; }

    public PageResult<DeploymentUnit> pageUnits(long tenantId,PageQuery page,DeploymentUnitQuery query){PageQuery normalized=page==null?new PageQuery(1,20):page;DeploymentUnitQuery q=query==null?DeploymentUnitQuery.empty():query;Map<String,Object> p=unitFilter(tenantId,q,normalized);return new PageResult<>(repository.pageUnits(p).stream().map(DeploymentUnitStore::unit).toList(),repository.countUnits(p),normalized.page(),normalized.size());}
    public Optional<DeploymentUnit> findUnit(long tenantId,long id){return first(repository.findUnit(p("tenantId",tenantId,"id",id))).map(DeploymentUnitStore::unit);}
    public Optional<DeploymentUnit> lockUnit(long tenantId,long id){return first(repository.lockUnit(p("tenantId",tenantId,"id",id))).map(DeploymentUnitStore::unit);}
    public Optional<DeploymentUnit> findUnitByName(long tenantId,String name){return first(repository.findUnitByName(p("tenantId",tenantId,"name",name))).map(DeploymentUnitStore::unit);}
    public boolean unitNameExists(long tenantId,String name,Long exclude){return repository.countUnitName(p("tenantId",tenantId,"name",name,"excludeUnitId",exclude))>0;}
    public void insertUnit(long id,long tenantId,String code,long physicalId,String name,String kind,Long zoneId,String zoneName,String description,String remark,long actor){repository.insertUnit(p("id",id,"tenantId",tenantId,"code",code,"physicalSubsystemId",physicalId,"name",name,"kind",kind,"defaultNetworkZoneId",zoneId,"defaultNetworkZoneName",zoneName,"description",description,"remark",remark,"actorId",actor));}
    public void insertUnit(long id,long tenantId,String code,long physicalId,String name,String kind,String description,String remark,long actor){insertUnit(id,tenantId,code,physicalId,name,kind,null,null,description,remark,actor);}
    public int updateUnitContent(long tenantId,long id,long version,String name,String kind,Long zoneId,String zoneName,String description,String remark,long actor){return repository.updateUnitContent(p("tenantId",tenantId,"id",id,"expectedRowVersion",version,"name",name,"kind",kind,"defaultNetworkZoneId",zoneId,"defaultNetworkZoneName",zoneName,"description",description,"remark",remark,"actorId",actor));}
    public int updateUnitContent(long tenantId,long id,long version,String name,String kind,String description,String remark,long actor){return updateUnitContent(tenantId,id,version,name,kind,null,null,description,remark,actor);}
    public int updateUnitStatus(long tenantId,long id,String from,String to,long actor){return repository.updateUnitStatus(p("tenantId",tenantId,"id",id,"fromStatus",from,"toStatus",to,"actorId",actor));}
    public void updateUnitCurrentVersion(long tenantId,long id,int version,long actor){repository.updateUnitCurrentVersion(p("tenantId",tenantId,"id",id,"versionNo",version,"actorId",actor));}
    public Optional<PhysicalSubsystemRef> findPhysical(long tenantId,long id){return first(repository.findPhysical(p("tenantId",tenantId,"id",id))).map(DeploymentUnitStore::physical);}
    public Optional<PhysicalSubsystemRef> findPhysicalByCode(long tenantId,String code){return first(repository.findPhysicalByCode(p("tenantId",tenantId,"code",code))).map(DeploymentUnitStore::physical);}

    public String allocateNumber(long tenantId,long physicalId,String physicalCode){String lock="du-alloc-"+tenantId+"-"+physicalId;Integer acquired=repository.acquireNumberLock(p("lockName",lock));if(acquired==null||acquired!=1)throw new IllegalStateException("部署单元编号分配繁忙，请重试");try {for(int attempt=0;attempt<3;attempt++){Integer next=repository.lockNextOrdinal(p("tenantId",tenantId,"physicalSubsystemId",physicalId));if(next==null){repository.insertNextOrdinal(p("tenantId",tenantId,"physicalSubsystemId",physicalId));return String.format(Locale.ROOT,"D%s%03d",physicalCode,1);}if(next>MAX_ORDINAL_PER_PHYSICAL)throw new DeploymentUnitNumberCapacityExceededException("物理子系统 "+physicalCode+" 的部署单元编号容量已用尽（最多 "+MAX_ORDINAL_PER_PHYSICAL+" 个）");if(repository.incrementNextOrdinal(p("tenantId",tenantId,"physicalSubsystemId",physicalId,"nextOrdinal",next+1))==1)return String.format(Locale.ROOT,"D%s%03d",physicalCode,next);}throw new IllegalStateException("部署单元编号分配失败，请重试");} finally {repository.releaseNumberLock(p("lockName",lock));}}
    public void insertVersion(long id,long tenantId,long unitId,int version,String name,String kind,Long zoneId,String zoneName,String description,String remark,long actor){repository.insertVersion(p("id",id,"tenantId",tenantId,"unitId",unitId,"versionNo",version,"name",name,"kind",kind,"defaultNetworkZoneId",zoneId,"defaultNetworkZoneName",zoneName,"description",description,"remark",remark,"actorId",actor));}
    public void insertVersion(long id,long tenantId,long unitId,int version,String name,String kind,String description,String remark,long actor){insertVersion(id,tenantId,unitId,version,name,kind,null,null,description,remark,actor);}
    public List<DeploymentUnitVersion> findVersions(long tenantId,long unitId){return repository.findVersions(p("tenantId",tenantId,"unitId",unitId)).stream().map(DeploymentUnitStore::version).toList();}
    public int countVersions(long tenantId,long unitId){return (int)repository.countVersions(p("tenantId",tenantId,"unitId",unitId));}
    public List<RelatedDeploymentUnitRow> findRelatedUnits(long tenantId,long unitId){return repository.findRelatedUnits(p("tenantId",tenantId,"unitId",unitId)).stream().map(m->new RelatedDeploymentUnitRow(n(m,"id"),s(m,"code"),s(m,"name"),s(m,"kind"),n(m,"physical_subsystem_id"),s(m,"physical_subsystem_name"),s(m,"status"))).toList();}
    public List<DeploymentUnit> lockActiveUnits(long tenantId,List<Long> ids){if(ids==null||ids.isEmpty())return List.of();return repository.lockActiveUnits(p("tenantId",tenantId,"ids",ids.stream().distinct().sorted().toList())).stream().map(DeploymentUnitStore::unit).toList();}
    public void replaceRelations(long tenantId,long sourceId,Set<Long> targetIds,long actor,int version){Set<Long> desired=targetIds==null?Set.of():new HashSet<>(targetIds);Set<Long> current=new HashSet<>(repository.lockRelatedIds(p("tenantId",tenantId,"sourceUnitId",sourceId)));Set<Long> additions=new HashSet<>(desired);additions.removeAll(current);Set<Long> removals=new HashSet<>(current);removals.removeAll(desired);for(Long target:additions.stream().sorted().toList()){long low=Math.min(sourceId,target),high=Math.max(sourceId,target);repository.insertRelation(p("tenantId",tenantId,"low",low,"high",high,"actorId",actor));relationHistory(tenantId,sourceId,low,high,"LINK",actor,version);}for(Long target:removals.stream().sorted().toList()){long low=Math.min(sourceId,target),high=Math.max(sourceId,target);repository.deleteRelation(p("tenantId",tenantId,"low",low,"high",high));relationHistory(tenantId,sourceId,low,high,"UNLINK",actor,version);}}
    public boolean hasRelations(long tenantId,long unitId){return repository.countRelations(p("tenantId",tenantId,"unitId",unitId))>0;}
    public PageResult<DeploymentUnit> searchActiveOptions(long tenantId,String keyword,Long excludeId,PageQuery page){PageQuery normalized=page==null?new PageQuery(1,20):page;Map<String,Object> p=p("tenantId",tenantId,"keyword",escapeLike(keyword==null?"":keyword.trim()),"excludeId",excludeId,"limit",normalized.size(),"offset",(normalized.page()-1)*normalized.size());return new PageResult<>(repository.activeOptions(p).stream().map(DeploymentUnitStore::unit).toList(),repository.countActiveOptions(p),normalized.page(),normalized.size());}
    private void relationHistory(long tenantId,long source,long low,long high,String action,long actor,int version){repository.insertRelationHistory(p("tenantId",tenantId,"sourceUnitId",source,"low",low,"high",high,"action",action,"actorId",actor,"sourceVersionNo",version));}
    public void insertBatch(long id,long tenantId,String file,long size,int total,int valid,long actor){repository.insertBatch(p("id",id,"tenantId",tenantId,"fileName",file,"fileSize",size,"totalRows",total,"validRows",valid,"actorId",actor));}
    public void insertItem(long id,long tenantId,long batchId,int line,String raw,String status,String error,String note,Long unitId){repository.insertItem(p("id",id,"tenantId",tenantId,"batchId",batchId,"lineNo",line,"rawJson",raw,"rowStatus",status,"errorMessage",error,"note",note,"unitId",unitId));}
    public void updateItemResult(long tenantId,long id,String status,String error,String note,Long unitId){repository.updateItemResult(p("tenantId",tenantId,"id",id,"rowStatus",status,"errorMessage",error,"note",note,"unitId",unitId));}
    public void updateBatchResult(long tenantId,long id,String status,int success,int failed,int skipped,String error){repository.updateBatchResult(p("tenantId",tenantId,"id",id,"status",status,"successRows",success,"failedRows",failed,"skippedRows",skipped,"errorMessage",error));}
    public Optional<DeploymentUnitImportBatch> findBatch(long tenantId,long id){return first(repository.findBatch(p("tenantId",tenantId,"id",id))).map(DeploymentUnitStore::batch);}
    public PageResult<DeploymentUnitImportBatch> pageBatches(long tenantId,PageQuery page){PageQuery normalized=page==null?new PageQuery(1,20):page;Map<String,Object> p=p("tenantId",tenantId,"limit",normalized.size(),"offset",(normalized.page()-1)*normalized.size());return new PageResult<>(repository.pageBatches(p).stream().map(DeploymentUnitStore::batch).toList(),repository.countBatches(p),normalized.page(),normalized.size());}
    public List<DeploymentUnitImportItem> findItems(long tenantId,long batchId,int limit){return repository.findItems(p("tenantId",tenantId,"batchId",batchId,"limit",limit)).stream().map(DeploymentUnitStore::item).toList();}

    private static Map<String,Object> unitFilter(long tenantId,DeploymentUnitQuery q,PageQuery page){return p("tenantId",tenantId,"code",like(q.code()),"name",like(q.name()),"physicalSubsystemId",q.physicalSubsystemId(),"kind",q.kind(),"status",q.status(),"limit",page.size(),"offset",(page.page()-1)*page.size());}
    private static String like(String value){return value==null||value.isBlank()?null:escapeLike(value.trim());} private static String escapeLike(String value){return value.replace("\\","\\\\").replace("%","\\%").replace("_","\\_");}
    private static DeploymentUnit unit(Map<String,Object> m){return new DeploymentUnit(n(m,"id"),s(m,"code"),n(m,"physical_subsystem_id"),s(m,"name"),s(m,"kind"),nullable(m,"default_network_zone_id"),s(m,"default_network_zone_name"),s(m,"status"),i(m,"current_version"),s(m,"description"),s(m,"remark"),n(m,"created_by"),n(m,"updated_by"),dt(m,"created_at"),dt(m,"updated_at"),n(m,"row_version"));}
    private static DeploymentUnitVersion version(Map<String,Object> m){return new DeploymentUnitVersion(n(m,"id"),n(m,"unit_id"),i(m,"version_no"),s(m,"name"),s(m,"kind"),nullable(m,"default_network_zone_id"),s(m,"default_network_zone_name"),s(m,"description"),s(m,"remark"),n(m,"published_by"),dt(m,"published_at"));}
    private static DeploymentUnitImportBatch batch(Map<String,Object> m){return new DeploymentUnitImportBatch(n(m,"id"),s(m,"file_name"),n(m,"file_size"),i(m,"total_rows"),i(m,"valid_rows"),i(m,"success_rows"),i(m,"failed_rows"),i(m,"skipped_rows"),s(m,"status"),s(m,"error_message"),n(m,"created_by"),dt(m,"created_at"),dt(m,"completed_at"));}
    private static DeploymentUnitImportItem item(Map<String,Object> m){return new DeploymentUnitImportItem(n(m,"id"),n(m,"batch_id"),i(m,"line_no"),s(m,"raw_json"),s(m,"row_status"),s(m,"error_message"),s(m,"note"),nullable(m,"unit_id"),dt(m,"created_at"));}
    private static PhysicalSubsystemRef physical(Map<String,Object> m){return new PhysicalSubsystemRef(n(m,"id"),s(m,"code"),s(m,"name"),s(m,"status"),bool(m,"deleted"));}
    private static Map<String,Object> p(Object... values){Map<String,Object> m=new HashMap<>();for(int x=0;x<values.length;x+=2)m.put((String)values[x],values[x+1]);return m;} private static <T> Optional<T> first(List<T> rows){return rows.stream().findFirst();} private static Object value(Map<String,Object> m,String key){return m.get(key);} private static String s(Map<String,Object> m,String key){Object value=value(m,key);return value==null?null:String.valueOf(value);} private static long n(Map<String,Object> m,String key){return ((Number)value(m,key)).longValue();} private static int i(Map<String,Object> m,String key){return ((Number)value(m,key)).intValue();} private static Long nullable(Map<String,Object> m,String key){Object value=value(m,key);return value==null?null:((Number)value).longValue();} private static boolean bool(Map<String,Object> m,String key){Object value=value(m,key);return value instanceof Boolean b?b:((Number)value).intValue()!=0;} private static LocalDateTime dt(Map<String,Object> m,String key){Object value=value(m,key);return value==null?null:value instanceof LocalDateTime time?time:((Timestamp)value).toLocalDateTime();}
}
