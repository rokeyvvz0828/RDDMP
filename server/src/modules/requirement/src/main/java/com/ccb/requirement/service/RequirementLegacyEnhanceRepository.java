package com.ccb.requirement.service;
import org.springframework.stereotype.Repository;
import java.util.*;
@Repository public class RequirementLegacyEnhanceRepository {
 private final RequirementLegacyEnhanceMapper m; public RequirementLegacyEnhanceRepository(RequirementLegacyEnhanceMapper m){this.m=m;}
 public List<Map<String,Object>> deliverables(long t,long r,String type){return m.deliverables(t,r,type);} public void insertDeliverable(String type,Map<String,Object> p){m.insertDeliverable(type,p);} public void deleteDeliverable(long t,long id,String type){m.deleteDeliverable(t,id,type);}
 public void submitReview(long t,long id,String type,String ids,String names,String report){m.submitReview(t,id,type,ids,names,report);} public void reviewDeliverable(long t,long id,String type,String status,long record){m.reviewDeliverable(t,id,type,status,record);}
 public List<Map<String,Object>> coordinationItems(long t,long r){return m.coordinationItems(t,r);} public void insertCoordination(Map<String,Object> p){m.insertCoordination(p);} public void updateCoordination(Map<String,Object> p){m.updateCoordination(p);} public void deleteCoordination(long t,long id){m.deleteCoordination(t,id);}
 public List<Map<String,Object>> reviewRecords(long t,String b,long id){return m.reviewRecords(t,b,id);} public void insertReviewRecord(Map<String,Object> p){m.insertReviewRecord(p);} public List<Map<String,Object>> approverNames(long t,List<Long> ids){return ids.isEmpty()?List.of():m.approverNames(t,ids);} public Map<String,Object> legacyRequirement(long t,long id){return m.legacyRequirement(t,id);} public List<String> versions(long t,long r,Long si,String sc,String type){return m.versions(t,r,si,sc,type);} public Map<String,Object> deliverable(long t,long id,String type){return m.deliverable(t,id,type);} public Map<String,Object> coordination(long t,long id){return m.coordination(t,id);}
}
