package com.ccb.architecture.persistence;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DeploymentUnitMapper {
    long countUnits(Map<String,Object> p); List<Map<String,Object>> pageUnits(Map<String,Object> p); List<Map<String,Object>> findUnit(Map<String,Object> p); List<Map<String,Object>> lockUnit(Map<String,Object> p); List<Map<String,Object>> findUnitByName(Map<String,Object> p); long countUnitName(Map<String,Object> p); int insertUnit(Map<String,Object> p); int updateUnitContent(Map<String,Object> p); int updateUnitStatus(Map<String,Object> p); int updateUnitCurrentVersion(Map<String,Object> p);
    List<Map<String,Object>> findPhysical(Map<String,Object> p); List<Map<String,Object>> findPhysicalByCode(Map<String,Object> p); Integer acquireNumberLock(Map<String,Object> p); Integer releaseNumberLock(Map<String,Object> p); Integer lockNextOrdinal(Map<String,Object> p); int insertNextOrdinal(Map<String,Object> p); int incrementNextOrdinal(Map<String,Object> p);
    int insertVersion(Map<String,Object> p); List<Map<String,Object>> findVersions(Map<String,Object> p); long countVersions(Map<String,Object> p);
    List<Map<String,Object>> findRelatedUnits(Map<String,Object> p); List<Map<String,Object>> lockActiveUnits(Map<String,Object> p); List<Long> lockRelatedIds(Map<String,Object> p); int insertRelation(Map<String,Object> p); int deleteRelation(Map<String,Object> p); int insertRelationHistory(Map<String,Object> p); long countRelations(Map<String,Object> p); long countActiveOptions(Map<String,Object> p); List<Map<String,Object>> activeOptions(Map<String,Object> p);
    int insertBatch(Map<String,Object> p); int insertItem(Map<String,Object> p); int updateItemResult(Map<String,Object> p); int updateBatchResult(Map<String,Object> p); List<Map<String,Object>> findBatch(Map<String,Object> p); long countBatches(Map<String,Object> p); List<Map<String,Object>> pageBatches(Map<String,Object> p); List<Map<String,Object>> findItems(Map<String,Object> p);
}
