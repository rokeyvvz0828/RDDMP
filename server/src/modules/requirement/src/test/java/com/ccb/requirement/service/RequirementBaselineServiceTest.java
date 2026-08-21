package com.ccb.requirement.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.requirement.support.StubJdbcTemplate;
import com.ccb.security.model.AuthUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequirementBaselineServiceTest {
    private static final AuthUser ADMIN = new AuthUser(1L, 1L, "admin", "", "管理员", 1L, true);

    private Fixture fixture(Function<String, Long> counts, List<Map<String, Object>> differences) {
        Map<String, Object> project = new LinkedHashMap<>();
        project.put("id", 1L);
        project.put("project_code", "P001");
        project.put("project_name", "测试项目");
        StubJdbcTemplate jdbc = new StubJdbcTemplate(
                sql -> sql.contains("requirement:admin") ? 1L : counts.apply(sql),
                differences,
                project);
        RequirementChangeLogService changeLog = new RequirementChangeLogService(jdbc);
        RequirementSecurityService security = new RequirementSecurityService(jdbc);
        RequirementBaselineService service = new RequirementBaselineService(jdbc, changeLog, security, new ObjectMapper());
        return new Fixture(jdbc, service);
    }

    @Test
    void createWithPendingDifferencesThrowsConflict() {
        Fixture fixture = fixture(sql -> sql.contains("review_status <> '已评审'") ? 1L : 1L, List.of());
        BusinessException exception = assertThrows(BusinessException.class,
                () -> fixture.service().create(1L, null, ADMIN));
        assertEquals(ErrorCode.CONFLICT, exception.code());
    }

    @Test
    void createWithAllReviewedBuildsBaselineSnapshot() {
        Map<String, Object> difference = new LinkedHashMap<>();
        difference.put("id", 10L);
        difference.put("name", "已评审差异");
        difference.put("review_status", "已评审");
        Fixture fixture = fixture(sql -> sql.contains("review_status <> '已评审'") ? 0L : 0L, List.of(difference));
        fixture.service().create(1L, "测试基线", ADMIN);
        assertTrue(fixture.jdbc().updates().stream().anyMatch(sql -> sql.contains("INSERT INTO `req_baseline`")));
        assertTrue(fixture.jdbc().updates().stream().anyMatch(sql -> sql.contains("INSERT INTO `req_baseline_item`")));
        assertTrue(fixture.jdbc().updates().stream().anyMatch(sql -> sql.contains("UPDATE req_difference SET baseline_id")));
        assertTrue(fixture.jdbc().updates().stream().anyMatch(sql -> sql.contains("INSERT INTO req_change_log")));
    }

    private record Fixture(StubJdbcTemplate jdbc, RequirementBaselineService service) {
    }
}
