package com.ccb.infrastructure.mock;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MockDataInitializerTest {
    @Test
    void bundledDevelopmentTasksKeepPhysicalSystemInTheirOwnProject() throws Exception {
        JsonNode root;
        try (InputStream input = new ClassPathResource("mock/mock-data.json").getInputStream()) {
            root = new ObjectMapper().readTree(input);
        }
        var systems = new java.util.HashMap<Long, JsonNode>();
        var tasks = new java.util.ArrayList<JsonNode>();
        for (var entry : root.path("database")) {
            for (var row : entry.path("rows")) {
                if ("arch_physical_subsystem".equals(entry.path("table").asText())) systems.put(row.path("id").asLong(), row);
                if ("dev_task".equals(entry.path("table").asText())) tasks.add(row);
            }
        }
        assertEquals(3, tasks.size());
        for (var task : tasks) {
            var system = systems.get(task.path("system_id").asLong());
            assertTrue(system != null, "开发示例引用的物理系统必须存在");
            assertEquals(task.path("project_id").asLong(), system.path("project_id").asLong(), task.path("task_no").asText());
        }
    }

    @Test
    void upsertsAllowlistedRowsAndRecordsDatasetState() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ResourceLoader resources = mock(ResourceLoader.class);
        Resource resource = mock(Resource.class);
        when(resources.getResource("classpath:mock/mock-data.json")).thenReturn(resource);
        when(resource.getInputStream()).thenReturn(new ByteArrayInputStream("""
                {
                  "datasetKey": "test",
                  "datasetVersion": "1",
                  "database": [{
                    "table": "sys_notification",
                    "keyColumns": ["id"],
                    "rows": [{"id": 910000000000999, "tenant_id": 1, "event_id": "MOCK_TEST", "module_code": "test", "module_name": "测试", "business_type": "test", "business_key": "MOCK-TEST", "title": "测试", "content": "内容", "notification_level": "INFO", "source_name": "测试", "action_path": null, "created_by": 1}]
                  }]
                }
                """.getBytes(StandardCharsets.UTF_8)));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        MockDataProperties properties = new MockDataProperties();
        properties.setEnabled(true);
        properties.setResource("classpath:mock/mock-data.json");
        new MockDataInitializer(jdbc, new ObjectMapper(), resources, properties).run(new DefaultApplicationArguments());

        verify(jdbc, atLeast(2)).update(anyString(), any(Object[].class));
    }

    @Test
    void rejectsTablesOutsideTheAllowlist() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ResourceLoader resources = mock(ResourceLoader.class);
        Resource resource = mock(Resource.class);
        when(resources.getResource("classpath:mock/mock-data.json")).thenReturn(resource);
        when(resource.getInputStream()).thenReturn(new ByteArrayInputStream("""
                {"datasetKey":"test","datasetVersion":"1","database":[{"table":"sys_user;drop","keyColumns":["id"],"rows":[]}]}
                """.getBytes(StandardCharsets.UTF_8)));

        MockDataProperties properties = new MockDataProperties();
        properties.setResource("classpath:mock/mock-data.json");
        MockDataInitializer initializer = new MockDataInitializer(jdbc, new ObjectMapper(), resources, properties);

        assertThrows(IllegalStateException.class, () -> initializer.run(new DefaultApplicationArguments()));
    }

    @Test
    void upsertsBusinessFormMetadataRows() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ResourceLoader resources = mock(ResourceLoader.class);
        Resource resource = mock(Resource.class);
        when(resources.getResource("classpath:mock/mock-data.json")).thenReturn(resource);
        when(resource.getInputStream()).thenReturn(new ByteArrayInputStream("""
                {
                  "datasetKey": "test",
                  "datasetVersion": "1",
                  "database": [{
                    "table": "biz_form_scope",
                    "keyColumns": ["id"],
                    "rows": [{"id": 910000000009001, "tenant_id": 1, "scope_key": "delivery.work-order", "scope_name": "交付工单", "module_key": "delivery", "entity_type": "work_order", "form_key": "default", "permission_prefix": "delivery:work-order", "published_revision_id": null, "enabled": 1, "deleted": 0, "created_by": 1, "updated_by": 1}]
                  }]
                }
                """.getBytes(StandardCharsets.UTF_8)));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        MockDataProperties properties = new MockDataProperties();
        properties.setResource("classpath:mock/mock-data.json");
        new MockDataInitializer(jdbc, new ObjectMapper(), resources, properties).run(new DefaultApplicationArguments());

        verify(jdbc, atLeast(2)).update(anyString(), any(Object[].class));
    }

    @Test
    void preservesWorkflowDefinitionSchemaVersionDuringMockSync() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ResourceLoader resources = mock(ResourceLoader.class);
        Resource resource = mock(Resource.class);
        when(resources.getResource("classpath:mock/mock-data.json")).thenReturn(resource);
        when(resource.getInputStream()).thenReturn(new ByteArrayInputStream("""
                {
                  "datasetKey": "test",
                  "datasetVersion": "1",
                  "database": [{
                    "table": "wf_definition",
                    "keyColumns": ["id"],
                    "rows": [{"id": 31, "tenant_id": 1, "code": "release_review", "name": "版本审批", "status": "PUBLISHED", "current_version": 1, "model_schema_version": 2, "deleted": 0}]
                  }]
                }
                """.getBytes(StandardCharsets.UTF_8)));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        MockDataProperties properties = new MockDataProperties();
        properties.setResource("classpath:mock/mock-data.json");
        new MockDataInitializer(jdbc, new ObjectMapper(), resources, properties).run(new DefaultApplicationArguments());

        verify(jdbc).update(contains("`model_schema_version`"), any(Object[].class));
    }

    @Test
    void repeatedlyUpsertsDevelopmentRowsWithStableKeysAndSnapshots() throws Exception {
        assertRepeatedUpserts(developmentTables());
    }

    @Test
    void repeatedlyUpsertsDevelopmentSourceRowsWithProjectAssociation() throws Exception {
        assertRepeatedUpserts(developmentSourceTables());
    }

    @Test
    void rejectsUnknownColumnsInDevelopmentAndSourceTablesBeforeWriting() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        for (String tables : List.of(developmentTables(), developmentSourceTables())) {
            for (JsonNode table : mapper.readTree(tables)) {
                ((ObjectNode) table.path("rows").get(0))
                        .put("unknown_mock_column", "不允许的字段");
                JdbcTemplate jdbc = mock(JdbcTemplate.class);
                MockDataInitializer initializer = initializer(jdbc, """
                        {"datasetKey":"test","datasetVersion":"1","database":[%s]}
                        """.formatted(table));

                IllegalStateException error = assertThrows(IllegalStateException.class,
                        () -> initializer.run(new DefaultApplicationArguments()));

                assertEquals("mock column is not allowlisted: " + table.path("table").asText()
                        + ".unknown_mock_column", error.getMessage());
                verifyNoInteractions(jdbc);
            }
        }
    }

    @Test
    void validatesAndUpsertsTrustedArchitectureRows() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L);
        when(jdbc.queryForObject(contains("SELECT MAX(org_name)"), eq(String.class), any(Object[].class)))
                .thenReturn("研发工程中心");
        MockDataInitializer initializer = initializer(jdbc, """
                {"datasetKey":"test","datasetVersion":"1","database":[
                  {"table":"arch_physical_subsystem","keyColumns":["id"],"rows":[
                    {"id":9201,"tenant_id":1,"code":"PHYSICAL_DEMO","short_name":"物理演示","name":"物理演示系统","logical_subsystem_name":"逻辑演示系统","business_component_code":"architecture.business-component.employee-portal","responsible_team_org_id":910000000000002,"responsible_team_name_snapshot":"研发工程中心","owner_user_id":910000000000002,"deleted":0,"created_by":1,"updated_by":1}
                  ]}
                ]}
                """);

        initializer.run(new DefaultApplicationArguments());
        initializer.run(new DefaultApplicationArguments());

        verify(jdbc, atLeast(4)).update(anyString(), any(Object[].class));
    }

    @Test
    void rejectsArchitectureRowWithoutExplicitPositiveTenant() throws Exception {
        MockDataInitializer initializer = initializer(mock(JdbcTemplate.class), """
                {"datasetKey":"test","datasetVersion":"1","database":[{
                  "table":"arch_physical_subsystem","keyColumns":["id"],"rows":[{"id":9101}]
                }]}
                """);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> initializer.run(new DefaultApplicationArguments()));
        assertTrue(error.getMessage().contains("explicit positive tenant_id"));
    }

    @Test
    void rejectsArchitectureTenantWithoutRootOrganization() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        MockDataInitializer initializer = initializer(jdbc, physicalRow("研发工程中心"));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> initializer.run(new DefaultApplicationArguments()));
        assertTrue(error.getMessage().contains("tenant 不存在活动根组织"));
    }

    @Test
    void rejectsBusinessComponentOutsideTenantCategory() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(contains("parent_id = 0"), eq(Long.class), any(Object[].class))).thenReturn(1L);
        when(jdbc.queryForObject(contains("sys_config c"), eq(Long.class), any(Object[].class))).thenReturn(0L);
        MockDataInitializer initializer = initializer(jdbc, """
                {"datasetKey":"test","datasetVersion":"1","database":[{
                  "table":"arch_physical_subsystem","keyColumns":["id"],"rows":[{
                    "id":9201,"tenant_id":1,"logical_subsystem_name":"逻辑演示系统",
                    "business_component_code":"wrong.category.option",
                    "responsible_team_org_id":910000000000002,
                    "responsible_team_name_snapshot":"研发工程中心","owner_user_id":null
                  }]
                }]}
                """);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> initializer.run(new DefaultApplicationArguments()));
        assertTrue(error.getMessage().contains("不是当前租户分类选项"));
    }

    @Test
    void rejectsResponsibleTeamSnapshotMismatch() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L);
        when(jdbc.queryForObject(contains("SELECT MAX(org_name)"), eq(String.class), any(Object[].class)))
                .thenReturn("研发工程中心");
        MockDataInitializer initializer = initializer(jdbc, physicalRow("错误团队"));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> initializer.run(new DefaultApplicationArguments()));
        assertTrue(error.getMessage().contains("名称快照与当前组织不一致"));
    }

    @Test
    void rejectsParameterOutsideTenantCategory() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L);
        when(jdbc.queryForObject(contains("sys_config c"), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbc.queryForObject(contains("SELECT MAX(org_name)"), eq(String.class), any(Object[].class)))
                .thenReturn("产品交付中心");
        MockDataInitializer initializer = initializer(jdbc, """
                {"datasetKey":"test","datasetVersion":"1","database":[{
                  "table":"arch_physical_subsystem","keyColumns":["id"],"rows":[{
                    "id":9201,"tenant_id":1,"logical_subsystem_name":"逻辑演示系统",
                    "deployment_platform":"wrong.category.option",
                    "responsible_team_org_id":910000000000002,
                    "responsible_team_name_snapshot":"产品交付中心","owner_user_id":null
                  }]
                }]}
                """);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> initializer.run(new DefaultApplicationArguments()));
        assertTrue(error.getMessage().contains("不是当前租户分类选项"));
    }

    @Test
    void isRestrictedToLocalEnabledProfileAndSingleTransaction() throws Exception {
        Profile profile = MockDataInitializer.class.getAnnotation(Profile.class);
        ConditionalOnProperty property = MockDataInitializer.class.getAnnotation(ConditionalOnProperty.class);
        assertEquals(List.of("local"), List.of(profile.value()));
        assertEquals("ccb.mock-data", property.prefix());
        assertEquals(List.of("enabled"), List.of(property.name()));
        assertEquals("true", property.havingValue());
        assertTrue(MockDataInitializer.class.getMethod("run", org.springframework.boot.ApplicationArguments.class)
                .isAnnotationPresent(Transactional.class));
    }

    @Test
    void bundledDatasetContainsValidatedFictionalArchitectureRows() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode root;
        try (InputStream input = new ClassPathResource("mock/mock-data.json").getInputStream()) {
            root = objectMapper.readTree(input);
        }
        JsonNode physical = table(root, "arch_physical_subsystem");
        assertEquals(3, physical.path("rows").size());
        physical.path("rows").forEach(row -> {
            assertTrue(row.path("tenant_id").asLong() > 0);
            assertTrue(row.path("project_id").asLong() > 0);
            assertTrue(row.path("logical_subsystem_name").isTextual());
            assertTrue(row.path("business_component_code").isTextual());
            assertTrue(row.path("responsible_team_name_snapshot").isTextual());
            assertTrue(row.path("contact_user_id").isMissingNode());
            assertEquals("虚构演示数据", row.path("remark").asText());
        });

        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L);
        when(jdbc.queryForObject(contains("SELECT MAX(org_name)"), eq(String.class), any(Object[].class)))
                .thenReturn("研发工程中心", "研发工程中心", "质量保障中心");
        MockDataProperties properties = new MockDataProperties();
        properties.setResource("classpath:mock/mock-data.json");
        new MockDataInitializer(jdbc, objectMapper, new DefaultResourceLoader(), properties)
                .run(new DefaultApplicationArguments());
        verify(jdbc, atLeast(1)).update(anyString(), any(Object[].class));
    }

    private MockDataInitializer initializer(JdbcTemplate jdbc, String json) throws Exception {
        ResourceLoader resources = mock(ResourceLoader.class);
        Resource resource = mock(Resource.class);
        when(resources.getResource("classpath:mock/mock-data.json")).thenReturn(resource);
        when(resource.getInputStream()).thenAnswer(ignored ->
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        MockDataProperties properties = new MockDataProperties();
        properties.setResource("classpath:mock/mock-data.json");
        return new MockDataInitializer(jdbc, new ObjectMapper(), resources, properties);
    }

    private void assertRepeatedUpserts(String tablesJson) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode tables = mapper.readTree(tablesJson);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        MockDataInitializer initializer = initializer(jdbc, """
                {"datasetKey":"test","datasetVersion":"1","database":%s}
                """.formatted(tablesJson));

        initializer.run(new DefaultApplicationArguments());
        initializer.run(new DefaultApplicationArguments());

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> valuesCaptor = ArgumentCaptor.forClass(Object[].class);
        int writesPerRun = tables.size() + 1;
        verify(jdbc, times(writesPerRun * 2)).update(sqlCaptor.capture(), valuesCaptor.capture());
        for (int index = 0; index < writesPerRun; index++) {
            String sql = sqlCaptor.getAllValues().get(index);
            Object[] values = valuesCaptor.getAllValues().get(index);
            assertEquals(sql, sqlCaptor.getAllValues().get(index + writesPerRun));
            assertArrayEquals(values, valuesCaptor.getAllValues().get(index + writesPerRun));
            assertTrue(sql.contains("ON DUPLICATE KEY UPDATE"));
            if (index == tables.size()) {
                assertTrue(sql.contains("INSERT INTO sys_mock_dataset_state"));
                continue;
            }
            JsonNode table = tables.get(index);
            JsonNode row = table.path("rows").get(0);
            assertTrue(sql.startsWith("INSERT INTO `" + table.path("table").asText() + "` ("));
            assertEquals(row.size(), values.length);
            List<String> keys = mapper.convertValue(table.path("keyColumns"),
                    new TypeReference<List<String>>() {});
            String updateSql = sql.substring(sql.indexOf("ON DUPLICATE KEY UPDATE"));
            row.fieldNames().forEachRemaining(column -> {
                String assignment = "`" + column + "` = VALUES(`" + column + "`)";
                if (keys.contains(column)) assertFalse(updateSql.contains(assignment));
                else assertTrue(updateSql.contains(assignment), column);
            });
            List<String> columns = new ArrayList<>(keys);
            row.fieldNames().forEachRemaining(column -> {
                if (!keys.contains(column)) columns.add(column);
            });
            for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
                JsonNode expected = row.get(columns.get(columnIndex));
                Object actual = values[columnIndex];
                if (expected.isContainerNode()) assertEquals(expected, mapper.readTree((String) actual));
                else if (expected.isIntegralNumber()) assertEquals(expected.longValue(), actual);
                else assertEquals(expected, mapper.valueToTree(actual));
            }
        }
    }

    private String developmentTables() {
        return """
                [
                  {"table":"dev_task_number_sequence","keyColumns":["tenant_id","sequence_key"],"rows":[
                    {"tenant_id":1,"sequence_key":"MOCK-DEV","next_value":2}
                  ]},
                  {"table":"dev_task","keyColumns":["id"],"rows":[
                    {"id":9301,"tenant_id":1,"project_id":9401,"project_ref":"MOCK-PROJECT","task_no":"MOCK-DEV-001",
                     "source_mode":"LINKED","source_type":"LEGACY","source_requirement_id":9501,"source_number":"MOCK-REQ-001",
                     "source_revision":"mock-revision","source_roles":["LEAD","CHANGE"],"source_system_codes":["MOCK-SYSTEM"],
                     "system_id":9601,"owner_id":9701,"title":"虚构开发任务","description":"虚构演示数据","status":"IN_PROGRESS",
                     "row_version":1,"development_plan_start":"2026-09-08","development_plan_end":"2026-09-10",
                     "test_plan_start":"2026-09-11","test_plan_end":"2026-09-12","request_id":"mock-request-001",
                     "request_hash":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","status_before_cancel":null,
                     "created_by":9701,"updated_by":9701,"created_at":"2026-09-08 09:00:00.000","updated_at":"2026-09-08 10:00:00.000"}
                  ]},
                  {"table":"dev_task_source_binding","keyColumns":["tenant_id","source_type","source_requirement_id","source_system_code"],"rows":[
                    {"tenant_id":1,"source_type":"LEGACY","source_requirement_id":9501,"source_system_code":"MOCK-SYSTEM","task_id":9301,"system_id":9601}
                  ]},
                  {"table":"dev_work_item","keyColumns":["id"],"rows":[
                    {"id":9302,"tenant_id":1,"task_id":9301,"title":"虚构工作项","description":"虚构演示数据","assignee_id":9701,
                     "status":"IN_PROGRESS","planned_start":"2026-09-08","planned_end":"2026-09-10","actual_start":"2026-09-08","actual_end":null,
                     "blocked":true,"block_reason":"虚构依赖待确认","row_version":1,"created_by":9701,"updated_by":9701,
                     "created_at":"2026-09-08 09:00:00.000","updated_at":"2026-09-08 10:00:00.000"}
                  ]},
                  {"table":"dev_task_change","keyColumns":["id"],"rows":[
                    {"id":9303,"tenant_id":1,"task_id":9301,"object_type":"TASK","object_id":9301,"action":"UPDATE",
                     "before_json":{"status":"NOT_STARTED"},"after_json":{"status":"IN_PROGRESS"},"actor_id":9701,
                     "actor_name":"虚构开发人员","trace_id":"mock-trace-001","created_at":"2026-09-08 10:00:00.000"}
                  ]},
                  {"table":"dev_task_stage","keyColumns":["tenant_id","task_id"],"rows":[
                    {"tenant_id":1,"task_id":9301,"design_plan_start":"2026-09-08","design_plan_end":"2026-09-09",
                     "design_document_path":"mock/design.md","implementation_actual_start":"2026-09-08","implementation_actual_end":null,
                     "not_applicable_design":false,"not_applicable_implementation":false,"design_registered_at":"2026-09-08 09:00:00.000",
                     "test_registered_at":null,"row_version":1,"updated_by":9701,"updated_at":"2026-09-08 10:00:00.000"}
                  ]},
                  {"table":"dev_stage_attachment_ref","keyColumns":["id"],"rows":[
                    {"id":9304,"tenant_id":1,"task_id":9301,"stage_version":1,"kind":"DESIGN","attachment_id":9801,
                     "created_by":9701,"created_at":"2026-09-08 09:00:00.000"}
                  ]},
                  {"table":"dev_calendar_snapshot","keyColumns":["id"],"rows":[
                    {"id":9305,"tenant_id":1,"task_id":9301,"object_type":"WORK_ITEM","object_id":9302,"object_version":1,
                     "calendar_version":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                     "calendar_json":{"weekdays":[1,2,3,4,5],"workingDates":[],"restDates":[]},"created_by":9701,"created_at":"2026-09-08 10:00:00.000"}
                  ]}
                ]
                """;
    }

    private String developmentSourceTables() {
        return """
                [
                  {"table":"req_legacy_requirement","keyColumns":["id"],"rows":[
                    {"id":9501,"tenant_id":1,"project_id":9401,"requirement_no":"MOCK-REQ-001","requirement_name":"虚构来源需求",
                     "created_by":9701,"deleted":0}
                  ]},
                  {"table":"req_legacy_system_item","keyColumns":["id"],"rows":[
                    {"id":9502,"tenant_id":1,"requirement_id":9501,"system_role":"主责","system_code":"MOCK-SYSTEM",
                     "system_name":"虚构系统","owner_user_id":9701,"owner_user_name":"虚构开发人员","remark":"虚构演示数据",
                     "created_by":9701,"created_at":"2026-09-08 09:00:00","updated_by":9701,"updated_at":"2026-09-08 10:00:00","deleted":0}
                  ]},
                  {"table":"req_coordination_item","keyColumns":["id"],"rows":[
                    {"id":9503,"tenant_id":1,"requirement_id":9501,"system_item_id":9502,"item_type":"改造","system_code":"MOCK-SYSTEM",
                     "system_name":"虚构系统","owner_user_id":9701,"owner_user_name":"虚构开发人员","start_date":"2026-09-08",
                     "end_date":"2026-09-10","status":"未开始","description":"虚构演示数据","created_by":9701,
                     "created_at":"2026-09-08 09:00:00","updated_by":9701,"updated_at":"2026-09-08 10:00:00","deleted":0}
                  ]}
                ]
                """;
    }

    private String physicalRow(String snapshot) {
        return """
                {"datasetKey":"test","datasetVersion":"1","database":[{
                  "table":"arch_physical_subsystem","keyColumns":["id"],"rows":[{
                    "id":9201,"tenant_id":1,"logical_subsystem_name":"逻辑演示系统",
                    "responsible_team_org_id":910000000000002,
                    "responsible_team_name_snapshot":"%s","owner_user_id":null
                  }]
                }]}
                """.formatted(snapshot);
    }

    private JsonNode table(JsonNode root, String tableName) {
        for (JsonNode table : root.path("database")) {
            if (tableName.equals(table.path("table").asText())) return table;
        }
        throw new AssertionError("missing mock table: " + tableName);
    }
}
