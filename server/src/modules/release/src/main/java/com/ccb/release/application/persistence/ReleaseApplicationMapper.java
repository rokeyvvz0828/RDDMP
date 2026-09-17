package com.ccb.release.application.persistence;

import com.ccb.release.application.model.ReleaseApplicationModels.Application;
import com.ccb.release.application.model.ReleaseApplicationModels.DeliverySnapshot;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;
import java.util.Map;

@Mapper
public interface ReleaseApplicationMapper {
    Long count(Map<String,Object> params); List<Application> page(Map<String,Object> params); Application byCode(Map<String,Object> params); Application byCodeForUpdate(Map<String,Object> params); Application byId(Map<String,Object> params); Long tenantId(Map<String,Object> params); String latestCode(Map<String,Object> params);
    int insert(Map<String,Object> params); int update(Map<String,Object> params); int deactivateDeliveries(Map<String,Object> params); int deactivateRequirements(Map<String,Object> params); int transition(Map<String,Object> params); int event(Map<String,Object> params);
    List<Long> conflicts(Map<String,Object> params); List<Long> relatedIds(Map<String,Object> params); int relation(Map<String,Object> params); int delivery(Map<String,Object> params); int requirement(Map<String,Object> params);
    List<DeliverySnapshot> deliveries(Map<String,Object> params); List<String> requirements(Map<String,Object> params);
}
