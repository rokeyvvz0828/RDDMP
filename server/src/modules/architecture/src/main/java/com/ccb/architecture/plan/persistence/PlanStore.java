package com.ccb.architecture.plan.persistence;

import com.ccb.architecture.plan.model.PlanModels.*;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** MyBatis-backed persistence boundary for setup plans. */
@Repository
public class PlanStore {
  private final PlanMapper mapper;
  public PlanStore(PlanMapper mapper) { this.mapper = mapper; }
  public void requireTransaction() { if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("计划数据操作必须在事务内执行"); }
  public void insertPlan(long t,Plan x){requireTransaction();mapper.insertPlan(p("tenantId",t,"x",x));}
  public Optional<Plan> findPlan(long t,long id){return Optional.ofNullable(mapper.findPlan(p("tenantId",t,"id",id))).map(PlanStore::plan);}
  public Optional<Plan> lockPlan(long t,long id){requireTransaction();return Optional.ofNullable(mapper.lockPlan(p("tenantId",t,"id",id))).map(PlanStore::plan);}
  public void updatePlanStatus(long t,long id,PlanStatus s,boolean c,String r,Long by,LocalDateTime at){requireTransaction();mapper.updatePlanStatus(p("tenantId",t,"id",id,"status",s.name(),"cancelled",c,"cancelReason",r,"cancelledBy",by,"cancelledAt",at,"updatedBy",by==null?0L:by));}
  public void updatePlanSchedule(long t,long id,LocalDateTime a,LocalDateTime b){requireTransaction();mapper.updatePlanSchedule(p("tenantId",t,"id",id,"plannedStart",a,"plannedEnd",b));}
  public void updatePlanActual(long t,long id,LocalDateTime a,LocalDateTime b){requireTransaction();mapper.updatePlanActual(p("tenantId",t,"id",id,"actualStart",a,"actualEnd",b));}
  public void insertTarget(long t,PlanTarget x,String r){requireTransaction();mapper.insertTarget(p("tenantId",t,"target",x,"addedReason",r));}
  public List<PlanTarget> findTargets(long t,long id,boolean all){return mapper.findTargets(p("tenantId",t,"planId",id,"removedIncluded",all)).stream().map(PlanStore::target).toList();}
  public List<PlanTarget> findActiveTargets(long t,long id){return findTargets(t,id,false);}
  public Optional<PlanTarget> findTarget(long t,long p,TargetType ty,long id){return Optional.ofNullable(mapper.findTarget(p("tenantId",t,"planId",p,"targetType",ty.name(),"targetId",id))).map(PlanStore::target);}
  public void removeTarget(long t,long id,String r,long by){requireTransaction();mapper.removeTarget(p("tenantId",t,"id",id,"reason",r,"removedBy",by));}
  public void insertStage(long t,Stage x){requireTransaction();mapper.insertStage(p("tenantId",t,"stage",x));}
  public List<Stage> findStages(long t,long p){return mapper.findStages(p("tenantId",t,"planId",p)).stream().map(PlanStore::stage).toList();}
  public Optional<Stage> findStage(long t,long id){return Optional.ofNullable(mapper.findStage(p("tenantId",t,"id",id))).map(PlanStore::stage);}
  public void updateStageStatus(long t,long id,PlanStatus s,boolean c,String r,Long by,LocalDateTime at){requireTransaction();mapper.updateStageStatus(p("tenantId",t,"id",id,"status",s.name(),"cancelled",c,"cancelReason",r,"cancelledBy",by,"cancelledAt",at));}
  public void updateStageSchedule(long t,long id,LocalDateTime a,LocalDateTime b){requireTransaction();mapper.updateStageSchedule(p("tenantId",t,"id",id,"plannedStart",a,"plannedEnd",b));}
  public void updateStageActual(long t,long id,LocalDateTime a,LocalDateTime b){requireTransaction();mapper.updateStageActual(p("tenantId",t,"id",id,"actualStart",a,"actualEnd",b));}
  public void insertTask(long t,Task x){requireTransaction();mapper.insertTask(p("tenantId",t,"task",x));}
  public Optional<Task> findTask(long t,long id){return Optional.ofNullable(mapper.findTask(p("tenantId",t,"id",id))).map(PlanStore::task);}
  public Optional<Task> lockTask(long t,long id){requireTransaction();return Optional.ofNullable(mapper.lockTask(p("tenantId",t,"id",id))).map(PlanStore::task);}
  public List<Task> findTasks(long t,Long p,Long s){return mapper.findTasks(p("tenantId",t,"planId",p,"stageId",s)).stream().map(PlanStore::task).toList();}
  public void updateTaskExecution(long t,long id,TaskStatus s,LocalDateTime a,LocalDateTime b,boolean w,long by){requireTransaction();mapper.updateTaskExecution(p("tenantId",t,"id",id,"status",s.name(),"actualStart",a,"actualEnd",b,"waivedAll",w,"updatedBy",by));}
  public void updateTaskCancel(long t,long id,boolean c,String r,Long by,LocalDateTime at){requireTransaction();mapper.updateTaskCancel(p("tenantId",t,"id",id,"cancelled",c,"cancelReason",r,"cancelledBy",by,"cancelledAt",at));}
  public void updateTaskSchedule(long t,long id,LocalDateTime a,LocalDateTime b){requireTransaction();mapper.updateTaskSchedule(p("tenantId",t,"id",id,"plannedStart",a,"plannedEnd",b));}
  public void updateTaskOwner(long t,long id,long owner,long by){requireTransaction();mapper.updateTaskOwner(p("tenantId",t,"id",id,"ownerUserId",owner,"updatedBy",by));}
  public void deleteTask(long t,long id){requireTransaction();mapper.deleteTask(p("tenantId",t,"id",id));}
  public void insertCheckItem(long t,CheckItem x){requireTransaction();mapper.insertCheckItem(p("tenantId",t,"item",x));}
  public List<CheckItem> findCheckItems(long t,long id){return mapper.findCheckItems(p("tenantId",t,"taskId",id)).stream().map(PlanStore::check).toList();}
  public Optional<CheckItem> findCheckItem(long t,long id){return Optional.ofNullable(mapper.findCheckItem(p("tenantId",t,"id",id))).map(PlanStore::check);}
  public void updateCheckItemCompletion(long t,long id,CheckItemStatus s,String r,Long by,LocalDateTime at,long up){requireTransaction();mapper.updateCheckItemCompletion(p("tenantId",t,"id",id,"status",s.name(),"remark",r,"completedBy",by,"completedAt",at,"updatedBy",up));}
  public void updateCheckItemCancel(long t,long id,boolean c,String r,Long by,LocalDateTime at){requireTransaction();mapper.updateCheckItemCancel(p("tenantId",t,"id",id,"cancelled",c,"cancelReason",r,"cancelledBy",by,"cancelledAt",at,"status",c?"CANCELLED":"PENDING"));}
  public void deleteCheckItem(long t,long id){requireTransaction();mapper.deleteCheckItem(p("tenantId",t,"id",id));}
  public void insertParticipant(long t,long id,long task,long user,long by){requireTransaction();mapper.insertParticipant(p("tenantId",t,"id",id,"taskId",task,"userId",user,"createdBy",by));}
  public List<Long> findParticipantUserIds(long t,long task){return mapper.findParticipantUserIds(p("tenantId",t,"taskId",task));}
  public void deleteParticipants(long t,long task){requireTransaction();mapper.deleteParticipants(p("tenantId",t,"taskId",task));}
  public void insertDependency(long t,long id,long task,long pre,long by){requireTransaction();mapper.insertDependency(p("tenantId",t,"id",id,"taskId",task,"predecessorId",pre,"createdBy",by));}
  public List<Dependency> findDependencies(long t,long task,boolean all){return mapper.findDependencies(p("tenantId",t,"taskId",task,"removedIncluded",all)).stream().map(PlanStore::dependency).toList();}
  public Optional<Dependency> findDependencyById(long t,long id){return Optional.ofNullable(mapper.findDependencyById(p("tenantId",t,"id",id))).map(PlanStore::dependency);}
  public void removeDependency(long t,long id,String r,long by){requireTransaction();mapper.removeDependency(p("tenantId",t,"id",id,"reason",r,"removedBy",by));}
  public void deleteDependenciesByTask(long t,long task){requireTransaction();mapper.deleteDependenciesByTask(p("tenantId",t,"taskId",task));}
  public void insertBlock(long t,Block x){requireTransaction();mapper.insertBlock(p("tenantId",t,"block",x));}
  public List<Block> findBlocks(long t,long task){return mapper.findBlocks(p("tenantId",t,"taskId",task)).stream().map(PlanStore::block).toList();}
  public Optional<Block> findBlock(long t,long id){return Optional.ofNullable(mapper.findBlock(p("tenantId",t,"id",id))).map(PlanStore::block);}
  public void updateBlock(long t,long id,String d,String i,long owner,LocalDateTime at){requireTransaction();mapper.updateBlock(p("tenantId",t,"id",id,"description",d,"impact",i,"ownerUserId",owner,"expectedResolveAt",at));}
  public void resolveBlock(long t,long id,String note,long by){requireTransaction();mapper.resolveBlock(p("tenantId",t,"id",id,"note",note,"resolvedBy",by));}
  public void deleteBlocksByTask(long t,long task){requireTransaction();mapper.deleteBlocksByTask(p("tenantId",t,"taskId",task));}
  public void insertCancelSuggestion(long t,CancelSuggestion x){requireTransaction();mapper.insertCancelSuggestion(p("tenantId",t,"suggestion",x));}
  public List<CancelSuggestion> findPendingSuggestions(long t,long item){return mapper.findPendingSuggestions(p("tenantId",t,"checkItemId",item>0?item:null)).stream().map(PlanStore::suggestion).toList();}
  public Optional<CancelSuggestion> findSuggestion(long t,long id){return Optional.ofNullable(mapper.findSuggestion(p("tenantId",t,"id",id))).map(PlanStore::suggestion);}
  public void handleSuggestion(long t,long id,String s,Long by,String note){requireTransaction();mapper.handleSuggestion(p("tenantId",t,"id",id,"status",s,"handledBy",by,"handlerNote",note));}
  public void insertEvent(long t,PlanEvent x){requireTransaction();mapper.insertEvent(p("tenantId",t,"event",x));}
  public List<PlanEvent> findEvents(long t,long p,String ty,long obj){return mapper.findEvents(p("tenantId",t,"planId",p,"objectType",ty,"objectId",obj)).stream().map(PlanStore::event).toList();}
  public Optional<PlanEvent> findEvent(long t,long id){return Optional.ofNullable(mapper.findEvent(p("tenantId",t,"id",id))).map(PlanStore::event);}
  public void insertWorkOrder(long t,TaskWorkOrder x){requireTransaction();mapper.insertWorkOrder(p("tenantId",t,"workOrder",x));}
  public List<TaskWorkOrder> findWorkOrders(long t,long task){return mapper.findWorkOrders(p("tenantId",t,"taskId",task)).stream().map(PlanStore::workOrder).toList();}
  public Optional<TaskWorkOrder> findWorkOrder(long t,long id){return Optional.ofNullable(mapper.findWorkOrder(p("tenantId",t,"id",id))).map(PlanStore::workOrder);}
  public void removeWorkOrder(long t,long id,String r,long by){requireTransaction();mapper.removeWorkOrder(p("tenantId",t,"id",id,"reason",r,"removedBy",by));}
  public List<Long> openResourceRequestIds(long t,List<Long> ids){return ids.isEmpty()?List.of():mapper.openResourceRequestIds(p("tenantId",t,"ids",ids));}
  public List<long[]> resourceRequestRefs(long t,List<Long> ids){return ids.isEmpty()?List.of():mapper.resourceRequestRefs(p("tenantId",t,"ids",ids)).stream().map(r->new long[]{n(r,"id").longValue(),n(r,"environment_id").longValue()}).toList();}
  public boolean networkWorkOrderRefs(long t,List<Long> ids){return !ids.isEmpty()&&Optional.ofNullable(mapper.countNetworkWorkOrderRefs(p("tenantId",t,"ids",ids))).orElse(0L)==ids.size();}
  public List<Long> openNetworkWorkOrderIds(long t,List<Long> ids){return ids.isEmpty()?List.of():mapper.openNetworkWorkOrderIds(p("tenantId",t,"ids",ids));}
  public record PlanListRow(Plan plan,String environmentCode,String environmentName,long taskCount,long totalCheckItems,long completedCheckItems,long cancelledCheckItems,long openBlocks){}
  public List<PlanListRow> searchPlans(long t,Long env,PlanStatus s,Long owner,boolean blocked,boolean overdue,boolean waived,String word,TargetType ty,Long target,int limit,int offset){if(limit<=0||offset<0)throw new IllegalArgumentException("分页参数无效");return mapper.searchPlans(search(t,env,s,owner,blocked,overdue,waived,word,ty,target,limit,offset)).stream().map(r->new PlanListRow(plan(r),str(r,"environment_code"),str(r,"environment_name"),n(r,"task_count").longValue(),n(r,"total_check_items").longValue(),n(r,"completed_check_items").longValue(),n(r,"cancelled_check_items").longValue(),n(r,"open_blocks").longValue())).toList();}
  public long countPlans(long t,Long env,PlanStatus s,Long owner,boolean blocked,boolean overdue,boolean waived,String word,TargetType ty,Long target){return Optional.ofNullable(mapper.countPlans(search(t,env,s,owner,blocked,overdue,waived,word,ty,target,null,null))).orElse(0L);}
  public Optional<Long> findPlanIdByTask(long t,long task){return Optional.ofNullable(mapper.findPlanIdByTask(p("tenantId",t,"taskId",task)));}
  public record TargetRef(long id,String code,String name,String status){} public record AlertPlan(long tenantId,long planId,String planNo,long planOwnerUserId){} public record EnvironmentRef(long id,String code,String name,String status){}
  public Optional<EnvironmentRef> envReference(long t,long id){return Optional.ofNullable(mapper.envReference(p("tenantId",t,"id",id))).map(PlanStore::env);}
  public List<TargetRef> listPhysicalSubsystemRefs(long t,List<Long> ids){return ids.isEmpty()?List.of():mapper.listPhysicalSubsystemRefs(p("tenantId",t,"ids",ids)).stream().map(PlanStore::ref).toList();}
  public List<TargetRef> listDeploymentUnitRefs(long t,List<Long> ids){return ids.isEmpty()?List.of():mapper.listDeploymentUnitRefs(p("tenantId",t,"ids",ids)).stream().map(PlanStore::ref).toList();}
  public Map<Long,String> currentTargetNames(long t,TargetType ty,List<Long> ids){Map<Long,String> out=new HashMap<>();(ty==TargetType.PHYSICAL_SUBSYSTEM?listPhysicalSubsystemRefs(t,ids):listDeploymentUnitRefs(t,ids)).forEach(r->out.put(r.id(),r.name()));return out;}
  public List<AlertPlan> planIdsNeedingAlert(){return mapper.planIdsNeedingAlert().stream().map(r->new AlertPlan(n(r,"tenant_id").longValue(),n(r,"id").longValue(),str(r,"plan_no"),n(r,"plan_owner_user_id").longValue())).toList();}
  public long countOverdueTasks(long t,long plan,LocalDateTime now){return Optional.ofNullable(mapper.countOverdueTasks(p("tenantId",t,"planId",plan,"now",now))).orElse(0L);}
  public void insertStageDependency(long t,long id,long plan,long stage,long pre,long by){requireTransaction();mapper.insertStageDependency(p("tenantId",t,"id",id,"planId",plan,"stageId",stage,"predecessorStageId",pre,"createdBy",by));}
  public List<Long[]> findStageDependencies(long t,long plan){return mapper.findStageDependencies(p("tenantId",t,"planId",plan)).stream().map(r->new Long[]{n(r,"stage_id").longValue(),n(r,"predecessor_stage_id").longValue()}).toList();}
  public void insertActivity(long t,long id,String scope,long scopeId,String type,Long obj,String action,long user,String reason,String before,String after){requireTransaction();mapper.insertActivity(p("tenantId",t,"id",id,"scopeType",scope,"scopeId",scopeId,"objectType",type,"objectId",obj,"action",action,"operatorUserId",user,"reason",reason,"beforeJson",before,"afterJson",after));}
  private static Map<String,Object> search(long t,Long e,PlanStatus s,Long o,boolean b,boolean d,boolean w,String k,TargetType ty,Long id,Integer l,Integer x){return p("tenantId",t,"environmentId",e,"status",s==null?null:s.name(),"ownerUserId",o,"hasBlocked",b,"hasOverdue",d,"hasWaived",w,"keyword",k==null||k.isBlank()?null:k.trim(),"targetType",ty==null?null:ty.name(),"targetId",id,"limit",l,"offset",x);}
  private static Plan plan(Map<String,Object> r){return new Plan(n(r,"id").longValue(),str(r,"plan_no"),str(r,"name"),n(r,"environment_id").longValue(),PlanStatus.valueOf(str(r,"status")),n(r,"template_id").longValue(),n(r,"template_version_no").intValue(),n(r,"plan_owner_user_id").longValue(),time(r,"planned_start"),time(r,"planned_end"),time(r,"actual_start"),time(r,"actual_end"),flag(r,"cancelled"),str(r,"cancel_reason"),lng(r,"cancelled_by"),time(r,"cancelled_at"),n(r,"row_version").longValue());}
  private static PlanTarget target(Map<String,Object> r){return new PlanTarget(n(r,"id").longValue(),n(r,"plan_id").longValue(),TargetType.valueOf(str(r,"target_type")),n(r,"target_id").longValue(),str(r,"target_no"),str(r,"target_name"),"REMOVED".equals(str(r,"status")),str(r,"removed_reason"));}
  private static Stage stage(Map<String,Object> r){return new Stage(n(r,"id").longValue(),n(r,"plan_id").longValue(),n(r,"stage_no").intValue(),str(r,"name"),n(r,"sort_no").intValue(),n(r,"owner_user_id").longValue(),time(r,"planned_start"),time(r,"planned_end"),time(r,"actual_start"),time(r,"actual_end"),PlanStatus.valueOf(str(r,"status")),flag(r,"cancelled"),str(r,"cancel_reason"),lng(r,"cancelled_by"),time(r,"cancelled_at"),str(r,"snapshot_json"));}
  private static Task task(Map<String,Object> r){return new Task(n(r,"id").longValue(),n(r,"plan_id").longValue(),n(r,"stage_id").longValue(),n(r,"task_no").intValue(),str(r,"name"),str(r,"target_type")==null?null:TargetType.valueOf(str(r,"target_type")),lng(r,"target_id"),str(r,"target_no"),str(r,"target_name"),lng(r,"task_template_id"),integer(r,"task_template_version_no"),str(r,"dimension"),str(r,"snapshot_json"),n(r,"owner_user_id").longValue(),time(r,"planned_start"),time(r,"planned_end"),time(r,"actual_start"),time(r,"actual_end"),TaskStatus.valueOf(str(r,"status")),flag(r,"waived_all"),flag(r,"cancelled"),str(r,"cancel_reason"),lng(r,"cancelled_by"),time(r,"cancelled_at"),n(r,"row_version").longValue());}
  private static CheckItem check(Map<String,Object> r){return new CheckItem(n(r,"id").longValue(),n(r,"task_id").longValue(),n(r,"check_no").intValue(),str(r,"name"),n(r,"sort_no").intValue(),str(r,"guide"),CheckItemStatus.valueOf(str(r,"status")),str(r,"remark"),lng(r,"completed_by"),time(r,"completed_at"),flag(r,"cancelled"),str(r,"cancel_reason"),lng(r,"cancelled_by"),time(r,"cancelled_at"),n(r,"row_version").longValue(),n(r,"created_by").longValue());}
  private static Dependency dependency(Map<String,Object> r){return new Dependency(n(r,"id").longValue(),n(r,"task_id").longValue(),n(r,"predecessor_id").longValue(),"REMOVED".equals(str(r,"status")),str(r,"removed_reason"));}
  private static Block block(Map<String,Object> r){return new Block(n(r,"id").longValue(),n(r,"task_id").longValue(),str(r,"description"),str(r,"impact"),n(r,"owner_user_id").longValue(),time(r,"expected_resolve_at"),"RESOLVED".equals(str(r,"status")),str(r,"resolved_note"),lng(r,"resolved_by"),time(r,"resolved_at"),n(r,"created_by").longValue());}
  private static CancelSuggestion suggestion(Map<String,Object> r){return new CancelSuggestion(n(r,"id").longValue(),n(r,"check_item_id").longValue(),str(r,"reason"),n(r,"submitter_user_id").longValue(),str(r,"status"),lng(r,"handled_by_user_id"),time(r,"handled_at"),str(r,"handler_note"));}
  private static PlanEvent event(Map<String,Object> r){return new PlanEvent(n(r,"id").longValue(),n(r,"plan_id").longValue(),str(r,"object_type"),n(r,"object_id").longValue(),EventType.valueOf(str(r,"event_type")),time(r,"occurred_at"),n(r,"operator_user_id").longValue(),str(r,"reason"),lng(r,"correct_of_event_id"));}
  private static TaskWorkOrder workOrder(Map<String,Object> r){return new TaskWorkOrder(n(r,"id").longValue(),n(r,"task_id").longValue(),n(r,"plan_id").longValue(),WorkOrderType.valueOf(str(r,"work_order_type")),n(r,"work_order_id").longValue(),WorkOrderSource.valueOf(str(r,"source")),"REMOVED".equals(str(r,"status")));}
  private static TargetRef ref(Map<String,Object> r){return new TargetRef(n(r,"id").longValue(),str(r,"code"),str(r,"name"),str(r,"status"));} private static EnvironmentRef env(Map<String,Object> r){return new EnvironmentRef(n(r,"id").longValue(),str(r,"code"),str(r,"name"),str(r,"status"));}
  private static Map<String,Object> p(Object... a){Map<String,Object> r=new HashMap<>();for(int i=0;i<a.length;i+=2)r.put((String)a[i],a[i+1]);return r;} private static Object v(Map<String,Object> r,String k){Object x=r.get(k);return x==null?r.get(camel(k)):x;} private static Number n(Map<String,Object> r,String k){return(Number)v(r,k);} private static String str(Map<String,Object> r,String k){Object x=v(r,k);return x==null?null:String.valueOf(x);} private static boolean flag(Map<String,Object> r,String k){Object x=v(r,k);return x instanceof Boolean b?b:x!=null&&((Number)x).intValue()!=0;} private static Long lng(Map<String,Object> r,String k){Object x=v(r,k);return x==null?null:((Number)x).longValue();} private static Integer integer(Map<String,Object> r,String k){Object x=v(r,k);return x==null?null:((Number)x).intValue();} private static LocalDateTime time(Map<String,Object> r,String k){Object x=v(r,k);return x==null?null:x instanceof Timestamp z?z.toLocalDateTime():(LocalDateTime)x;} private static String camel(String k){StringBuilder b=new StringBuilder();boolean u=false;for(char c:k.toCharArray()){if(c=='_')u=true;else{b.append(u?Character.toUpperCase(c):c);u=false;}}return b.toString();}
}
