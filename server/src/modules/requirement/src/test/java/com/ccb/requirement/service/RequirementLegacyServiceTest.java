package com.ccb.requirement.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.requirement.support.StubJdbcTemplate;
import com.ccb.security.model.AuthUser;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequirementLegacyServiceTest {
    private static final AuthUser MEMBER = new AuthUser(9L, 1L, "mock.product", "", "演示产品经理", 1L, true);

    private Map<String, Object> row(String stageStatus) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 1L);
        row.put("business_group", "零售一组");
        row.put("current_stage", "PROPOSE");
        row.put("propose_stage_status", stageStatus);
        row.put("docking_stage_status", "未开始");
        row.put("workload_stage_status", "未开始");
        row.put("project_stage_status", "未开始");
        row.put("soft_stage_status", "未开始");
        row.put("launch_stage_status", "未开始");
        // 核心标识字段
        row.put("requirement_no", "JG-W00001");
        row.put("requirement_name", "测试需求");
        row.put("business_group", "零售一组");
        // 部分阶段业务字段已填，其余留空用于验证缺失提醒
        row.put("legacy_doc_name", "测试文档");
        row.put("content_summary", "测试描述");
        row.put("propose_dept", "测试部门");
        row.put("proposer", "张三 13800000000");
        row.put("workflow_instance_id", null);
        return row;
    }

    private Fixture fixture(String stageStatus) {
        StubJdbcTemplate jdbc = new StubJdbcTemplate(count -> 1L, List.of(row(stageStatus)), Map.of());
        RequirementChangeLogService changeLog = new RequirementChangeLogService(jdbc);
        RequirementSecurityService security = new RequirementSecurityService(jdbc);
        RequirementLegacyService service = new RequirementLegacyService(jdbc, changeLog, security);
        return new Fixture(jdbc, service);
    }

    @Test
    void stageStartTransitionsToInProgressAndWritesLogs() {
        Fixture fixture = fixture("未开始");
        fixture.service().stageTransition(1L, "PROPOSE", "START", "开始需求提出", true, MEMBER);
        // 直接流转：阶段状态置为"进行中"，清理审批实例，不启动审批流
        assertTrue(fixture.jdbc().updates().stream().anyMatch(sql -> sql.contains("propose_stage_status") && sql.contains("进行中")));
        assertTrue(fixture.jdbc().updates().stream().anyMatch(sql -> sql.contains("workflow_instance_id = NULL")));
        // 写入阶段日志与变更记录
        assertTrue(fixture.jdbc().updates().stream().anyMatch(sql -> sql.contains("INSERT INTO req_stage_log")));
        assertTrue(fixture.jdbc().updates().stream().anyMatch(sql -> sql.contains("INSERT INTO req_change_log")));
    }

    @Test
    void stageStartFromInProgressThrowsConflict() {
        Fixture fixture = fixture("进行中");
        BusinessException exception = assertThrows(BusinessException.class,
                () -> fixture.service().stageTransition(1L, "PROPOSE", "START", null, false, MEMBER));
        assertEquals(ErrorCode.CONFLICT, exception.code());
    }

    @Test
    void stageCompleteFromNotStartedThrowsConflict() {
        Fixture fixture = fixture("未开始");
        BusinessException exception = assertThrows(BusinessException.class,
                () -> fixture.service().stageTransition(1L, "PROPOSE", "COMPLETE", null, false, MEMBER));
        assertEquals(ErrorCode.CONFLICT, exception.code());
    }

    @Test
    void invalidStageActionThrowsBadRequest() {
        Fixture fixture = fixture("未开始");
        BusinessException exception = assertThrows(BusinessException.class,
                () -> fixture.service().stageTransition(1L, "PROPOSE", "JUMP", null, false, MEMBER));
        assertEquals(ErrorCode.BAD_REQUEST, exception.code());
    }

    @Test
    void missingStageFieldsReturnReminderAndConfirmedContinues() {
        Fixture fixture = fixture("未开始");
        Map<String, Object> reminder = fixture.service().stageTransition(1L, "PROPOSE", "START", null, false, MEMBER);
        assertEquals(Boolean.FALSE, reminder.get("confirmed"));
        assertFalse(((List<?>) reminder.get("missingFields")).isEmpty());
        // 用户确认后携带 ignoreMissingStageFields=true 继续推进
        fixture.jdbc().updates().clear();
        fixture.service().stageTransition(1L, "PROPOSE", "START", null, true, MEMBER);
        assertTrue(fixture.jdbc().updates().stream().anyMatch(sql -> sql.contains("propose_stage_status") && sql.contains("进行中")));
    }

    @Test
    void backTransitionsToNotStartedAndWritesLogWithoutClearingFields() {
        Fixture fixture = fixture("进行中");
        fixture.service().stageTransition(1L, "PROPOSE", "BACK", "回退补填", false, MEMBER);
        assertTrue(fixture.jdbc().updates().stream().anyMatch(sql -> sql.contains("propose_stage_status") && sql.contains("未开始")));
        assertTrue(fixture.jdbc().updates().stream().anyMatch(sql -> sql.contains("INSERT INTO req_stage_log")));
        // 回退仅改状态：不出现对业务字段的写回
        assertTrue(fixture.jdbc().updates().stream().noneMatch(sql -> sql.contains("legacy_doc_name =")));
    }

    @Test
    void missingCoreFieldThrowsBadRequest() {
        Map<String, Object> row = row("未开始");
        row.put("requirement_name", null);
        StubJdbcTemplate jdbc = new StubJdbcTemplate(count -> 1L, List.of(row), Map.of());
        RequirementChangeLogService changeLog = new RequirementChangeLogService(jdbc);
        RequirementSecurityService security = new RequirementSecurityService(jdbc);
        RequirementLegacyService service = new RequirementLegacyService(jdbc, changeLog, security);
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.stageTransition(1L, "PROPOSE", "START", null, true, MEMBER));
        assertEquals(ErrorCode.BAD_REQUEST, exception.code());
    }

    private record Fixture(StubJdbcTemplate jdbc, RequirementLegacyService service) {
    }
}
