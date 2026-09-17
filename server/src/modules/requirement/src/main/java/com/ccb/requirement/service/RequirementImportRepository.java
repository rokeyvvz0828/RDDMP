package com.ccb.requirement.service;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Map;
@Repository
public class RequirementImportRepository {
 private final RequirementImportMapper mapper;
 public RequirementImportRepository(RequirementImportMapper mapper){this.mapper=mapper;}
 public void insertBatch(Map<String,Object> p){mapper.insertBatch(p);} public List<Map<String,Object>> listBatches(long t){return mapper.listBatches(t);}
 public boolean projectExists(long t,long p){return mapper.projectCount(t,p)>0;} public long maxDifferenceSequence(long t,long p){Long value=mapper.maxDifferenceSequence(t,p);return value==null?0:value;}
 public void insertDifference(Map<String,Object> p){mapper.insertDifference(p);} public void insertLegacy(Map<String,Object> p){mapper.insertLegacy(p);} public boolean legacyRequirementExists(long t,String no){return mapper.legacyRequirementCount(t,no)>0;}
}
