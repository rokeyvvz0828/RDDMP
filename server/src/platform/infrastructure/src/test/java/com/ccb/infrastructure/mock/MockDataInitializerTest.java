package com.ccb.infrastructure.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MockDataInitializerTest {
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
    void validatesAndUpsertsTrustedArchitectureRows() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L);
        when(jdbc.queryForObject(contains("SELECT MAX(org_name)"), eq(String.class), any(Object[].class)))
                .thenReturn("研发工程中心");
        MockDataInitializer initializer = initializer(jdbc, """
                {"datasetKey":"test","datasetVersion":"1","database":[
                  {"table":"arch_logical_subsystem","keyColumns":["id"],"rows":[
                    {"id":9101,"tenant_id":1,"code":"LOGICAL_DEMO","short_name":"逻辑演示","name":"逻辑演示系统","business_org_id":910000000000002,"contact_user_id":910000000000002,"deleted":0,"created_by":1,"updated_by":1}
                  ]},
                  {"table":"arch_physical_subsystem","keyColumns":["id"],"rows":[
                    {"id":9201,"tenant_id":1,"code":"PHYSICAL_DEMO","short_name":"物理演示","name":"物理演示系统","logical_subsystem_id":9101,"responsible_team_org_id":910000000000002,"responsible_team_name_snapshot":"研发工程中心","owner_user_id":910000000000002,"deleted":0,"created_by":1,"updated_by":1}
                  ]}
                ]}
                """);

        initializer.run(new DefaultApplicationArguments());
        initializer.run(new DefaultApplicationArguments());

        verify(jdbc, atLeast(6)).update(anyString(), any(Object[].class));
    }

    @Test
    void rejectsArchitectureRowWithoutExplicitPositiveTenant() throws Exception {
        MockDataInitializer initializer = initializer(mock(JdbcTemplate.class), """
                {"datasetKey":"test","datasetVersion":"1","database":[{
                  "table":"arch_logical_subsystem","keyColumns":["id"],"rows":[{"id":9101}]
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
        MockDataInitializer initializer = initializer(jdbc, logicalRow());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> initializer.run(new DefaultApplicationArguments()));
        assertTrue(error.getMessage().contains("tenant 不存在活动根组织"));
    }

    @Test
    void rejectsCrossTenantLogicalReference() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(contains("parent_id = 0"), eq(Long.class), any(Object[].class))).thenReturn(1L);
        when(jdbc.queryForObject(contains("arch_logical_subsystem"), eq(Long.class), any(Object[].class))).thenReturn(0L);
        MockDataInitializer initializer = initializer(jdbc, physicalRow("研发工程中心"));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> initializer.run(new DefaultApplicationArguments()));
        assertTrue(error.getMessage().contains("所属逻辑子系统不是当前租户活动引用"));
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
                  "table":"arch_logical_subsystem","keyColumns":["id"],"rows":[{
                    "id":9101,"tenant_id":1,"business_org_id":9201,"contact_user_id":9301,
                    "deployment_platform_code":"wrong.category.option"
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
        JsonNode logical = table(root, "arch_logical_subsystem");
        JsonNode physical = table(root, "arch_physical_subsystem");
        assertEquals(2, logical.path("rows").size());
        assertEquals(3, physical.path("rows").size());
        logical.path("rows").forEach(row -> assertTrue(row.path("tenant_id").asLong() > 0));
        physical.path("rows").forEach(row -> {
            assertTrue(row.path("tenant_id").asLong() > 0);
            assertTrue(row.path("responsible_team_name_snapshot").isTextual());
            assertTrue(row.path("contact_user_id").isMissingNode());
            assertEquals("虚构演示数据", row.path("remark").asText());
        });

        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L);
        when(jdbc.queryForObject(contains("SELECT MAX(org_name)"), eq(String.class), any(Object[].class)))
                .thenReturn("产品交付中心", "研发工程中心", "研发工程中心", "研发工程中心", "质量保障中心");
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

    private String logicalRow() {
        return """
                {"datasetKey":"test","datasetVersion":"1","database":[{
                  "table":"arch_logical_subsystem","keyColumns":["id"],"rows":[{
                    "id":9101,"tenant_id":2,"business_org_id":9201,"contact_user_id":9301
                  }]
                }]}
                """;
    }

    private String physicalRow(String snapshot) {
        return """
                {"datasetKey":"test","datasetVersion":"1","database":[{
                  "table":"arch_physical_subsystem","keyColumns":["id"],"rows":[{
                    "id":9201,"tenant_id":1,"logical_subsystem_id":9101,"responsible_team_org_id":910000000000002,
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
