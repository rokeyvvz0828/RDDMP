package com.ccb.workflow.service;

import org.apache.ibatis.annotations.Mapper;
import java.util.List;
import java.util.Map;

@Mapper
public interface WorkflowSubmittedMapper {
    List<Map<String, Object>> page(Map<String, Object> params);
    long count(Map<String, Object> params);
    List<Map<String, Object>> activeTasks(Map<String, Object> params);
}
