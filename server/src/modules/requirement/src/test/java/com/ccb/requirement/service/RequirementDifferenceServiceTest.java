package com.ccb.requirement.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.requirement.support.StubJdbcTemplate;
import com.ccb.requirement.support.StubWorkflowService;
import com.ccb.security.model.AuthUser;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequirementDifferenceServiceTest {
    private static final AuthUser ADMIN = new AuthUser(1L, 1L, "admin", "", "管理员", 1L, true);

    private Fixture fixture(Function<String, Long> counts, Map<String, Object> row) {
        StubJdbcTemplate jdbc = new StubJdbcTemplate(counts, List.of(row), Map.of(
                "id", 1L, "project_code", "P001", "project_name", "测试项目"));
        RequirementChangeLogService changeLog = new RequirementChangeLogService(jdbc);
        RequirementSecurityService security = new RequirementSecurityService(jdbc);
        RequirementSystemService systemService = new RequirementSystemService(jdbc, changeLog);
        StubWorkflowService workflow = new StubWorkflowService();
        RequirementDifferenceService service = new RequirementDifferenceService(jdbc, changeLog, security, systemService, workflow);
        return new Fixture(jdbc, workflow, service);
    }

    @Test
    void submitReviewTransitionsPendingToReviewingAndRecordsChange() {
        Fixture fixture = fixture(count -> 1L, row("待评审"));
        fixture.service().submitReview(1L, List.of(2L, 3L), null, ADMIN);
        assertTrue(fixture.jdbc().updates().stream().anyMatch(sql -> sql.contains("review_status = '评审中'")));
        // changeLog.record 将 changeType 作为参数，断言 INSERT INTO req_change_log 被执行即可
        assertTrue(fixture.jdbc().updates().stream().anyMatch(sql -> sql.contains("INSERT INTO req_change_log")));
        // 启动了 requirement.diff.review 审批流
        assertEquals(List.of("req-diff:1"), fixture.workflow().started());
    }

    @Test
    void submitReviewOnReviewedThrowsConflict() {
        Fixture fixture = fixture(count -> 1L, row("已评审"));
        BusinessException exception = assertThrows(BusinessException.class,
                () -> fixture.service().submitReview(1L, List.of(2L), null, ADMIN));
        assertEquals(ErrorCode.CONFLICT, exception.code());
    }

    @Test
    void submitReviewWithoutApproversThrowsBadRequest() {
        Fixture fixture = fixture(count -> 1L, row("待评审"));
        BusinessException exception = assertThrows(BusinessException.class,
                () -> fixture.service().submitReview(1L, List.of(), null, ADMIN));
        assertEquals(ErrorCode.BAD_REQUEST, exception.code());
    }

    @Test
    void updateReviewedDifferenceThrowsConflict() {
        Fixture fixture = fixture(count -> 1L, row("已评审"));
        BusinessException exception = assertThrows(BusinessException.class,
                () -> fixture.service().update(1L, Map.of("name", "新名称"), ADMIN));
        assertEquals(ErrorCode.CONFLICT, exception.code());
    }

    @Test
    void createValidatesEnumAndWritesCreateChangeLog() {
        Fixture fixture = fixture(count -> 1L, row("待评审"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "示例差异");
        body.put("business_group", "零售一组");
        body.put("category", "功能");
        body.put("system_id", 10L);
        fixture.service().create(1L, body, ADMIN);
        assertTrue(fixture.jdbc().updates().stream().anyMatch(sql -> sql.contains("INSERT INTO `req_difference`")));
        assertTrue(fixture.jdbc().updates().stream().anyMatch(sql -> sql.contains("INSERT INTO req_change_log")));
    }

    private static Map<String, Object> row(String reviewStatus) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 1L);
        row.put("project_id", 1L);
        row.put("review_status", reviewStatus);
        row.put("baseline_id", null);
        row.put("name", "示例差异");
        return row;
    }

    private record Fixture(StubJdbcTemplate jdbc, StubWorkflowService workflow, RequirementDifferenceService service) {
    }
}
