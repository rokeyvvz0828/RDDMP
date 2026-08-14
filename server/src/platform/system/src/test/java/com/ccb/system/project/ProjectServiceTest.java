package com.ccb.system.project;

import com.ccb.common.exception.BusinessException;
import com.ccb.infrastructure.storage.MinioStorageService;
import com.ccb.security.model.AuthUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
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

        service.createPlan(9001L, input, admin);

        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("main-plan"),
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

        service.createPlan(9001L, input, admin);

        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("(id, tenant_id, project_id, parent_id, plan_name, plan_code, description, owner_id, planned_start_date, planned_end_date, progress, status, phase, sort_no) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("RDC-P001"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("NOT_STARTED"),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
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

        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(9001L),
                org.mockito.ArgumentMatchers.eq(9002L),
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

        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(9001L),
                org.mockito.ArgumentMatchers.eq(9002L), org.mockito.ArgumentMatchers.eq("legacy-child-plan"),
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

        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("UPDATE pm_project_plan SET group_id = ?"),
                org.mockito.ArgumentMatchers.eq(22L), org.mockito.ArgumentMatchers.eq(9101L),
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
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1, 1, 1, 0);
        when(jdbc.queryForMap(org.mockito.ArgumentMatchers.contains("SELECT id, project_id, group_name"), any(Object[].class)))
                .thenReturn(new HashMap<>(Map.of("id", 9301L, "project_id", 9001L, "group_name", "重点交付", "color_key", "accent", "sort_no", 1)));

        service.createPlanGroup(9001L, Map.of("group_name", "重点交付", "color_key", "accent", "sort_no", 1), admin);

        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("group_name, color_key, description, sort_no"),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq(9001L),
                org.mockito.ArgumentMatchers.eq("重点交付"), org.mockito.ArgumentMatchers.eq("accent"),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.eq(1));
    }

    @Test
    void persistsSemanticColorTokenWhenUpdatingPlanGroup() {
        ProjectService service = new ProjectService(jdbc, storage);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1, 1);
        when(jdbc.queryForMap(org.mockito.ArgumentMatchers.contains("SELECT id, project_id, group_name"), any(Object[].class)))
                .thenReturn(new HashMap<>(Map.of("id", 9301L, "project_id", 9001L, "group_name", "重点交付", "color_key", "success", "sort_no", 1)));

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
}
