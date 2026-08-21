package com.ccb.system.project;

import com.ccb.common.exception.BusinessException;
import com.ccb.infrastructure.storage.MinioStorageService;
import com.ccb.security.model.AuthUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {
    @Mock
    private JdbcTemplate jdbc;
    @Mock
    private MinioStorageService storage;

    private final AuthUser member = new AuthUser(7L, 1L, "member", "", "Member", 1L, true);
    private final AuthUser admin = new AuthUser(1L, 1L, "admin", "", "Admin", 1L, true);

    @Test
    void rejectsDetailForUserOutsideProjectMembership() {
        ProjectService service = new ProjectService(jdbc, storage);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(0, 1, 1, 0, 0);

        assertThrows(BusinessException.class, () -> service.detail(9001L, member));
    }

    @Test
    void superAdminUsesAllProjectScope() {
        ProjectService service = new ProjectService(jdbc, storage);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

        service.workbench(admin);

        verify(jdbc).queryForList(org.mockito.ArgumentMatchers.contains("1 = 1"), eq(1L));
    }

    @Test
    void rejectsProjectDateRangeBeforePersistence() {
        ProjectService service = new ProjectService(jdbc, storage);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1, 1);

        Map<String, Object> input = new HashMap<>();
        input.put("project_code", "P-001");
        input.put("project_name", "date-validation");
        input.put("planned_start_date", "2026-08-20");
        input.put("planned_end_date", "2026-08-19");

        assertThrows(BusinessException.class, () -> service.create(input, admin));
    }

    @Test
    void createsMainPlanWithServerGeneratedCode() {
        ProjectService service = new ProjectService(jdbc, storage);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);
        when(jdbc.queryForMap(org.mockito.ArgumentMatchers.contains("FOR UPDATE"), eq(9001L), eq(1L)))
                .thenReturn(Map.of("project_code", "P-001", "plan_number_rule", "{PROJECT_CODE}-P{SEQ:3}", "next_plan_sequence", 2L));
        when(jdbc.queryForMap(org.mockito.ArgumentMatchers.contains("SELECT p.id"), any(Object[].class)))
                .thenReturn(new HashMap<>(Map.of("id", 9002L, "project_id", 9001L, "parent_id", 0L, "plan_code", "P-001-P002")));
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

        Map<String, Object> input = new HashMap<>();
        input.put("plan_name", "main-plan");
        input.put("plan_code", "CLIENT-MUST-BE-IGNORED");
        input.put("planned_start_date", "2026-08-01");
        input.put("planned_end_date", "2026-08-31");

        service.createPlan(9001L, input, admin);

        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("main-plan"),
                org.mockito.ArgumentMatchers.eq("P-001-P002"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("NOT_STARTED"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void insertsPlanWithMatchingColumnAndParameterCount() {
        ProjectService service = new ProjectService(jdbc, storage);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);
        when(jdbc.queryForMap(org.mockito.ArgumentMatchers.contains("FOR UPDATE"), eq(9001L), eq(1L)))
                .thenReturn(Map.of("project_code", "RDC", "plan_number_rule", "{PROJECT_CODE}-P{SEQ:3}", "next_plan_sequence", 1L));
        when(jdbc.queryForMap(org.mockito.ArgumentMatchers.contains("SELECT p.id"), any(Object[].class)))
                .thenReturn(new HashMap<>(Map.of("id", 9002L, "project_id", 9001L, "parent_id", 0L, "plan_code", "RDC-P001")));
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

        Map<String, Object> input = new HashMap<>();
        input.put("plan_name", "column-count-plan");
        input.put("planned_start_date", "2026-08-01");
        input.put("planned_end_date", "2026-08-31");

        service.createPlan(9001L, input, admin);

        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("(id, tenant_id, project_id, group_id, parent_id, plan_name, plan_code, description, owner_id, planned_start_date, planned_end_date, progress, status, phase, sort_no) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("RDC-P001"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("NOT_STARTED"),
                 org.mockito.ArgumentMatchers.eq("PLAN_INITIATION"), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void skipsUsedMainPlanCodeWhenProjectSequenceIsBehindExistingPlans() {
        ProjectService service = new ProjectService(jdbc, storage);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);
        when(jdbc.queryForMap(org.mockito.ArgumentMatchers.contains("FOR UPDATE"), eq(9001L), eq(1L)))
                .thenReturn(Map.of("project_code", "RDC", "plan_number_rule", "{PROJECT_CODE}-P{SEQ:3}", "next_plan_sequence", 1L));
        when(jdbc.queryForObject(org.mockito.ArgumentMatchers.contains("plan_code = ?"), eq(Long.class), eq(9001L), eq(1L), eq("RDC-P001")))
                .thenReturn(1L);
        when(jdbc.queryForObject(org.mockito.ArgumentMatchers.contains("plan_code = ?"), eq(Long.class), eq(9001L), eq(1L), eq("RDC-P002")))
                .thenReturn(0L);
        when(jdbc.queryForMap(org.mockito.ArgumentMatchers.contains("SELECT p.id"), any(Object[].class)))
                .thenReturn(new HashMap<>(Map.of("id", 9002L, "project_id", 9001L, "parent_id", 0L, "plan_code", "RDC-P002")));
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

        service.createPlan(9001L, Map.of("plan_name", "新增主计划", "planned_start_date", "2026-08-01", "planned_end_date", "2026-08-31"), admin);

        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("INSERT INTO pm_project_plan"),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("新增主计划"),
                org.mockito.ArgumentMatchers.eq("RDC-P002"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("NOT_STARTED"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("UPDATE pm_project SET next_plan_sequence = ?"), eq(3L), eq(9001L), eq(1L));
    }

    @Test
    void rejectsMainPlanWithoutPlannedDates() {
        ProjectService service = new ProjectService(jdbc, storage);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);

        assertThrows(BusinessException.class, () -> service.createPlan(9001L, Map.of("plan_name", "缺少日期的主计划"), admin));
    }

    @Test
    void rejectsLaterMainPlanStartingBeforePreviousPlanEndsInSameGroup() {
        ProjectService service = new ProjectService(jdbc, storage);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);
        when(jdbc.queryForMap(org.mockito.ArgumentMatchers.contains("FOR UPDATE"), eq(9001L), eq(1L)))
                .thenReturn(Map.of("project_code", "RDC", "plan_number_rule", "{PROJECT_CODE}-P{SEQ:3}", "next_plan_sequence", 2L));
        Map<String, Object> group = new HashMap<>(Map.of("id", 8001L, "project_id", 9001L, "phase", "PLAN_INITIATION", "group_name", "1-1"));
        when(jdbc.queryForMap(org.mockito.ArgumentMatchers.contains("SELECT id, project_id, phase"), eq(8001L), eq(9001L), eq(1L)))
                .thenReturn(group);
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.contains("SELECT id, parent_id, planned_start_date, planned_end_date"), eq(9001L), eq(1L), eq(8001L)))
                .thenReturn(List.of(Map.of("id", 9002L, "parent_id", 0L, "planned_start_date", "2026-08-01", "planned_end_date", "2026-08-31")));

        Map<String, Object> input = new HashMap<>();
        input.put("group_id", 8001L);
        input.put("plan_name", "后建主计划");
        input.put("planned_start_date", "2026-08-31");
        input.put("planned_end_date", "2026-09-30");

        assertThrows(BusinessException.class, () -> service.createPlan(9001L, input, admin));
    }

    @Test
    void createsChildPlanByExtendingParentCodeWithIndependentSequence() {
        ProjectService service = new ProjectService(jdbc, storage);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);
        when(jdbc.queryForMap(org.mockito.ArgumentMatchers.contains("SELECT planned_start_date"), eq(9002L), eq(9001L), eq(1L)))
                .thenReturn(Map.of("planned_start_date", "2026-08-01", "planned_end_date", "2026-08-31"));
        when(jdbc.queryForMap(org.mockito.ArgumentMatchers.contains("FOR UPDATE"), eq(9001L), eq(1L)))
                .thenReturn(Map.of("project_code", "RDC", "plan_number_rule", "{PROJECT_CODE}-P{SEQ:3}", "child_plan_number_rule", "{PARENT_CODE}-S{SEQ:3}", "next_plan_sequence", 4L));
        when(jdbc.queryForMap(org.mockito.ArgumentMatchers.contains("next_child_plan_sequence"), eq(9002L), eq(9001L), eq(1L)))
                .thenReturn(Map.of("id", 9002L, "plan_code", "RDC-P004", "next_child_plan_sequence", 2L));
        when(jdbc.queryForMap(org.mockito.ArgumentMatchers.contains("SELECT p.id"), any(Object[].class)))
                .thenReturn(new HashMap<>(Map.of("id", 9003L, "project_id", 9001L, "parent_id", 9002L, "plan_code", "RDC-P004-S002")));
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

        Map<String, Object> input = new HashMap<>();
        input.put("parent_id", 9002L);
        input.put("plan_name", "child-plan-number");
        input.put("plan_code", "CLIENT-MUST-BE-IGNORED");

        service.createPlan(9001L, input, admin);

        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(9001L),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(9002L),
                org.mockito.ArgumentMatchers.eq("child-plan-number"), org.mockito.ArgumentMatchers.eq("RDC-P004-S002"),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("NOT_STARTED"),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void usesDefaultChildRuleForLegacyProjectWithNullRule() {
        ProjectService service = new ProjectService(jdbc, storage);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);
        when(jdbc.queryForMap(org.mockito.ArgumentMatchers.contains("SELECT planned_start_date"), eq(9002L), eq(9001L), eq(1L)))
                .thenReturn(Map.of("planned_start_date", "2026-08-01", "planned_end_date", "2026-08-31"));
        Map<String, Object> project = new HashMap<>();
        project.put("project_code", "RDC");
        project.put("plan_number_rule", "{PROJECT_CODE}-P{SEQ:3}");
        project.put("child_plan_number_rule", null);
        project.put("next_plan_sequence", 2L);
        when(jdbc.queryForMap(org.mockito.ArgumentMatchers.contains("FOR UPDATE"), eq(9001L), eq(1L))).thenReturn(project);
        when(jdbc.queryForMap(org.mockito.ArgumentMatchers.contains("next_child_plan_sequence"), eq(9002L), eq(9001L), eq(1L)))
                .thenReturn(Map.of("id", 9002L, "plan_code", "RDC-P001", "next_child_plan_sequence", 1L));
        when(jdbc.queryForMap(org.mockito.ArgumentMatchers.contains("SELECT p.id"), any(Object[].class)))
                .thenReturn(new HashMap<>(Map.of("id", 9003L, "project_id", 9001L, "parent_id", 9002L, "plan_code", "RDC-P001-S001")));
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

        Map<String, Object> input = new HashMap<>();
        input.put("parent_id", 9002L);
        input.put("plan_name", "legacy-child-plan");

        service.createPlan(9001L, input, admin);

        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(9001L),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(9002L), org.mockito.ArgumentMatchers.eq("legacy-child-plan"),
                org.mockito.ArgumentMatchers.eq("RDC-P001-S001"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("NOT_STARTED"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void movesMainPlanAndAllDescendantsToOneGroup() {
        ProjectService service = new ProjectService(jdbc, storage);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);
        when(jdbc.queryForMap(org.mockito.ArgumentMatchers.contains("FOR UPDATE"), eq(9101L), eq(9101L), eq(1L)))
                .thenReturn(Map.of("id", 9101L, "parent_id", 0L, "group_id", 11L));
        when(jdbc.queryForMap(org.mockito.ArgumentMatchers.contains("SELECT id, project_id, phase, group_name"), eq(22L), eq(9101L), eq(1L)))
                .thenReturn(new HashMap<>(Map.of("id", 22L, "project_id", 9101L, "phase", "PLAN_REQUIREMENT", "group_name", "需求阶段", "color_key", "brand", "sort_no", 1)));
        doAnswer(invocation -> {
            long parentId = ((Number) invocation.getArgument(2)).longValue();
            if (parentId == 9101L) {
                return List.of(9102L, 9103L);
            }
            if (parentId == 9102L) {
                return List.of(9104L);
            }
            return List.<Long>of();
        }).when(jdbc).queryForList(org.mockito.ArgumentMatchers.contains("parent_id = ?"), eq(Long.class), anyLong(), anyLong(), anyLong());

        service.movePlanToGroup(9101L, 9101L, Map.of("group_id", 22L), admin);

        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("UPDATE pm_project_plan SET group_id = ?, phase = ?"),
                org.mockito.ArgumentMatchers.eq(22L), org.mockito.ArgumentMatchers.eq("PLAN_REQUIREMENT"),
                org.mockito.ArgumentMatchers.eq(9101L),
                org.mockito.ArgumentMatchers.eq(9102L), org.mockito.ArgumentMatchers.eq(9103L),
                org.mockito.ArgumentMatchers.eq(9104L), org.mockito.ArgumentMatchers.eq(9101L),
                org.mockito.ArgumentMatchers.eq(1L));
    }

    @Test
    void rejectsChildPlanAsGroupMoveSource() {
        ProjectService service = new ProjectService(jdbc, storage);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);
        when(jdbc.queryForMap(org.mockito.ArgumentMatchers.contains("FOR UPDATE"), eq(9202L), eq(9201L), eq(1L)))
                .thenReturn(Map.of("id", 9202L, "parent_id", 9201L, "group_id", 11L));

        assertThrows(BusinessException.class,
                () -> service.movePlanToGroup(9201L, 9202L, Map.of("group_id", 22L), admin));
    }

    @Test
    void persistsSemanticColorTokenWhenCreatingPlanGroup() {
        ProjectService service = new ProjectService(jdbc, storage);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1, 1, 1, 1, 0);
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.contains("SELECT c.config_key AS value"), any(Object[].class)))
                .thenReturn(List.of(Map.of("value", "PLAN_INITIATION"), Map.of("value", "PLAN_REQUIREMENT")));
        when(jdbc.queryForObject(org.mockito.ArgumentMatchers.contains("MAX(CASE"), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbc.queryForMap(org.mockito.ArgumentMatchers.contains("SELECT id, project_code, plan_number_rule"), eq(9001L), eq(1L)))
                .thenReturn(Map.of("id", 9001L, "project_code", "RDC", "plan_number_rule", "{PROJECT_CODE}-P{SEQ:3}", "child_plan_number_rule", "{PARENT_CODE}-S{SEQ:3}", "next_plan_sequence", 1L));
        when(jdbc.queryForMap(org.mockito.ArgumentMatchers.contains("SELECT id, project_id, phase, group_name"), any(Object[].class)))
                .thenReturn(new HashMap<>(Map.of("id", 9301L, "project_id", 9001L, "phase", "PLAN_INITIATION", "group_name", "1-1", "color_key", "accent", "sort_no", 1)));

        service.createPlanGroup(9001L, Map.of("phase", "PLAN_INITIATION", "color_key", "accent", "sort_no", 1), admin);

        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("phase, group_name, color_key, description, sort_no"),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq(9001L),
                org.mockito.ArgumentMatchers.eq("PLAN_INITIATION"),
                org.mockito.ArgumentMatchers.eq("1-1"), org.mockito.ArgumentMatchers.eq("accent"),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.eq(1));
    }

    @Test
    void persistsSemanticColorTokenWhenUpdatingPlanGroup() {
        ProjectService service = new ProjectService(jdbc, storage);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1, 1);
        when(jdbc.queryForMap(org.mockito.ArgumentMatchers.contains("SELECT id, project_id, phase, group_name"), any(Object[].class)))
                .thenReturn(new HashMap<>(Map.of("id", 9301L, "project_id", 9001L, "phase", "PLAN_INITIATION", "group_name", "重点交付", "color_key", "success", "sort_no", 1)));

        service.updatePlanGroup(9001L, 9301L, Map.of("color_key", "success"), admin);

        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("UPDATE pm_project_plan_group SET color_key = ?"),
                org.mockito.ArgumentMatchers.eq("success"), org.mockito.ArgumentMatchers.eq(9301L),
                org.mockito.ArgumentMatchers.eq(9001L), org.mockito.ArgumentMatchers.eq(1L));
    }

    @Test
    void rejectsUnknownPlanGroupColorToken() {
        ProjectService service = new ProjectService(jdbc, storage);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1, 0);

        assertThrows(BusinessException.class,
                () -> service.createPlanGroup(9001L, Map.of("group_name", "非法色阶", "color_key", "tech-blue"), admin));
    }

    @Test
    void createsRiskWithServerGeneratedCodeAndIgnoresClientCode() {
        ProjectService service = new ProjectService(jdbc, storage);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);
        when(jdbc.queryForMap(org.mockito.ArgumentMatchers.contains("FOR UPDATE"), eq(9001L), eq(1L)))
                .thenReturn(Map.of("project_code", "RDC", "risk_number_rule", "{PROJECT_CODE}-R{SEQ:3}", "next_risk_sequence", 7L));
        when(jdbc.queryForMap(org.mockito.ArgumentMatchers.contains("SELECT r.id"), any(Object[].class)))
                .thenReturn(new HashMap<>(Map.of("id", 9401L, "project_id", 9001L, "risk_code", "RDC-R007", "current_status", "OPEN")));

        Map<String, Object> input = new HashMap<>();
        input.put("risk_code", "CLIENT-MUST-BE-IGNORED");
        input.put("current_status", "OPEN");

        service.createRisk(9001L, input, admin);

        ArgumentCaptor<Object[]> parameters = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("INSERT INTO pm_project_risk"), parameters.capture());
        assertEquals("RDC-R007", parameters.getValue()[3]);
    }

    @Test
    void rejectsRiskWhenConfiguredParameterIsInvalid() {
        ProjectService service = new ProjectService(jdbc, storage);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1, 0);

        assertThrows(BusinessException.class,
                () -> service.createRisk(9001L, Map.of("current_status", "UNKNOWN"), admin));
    }

    @Test
    void rejectsEmptyRiskCommentBeforePersistence() {
        ProjectService service = new ProjectService(jdbc, storage);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);

        assertThrows(BusinessException.class,
                () -> service.createRiskComment(9001L, 9401L, Map.of("comment_text", "  "), member));
    }

    @Test
    void createsRiskCommentWithAuthenticatedUser() {
        ProjectService service = new ProjectService(jdbc, storage);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);
        when(jdbc.queryForMap(org.mockito.ArgumentMatchers.contains("SELECT c.id"), any(Object[].class)))
                .thenReturn(new HashMap<>(Map.of("id", 9501L, "project_id", 9001L, "risk_id", 9401L, "user_id", 1L, "comment_text", "已完成复核")));

        service.createRiskComment(9001L, 9401L, Map.of("comment_text", "已完成复核", "user_id", 9999L), admin);

        ArgumentCaptor<Object[]> parameters = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("INSERT INTO pm_project_risk_comment"), parameters.capture());
        assertEquals(1L, parameters.getValue()[4]);
        verify(jdbc).queryForMap(org.mockito.ArgumentMatchers.contains("SELECT c.id"), any(Object[].class));
    }

    @Test
    void listsRiskCommentsNewestFirst() {
        ProjectService service = new ProjectService(jdbc, storage);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.contains("ORDER BY c.created_at DESC, c.id DESC"), any(Object[].class)))
                .thenReturn(List.of());

        service.riskComments(9001L, 9401L, admin);

        verify(jdbc).queryForList(org.mockito.ArgumentMatchers.contains("ORDER BY c.created_at DESC, c.id DESC"), any(Object[].class));
    }

    @Test
    void rejectsProjectOrganizationAsItsOwnParent() {
        ProjectService service = new ProjectService(jdbc, storage);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(0, 1, 1, 1);

        assertThrows(BusinessException.class,
                () -> service.updateOrganization(9001L, 9601L, Map.of("parent_id", 9601L), admin));
    }

    @Test
    void listsIndependentProjectOrganizations() {
        ProjectService service = new ProjectService(jdbc, storage);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.contains("FROM pm_project_org"), any(Object[].class)))
                .thenReturn(List.of(Map.of("id", 9601L, "project_id", 9001L, "parent_id", 0L, "org_code", "DEV", "org_name", "项目研发组", "status", 1)));

        List<Map<String, Object>> rows = service.organizations(9001L, admin);

        assertEquals(1, rows.size());
        verify(jdbc).queryForList(org.mockito.ArgumentMatchers.contains("FROM pm_project_org"), eq(9001L), eq(1L));
    }
}
