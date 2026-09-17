package com.ccb.requirement.support;

import org.springframework.jdbc.core.JdbcTemplate;
import com.ccb.requirement.service.RequirementSystemMapper;
import com.ccb.requirement.service.RequirementSecurityMapper;
import com.ccb.requirement.service.RequirementBaselineMapper;
import com.ccb.requirement.service.RequirementImportMapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** 测试用 JdbcTemplate：记录 update 语句、按 SQL 内容返回 count 或列表结果。 */
public final class StubJdbcTemplate extends JdbcTemplate implements RequirementSystemMapper, RequirementSecurityMapper, RequirementBaselineMapper, RequirementImportMapper {
    private final List<String> updates = new ArrayList<>();
    private final Function<String, Long> countResolver;
    private final List<Map<String, Object>> listResult;
    private final Map<String, Object> mapResult;

    public StubJdbcTemplate() {
        this(sql -> 0L, List.of(), Map.of());
    }

    public StubJdbcTemplate(Function<String, Long> countResolver,
                            List<Map<String, Object>> listResult,
                            Map<String, Object> mapResult) {
        this.countResolver = countResolver;
        this.listResult = listResult;
        this.mapResult = mapResult;
    }

    public List<String> updates() {
        return updates;
    }

    @Override
    public List<Map<String, Object>> queryForList(String sql, Object... args) {
        return listResult;
    }

    @Override
    public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
        Long count = countResolver.apply(sql);
        if (Integer.class.equals(requiredType)) {
            return requiredType.cast(count.intValue());
        }
        return requiredType.cast(count);
    }

    @Override
    public Map<String, Object> queryForMap(String sql, Object... args) {
        return mapResult;
    }

    @Override
    public int update(String sql, Object... args) {
        updates.add(sql + " | args=" + Arrays.toString(args));
        return 1;
    }

    @Override public List<Map<String, Object>> list(long tenantId) { return listResult; }
    @Override public Map<String, Object> find(long tenantId, long id) { return mapResult; }
    @Override public int countByCode(long tenantId, String systemCode) { return countResolver.apply("req_system").intValue(); }
    @Override public int insert(Map<String, Object> values) { updates.add("req_system insert " + values); return 1; }
    @Override public int update(Map<String, Object> values) { updates.add("req_system update " + values); return 1; }
    @Override public int softDelete(long tenantId, long id, long operatorId) { updates.add("req_system delete " + id); return 1; }
    @Override public Long findIdByCode(long tenantId, String systemCode) { return 0L; }
    @Override public int permissionCount(long tenantId, long userId, String permissionCode) { return countResolver.apply(permissionCode).intValue(); }
    @Override public int legacySystemOwnerCount(long tenantId, long requirementId, long userId) { return 0; }
    @Override public int legacySystemMemberCount(long tenantId, long requirementId, long userId) { return 0; }
    @Override public int currentFlowAssigneeCount(long tenantId, long requirementId, long userId) { return 0; }
    @Override public int legacyMemberCount(long tenantId, long requirementId, long userId) { return 0; }
    @Override public int projectMemberCount(long tenantId, long projectId, long userId) { return 0; }
    @Override public int activeProjectCount(long tenantId, long projectId) { return 1; }
    @Override public int businessGroupMemberCount(long tenantId, String businessGroup, long userId) { return 0; }
    @Override public List<String> listBusinessGroups(long tenantId, long userId) { return List.of(); }
    @Override public List<Map<String, Object>> list(long tenantId, long projectId) { return listResult; }
    @Override public Map<String, Object> findBaseline(long tenantId, long baselineId) { return mapResult; }
    @Override public List<Map<String, Object>> items(long tenantId, long baselineId) { return listResult; }
    @Override public Map<String, Object> project(long tenantId, long projectId) { return mapResult; }
    @Override public long pendingDifferenceCount(long tenantId, long projectId) { return countResolver.apply("review_status <> '已评审'"); }
    @Override public List<Map<String, Object>> reviewedDifferences(long tenantId, long projectId) { return listResult; }
    @Override public long baselineCount(long tenantId, long projectId) { return countResolver.apply("req_baseline"); }
    @Override public int insertBaseline(Map<String, Object> values) { updates.add("baseline insert " + values); return 1; }
    @Override public int insertItem(Map<String, Object> values) { updates.add("baseline item insert " + values); return 1; }
    @Override public int assignDifference(long baselineId, long operatorId, long tenantId, long differenceId) { updates.add("difference baseline " + differenceId); return 1; }
    @Override public int markProjectBaselined(long operatorId, long tenantId, long projectId) { updates.add("project baselined " + projectId); return 1; }
    @Override public int insertBatch(Map<String,Object> values) { updates.add("import batch insert " + values); return 1; }
    @Override public List<Map<String,Object>> listBatches(long tenantId) { return listResult; }
    @Override public int projectCount(long tenantId,long projectId) { return countResolver.apply("FROM req_project").intValue(); }
    @Override public Long maxDifferenceSequence(long tenantId,long projectId) { return 0L; }
    @Override public int insertDifference(Map<String,Object> values) { updates.add("import difference insert " + values); return 1; }
    @Override public int insertLegacy(Map<String,Object> values) { updates.add("import legacy insert " + values); return 1; }
    @Override public int legacyRequirementCount(long tenantId,String requirementNo) { return 0; }
}
