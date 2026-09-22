package com.ccb.system.project;

import com.ccb.attachment.model.AttachmentCategory;
import com.ccb.attachment.model.AttachmentItem;
import com.ccb.attachment.model.AttachmentPort;
import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.infrastructure.storage.MinioStorageService;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.ProjectDeletionGuard;
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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"NEW", "CONTINUATION", "ABSENT"})
    void persistsProjectCreationType(String type) {
        ProjectService service = org.mockito.Mockito.spy(new ProjectService(jdbc, storage));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);
        org.mockito.Mockito.doReturn(Map.of()).when(service).detail(anyLong(), eq(admin));
        Map<String, Object> input = new HashMap<>(Map.of("project_code", "TEST-070", "project_name", "Creation type"));
        if (!"ABSENT".equals(type)) input.put("creation_type", type);

        service.create(input, admin);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc, org.mockito.Mockito.atLeastOnce()).update(sql.capture(), args.capture());
        int index = java.util.stream.IntStream.range(0, sql.getAllValues().size())
                .filter(i -> sql.getAllValues().get(i).startsWith("INSERT INTO pm_project (")).findFirst().orElseThrow();
        String insert = sql.getAllValues().get(index);
        String[] columns = insert.substring(insert.indexOf('(') + 1, insert.indexOf(')')).split(",\\s*");
        int field = java.util.Arrays.asList(columns).indexOf("creation_type");
        org.junit.jupiter.api.Assertions.assertTrue(field >= 0, "creation_type must be persisted");
        assertEquals("ABSENT".equals(type) ? "NEW" : type, args.getAllValues().get(index)[field]);
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("INSERT INTO sys_operation_log"),
                any(), eq(1L), eq(1L), eq("project:create"), any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.NullAndEmptySource
    @org.junit.jupiter.params.provider.ValueSource(strings = {" ", "INVALID", "new"})
    void rejectsInvalidCreationTypeBeforeWriting(String type) {
        ProjectService service = new ProjectService(jdbc, storage);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);
        Map<String, Object> input = new HashMap<>(Map.of("project_code", "TEST-070", "project_name", "Creation type"));
        input.put("creation_type", type);
        assertThrows(BusinessException.class, () -> service.create(input, admin));
        assertThrows(BusinessException.class, () -> service.update(9001L, input, admin));
        verify(jdbc, org.mockito.Mockito.never()).update(anyString(), any(Object[].class));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"NEW", "CONTINUATION", "ABSENT"})
    void updatesCreationTypeOnlyWhenProvided(String type) {
        ProjectService service = org.mockito.Mockito.spy(new ProjectService(jdbc, storage));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);
        when(jdbc.queryForMap(anyString(), any(Object[].class))).thenReturn(new HashMap<>(Map.of("creation_type", "CONTINUATION")));
        org.mockito.Mockito.doReturn(Map.of()).when(service).detail(anyLong(), eq(admin));
        Map<String, Object> input = new HashMap<>(Map.of("project_name", "Changed name"));
        if (!"ABSENT".equals(type)) input.put("creation_type", type);
        service.update(9001L, input, admin);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc, org.mockito.Mockito.atLeastOnce()).update(sql.capture(), args.capture());
        String update = sql.getAllValues().get(0);
        assertEquals(!"ABSENT".equals(type), update.contains("creation_type = ?"));
        if (!"ABSENT".equals(type)) assertEquals(type, args.getAllValues().get(0)[1]);
        org.junit.jupiter.api.Assertions.assertTrue(update.contains("tenant_id = ?"));
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("INSERT INTO sys_operation_log"),
                any(), eq(1L), eq(1L), eq("project:update"), eq("9001"));
    }

    @Test
    void workbenchSelectsCreationType() {
        ProjectService service = new ProjectService(jdbc, storage);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
        service.workbench(admin);
        verify(jdbc).queryForList(org.mockito.ArgumentMatchers.contains("creation_type"), eq(1L));
    }

    @Mock
    private JdbcTemplate jdbc;
    @Mock
    private MinioStorageService storage;
    @Mock
    private AttachmentPort attachmentPort;

    private final AuthUser member = new AuthUser(7L, 1L, "member", "", "Member", 1L, true);
    private final AuthUser admin = new AuthUser(1L, 1L, "admin", "", "Admin", 1L, true);

    @Test
    void readsProjectAttachmentCategoriesThroughPublicPort() {
        ProjectService service = new ProjectService(jdbc, storage, attachmentPort);
        AttachmentCategory category = new AttachmentCategory(3001L, "需求文档", 1);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);
        when(attachmentPort.listCategories("PROJECT", 9001L, admin.tenantId())).thenReturn(List.of(category));

        assertEquals(List.of(category), service.attachmentCategories(9001L, admin));
    }

    @Test
    void createsAndUpdatesProjectAttachmentCategoryThroughPublicPort() {
        ProjectService service = new ProjectService(jdbc, storage, attachmentPort);
        AttachmentCategory category = new AttachmentCategory(3001L, "需求文档", 1);
        AttachmentItem item = new AttachmentItem(4001L, "说明.pdf", "application/pdf", 12L, admin.id(), null,
                "2026-08-28 10:00:00", category.id(), category.name());
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);
        when(attachmentPort.createCategory("PROJECT", 9001L, "需求文档", admin.tenantId(), admin.id()))
                .thenReturn(category);
        when(attachmentPort.updateCategory(4001L, "PROJECT", 9001L, category.id(), admin.tenantId()))
                .thenReturn(item);

        assertEquals(category, service.createAttachmentCategory(9001L, Map.of("name", "需求文档"), admin));
        assertEquals(category.id(), service.updateAttachmentCategory(9001L, 4001L, category.id(), admin).categoryId());
    }

    @Test
    void forwardsAttachmentCategoryFilterThroughPublicPort() {
        ProjectService service = new ProjectService(jdbc, storage, attachmentPort);
        AttachmentItem item = new AttachmentItem(4001L, "说明.pdf", "application/pdf", 12L, admin.id(), null,
                "2026-08-28 10:00:00", 3001L, "需求文档");
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);
        when(attachmentPort.list(eq("PROJECT"), eq(9001L), eq(admin.tenantId()), any(PageQuery.class),
                eq("说明"), eq(3001L))).thenReturn(new PageResult<>(List.of(item), 1L, 1L, 20L));

        PageResult<AttachmentItem> result = service.attachments(9001L, 1L, 20L, "说明", 3001L, admin);

        assertEquals(1L, result.total());
        assertEquals("需求文档", result.records().get(0).categoryName());
        verify(attachmentPort).list(eq("PROJECT"), eq(9001L), eq(admin.tenantId()), any(PageQuery.class),
                eq("说明"), eq(3001L));
    }

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
    void checksDeletionGuardsBeforeDeletingAttachmentsAndProjectData() {
        ProjectService service = new ProjectService(jdbc, storage, attachmentPort);
        ProjectDeletionGuard guard = org.mockito.Mockito.mock(ProjectDeletionGuard.class);
        service.setDeletionGuards(List.of(guard));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);
        when(attachmentPort.list(eq("PROJECT"), eq(9001L), eq(admin.tenantId()), any(PageQuery.class),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(new PageResult<>(List.of(), 0L, 1L, 100L));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        service.delete(9001L, admin);

        org.mockito.InOrder order = inOrder(guard, attachmentPort, jdbc);
        order.verify(guard).requireNoReferences(admin.tenantId(), 9001L);
        order.verify(attachmentPort).list(eq("PROJECT"), eq(9001L), eq(admin.tenantId()), any(PageQuery.class),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull());
        order.verify(jdbc).update(org.mockito.ArgumentMatchers.contains("UPDATE pm_project SET deleted = 1"),
                eq(9001L), eq(admin.tenantId()));
    }

    @Test
    void keepsProjectAndAttachmentsWhenDeletionGuardRejects() {
        ProjectService service = new ProjectService(jdbc, storage, attachmentPort);
        ProjectDeletionGuard guard = (tenantId, projectId) -> {
            throw new BusinessException(com.ccb.common.exception.ErrorCode.CONFLICT, "项目仍有关联数据");
        };
        service.setDeletionGuards(List.of(guard));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);

        assertThrows(BusinessException.class, () -> service.delete(9001L, admin));

        verify(attachmentPort, never()).list(anyString(), anyLong(), anyLong(), any(PageQuery.class),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void deletesProjectWhenNoDeletionGuardIsRegistered() {
        ProjectService service = new ProjectService(jdbc, storage);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        service.delete(9001L, admin);

        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("UPDATE pm_project SET deleted = 1"),
                eq(9001L), eq(admin.tenantId()));
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
    void createsProjectStageWhenStageHasNoPlans() {
        ProjectService service = new ProjectService(jdbc, storage);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1, 1, 0, 1, 0);
        Map<String, Object> result = service.createStage(9001L, Map.of("stage_name", "新阶段"), admin);

        assertEquals("新阶段", result.get("stage_name"));
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("INSERT INTO pm_project_stage"),
                org.mockito.ArgumentMatchers.any(), eq(1L), eq(9001L), org.mockito.ArgumentMatchers.any(), eq("新阶段"), eq(0));
    }

    @Test
    void backfillsDefaultProjectStagesBeforeReadingStages() {
        ProjectService service = new ProjectService(jdbc, storage);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.contains("SELECT s.id"), eq(9001L), eq(1L)))
                .thenReturn(List.of());

        assertEquals(List.of(), service.stages(9001L, admin));

        verify(jdbc, org.mockito.Mockito.times(7)).update(
                org.mockito.ArgumentMatchers.contains("INSERT IGNORE INTO pm_project_stage"),
                org.mockito.ArgumentMatchers.any(), eq(1L), eq(9001L),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void updatesProjectStageWhenNoMainPlanExists() {
        ProjectService service = new ProjectService(jdbc, storage);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1, 1, 0, 1, 0);
        when(jdbc.queryForMap(org.mockito.ArgumentMatchers.contains("FROM pm_project_stage"), eq(9801L), eq(9001L), eq(1L)))
                .thenReturn(Map.of("id", 9801L, "project_id", 9001L, "stage_code", "CUSTOM", "stage_name", "旧名称", "sort_no", 1));
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(new HashMap<>(Map.of(
                "id", 9801L, "project_id", 9001L, "stage_code", "CUSTOM", "phase", "CUSTOM", "stage_name", "新名称",
                "sort_no", 2, "status", 1, "has_master_plans", 0))));

        Map<String, Object> result = service.updateStage(9001L, 9801L, Map.of("stage_name", "新名称", "sort_no", 2), admin);

        assertEquals("新名称", result.get("stage_name"));
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("UPDATE pm_project_stage SET stage_name = ?, sort_no = ?"),
                eq("新名称"), eq(2), eq(9801L), eq(9001L), eq(1L));
    }

    @Test
    void rejectsProjectStageUpdateWhenMainPlanExists() {
        ProjectService service = new ProjectService(jdbc, storage);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1, 1, 0, 1, 1);
        when(jdbc.queryForMap(org.mockito.ArgumentMatchers.contains("FROM pm_project_stage"), eq(9801L), eq(9001L), eq(1L)))
                .thenReturn(Map.of("id", 9801L, "project_id", 9001L, "stage_code", "CUSTOM", "stage_name", "锁定阶段", "sort_no", 1));

        assertThrows(BusinessException.class, () -> service.updateStage(9001L, 9801L, Map.of("stage_name", "不应修改"), admin));
        verify(jdbc, org.mockito.Mockito.never()).update(org.mockito.ArgumentMatchers.contains("UPDATE pm_project_stage SET"), org.mockito.ArgumentMatchers.any(Object[].class));
    }

    @Test
    void rejectsProjectStageDeleteWhenMainPlanExists() {
        ProjectService service = new ProjectService(jdbc, storage);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1, 1, 0, 1, 1);
        when(jdbc.queryForMap(org.mockito.ArgumentMatchers.contains("FROM pm_project_stage"), eq(9801L), eq(9001L), eq(1L)))
                .thenReturn(Map.of("id", 9801L, "project_id", 9001L, "stage_code", "CUSTOM", "stage_name", "锁定阶段", "sort_no", 1));

        assertThrows(BusinessException.class, () -> service.deleteStage(9001L, 9801L, admin));
        verify(jdbc, org.mockito.Mockito.never()).update(org.mockito.ArgumentMatchers.contains("UPDATE pm_project_stage SET deleted"), org.mockito.ArgumentMatchers.any(Object[].class));
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
        when(jdbc.queryForMap(org.mockito.ArgumentMatchers.contains("SELECT g.id, g.project_id, g.phase"), eq(8001L), eq(9001L), eq(1L)))
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
        when(jdbc.queryForMap(org.mockito.ArgumentMatchers.contains("SELECT g.id, g.project_id, g.phase, g.group_name"), eq(22L), eq(9101L), eq(1L)))
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
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.contains("FROM pm_project_stage"), eq(9001L), eq(1L)))
                .thenReturn(List.of(Map.of("stage_code", "PLAN_INITIATION", "stage_name", "立项", "sort_no", 0, "has_master_plans", 0)));
        when(jdbc.queryForObject(org.mockito.ArgumentMatchers.contains("MAX(CASE"), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbc.queryForMap(org.mockito.ArgumentMatchers.contains("SELECT id, project_code, plan_number_rule"), eq(9001L), eq(1L)))
                .thenReturn(Map.of("id", 9001L, "project_code", "RDC", "plan_number_rule", "{PROJECT_CODE}-P{SEQ:3}", "child_plan_number_rule", "{PARENT_CODE}-S{SEQ:3}", "next_plan_sequence", 1L));
        when(jdbc.queryForMap(org.mockito.ArgumentMatchers.contains("SELECT g.id, g.project_id, g.phase, g.group_name"), any(Object[].class)))
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
        when(jdbc.queryForMap(org.mockito.ArgumentMatchers.contains("SELECT g.id, g.project_id, g.phase, g.group_name"), any(Object[].class)))
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

    @Test
    void letsAnActiveProjectMemberCreateReleaseCalendarEntries() {
        ProjectService service = new ProjectService(jdbc, storage);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1, 0, 1, 0);
        when(jdbc.queryForMap(org.mockito.ArgumentMatchers.contains("FROM pm_project_release_calendar"), any(Object[].class)))
                .thenReturn(new HashMap<>(Map.of("id", 9701L, "project_id", 9001L, "title", "一期上线", "release_start_date", "2026-09-25", "release_end_date", "2026-09-25", "row_version", 1L)));

        Map<String, Object> result = service.createReleaseCalendar(9001L,
                Map.of("title", "一期上线", "release_start_date", "2026-09-25", "release_end_date", "2026-09-25", "remark", "夜间窗口"), member);

        assertEquals("一期上线", result.get("title"));
        verify(jdbc).queryForObject(org.mockito.ArgumentMatchers.contains("release_start_date <= ? AND release_end_date >= ?"),
                eq(Integer.class), eq(9001L), eq(member.tenantId()), eq(java.sql.Date.valueOf("2026-09-25")), eq(java.sql.Date.valueOf("2026-09-25")));
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("INSERT INTO pm_project_release_calendar"),
                any(), eq(member.tenantId()), eq(9001L), eq("一期上线"), eq(java.sql.Date.valueOf("2026-09-25")), eq(java.sql.Date.valueOf("2026-09-25")), eq(java.sql.Date.valueOf("2026-09-25")),
                eq("夜间窗口"), eq("tone-1"), eq(member.id()), eq(member.id()));
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("sys_operation_log"), any(), eq(member.tenantId()),
                eq(member.id()), eq("project:release-calendar:create"), any());
    }

    @Test
    void rejectsReleaseCalendarRangeWhenStartIsAfterEnd() {
        ProjectService service = new ProjectService(jdbc, storage);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1, 0, 1, 0);

        assertThrows(BusinessException.class, () -> service.createReleaseCalendar(9001L,
                Map.of("title", "一期上线", "release_start_date", "2026-09-26", "release_end_date", "2026-09-25"), member));

        verify(jdbc, never()).update(org.mockito.ArgumentMatchers.contains("INSERT INTO pm_project_release_calendar"), any(Object[].class));
    }

    @Test
    void allowsASecondReleaseCalendarEntryWhenItsRangeDoesNotOverlap() {
        ProjectService service = new ProjectService(jdbc, storage);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1, 0, 1, 0);
        when(jdbc.queryForMap(org.mockito.ArgumentMatchers.contains("FROM pm_project_release_calendar"), any(Object[].class)))
                .thenReturn(new HashMap<>(Map.of("id", 9702L, "project_id", 9001L, "title", "二期上线", "release_start_date", "2026-10-10", "release_end_date", "2026-10-12", "row_version", 1L)));

        Map<String, Object> result = service.createReleaseCalendar(9001L,
                Map.of("title", "二期上线", "release_start_date", "2026-10-10", "release_end_date", "2026-10-12"), member);

        assertEquals("二期上线", result.get("title"));
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("INSERT INTO pm_project_release_calendar"),
                any(), eq(member.tenantId()), eq(9001L), eq("二期上线"), eq(java.sql.Date.valueOf("2026-10-10")), eq(java.sql.Date.valueOf("2026-10-12")), eq(java.sql.Date.valueOf("2026-10-10")),
                org.mockito.ArgumentMatchers.isNull(), eq("tone-1"), eq(member.id()), eq(member.id()));
        verify(jdbc).queryForObject(org.mockito.ArgumentMatchers.contains("FROM pm_project_release_calendar"),
                eq(Integer.class), eq(9001L), eq(member.tenantId()), eq(java.sql.Date.valueOf("2026-10-12")), eq(java.sql.Date.valueOf("2026-10-10")));
    }

    @Test
    void rejectsOverlappingReleaseCalendarRange() {
        ProjectService service = new ProjectService(jdbc, storage);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1, 0, 1, 1);

        assertThrows(BusinessException.class, () -> service.createReleaseCalendar(9001L,
                Map.of("title", "重叠安排", "release_start_date", "2026-09-25", "release_end_date", "2026-09-27"), member));

        verify(jdbc, never()).update(org.mockito.ArgumentMatchers.contains("INSERT INTO pm_project_release_calendar"), any(Object[].class));
        verify(jdbc).queryForObject(org.mockito.ArgumentMatchers.contains("FROM pm_project_release_calendar"),
                eq(Integer.class), eq(9001L), eq(member.tenantId()), eq(java.sql.Date.valueOf("2026-09-27")), eq(java.sql.Date.valueOf("2026-09-25")));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"tone-1", "tone-2", "tone-3", "tone-4", "tone-5"})
    void persistsThemeInternalColorFamiliesForReleaseCalendarEntries(String themeKey) {
        ProjectService service = new ProjectService(jdbc, storage);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1, 0, 1, 0);
        when(jdbc.queryForMap(org.mockito.ArgumentMatchers.contains("FROM pm_project_release_calendar"), any(Object[].class)))
                .thenReturn(new HashMap<>(Map.of("id", 9701L, "project_id", 9001L, "title", "一期上线", "release_start_date", "2026-09-25", "release_end_date", "2026-09-25", "theme_key", themeKey, "row_version", 1L)));

        service.createReleaseCalendar(9001L,
                Map.of("title", "一期上线", "release_start_date", "2026-09-25", "release_end_date", "2026-09-25", "theme_key", themeKey), member);

        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("INSERT INTO pm_project_release_calendar"),
                any(), eq(member.tenantId()), eq(9001L), eq("一期上线"), eq(java.sql.Date.valueOf("2026-09-25")), eq(java.sql.Date.valueOf("2026-09-25")), eq(java.sql.Date.valueOf("2026-09-25")),
                org.mockito.ArgumentMatchers.isNull(), eq(themeKey), eq(member.id()), eq(member.id()));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"system", "ocean", "tech-blue", "primary", "success", "warning", "danger", "info"})
    void keepsLegacyReleaseCalendarThemeKeysReadable(String themeKey) {
        ProjectService service = new ProjectService(jdbc, storage);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1, 0, 1, 0);
        when(jdbc.queryForMap(org.mockito.ArgumentMatchers.contains("FROM pm_project_release_calendar"), any(Object[].class)))
                .thenReturn(new HashMap<>(Map.of("id", 9701L, "project_id", 9001L, "title", "一期上线", "release_start_date", "2026-09-25", "release_end_date", "2026-09-25", "theme_key", themeKey, "row_version", 1L)));

        service.createReleaseCalendar(9001L,
                Map.of("title", "一期上线", "release_start_date", "2026-09-25", "release_end_date", "2026-09-25", "theme_key", themeKey), member);

        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("INSERT INTO pm_project_release_calendar"),
                any(), eq(member.tenantId()), eq(9001L), eq("一期上线"), eq(java.sql.Date.valueOf("2026-09-25")), eq(java.sql.Date.valueOf("2026-09-25")), eq(java.sql.Date.valueOf("2026-09-25")),
                org.mockito.ArgumentMatchers.isNull(), eq(themeKey), eq(member.id()), eq(member.id()));
    }

    @Test
    void rejectsReleaseCalendarAccessForNonMembers() {
        ProjectService service = new ProjectService(jdbc, storage);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1, 0, 0);

        assertThrows(BusinessException.class, () -> service.releaseCalendar(9001L, "2026-09", member));

        verify(jdbc, never()).queryForList(org.mockito.ArgumentMatchers.contains("pm_project_release_calendar"), any(Object[].class));
        verify(jdbc, never()).update(org.mockito.ArgumentMatchers.contains("pm_project_release_calendar"), any(Object[].class));
    }

    @Test
    void filtersReleaseCalendarEntriesToTheRequestedMonth() {
        ProjectService service = new ProjectService(jdbc, storage);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1, 0, 1);
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.contains("FROM pm_project_release_calendar"), any(Object[].class))).thenReturn(List.of());

        assertEquals(List.of(), service.releaseCalendar(9001L, "2026-09", member));

        verify(jdbc).queryForList(org.mockito.ArgumentMatchers.contains("release_start_date < ? AND release_end_date >= ?"),
                eq(9001L), eq(member.tenantId()), eq(java.sql.Date.valueOf("2026-10-01")), eq(java.sql.Date.valueOf("2026-09-01")));
    }

    @Test
    void rejectsStaleOrCrossProjectReleaseCalendarUpdates() {
        ProjectService service = new ProjectService(jdbc, storage);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1, 1, 0);
        when(jdbc.update(org.mockito.ArgumentMatchers.contains("UPDATE pm_project_release_calendar"), any(Object[].class))).thenReturn(0);

        assertThrows(BusinessException.class, () -> service.updateReleaseCalendar(9001L, 9701L,
                Map.of("title", "一期上线", "release_start_date", "2026-09-25", "release_end_date", "2026-09-25", "row_version", 1), admin));

        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("id = ? AND project_id = ? AND tenant_id = ? AND row_version = ?"),
                eq("一期上线"), eq(java.sql.Date.valueOf("2026-09-25")), eq(java.sql.Date.valueOf("2026-09-25")), eq(java.sql.Date.valueOf("2026-09-25")), org.mockito.ArgumentMatchers.isNull(), eq("tone-1"),
                eq(admin.id()), eq(9701L), eq(9001L), eq(admin.tenantId()), eq(1L));
    }

    @Test
    void deletesReleaseCalendarEntriesWithProjectTenantAndVersionGuards() {
        ProjectService service = new ProjectService(jdbc, storage);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1, 1);
        when(jdbc.update(org.mockito.ArgumentMatchers.contains("UPDATE pm_project_release_calendar"), any(Object[].class))).thenReturn(1);

        service.deleteReleaseCalendar(9001L, 9701L, 3L, admin);

        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("id = ? AND project_id = ? AND tenant_id = ? AND row_version = ?"),
                eq(admin.id()), eq(9701L), eq(9001L), eq(admin.tenantId()), eq(3L));
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("sys_operation_log"), any(), eq(admin.tenantId()),
                eq(admin.id()), eq("project:release-calendar:delete"), any());
    }

    @Test
    void letsAnActiveProjectMemberCreateFixedCategoryAnnouncement() {
        ProjectService service = new ProjectService(jdbc, storage);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1, 0, 1, 1);
        when(jdbc.queryForMap(org.mockito.ArgumentMatchers.contains("FROM pm_project_announcement"), any(Object[].class)))
                .thenReturn(new HashMap<>(Map.of("id", 9801L, "project_id", 9001L, "stage_code", "REQUIREMENT", "title", "需求评审", "content_html", "<p>请按时参加</p>", "pinned", 1, "row_version", 1L)));

        Map<String, Object> result = service.createAnnouncement(9001L,
                Map.of("stage_code", "REQUIREMENT", "title", "需求评审", "content_html", "<p>请按时参加</p>", "pinned", true), member);

        assertEquals("需求评审", result.get("title"));
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("INSERT INTO pm_project_announcement"),
                any(), eq(member.tenantId()), eq(9001L), eq("REQUIREMENT"), eq("需求评审"),
                eq("<p>请按时参加</p>"), eq(1), eq(member.id()), eq(member.id()));
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("sys_operation_log"), any(), eq(member.tenantId()),
                eq(member.id()), eq("project:announcement:create"), any());
    }

    @Test
    void rejectsAnnouncementCategoryOutsideFixedSet() {
        ProjectService service = new ProjectService(jdbc, storage);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1, 0, 1);

        assertThrows(BusinessException.class, () -> service.createAnnouncement(9001L,
                Map.of("stage_code", "PLAN_REQUIREMENT", "title", "需求评审", "content_html", "<p>请按时参加</p>"), member));

        verify(jdbc, never()).update(org.mockito.ArgumentMatchers.contains("INSERT INTO pm_project_announcement"), any(Object[].class));
    }

    @Test
    void rejectsAnnouncementAccessForNonMembers() {
        ProjectService service = new ProjectService(jdbc, storage);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1, 0, 0);

        assertThrows(BusinessException.class, () -> service.announcements(9001L, null, member));

        verify(jdbc, never()).queryForList(org.mockito.ArgumentMatchers.contains("pm_project_announcement"), any(Object[].class));
    }

    @Test
    void rejectsUnsafeAnnouncementHtmlBeforeWriting() {
        ProjectService service = new ProjectService(jdbc, storage);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1, 0, 1, 1);

        assertThrows(BusinessException.class, () -> service.createAnnouncement(9001L,
                Map.of("stage_code", "REQUIREMENT", "title", "需求评审", "content_html", "<script>alert(1)</script>"), member));

        verify(jdbc, never()).update(org.mockito.ArgumentMatchers.contains("pm_project_announcement"), any(Object[].class));
    }

    @Test
    void currentAnnouncementsReturnPinnedAndLatestAnnouncementsAcrossCategories() {
        ProjectService service = new ProjectService(jdbc, storage);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1, 0, 1);
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.contains("FROM pm_project_announcement"), any(Object[].class))).thenReturn(List.of());

        assertEquals(List.of(), service.currentAnnouncements(9001L, member));

        verify(jdbc, org.mockito.Mockito.times(2)).queryForList(org.mockito.ArgumentMatchers.contains("FROM pm_project_announcement"), any(Object[].class));
        verify(jdbc).queryForList(org.mockito.ArgumentMatchers.contains("a.pinned = 1 ORDER BY a.created_at DESC, a.id DESC"), any(Object[].class));
        verify(jdbc).queryForList(org.mockito.ArgumentMatchers.contains("a.pinned = 0 ORDER BY a.created_at DESC, a.id DESC LIMIT 3"), any(Object[].class));
    }

    @Test
    void rejectsStaleAnnouncementUpdatesWithProjectTenantAndVersionGuards() {
        ProjectService service = new ProjectService(jdbc, storage);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1, 1, 1);
        when(jdbc.update(org.mockito.ArgumentMatchers.contains("UPDATE pm_project_announcement"), any(Object[].class))).thenReturn(0);

        assertThrows(BusinessException.class, () -> service.updateAnnouncement(9001L, 9801L,
                Map.of("stage_code", "REQUIREMENT", "title", "需求评审", "content_html", "<p>请按时参加</p>", "row_version", 1), admin));

        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("id = ? AND project_id = ? AND tenant_id = ? AND row_version = ?"),
                eq("REQUIREMENT"), eq("需求评审"), eq("<p>请按时参加</p>"), eq(0), eq(admin.id()),
                eq(9801L), eq(9001L), eq(admin.tenantId()), eq(1L));
    }
}
