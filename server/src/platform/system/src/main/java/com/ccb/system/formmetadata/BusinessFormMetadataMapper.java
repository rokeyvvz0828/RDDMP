package com.ccb.system.formmetadata;

import org.apache.ibatis.annotations.Mapper;
import java.util.List;
import java.util.Map;

@Mapper
public interface BusinessFormMetadataMapper {
    List<Map<String, Object>> listScopes(Map<String, Object> p); Map<String, Object> scope(Map<String, Object> p);
    List<Map<String, Object>> sections(Map<String, Object> p); List<Map<String, Object>> fields(Map<String, Object> p);
    List<Map<String, Object>> rules(Map<String, Object> p); List<Map<String, Object>> options(Map<String, Object> p); List<Map<String, Object>> revisions(Map<String, Object> p);
    Integer scopeDuplicate(Map<String, Object> p); int insertScope(Map<String, Object> p); int updateScope(Map<String, Object> p);
    Integer sectionCount(Map<String, Object> p); Integer fieldCountForSection(Map<String, Object> p); Map<String, Object> section(Map<String, Object> p); int insertSection(Map<String, Object> p); int updateSection(Map<String, Object> p); int deleteSection(Map<String, Object> p);
    Integer fieldCount(Map<String, Object> p); Integer fieldDuplicate(Map<String, Object> p); Map<String, Object> field(Map<String, Object> p); int insertField(Map<String, Object> p); int updateField(Map<String, Object> p); int deleteField(Map<String, Object> p);
    int deleteRules(Map<String, Object> p); int insertRule(Map<String, Object> p); int deleteOptions(Map<String, Object> p); int insertOption(Map<String, Object> p);
    Integer nextRevisionNo(Map<String, Object> p); int archivePublished(Map<String, Object> p); int insertRevision(Map<String, Object> p); int publishScope(Map<String, Object> p);
}
