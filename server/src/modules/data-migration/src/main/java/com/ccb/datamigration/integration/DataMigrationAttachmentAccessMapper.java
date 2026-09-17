package com.ccb.datamigration.integration;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface DataMigrationAttachmentAccessMapper {
    Map<String, Object> meeting(Map<String, Object> params);
    List<Map<String, Object>> assets(Map<String, Object> params);
}
