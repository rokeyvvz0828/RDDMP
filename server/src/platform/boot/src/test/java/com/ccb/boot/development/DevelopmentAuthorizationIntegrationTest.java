package com.ccb.boot.development;

import com.ccb.boot.CcbApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class DevelopmentAuthorizationIntegrationTest extends DevelopmentIntegrationSupport {
    @Test
    void systemOptionsAndCreationCannotCrossProjectEvenForOwnerOrAdministrator() throws Exception {
        long otherSystem = 91007120003L;
        jdbc.update("INSERT INTO arch_physical_subsystem(id,tenant_id,project_id,code,short_name,name,responsible_team_org_id,responsible_team_name_snapshot,owner_user_id,status,row_version,created_by,updated_by,deleted) VALUES(?,1,91007100002,'DEV071-OTHER-SYSTEM','虚构其他项目系统','虚构其他项目系统',1,'虚构团队',91007101,'ACTIVE',0,1,1,0)", otherSystem);
        try {
            for (String actor : List.of("owner", "admin")) {
                var options = request(token(actor), get("/api/development/options/systems").param("projectRef", PROJECT), 200);
                assertEquals(2, options.path("total").asInt(), "不得列出同Owner的其他项目系统");
                for (var option : options.path("records")) assertNotEquals(otherSystem, option.path("id").asLong());
                request(token(actor), post("/api/development/tasks").content(json(standalone().put("systemId", otherSystem))), 404);
            }
        } finally {
            jdbc.update("DELETE FROM arch_physical_subsystem WHERE id=?", otherSystem);
        }
    }

    @Test
    void actualLoginAndFilterChainEnforceAuthenticationRbacProjectAndEntity() throws Exception {
        request(null, get("/api/development/tasks").param("projectRef", PROJECT), 401);
        request(token("denied"), get("/api/development/tasks").param("projectRef", PROJECT), 403);
        request(token("admin"), get("/api/development/tasks").param("projectRef", "DEV071-OTHER"), 403);
        var task = create("owner", standalone());
        request(token("outsider"), get(taskUrl(task)), 403);
        // 登录入口目前固定租户 1，以租户 2 的真实记录验证 HTTP 查询不会串租户。
        jdbc.update("INSERT INTO dev_task(tenant_id,project_id,project_ref,task_no,source_mode,source_roles,source_system_codes,system_id,owner_id,title,description,request_id,request_hash,created_by,updated_by) VALUES(2,91007100001,?,'FOREIGN-071','STANDALONE','[]','[]',?,91007101,'虚构其他租户任务','','foreign-071',REPEAT('0',64),91007101,91007101)", PROJECT, SYSTEM_A);
        long foreignId = jdbc.queryForObject("SELECT id FROM dev_task WHERE tenant_id=2", Long.class);
        request(token("owner"), get("/api/development/tasks/" + foreignId), 404);
        var invisible = request(token("outsider"), get("/api/development/tasks").param("projectRef", PROJECT), 200);
        assertEquals(0, invisible.path("total").asInt());
        request(token("outsider"), post("/api/development/tasks").content(json(standalone())), 403);
        assertEquals("91007101", task.path("owner").path("id").asText());
        assertFalse(task.has("tenantId"));
        request(token("owner"), post("/api/development/tasks").content(json(standalone().put("tenantId", 2))), 400);
        request(token("owner"), put(taskUrl(task)).content("{\"title\":\"越过版本\"}"), 400);
        request(token("owner"), put(taskUrl(task)).content("{\"rowVersion\":0,\"status\":\"COMPLETED\"}"), 400);
    }

    @Test
    void assigneeCanExecuteButCannotAcceptOrReadOtherAssignments() throws Exception {
        var task = create("owner", standalone().put("ownerId", 91007102));
        var own = item(task, 91007103);
        var other = item(task, 91007102);
        var visible = request(token("executor"), get("/api/development/work-items").param("projectRef", PROJECT), 200);
        assertEquals(1, visible.path("total").asInt());
        assertEquals(1, visible.path("counts").path("TODO").asInt());
        assertEquals(1, request(token("executor"), get(taskUrl(task)), 200).path("workItemCount").asInt());
        request(token("executor"), get(itemUrl(other)), 403);
        request(token("manager"), post(itemUrl(own) + "/actions").content(action(own, "START")), 403);
        own = itemAction("executor", own, "START");
        own = itemAction("executor", own, "SUBMIT");
        request(token("executor"), post(itemUrl(own) + "/actions").content(action(own, "ACCEPT")), 403);
        own = itemAction("manager", own, "ACCEPT");
        assertEquals("DONE", own.path("status").asText());
        var history = request(token("executor"), get(taskUrl(task) + "/changes"), 200);
        for (var row : history.path("records")) {
            if ("WORK_ITEM".equals(row.path("objectType").asText())) assertEquals(own.path("id"), row.path("objectId"));
        }
        assertEquals("NOT_STARTED", request(token("manager"), get(taskUrl(task)), 200).path("status").asText());
    }

    @Test
    void claimsAreUniqueAcrossRequestsButIndependentAcrossSystems() throws Exception {
        var pending = request(token("owner"), get("/api/development/pending-requirements").param("projectRef", PROJECT), 200);
        assertEquals(3, pending.path("total").asInt());
        var source = pending.path("records").findValues("sourceRequirementId");
        assertFalse(source.isEmpty());
        var linked = linked(91007130001L, SYSTEM_A);
        var bearer = token("owner");
        // 并发请求使用不同幂等键，必须由真实事务与来源唯一键共同收敛。
        var pool = Executors.newFixedThreadPool(4);
        try {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
            for (int i = 0; i < 4; i++) {
                String body = json(linked.deepCopy().put("requestId", UUID.randomUUID().toString()));
                futures.add(pool.submit(() -> mvc.perform(post("/api/development/tasks").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus()));
            }
            var statuses = new java.util.ArrayList<Integer>();
            for (var future : futures) statuses.add(future.get());
            assertEquals(1, statuses.stream().filter(s -> s == 200).count(), statuses.toString());
            assertEquals(3, statuses.stream().filter(s -> s == 409).count(), statuses.toString());
        } finally {
            pool.shutdownNow();
        }
        var pure = create("owner", linked(91007130001L, SYSTEM_B));
        assertEquals(List.of("TEST"), mapper.convertValue(pure.path("source").path("roles"), List.class));
        assertEquals(0, pure.path("workItemCount").asInt());
        var mixed = create("owner", linked(91007130002L, SYSTEM_A));
        assertEquals(2, mixed.path("source").path("roles").size());
        assertEquals("91007101", mixed.path("owner").path("id").asText());
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM dev_task", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dev_work_item", Integer.class));
    }

    @Test
    void optimisticWriteKeepsFirstChangeAndFailedWritesAreAudited() throws Exception {
        var task = create("owner", standalone());
        ObjectNode update = mapper.createObjectNode().put("title", "已确认修改").put("ownerId", 91007101).put("rowVersion", 0);
        var saved = request(token("owner"), put(taskUrl(task)).content(json(update)), 200);
        String auditQuery = "SELECT COUNT(*) FROM sys_operation_log WHERE tenant_id=1 AND operator_id=91007101 AND request_method='PUT' AND request_path=? AND success=0";
        long beforeFailures = jdbc.queryForObject(auditQuery, Long.class, taskUrl(task));
        var rejected = mvc.perform(put(taskUrl(task)).header("Authorization", token("owner"))
                .contentType(MediaType.APPLICATION_JSON).content(json(update.put("title", "过期覆盖"))))
                .andReturn().getResponse();
        assertEquals(409, rejected.getStatus());
        String traceId = mapper.readTree(rejected.getContentAsByteArray()).path("traceId").asText();
        assertFalse(traceId.isBlank());
        assertEquals("已确认修改", request(token("owner"), get(taskUrl(task)), 200).path("title").asText());
        assertEquals(1, saved.path("rowVersion").asInt());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM dev_task_change WHERE task_id=?", Integer.class, task.path("id").asLong()));
        assertEquals(beforeFailures + 1, jdbc.queryForObject(auditQuery, Long.class, taskUrl(task)));
        assertEquals(1, jdbc.queryForObject(auditQuery + " AND trace_id=?", Integer.class, taskUrl(task), traceId));
    }

    @Test
    void templateChangeAffectsOnlyNewTasksAndConcurrentStandaloneNumbersAreUnique() throws Exception {
        var original = create("owner", standalone());
        assertTrue(original.path("number").asText().matches("RW_ZZ_\\d{8}_001"));
        String previous = jdbc.queryForObject("SELECT config_value FROM sys_config WHERE tenant_id=1 AND category_id=910713 AND config_key='STANDALONE'", String.class);
        var pool = Executors.newFixedThreadPool(4);
        try {
            jdbc.update("UPDATE sys_config SET config_value='CUSTOM_{date}_{seq}' WHERE tenant_id=1 AND category_id=910713 AND config_key='STANDALONE'");
            String bearer = token("owner");
            var results = new java.util.ArrayList<java.util.concurrent.Future<JsonNode>>();
            for (int i = 0; i < 8; i++) {
                String body = json(standalone());
                results.add(pool.submit(() -> request(bearer, post("/api/development/tasks").content(body), 200)));
            }
            var numbers = new java.util.HashSet<String>();
            for (var result : results) {
                JsonNode created = result.get();
                String number = created.path("number").asText();
                assertTrue(number.matches("CUSTOM_\\d{8}_\\d{3}"), number);
                assertTrue(numbers.add(number), "并发任务编号重复");
                assertEquals(number, request(bearer, get(taskUrl(created)), 200).path("number").asText());
            }
            assertEquals(8, numbers.size());
            assertEquals(9, jdbc.queryForObject("SELECT COUNT(*) FROM dev_task WHERE tenant_id=1 AND project_ref=?", Integer.class, PROJECT));
            assertEquals(original.path("number"), request(bearer, get(taskUrl(original)), 200).path("number"));
        } finally {
            pool.shutdownNow();
            jdbc.update("UPDATE sys_config SET config_value=? WHERE tenant_id=1 AND category_id=910713 AND config_key='STANDALONE'", previous);
        }
    }
}

@SpringBootTest(classes = CcbApplication.class, properties = {
        "spring.config.import=", "ccb.mock-data.enabled=true", "ccb.file-preview.enabled=false",
        "ccb.workflow.seeded-definition-publisher.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("local")
abstract class DevelopmentIntegrationSupport {
    static final String PROJECT = "DEV071-ALPHA";
    static final long SYSTEM_A = 91007120001L;
    static final long SYSTEM_B = 91007120002L;
    static final String PASSWORD = "Development071!";
    static final String HASH = new BCryptPasswordEncoder().encode(PASSWORD);
    // 两个测试类共享容器与 Spring 上下文，容器由 Testcontainers 在 JVM 退出后回收。
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("development_071_integration").withUsername("fixture").withPassword(UUID.randomUUID().toString())
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");
    static final String MINIO_SECRET = UUID.randomUUID().toString();
    static final GenericContainer<?> MINIO = new GenericContainer<>("minio/minio:RELEASE.2025-04-22T22-12-26Z")
            .withEnv("MINIO_ROOT_USER", "fixture071").withEnv("MINIO_ROOT_PASSWORD", MINIO_SECRET)
            .withCommand("server", "/data").withExposedPorts(9000).waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));
    static final List<Map<String, Object>> MIGRATION_BASELINE;
    static {
        MYSQL.start();
        MINIO.start();
        var migrations = Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration").placeholders(Map.of("bootstrap_admin_password_hash", HASH));
        migrations.target("155").load().migrate();
        var seed = new JdbcTemplate(new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));
        // 远端项目隔离迁移要求先有唯一默认项目；测试按其原始前置条件准备存量159基线。
        seed.update("INSERT IGNORE INTO pm_project(id,tenant_id,project_code,project_name,status,owner_id,created_by,deleted) VALUES(910000000003001,1,'RDDMP-PLATFORM','虚构平台项目','RUNNING',1,1,0)");
        migrations.target("159").load().migrate();
        MIGRATION_BASELINE = seed.queryForList("SELECT version,script,checksum FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank");
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", MYSQL::getJdbcUrl);
        r.add("spring.datasource.username", MYSQL::getUsername);
        r.add("spring.datasource.password", MYSQL::getPassword);
        r.add("spring.flyway.placeholders.bootstrap_admin_password_hash", () -> HASH);
        r.add("ccb.security.jwt.secret", () -> "development-071-isolated-integration-secret-at-least-64-characters-only");
        r.add("ccb.storage.minio.endpoint", () -> "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000));
        r.add("ccb.storage.minio.access-key", () -> "fixture071");
        r.add("ccb.storage.minio.secret-key", () -> MINIO_SECRET);
        r.add("ccb.storage.minio.bucket", () -> "development-071-integration");
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    final Map<String, String> tokens = new java.util.HashMap<>();

    @BeforeEach
    void fixture() {
        tokens.clear();
        assertEquals("development_071_integration", jdbc.queryForObject("SELECT DATABASE()", String.class));
        for (String table : List.of("dev_stage_attachment_ref", "dev_task_stage", "dev_calendar_snapshot", "dev_task_source_binding",
                "dev_task_change", "dev_work_item", "dev_task", "dev_task_number_sequence")) jdbc.update("DELETE FROM " + table);
        String[] names = {"owner", "manager", "executor", "outsider", "admin", "denied"};
        for (int i = 0; i < names.length; i++) {
            long user = 91007101 + i, role = 91007201 + i, tenant = 1;
            jdbc.update("INSERT INTO sys_user(id,tenant_id,username,password_hash,display_name,org_id,status,deleted) VALUES(?,?,?,?,?,1,1,0) ON DUPLICATE KEY UPDATE password_hash=VALUES(password_hash)",
                    user, tenant, "it071." + names[i], HASH, "虚构" + names[i]);
            jdbc.update("INSERT IGNORE INTO sys_role(id,tenant_id,role_code,role_name,status,deleted) VALUES(?,?,?, ?,1,0)", role, tenant, "IT071_" + names[i], "虚构" + names[i]);
            jdbc.update("INSERT IGNORE INTO sys_user_role(user_id,role_id,tenant_id) VALUES(?,?,?)", user, role, tenant);
            jdbc.update("DELETE FROM sys_role_permission WHERE role_id=?", role);
            if (i != 5) {
                int[] codes = i == 3 ? new int[]{1} : i == 2 ? new int[]{1,4,5} : i == 4 ? new int[]{1,7} : new int[]{1,2,3,4,5,6};
                for (int code : codes) {
                    jdbc.update("INSERT IGNORE INTO sys_role_permission(role_id,permission_id,tenant_id) VALUES(?,?,?)", role, 9107110 + code, tenant);
                }
            }
        }
        jdbc.update("INSERT IGNORE INTO pm_project(id,tenant_id,project_code,project_name,status,owner_id,created_by,deleted) VALUES(91007100001,1,?,'虚构开发验收项目','RUNNING',91007101,1,0),(91007100002,1,'DEV071-OTHER','虚构隔离项目','RUNNING',1,1,0)", PROJECT);
        for (int i = 0; i < 6; i++) jdbc.update("INSERT IGNORE INTO pm_project_member(id,tenant_id,project_id,user_id,org_id,status,deleted) VALUES(?,1,91007100001,?,1,1,0)", 91007301 + i, 91007101 + i);
        jdbc.update("INSERT IGNORE INTO req_project(id,tenant_id,project_code,project_name,status,deleted) VALUES(91007110001,1,?,'虚构开发验收项目','ACTIVE',0)", PROJECT);
        for (int i = 0; i < 2; i++) {
            jdbc.update("INSERT INTO arch_physical_subsystem(id,tenant_id,project_id,code,short_name,name,responsible_team_org_id,responsible_team_name_snapshot,owner_user_id,status,row_version,created_by,updated_by,deleted) VALUES(?,1,91007100001,?,?,?,1,'虚构团队',91007101,'ACTIVE',0,1,1,0) ON DUPLICATE KEY UPDATE owner_user_id=91007101,status='ACTIVE'", SYSTEM_A + i, "DEV071-PHY-" + i, "虚构系统" + i, "虚构系统" + i);
            jdbc.update("INSERT IGNORE INTO req_legacy_requirement(id,tenant_id,project_id,requirement_no,requirement_name,deleted) VALUES(?,1,91007110001,?,?,0)", 91007130001L + i, "DEV071-REQ-" + i, "虚构来源" + i);
            jdbc.update("INSERT INTO sys_config(id,tenant_id,category_id,config_key,config_value,config_type,status,deleted) VALUES(?,1,910712,?,?,'string',1,0) ON DUPLICATE KEY UPDATE config_value=VALUES(config_value),status=1,deleted=0", 9107121 + i, "DEV071-SOURCE-" + i, Long.toString(SYSTEM_A + i));
        }
        jdbc.update("DELETE FROM req_legacy_system_item WHERE requirement_id IN (91007130001,91007130002)");
        jdbc.update("DELETE FROM req_coordination_item WHERE requirement_id IN (91007130001,91007130002)");
        for (int i = 0; i < 2; i++) jdbc.update("INSERT INTO req_legacy_system_item(id,tenant_id,requirement_id,system_role,system_code,system_name,owner_user_id,owner_user_name,created_by,deleted) VALUES(?,1,?,'主责','DEV071-SOURCE-0','虚构来源系统',91007102,'虚构建议负责人',1,0)", 91007140001L + i, 91007130001L + i);
        jdbc.update("INSERT INTO req_coordination_item(id,tenant_id,requirement_id,item_type,system_code,system_name,owner_user_id,owner_user_name,created_by,deleted) VALUES(91007150001,1,91007130001,'测试','DEV071-SOURCE-1','虚构测试系统',91007103,'虚构执行人',1,0),(91007150002,1,91007130002,'测试','DEV071-SOURCE-0','虚构混合系统',91007103,'虚构执行人',1,0)");
    }

    String token(String user) throws Exception {
        if (!tokens.containsKey(user)) {
            var data = request(null, post("/api/auth/login").content(json(Map.of("username", "it071." + user, "password", PASSWORD))), 200);
            assertFalse(data.path("accessToken").asText().isBlank());
            tokens.put(user, "Bearer " + data.path("accessToken").asText());
        }
        return tokens.get(user);
    }
    JsonNode request(String bearer, MockHttpServletRequestBuilder builder, int expected) throws Exception {
        builder.contentType(MediaType.APPLICATION_JSON);
        if (bearer != null) builder.header("Authorization", bearer);
        var response = mvc.perform(builder).andReturn().getResponse();
        assertEquals(expected, response.getStatus(), "HTTP 状态不符：" + response.getContentAsString());
        return mapper.readTree(response.getContentAsByteArray()).path("data");
    }
    String json(Object value) throws Exception { return mapper.writeValueAsString(value); }
    ObjectNode standalone() {
        return mapper.createObjectNode().put("projectRef", PROJECT).put("sourceMode", "STANDALONE").put("systemId", SYSTEM_A)
                .put("title", "虚构独立开发任务").put("requestId", UUID.randomUUID().toString())
                .put("developmentPlanStart", "2026-09-07").put("developmentPlanEnd", "2026-09-11");
    }
    ObjectNode linked(long source, long system) throws Exception {
        var rows = request(token("owner"), get("/api/development/pending-requirements").param("projectRef", PROJECT), 200).path("records");
        for (var row : rows) if (row.path("sourceRequirementId").asLong() == source && row.path("system").path("id").asLong() == system) {
            return standalone().put("sourceMode", "LINKED").put("sourceRequirementId", source).put("systemId", system)
                    .put("sourceRevision", row.path("sourceRevision").asText());
        }
        throw new AssertionError("缺少待承接来源：" + source + "/" + system);
    }
    JsonNode create(String actor, ObjectNode input) throws Exception { return request(token(actor), post("/api/development/tasks").content(json(input)), 200); }
    JsonNode item(JsonNode task, long assignee) throws Exception {
        return request(token("manager"), post("/api/development/work-items").content(json(Map.of("taskId", task.path("id").asLong(),
                "title", "虚构工作项", "assigneeId", assignee, "plannedStart", "2026-09-07", "plannedEnd", "2026-09-11"))), 200);
    }
    String taskUrl(JsonNode task) { return "/api/development/tasks/" + task.path("id").asText(); }
    String itemUrl(JsonNode item) { return "/api/development/work-items/" + item.path("id").asText(); }
    String action(JsonNode entity, String action) throws Exception { return json(Map.of("action", action, "rowVersion", entity.path("rowVersion").asLong(), "reason", "虚构验收原因")); }
    JsonNode itemAction(String actor, JsonNode item, String action) throws Exception { return request(token(actor), post(itemUrl(item) + "/actions").content(action(item, action)), 200); }
    JsonNode taskAction(String actor, JsonNode task, String action) throws Exception { return request(token(actor), post(taskUrl(task) + "/actions").content(action(task, action)), 200); }
}
