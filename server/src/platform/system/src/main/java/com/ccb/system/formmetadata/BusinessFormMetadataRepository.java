package com.ccb.system.formmetadata;

import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Map;

@Repository
public class BusinessFormMetadataRepository {
    private final BusinessFormMetadataMapper mapper;
    public BusinessFormMetadataRepository(BusinessFormMetadataMapper mapper) { this.mapper = mapper; }
    public List<Map<String,Object>> listScopes(Map<String,Object> p) { return mapper.listScopes(p); } public Map<String,Object> scope(Map<String,Object> p) { return mapper.scope(p); }
    public List<Map<String,Object>> sections(Map<String,Object> p) { return mapper.sections(p); } public List<Map<String,Object>> fields(Map<String,Object> p) { return mapper.fields(p); }
    public List<Map<String,Object>> rules(Map<String,Object> p) { return mapper.rules(p); } public List<Map<String,Object>> options(Map<String,Object> p) { return mapper.options(p); } public List<Map<String,Object>> revisions(Map<String,Object> p) { return mapper.revisions(p); }
    public Integer scopeDuplicate(Map<String,Object> p) { return mapper.scopeDuplicate(p); } public int insertScope(Map<String,Object> p) { return mapper.insertScope(p); } public int updateScope(Map<String,Object> p) { return mapper.updateScope(p); }
    public Integer sectionCount(Map<String,Object> p) { return mapper.sectionCount(p); } public Integer fieldCountForSection(Map<String,Object> p) { return mapper.fieldCountForSection(p); } public Map<String,Object> section(Map<String,Object> p) { return mapper.section(p); } public int insertSection(Map<String,Object> p) { return mapper.insertSection(p); } public int updateSection(Map<String,Object> p) { return mapper.updateSection(p); } public int deleteSection(Map<String,Object> p) { return mapper.deleteSection(p); }
    public Integer fieldCount(Map<String,Object> p) { return mapper.fieldCount(p); } public Integer fieldDuplicate(Map<String,Object> p) { return mapper.fieldDuplicate(p); } public Map<String,Object> field(Map<String,Object> p) { return mapper.field(p); } public int insertField(Map<String,Object> p) { return mapper.insertField(p); } public int updateField(Map<String,Object> p) { return mapper.updateField(p); } public int deleteField(Map<String,Object> p) { return mapper.deleteField(p); }
    public int deleteRules(Map<String,Object> p) { return mapper.deleteRules(p); } public int insertRule(Map<String,Object> p) { return mapper.insertRule(p); } public int deleteOptions(Map<String,Object> p) { return mapper.deleteOptions(p); } public int insertOption(Map<String,Object> p) { return mapper.insertOption(p); }
    public Integer nextRevisionNo(Map<String,Object> p) { return mapper.nextRevisionNo(p); } public int archivePublished(Map<String,Object> p) { return mapper.archivePublished(p); } public int insertRevision(Map<String,Object> p) { return mapper.insertRevision(p); } public int publishScope(Map<String,Object> p) { return mapper.publishScope(p); }
}
