package com.ccb.boot.development;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class DevelopmentJourneyIntegrationTest extends DevelopmentIntegrationSupport {
    @Autowired Flyway flyway;
    @Autowired PlatformTransactionManager transactions;
    @Autowired @Qualifier("mockDataInitializer") ApplicationRunner mockInitializer;

    @Test
    void developmentJourneyUsesBoundRealFileManualCompletionAndFrozenCalendar() throws Exception {
        var task = create("owner", linked(91007130001L, SYSTEM_A).put("ownerId", 91007102));
        var item = item(task, 91007103);
        item = itemAction("executor", item, "START");
        item = itemAction("executor", item, "SUBMIT");
        item = itemAction("manager", item, "RETURN");
        assertEquals("IN_PROGRESS", item.path("status").asText());
        item = itemAction("executor", item, "SUBMIT");
        itemAction("manager", item, "ACCEPT");
        task = request(token("manager"), get(taskUrl(task)), 200);
        assertEquals("NOT_STARTED", task.path("status").asText());
        request(token("manager"), post(taskUrl(task) + "/actions").content(action(task, "COMPLETE")), 409);

        byte[] content = "虚构代码走查记录：范围已复核，无真实业务数据。".getBytes(StandardCharsets.UTF_8);
        var response = mvc.perform(multipart("/api/attachments").file(new MockMultipartFile("file", "walk.txt", "text/plain", content))
                .header("Authorization", token("manager"))).andReturn().getResponse();
        assertEquals(200, response.getStatus(), response.getContentAsString());
        var file = mapper.readTree(response.getContentAsByteArray()).path("data");
        long attachmentId = file.path("id").asLong();
        assertEquals("TEMP", file.path("status").asText());
        var stageInput = stage().put("implementationActualStart", "2026-09-07").put("implementationActualEnd", "2026-09-11");
        stageInput.putArray("codeWalkAttachmentIds").add(attachmentId);
        var stages = request(token("manager"), put(taskUrl(task) + "/stages").content(json(stageInput)), 200);
        assertEquals(1, stages.path("codeWalkAttachments").size());
        assertEquals(0, stages.path("testReportAttachments").size());
        assertEquals("BOUND", request(token("manager"), get("/api/attachments/" + attachmentId), 200).path("status").asText());
        request(token("outsider"), get("/api/attachments/" + attachmentId + "/download"), 403);
        jdbc.update("UPDATE att_file SET tenant_id=2 WHERE id=?", attachmentId);
        try {
            request(token("manager"), get("/api/attachments/" + attachmentId), 400);
        } finally {
            jdbc.update("UPDATE att_file SET tenant_id=1 WHERE id=?", attachmentId);
        }
        request(token("manager"), delete("/api/attachments/" + attachmentId), 403);
        var other = create("owner", standalone().put("ownerId", 91007102));
        request(token("manager"), put(taskUrl(other) + "/stages").content(json(stageInput)), 409);
        var download = request(token("executor"), get("/api/attachments/" + attachmentId + "/download"), 200);
        {
            var http = HttpClient.newHttpClient();
            var bytes = http.send(HttpRequest.newBuilder(URI.create(download.path("downloadUrl").asText())).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, bytes.statusCode());
            assertArrayEquals(content, bytes.body());
        }
        task = request(token("manager"), get(taskUrl(task)), 200);
        task = taskAction("manager", task, "COMPLETE");
        assertEquals("COMPLETED", task.path("status").asText());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM dev_calendar_snapshot WHERE task_id=?", Integer.class, task.path("id").asLong()));
        var frozen = request(token("manager"), get(taskUrl(task) + "/stages"), 200);
        assertTrue(frozen.path("readOnly").asBoolean());
        assertEquals(5, frozen.path("duration").path("plannedDays").asInt());
        assertEquals(5, frozen.path("duration").path("actualDays").asInt());
        String oldCalendar = jdbc.queryForObject("SELECT config_value FROM sys_config WHERE tenant_id=1 AND category_id=910714 AND config_key='DEFAULT'", String.class);
        try {
            jdbc.update("UPDATE sys_config SET config_value=? WHERE tenant_id=1 AND category_id=910714 AND config_key='DEFAULT'",
                    "{\"weekdays\":[1,2,3,4,5],\"workingDates\":[],\"restDates\":[\"2026-09-08\"]}");
            assertEquals(frozen.path("duration"), request(token("manager"), get(taskUrl(task) + "/stages"), 200).path("duration"));
        } finally {
            jdbc.update("UPDATE sys_config SET config_value=? WHERE tenant_id=1 AND category_id=910714 AND config_key='DEFAULT'", oldCalendar);
        }
        request(token("manager"), put(taskUrl(task) + "/stages").content(json(stageInput.put("rowVersion", 1))), 409);
        task = taskAction("manager", task, "REOPEN");
        assertEquals("IN_PROGRESS", task.path("status").asText());
    }

    @Test
    void pureTestCanCompleteWithZeroItemsAndNoReportButMixedAndStandaloneCannot() throws Exception {
        var pure = create("owner", linked(91007130001L, SYSTEM_B));
        var stages = request(token("owner"), put(taskUrl(pure) + "/stages")
                .content(json(stage().put("notApplicableDesign", true).put("notApplicableImplementation", true))), 200);
        assertTrue(stages.path("pureTest").asBoolean());
        pure = request(token("owner"), get(taskUrl(pure)), 200);
        assertEquals("COMPLETED", taskAction("owner", pure, "COMPLETE").path("status").asText());
        for (var task : List.of(create("owner", linked(91007130002L, SYSTEM_A)), create("owner", standalone()))) {
            request(token("owner"), put(taskUrl(task) + "/stages").content(json(stage().put("notApplicableImplementation", true))), 400);
            request(token("owner"), post(taskUrl(task) + "/actions").content(action(task, "COMPLETE")), 409);
        }
    }

    @Test
    void currentRolesAndSystemOwnershipAreRecheckedWithoutReassigningExistingItems() throws Exception {
        var pure = create("owner", linked(91007130001L, SYSTEM_B));
        request(token("owner"), put(taskUrl(pure) + "/stages")
                .content(json(stage().put("notApplicableDesign", true).put("notApplicableImplementation", true))), 200);
        jdbc.update("INSERT INTO req_legacy_system_item(id,tenant_id,requirement_id,system_role,system_code,system_name,created_by,deleted) VALUES(91007140003,1,91007130001,'主责','DEV071-SOURCE-1','虚构角色变更',1,0)");
        pure = request(token("owner"), get(taskUrl(pure)), 200);
        request(token("owner"), post(taskUrl(pure) + "/actions").content(action(pure, "COMPLETE")), 409);
        var corrected = request(token("owner"), put(taskUrl(pure) + "/stages").content(json(stage().put("rowVersion", 1))), 200);
        assertFalse(corrected.path("pureTest").asBoolean());

        var task = create("owner", standalone().put("ownerId", 91007102));
        var existing = item(task, 91007103);
        task = request(token("owner"), get(taskUrl(task)), 200);
        request(token("owner"), put(taskUrl(task)).content(json(mapper.createObjectNode().put("title", "虚构转交任务")
                .put("ownerId", 91007104).put("rowVersion", task.path("rowVersion").asLong()))), 200);
        assertEquals("91007103", request(token("owner"), get(itemUrl(existing)), 200).path("assignee").path("id").asText());
        jdbc.update("UPDATE arch_physical_subsystem SET owner_user_id=91007105 WHERE id=?", SYSTEM_A);
        request(token("owner"), get(taskUrl(task)), 403);
        assertEquals("91007104", request(token("outsider"), get(taskUrl(task)), 200).path("owner").path("id").asText());
    }

    @Test
    void freshSchemaAndRepeatMigrationHaveNoDuplicateSeeds() {
        assertEquals("201", flyway.info().current().getVersion().toString());
        var before = jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1", Integer.class);
        assertEquals(MIGRATION_BASELINE.size() + 3, before);
        assertEquals(MIGRATION_BASELINE, jdbc.queryForList("SELECT version,script,checksum FROM flyway_schema_history WHERE installed_rank<=? ORDER BY installed_rank", MIGRATION_BASELINE.size()));
        assertEquals(0, flyway.migrate().migrationsExecuted);
        assertEquals(before, jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1", Integer.class));
        assertEquals(7, jdbc.queryForObject("SELECT COUNT(*) FROM sys_menu_permission WHERE tenant_id=1 AND id BETWEEN 9107111 AND 9107117", Integer.class));
    }

    @Test
    void bundledMockDataCanBeAppliedTwiceToRealSchemaWithoutDuplicateDevelopmentRows() {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            try {
                mockInitializer.run(new DefaultApplicationArguments());
                mockInitializer.run(new DefaultApplicationArguments());
            } catch (Exception error) {
                throw new AssertionError("真实启动组件重复同步失败", error);
            }
            assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM dev_task", Integer.class));
            assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM dev_work_item", Integer.class));
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM dev_calendar_snapshot", Integer.class));
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM dev_task_source_binding", Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dev_stage_attachment_ref", Integer.class));
            tx.setRollbackOnly();
        });
    }

    private ObjectNode stage() {
        return mapper.createObjectNode().put("rowVersion", 0).put("designDocumentPath", "虚构共享文档/设计说明.md");
    }
}
