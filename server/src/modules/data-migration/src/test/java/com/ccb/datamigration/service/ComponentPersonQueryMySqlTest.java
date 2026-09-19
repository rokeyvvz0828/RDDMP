package com.ccb.datamigration.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ccb.common.api.PageQuery;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 组件清单关联人员查询测试（T2）：列表 persons/person_count 按项目数据范围返回、导出
 * “关联人员（角色）”列、成员选项仅含当前项目未删除且启用的成员。
 */
@Testcontainers
class ComponentPersonQueryMySqlTest {
    private static final AuthUser USER = new AuthUser(7L, 3L, "developer", "", "研发人员", 11L, true);

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("component_person_query")
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
    void listReturnsPersonsPerComponentWithinProjectScope() {
        var result = service.components(USER, 91L, new PageQuery(1, 20));
        assertEquals(3, result.records().size(), "仅返回当前项目组件");
        Map<String, Object> sysA = result.records().stream()
                .filter(row -> "SYS-A".equals(row.get("system_code"))).findFirst().orElseThrow();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> personsA = (List<Map<String, Object>>) sysA.get("persons");
        assertEquals(2, personsA.size());
        assertEquals(2, sysA.get("person_count"));
        assertEquals(7L, ((Number) personsA.get(0).get("user_id")).longValue());
        assertEquals("张三", personsA.get(0).get("display_name"));
        assertEquals("OWNER", personsA.get(0).get("person_role"));
        assertEquals("负责人", personsA.get(0).get("person_role_label"));
        assertEquals("李四", personsA.get(1).get("display_name"));
        assertEquals("对接人", personsA.get(1).get("person_role_label"));
        Map<String, Object> sysB = result.records().stream()
                .filter(row -> "SYS-B".equals(row.get("system_code"))).findFirst().orElseThrow();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> personsB = (List<Map<String, Object>>) sysB.get("persons");
        assertEquals(1, personsB.size());
        assertEquals(1, sysB.get("person_count"));
    }

    @Test
    void listReturnsEmptyPersonsForComponentWithoutRelations() {
        var result = service.components(USER, 91L, new PageQuery(1, 20));
        Map<String, Object> sysD = result.records().stream()
                .filter(row -> "SYS-D".equals(row.get("system_code"))).findFirst().orElseThrow();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> personsD = (List<Map<String, Object>>) sysD.get("persons");
        assertTrue(personsD.isEmpty());
        assertEquals(0, sysD.get("person_count"));
    }

    @Test
    void exportAddsPersonsRoleColumnAndUsesDashForEmpty() throws Exception {
        byte[] bytes = service.exportComponents(USER, 91L, null, null, null, null, null, null);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheetAt(0);
            assertEquals("关联人员（角色）", sheet.getRow(0).getCell(14).getStringCellValue());
            Map<String, String> bySystem = new HashMap<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                var row = sheet.getRow(i);
                bySystem.put(row.getCell(2).getStringCellValue(), row.getCell(14).getStringCellValue());
            }
            assertEquals("张三（负责人）, 李四（对接人）", bySystem.get("SYS-A"));
            assertEquals("张三（负责人）", bySystem.get("SYS-B"));
            assertEquals("-", bySystem.get("SYS-D"));
        }
    }

    @Test
    void memberOptionsExcludeDisabledAndNonProjectMembers() {
        List<Map<String, Object>> options = service.getMemberOptions(91L, USER);
        Map<Long, String> byId = new LinkedHashMap<>();
        for (Map<String, Object> option : options) {
            byId.put(((Number) option.get("user_id")).longValue(), String.valueOf(option.get("display_name")));
        }
        assertEquals("张三", byId.get(7L));
        assertEquals("王五", byId.get(9L));
        assertTrue(!byId.containsKey(8L), "停用用户不应出现在成员选项中");
        assertTrue(!byId.containsKey(10L), "其他项目成员不应出现在成员选项中");
        assertTrue(!byId.containsKey(11L), "停用成员关系不应出现在成员选项中");
    }

    @Test
    void memberOptionsWithoutProjectAccessAreRejected() {
        ProjectComponentService denied = new ProjectComponentService(jdbc,
                new DataMigrationPermissionService(jdbc, StubProjectAccess.withDecision(StubProjectAccess.Decision.NOT_MEMBER)),
                new DataMigrationCodeValueService(new StubSystemReferenceQuery(jdbc)));
        BusinessException ex = assertThrows(BusinessException.class, () -> denied.getMemberOptions(92L, USER));
        assertEquals(ErrorCode.FORBIDDEN, ex.code());
    }

    private void dropTables() {
        for (String table : List.of("dm_component_person", "dm_component", "arch_physical_subsystem",
                "pm_project_member", "pm_project", "sys_user", "sys_config", "sys_dict_type")) {
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
    }

    private void insertFixtures() {
        jdbc.update("INSERT INTO pm_project (id, tenant_id, project_code, project_name, deleted) VALUES (91, 3, 'P-91', '组件查询项目', 0), (92, 3, 'P-92', '其他项目', 0)");
        jdbc.update("INSERT INTO sys_user (id, tenant_id, username, display_name, status, deleted) VALUES (7, 3, 'zhangsan', '张三', 1, 0), (8, 3, 'lisi', '李四', 0, 0), (9, 3, 'wangwu', '王五', 1, 0), (10, 3, 'zhaoliu', '赵六', 1, 0), (11, 3, 'qianqi', '钱七', 1, 0)");
        jdbc.update("INSERT INTO pm_project_member (id, tenant_id, project_id, user_id, status, deleted) VALUES (1, 3, 91, 7, 1, 0), (2, 3, 91, 8, 1, 0), (3, 3, 91, 9, 1, 0), (4, 3, 92, 10, 1, 0), (5, 3, 91, 11, 0, 0)");
        jdbc.update("INSERT INTO arch_physical_subsystem (tenant_id, project_id, code, business_group_name, short_name, name, description, responsible_team_name_snapshot, deleted) VALUES (3, 91, 'SYS-A', '事业群一', '系统甲', '系统A名称', '描述A', '团队一', 0), (3, 91, 'SYS-B', '事业群一', '系统乙', '系统B名称', '描述B', '团队一', 0), (3, 91, 'SYS-D', '事业群二', '系统丁', '系统D名称', '描述D', '团队二', 0), (3, 92, 'SYS-C', '事业群三', '系统丙', '系统C名称', '描述C', '团队三', 0)");
        jdbc.update("INSERT INTO dm_component (tenant_id, project_id, system_code, enabled, total_check, owner_id, created_by, updated_by) VALUES (3, 91, 'SYS-A', 1, 0, 7, 7, 7), (3, 91, 'SYS-B', 1, 1, 7, 7, 7), (3, 91, 'SYS-D', 0, 0, 7, 7, 7), (3, 92, 'SYS-C', 1, 0, 7, 7, 7)");
        jdbc.update("INSERT INTO sys_dict_type (id, tenant_id, dict_code, dict_name, status, deleted) VALUES (5824, 3, 'DM_COMPONENT_PERSON_ROLE', '组件人员职责', 1, 0)");
        jdbc.update("INSERT INTO sys_config (id, tenant_id, category_id, config_key, config_value, status, deleted) VALUES (58240, 3, 5824, 'DM_COMPONENT_PERSON_ROLE.OWNER', '负责人', 1, 0), (58241, 3, 5824, 'DM_COMPONENT_PERSON_ROLE.LIAISON', '对接人', 1, 0)");
        jdbc.update("INSERT INTO dm_component_person (tenant_id, project_id, system_code, user_id, person_role, created_by, updated_by) VALUES (3, 91, 'SYS-A', 7, 'OWNER', 7, 7), (3, 91, 'SYS-A', 8, 'LIAISON', 7, 7), (3, 91, 'SYS-B', 7, 'OWNER', 7, 7), (3, 92, 'SYS-C', 7, 'OWNER', 7, 7)");
    }
}
