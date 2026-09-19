package com.ccb.datamigration.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 组件清单关联人员写路径测试（T3）：全量替换保存事务与审计、非成员/停用成员/非法角色/重复
 * 人员拒绝、角色字典选项、组件删除级联清理关系。
 */
@Testcontainers
class ComponentPersonWriteMySqlTest {
    private static final AuthUser ADMIN = new AuthUser(7L, 3L, "zhangsan", "", "张三", 11L, true);

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("component_person_write")
            .withUsername("test")
            .withPassword("test");

    private JdbcTemplate jdbc;
    private ProjectComponentService service;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        dropTables();
        createTables();
        insertFixtures();
        service = new ProjectComponentService(jdbc,
                new DataMigrationPermissionService(jdbc, StubProjectAccess.allow()),
                new DataMigrationCodeValueService(new StubSystemReferenceQuery(jdbc)));
    }

    @Test
    void savePersonsReplacesRelationsAndWritesAudit() {
        service.savePersons(body("SYS-A", List.of(
                person(7L, "OWNER"), person(9L, "LIAISON"))), ADMIN);

        List<Map<String, Object>> relations = relations(91L, "SYS-A");
        assertEquals(2, relations.size(), "全量替换应移除预置关系并写入新关系");
        assertEquals(7L, ((Number) relations.get(0).get("user_id")).longValue());
        assertEquals("OWNER", relations.get(0).get("person_role"));
        assertEquals(9L, ((Number) relations.get(1).get("user_id")).longValue());
        assertEquals("LIAISON", relations.get(1).get("person_role"));

        Integer auditRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dm_operation_log WHERE tenant_id = 3 AND project_id = 91 "
                        + "AND operation_code = 'COMPONENT_PERSON_SAVE' AND entity_type = 'COMPONENT'",
                Integer.class);
        assertEquals(1, auditRows, "保存必须写 dm_operation_log 审计");
    }

    @Test
    void savePersonsRejectsNonActiveMembersAndKeepsExistingRelations() {
        assertRejected(body("SYS-A", List.of(person(8L, "OWNER"))), "停用用户不可关联");
        assertRejected(body("SYS-A", List.of(person(11L, "OWNER"))), "停用成员关系不可关联");
        assertRejected(body("SYS-A", List.of(person(10L, "OWNER"))), "非项目成员不可关联");
        assertEquals(1, relations(91L, "SYS-A").size(), "拒绝后既有关系保持不变");
    }

    @Test
    void savePersonsRejectsInvalidRoleDuplicatePersonAndBlankRole() {
        assertRejected(body("SYS-A", List.of(person(7L, "HACKER"))), "非法角色编码被拒绝");
        assertRejected(body("SYS-A", List.of(person(7L, ""))), "空角色被拒绝");

        LinkedHashMap<String, Object> duplicate = body("SYS-A", List.of(person(7L, "OWNER"), person(7L, "LIAISON")));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.savePersons(duplicate, ADMIN));
        assertEquals(ErrorCode.BAD_REQUEST, ex.code());
        assertTrue(ex.getMessage().contains("重复"), ex.getMessage());
    }

    @Test
    void savePersonsRejectsUnknownComponent() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.savePersons(body("SYS-NOPE", List.of(person(7L, "OWNER"))), ADMIN));
        assertEquals(ErrorCode.BAD_REQUEST, ex.code());
    }

    @Test
    void roleOptionsReturnSeededDefaults() {
        List<Map<String, Object>> options =
                new DataMigrationCodeValueService(new StubSystemReferenceQuery(jdbc))
                        .options(DataMigrationCodeValueService.DM_COMPONENT_PERSON_ROLE, ADMIN);
        Map<String, String> byValue = new LinkedHashMap<>();
        for (Map<String, Object> option : options) {
            byValue.put(String.valueOf(option.get("value")), String.valueOf(option.get("label")));
        }
        assertEquals(Map.of(
                "OWNER", "负责人",
                "LIAISON", "对接人",
                "BUSINESS", "业务人员",
                "IMPLEMENTER", "实施人员"), byValue);
    }

    @Test
    void deleteComponentCascadesRelationsAndKeepsOthers() {
        service.savePersons(body("SYS-A", List.of(person(7L, "OWNER"))), ADMIN);
        service.savePersons(body("SYS-B", List.of(person(9L, "BUSINESS"))), ADMIN);

        service.deleteComponent(91L, "SYS-A", ADMIN);

        assertEquals(0, relations(91L, "SYS-A").size(), "删除组件后关系应级联清理");
        assertEquals(1, relations(91L, "SYS-B").size(), "其他组件关系不受影响");
        Integer componentRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dm_component WHERE tenant_id = 3 AND project_id = 91 AND system_code = 'SYS-A'",
                Integer.class);
        assertEquals(0, componentRows, "组件本身应被删除");
        Integer deleteAudit = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dm_operation_log WHERE tenant_id = 3 AND operation_code = 'COMPONENT_DELETE'",
                Integer.class);
        assertEquals(1, deleteAudit, "既有组件删除审计保留");
    }

    @Test
    void getPersonsReturnsRelatedMembersWithRoleLabels() {
        service.savePersons(body("SYS-A", List.of(person(7L, "OWNER"), person(9L, "LIAISON"))), ADMIN);
        List<Map<String, Object>> persons = service.getPersons(91L, "SYS-A", ADMIN);
        assertEquals(2, persons.size());
        assertEquals("张三", persons.get(0).get("display_name"));
        assertEquals("负责人", persons.get(0).get("person_role_label"));
        assertEquals("王五", persons.get(1).get("display_name"));
        assertEquals("对接人", persons.get(1).get("person_role_label"));
    }

    private void assertRejected(Map<String, Object> body, String reason) {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.savePersons(body, ADMIN), reason);
        assertEquals(ErrorCode.BAD_REQUEST, ex.code(), reason);
    }

    private LinkedHashMap<String, Object> body(String systemCode, List<Map<String, Object>> persons) {
        LinkedHashMap<String, Object> body = new LinkedHashMap<>();
        body.put("projectId", 91L);
        body.put("systemCode", systemCode);
        body.put("persons", persons);
        return body;
    }

    private LinkedHashMap<String, Object> person(long userId, String personRole) {
        LinkedHashMap<String, Object> person = new LinkedHashMap<>();
        person.put("userId", userId);
        person.put("personRole", personRole);
        return person;
    }

    private List<Map<String, Object>> relations(long projectId, String systemCode) {
        return jdbc.queryForList(
                "SELECT user_id, person_role FROM dm_component_person "
                        + "WHERE tenant_id = 3 AND project_id = ? AND system_code = ? ORDER BY user_id",
                projectId, systemCode);
    }

    private void dropTables() {
        for (String table : List.of("dm_component_person", "dm_component", "arch_physical_subsystem",
                "pm_project_member", "pm_project", "sys_user", "sys_config", "sys_dict_type",
                "sys_user_role", "sys_role", "sys_role_permission", "sys_menu_permission", "dm_operation_log",
                "dm_plan", "dm_mapping_doc", "dm_dependency", "dm_script", "dm_topic",
                "dm_release_drill", "dm_report", "dm_rule", "dm_parameter")) {
            jdbc.execute("DROP TABLE IF EXISTS " + table);
        }
    }

    private void createTables() {
        jdbc.execute("CREATE TABLE pm_project (id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL DEFAULT 1, project_code VARCHAR(64), project_name VARCHAR(128), deleted TINYINT NOT NULL DEFAULT 0)");
        jdbc.execute("CREATE TABLE sys_user (id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL DEFAULT 1, username VARCHAR(64), display_name VARCHAR(128), status TINYINT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0)");
        jdbc.execute("CREATE TABLE pm_project_member (id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL DEFAULT 1, project_id BIGINT NOT NULL, user_id BIGINT NOT NULL, status TINYINT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0, UNIQUE KEY uk_pm_member (tenant_id, project_id, user_id, deleted))");
        jdbc.execute("CREATE TABLE arch_physical_subsystem (tenant_id BIGINT NOT NULL DEFAULT 1, project_id BIGINT NOT NULL, code VARCHAR(64) NOT NULL, business_group_name VARCHAR(128), short_name VARCHAR(128), name VARCHAR(128), description VARCHAR(500), responsible_team_name_snapshot VARCHAR(128), deleted TINYINT NOT NULL DEFAULT 0, PRIMARY KEY (tenant_id, project_id, code))");
        jdbc.execute("CREATE TABLE dm_component (tenant_id BIGINT NOT NULL DEFAULT 1, project_id BIGINT NOT NULL, system_code VARCHAR(64) NOT NULL, enabled TINYINT NOT NULL DEFAULT 1, total_check TINYINT NOT NULL DEFAULT 0, owner_id BIGINT NOT NULL, created_by BIGINT, updated_by BIGINT, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, PRIMARY KEY (tenant_id, project_id, system_code))");
        jdbc.execute("CREATE TABLE sys_dict_type (id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL DEFAULT 1, dict_code VARCHAR(64) NOT NULL, dict_name VARCHAR(128) NOT NULL, status TINYINT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0, UNIQUE KEY uk_sys_dict_code (tenant_id, dict_code, deleted))");
        jdbc.execute("CREATE TABLE sys_config (id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL DEFAULT 1, category_id BIGINT NOT NULL, config_key VARCHAR(128) NOT NULL, config_value TEXT NOT NULL, config_type VARCHAR(32) NOT NULL DEFAULT 'string', remark VARCHAR(255), status TINYINT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0, UNIQUE KEY uk_sys_config_key (tenant_id, config_key, deleted))");
        jdbc.execute("CREATE TABLE dm_component_person (tenant_id BIGINT NOT NULL DEFAULT 1, project_id BIGINT NOT NULL, system_code VARCHAR(64) NOT NULL, user_id BIGINT NOT NULL, person_role VARCHAR(64) NOT NULL, created_by BIGINT NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_by BIGINT NOT NULL, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, PRIMARY KEY (tenant_id, project_id, system_code, user_id))");
        jdbc.execute("CREATE TABLE sys_menu_permission (id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL DEFAULT 1, menu_id BIGINT NOT NULL, action_code VARCHAR(32) NOT NULL, permission_code VARCHAR(160) NOT NULL, permission_name VARCHAR(64) NOT NULL, status TINYINT NOT NULL DEFAULT 1)");
        jdbc.execute("CREATE TABLE sys_role (id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL DEFAULT 1, role_code VARCHAR(64) NOT NULL, role_name VARCHAR(128) NOT NULL, status TINYINT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0)");
        jdbc.execute("CREATE TABLE sys_role_permission (role_id BIGINT NOT NULL, permission_id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 1, PRIMARY KEY (role_id, permission_id))");
        jdbc.execute("CREATE TABLE sys_user_role (user_id BIGINT NOT NULL, role_id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 1, PRIMARY KEY (user_id, role_id))");
        jdbc.execute("CREATE TABLE dm_operation_log (id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL DEFAULT 1, project_id BIGINT NOT NULL DEFAULT 0, actor_id BIGINT NOT NULL, operation_code VARCHAR(64) NOT NULL, entity_type VARCHAR(64) NOT NULL, entity_id BIGINT, detail_json JSON, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        for (String table : List.of("dm_plan", "dm_mapping_doc", "dm_dependency", "dm_script", "dm_topic",
                "dm_release_drill", "dm_report", "dm_rule", "dm_parameter")) {
            jdbc.execute("CREATE TABLE " + table + " (id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL DEFAULT 1, project_id BIGINT NOT NULL, system_code VARCHAR(64), deleted TINYINT NOT NULL DEFAULT 0)");
        }
    }

    private void insertFixtures() {
        jdbc.update("INSERT INTO pm_project (id, tenant_id, project_code, project_name, deleted) VALUES (91, 3, 'P-91', '写路径项目', 0)");
        jdbc.update("INSERT INTO sys_user (id, tenant_id, username, display_name, status, deleted) VALUES (7, 3, 'zhangsan', '张三', 1, 0), (9, 3, 'wangwu', '王五', 1, 0), (8, 3, 'lisi', '李四', 0, 0), (10, 3, 'zhaoliu', '赵六', 1, 0), (11, 3, 'qianqi', '钱七', 1, 0)");
        jdbc.update("INSERT INTO pm_project_member (id, tenant_id, project_id, user_id, status, deleted) VALUES (1, 3, 91, 7, 1, 0), (2, 3, 91, 8, 1, 0), (3, 3, 91, 9, 1, 0), (4, 3, 91, 11, 0, 0)");
        jdbc.update("INSERT INTO arch_physical_subsystem (tenant_id, project_id, code, business_group_name, short_name, name, description, responsible_team_name_snapshot, deleted) VALUES (3, 91, 'SYS-A', '事业群一', '系统甲', '系统A名称', '描述A', '团队一', 0), (3, 91, 'SYS-B', '事业群一', '系统乙', '系统B名称', '描述B', '团队一', 0)");
        jdbc.update("INSERT INTO dm_component (tenant_id, project_id, system_code, enabled, total_check, owner_id, created_by, updated_by) VALUES (3, 91, 'SYS-A', 1, 0, 7, 7, 7), (3, 91, 'SYS-B', 1, 0, 7, 7, 7)");
        jdbc.update("INSERT INTO sys_dict_type (id, tenant_id, dict_code, dict_name, status, deleted) VALUES (5824, 3, 'DM_COMPONENT_PERSON_ROLE', '组件人员职责', 1, 0)");
        jdbc.update("INSERT INTO sys_config (id, tenant_id, category_id, config_key, config_value, status, deleted) VALUES (58240, 3, 5824, 'DM_COMPONENT_PERSON_ROLE.OWNER', '负责人', 1, 0), (58241, 3, 5824, 'DM_COMPONENT_PERSON_ROLE.LIAISON', '对接人', 1, 0), (58242, 3, 5824, 'DM_COMPONENT_PERSON_ROLE.BUSINESS', '业务人员', 1, 0), (58243, 3, 5824, 'DM_COMPONENT_PERSON_ROLE.IMPLEMENTER', '实施人员', 1, 0)");
        jdbc.update("INSERT INTO dm_component_person (tenant_id, project_id, system_code, user_id, person_role, created_by, updated_by) VALUES (3, 91, 'SYS-A', 9, 'OWNER', 7, 7)");
        jdbc.update("INSERT INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name, status) VALUES (1, 3, 720, 'read', 'system:admin', '超级管理员', 1)");
        jdbc.update("INSERT INTO sys_role (id, tenant_id, role_code, role_name, status, deleted) VALUES (1, 3, 'ADMIN', '管理员', 1, 0)");
        jdbc.update("INSERT INTO sys_role_permission (role_id, permission_id, tenant_id) VALUES (1, 1, 3)");
        jdbc.update("INSERT INTO sys_user_role (user_id, role_id, tenant_id) VALUES (7, 1, 3)");
    }
}
