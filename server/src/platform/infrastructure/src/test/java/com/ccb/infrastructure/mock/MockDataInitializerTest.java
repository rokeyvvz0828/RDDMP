package com.ccb.infrastructure.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
                    "rows": [{"id": 910000000000999, "tenant_id": 1, "event_id": "MOCK_TEST", "business_type": "test", "business_key": "MOCK-TEST", "title": "测试", "content": "内容", "notification_level": "INFO", "source_name": "测试", "action_path": null, "created_by": 1}]
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
}
