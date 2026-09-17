package com.ccb.testmanagement.persistence;

import org.apache.ibatis.annotations.Mapper;

import java.util.Map;

@Mapper
public interface TestAttachmentAccessMapper {
    String defectDomain(Map<String, Object> params);
    String caseDomain(Map<String, Object> params);
    Long announcementCount(Map<String, Object> params);
    String planDomain(Map<String, Object> params);
    String executionDomain(Map<String, Object> params);
}
