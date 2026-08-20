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

class RequirementLegacyServiceTest {
    private static final AuthUser MEMBER = new AuthUser(9L, 1L, "mock.product", "", "演示产品经理", 1L, true);

    private Fixture fixture(String stageStatus) {
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
        // PROPOSE 阶段必填字段
        row.put("legacy_doc_name", "测试文档");
        row.put("requirement_no", "JG-W00001");
        row.put("requirement_name", "测试需求");
        row.put("content_summary", "测试描述");
        row.put("propose_dept", "测试部门");
        row.put("proposer", "张三 13800000000");
        row.put("workflow_instance_id", null);
        StubJdbcTemplate jdbc = new StubJdbcTemplate(count -> 1L, List.of(row), Map.of());
        RequirementChangeLogService changeLog = new RequirementChangeLogService(jdbc);
        RequirementSecurityService security = new RequirementSecurityService(jdbc);
        StubWorkflowService workflow = new StubWorkflowService();
        RequirementLegacyService service = new RequirementLegacyService(jdbc, changeLog, security, workflow);
        return new Fixture(jdbc, workflow, service);
    }

    @Test
    void stageStartTransitionsToApprovingAndStartsWorkflow() {
        Fixture fixture = fixture("未开始");
        fixture.service().stageTransition(1L, "PROPOSE", "START", "开始需求提出", java.util.List.of(1L), MEMBER);
        // 阶段状态被置为"审批中"，并写入 workflow_instance_id
        assertTrue(fixture.jdbc().updates().stream().anyMatch(sql -> sql.contains("propose_stage_status") && sql.contains("'审批中'")));
        assertTrue(fixture.jdbc().updates().stream().anyMatch(sql -> sql.contains("workflow_instance_id")));
        // 启动了 legacy.stage.transition 审批流，businessKey 编码 reqId:stage:action
        assertEquals(List.of("req-legacy:1:PROPOSE:START"), fixture.workflow().started());
        // 写入阶段日志与变更记录（changeLog.record 将 changeType 作为参数，仅能断言 INSERT INTO req_change_log）
        assertTrue(fixture.jdbc().updates().stream().anyMatch(sql -> sql.contains("INSERT INTO req_stage_log")));
        assertTrue(fixture.jdbc().updates().stream().anyMatch(sql -> sql.contains("INSERT INTO req_change_log")));
    }

    @Test
    void stageStartOnApprovingThrowsConflict() {
        Fixture fixture = fixture("审批中");
        BusinessException exception = assertThrows(BusinessException.class,
                () -> fixture.service().stageTransition(1L, "PROPOSE", "START", null, java.util.List.of(1L), MEMBER));
        assertEquals(ErrorCode.CONFLICT, exception.code());
    }

    @Test
    void stageCompleteFromNotStartedThrowsConflict() {
        Fixture fixture = fixture("未开始");
        BusinessException exception = assertThrows(BusinessException.class,
                () -> fixture.service().stageTransition(1L, "PROPOSE", "COMPLETE", null, java.util.List.of(1L), MEMBER));
        assertEquals(ErrorCode.CONFLICT, exception.code());
    }

    @Test
    void invalidStageActionThrowsBadRequest() {
        Fixture fixture = fixture("未开始");
        BusinessException exception = assertThrows(BusinessException.class,
                () -> fixture.service().stageTransition(1L, "PROPOSE", "JUMP", null, java.util.List.of(1L), MEMBER));
        assertEquals(ErrorCode.BAD_REQUEST, exception.code());
    }

    @Test
    void stageStartWithoutApproverThrowsBadRequest() {
        Fixture fixture = fixture("未开始");
        BusinessException exception = assertThrows(BusinessException.class,
                () -> fixture.service().stageTransition(1L, "PROPOSE", "START", null, java.util.List.of(), MEMBER));
        assertEquals(ErrorCode.BAD_REQUEST, exception.code());
    }

    private record Fixture(StubJdbcTemplate jdbc, StubWorkflowService workflow, RequirementLegacyService service) {
    }
}
