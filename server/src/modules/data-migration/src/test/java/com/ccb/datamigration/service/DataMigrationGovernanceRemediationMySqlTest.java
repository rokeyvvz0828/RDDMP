package com.ccb.datamigration.service;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import com.ccb.security.model.AuthUser;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 9.2 治理项迁移测试（V171-V173，REQ-20260820-031）：
 * V171 子系统引用收敛回填、V172 软删唯一键统一为活动生成列、V173 活动编号生成列注释。
 * 基线为 V170，目标版本逐级执行；被基线跳过的历史表结构以最小夹具复刻（与 ContentAssetMigrationMySqlTest 相同口径）。
 */
@Testcontainers
class DataMigrationGovernanceRemediationMySqlTest {
    private static final AuthUser ADMIN = new AuthUser(1L, 1L, "admin", "", "Administrator", 1L, true);

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("data_migration_governance")
            .withUsername("test")
            .withPassword("test");

    private void cleanFixtureTables() throws Exception {
        try (Connection connection = connection()) {
            execute(connection, "DROP TABLE IF EXISTS "
                + "dm_component, dm_target_table, dm_target_table_field, dm_issue, dm_meeting, dm_meeting_system, dm_plan, "
                + "dm_mapping_doc, dm_dependency, dm_script, dm_topic, dm_release_drill, dm_report, dm_rule, dm_parameter, "
                + "arch_physical_subsystem, pm_project, sys_user, sys_role, sys_user_role, dm_operation_log, dm_issue_relation, "
                + "flyway_schema_history");
        }
    }

    /** V175 端到端：真实服务 create 只写 system_code / physical_subsystem_code，id 引用列已下线。 */
    @Test
    void v175ServiceLayerWritesSystemCodeOnlyAndDropsIdColumns() throws Exception {
        cleanFixtureTables();
        try (Connection connection = connection()) {
            execute(connection, """
                    CREATE TABLE arch_physical_subsystem (
                        id BIGINT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        code VARCHAR(160) NOT NULL,
                        short_name VARCHAR(160) NULL,
                        name VARCHAR(160) NULL,
                        business_group_name VARCHAR(160) NULL,
                        description VARCHAR(500) NULL,
                        responsible_team_name_snapshot VARCHAR(160) NULL,
                        deleted TINYINT NOT NULL DEFAULT 0,
                        UNIQUE KEY uk_arch_physical_code (tenant_id, code)
                    )
                    """);
            execute(connection, """
                    CREATE TABLE pm_project (
                        id BIGINT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        project_code VARCHAR(64) NULL,
                        project_name VARCHAR(128) NOT NULL,
                        deleted TINYINT NOT NULL DEFAULT 0
                    )
                    """);
            execute(connection, """
                    CREATE TABLE sys_user (
                        id BIGINT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        display_name VARCHAR(128) NOT NULL,
                        deleted TINYINT NOT NULL DEFAULT 0
                    )
                    """);
            execute(connection, """
                    CREATE TABLE sys_role (
                        id BIGINT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        role_code VARCHAR(64) NOT NULL,
                        status TINYINT NOT NULL DEFAULT 1,
                        deleted TINYINT NOT NULL DEFAULT 0
                    )
                    """);
            execute(connection, """
                    CREATE TABLE sys_user_role (
                        user_id BIGINT NOT NULL,
                        role_id BIGINT NOT NULL,
                        tenant_id BIGINT NOT NULL
                    )
                    """);
            execute(connection, "INSERT INTO sys_role (id, tenant_id, role_code) VALUES (1, 1, 'ADMIN')");
            execute(connection, "INSERT INTO sys_user_role (user_id, role_id, tenant_id) VALUES (1, 1, 1)");
            execute(connection, """
                    CREATE TABLE dm_operation_log (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        tenant_id BIGINT NOT NULL,
                        project_id BIGINT NOT NULL DEFAULT 0,
                        actor_id BIGINT NOT NULL,
                        operation_code VARCHAR(64) NOT NULL,
                        entity_type VARCHAR(64) NOT NULL,
                        entity_id BIGINT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            execute(connection, """
                    CREATE TABLE dm_issue_relation (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        tenant_id BIGINT NOT NULL,
                        issue_id BIGINT NOT NULL,
                        related_type VARCHAR(32) NOT NULL,
                        related_id BIGINT NOT NULL,
                        created_by BIGINT NULL,
                        created_at TIMESTAMP NULL
                    )
                    """);
            execute(connection, """
                    CREATE TABLE dm_meeting (
                        meeting_id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        tenant_id BIGINT NOT NULL DEFAULT 1,
                        project_id BIGINT NOT NULL,
                        meeting_code VARCHAR(96) NOT NULL,
                        meeting_title VARCHAR(500) NOT NULL,
                        deleted TINYINT NOT NULL DEFAULT 0
                    )
                    """);
            execute(connection, """
                    CREATE TABLE dm_component (
                        id BIGINT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL DEFAULT 1,
                        project_id BIGINT NOT NULL,
                        physical_subsystem_code VARCHAR(64) NULL,
                        total_check TINYINT NOT NULL DEFAULT 0,
                        owner_id BIGINT NOT NULL,
                        created_by BIGINT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_by BIGINT NULL,
                        updated_at TIMESTAMP NULL,
                        deleted TINYINT NOT NULL DEFAULT 0,
                        deleted_by BIGINT NULL,
                        deleted_at TIMESTAMP NULL,
                        UNIQUE KEY uk_dm_component_subsystem (tenant_id, project_id, physical_subsystem_code)
                    )
                    """);
            execute(connection, """
                    CREATE TABLE dm_issue (
                        id BIGINT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL DEFAULT 1,
                        project_id BIGINT NOT NULL,
                        issue_code VARCHAR(96) NOT NULL,
                        issue_name VARCHAR(255) NOT NULL,
                        granularity VARCHAR(16) NULL,
                        system_code VARCHAR(96) NULL,
                        issue_source VARCHAR(32) NULL,
                        defect_type VARCHAR(32) NULL,
                        issue_description TEXT NULL,
                        solution TEXT NULL,
                        meeting_conclusion TEXT NULL,
                        processing_steps TEXT NULL,
                        business_scenario VARCHAR(500) NULL,
                        handler VARCHAR(160) NULL,
                        responsible_party VARCHAR(160) NULL,
                        keywords VARCHAR(500) NULL,
                        frequency VARCHAR(16) NULL,
                        owner_id BIGINT NOT NULL,
                        deleted TINYINT NOT NULL DEFAULT 0,
                        deleted_by BIGINT NULL,
                        deleted_at TIMESTAMP NULL,
                        created_by BIGINT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_by BIGINT NULL,
                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        UNIQUE KEY uk_dm_issue_code (tenant_id, project_id, issue_code)
                    )
                    """);
            execute(connection, """
                    CREATE TABLE dm_target_table (
                        tenant_id BIGINT NOT NULL DEFAULT 1,
                        table_code BIGINT NOT NULL,
                        project_id BIGINT NOT NULL,
                        system_code VARCHAR(64) NOT NULL,
                        table_name_en VARCHAR(128) NOT NULL,
                        table_name_cn VARCHAR(128) NOT NULL,
                        table_meaning VARCHAR(500) NULL,
                        table_category VARCHAR(32) NOT NULL,
                        owner_id BIGINT NOT NULL,
                        created_by BIGINT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_by BIGINT NULL,
                        updated_at TIMESTAMP NULL,
                        deleted TINYINT NOT NULL DEFAULT 0,
                        deleted_by BIGINT NULL,
                        deleted_at TIMESTAMP NULL,
                        PRIMARY KEY (table_code),
                        UNIQUE KEY uk_target_table_tenant_code (tenant_id, table_code),
                        UNIQUE KEY uk_target_table_en (tenant_id, project_id, system_code, table_name_en),
                        UNIQUE KEY uk_target_table_cn (tenant_id, project_id, system_code, table_name_cn)
                    )
                    """);
            execute(connection, """
                    CREATE TABLE dm_target_table_field (
                        field_code BIGINT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL DEFAULT 1,
                        table_code BIGINT NOT NULL,
                        field_name_en VARCHAR(128) NOT NULL,
                        field_name_cn VARCHAR(128) NOT NULL,
                        field_meaning VARCHAR(500) NULL,
                        code_description VARCHAR(500) NULL,
                        is_key_field TINYINT NULL,
                        is_primary_key TINYINT NULL,
                        is_nullable TINYINT NULL,
                        oracle_type VARCHAR(64) NULL,
                        mysql_type VARCHAR(64) NULL,
                        dict_code VARCHAR(64) NULL,
                        owner_id BIGINT NOT NULL,
                        deleted TINYINT NOT NULL DEFAULT 0,
                        created_by BIGINT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_by BIGINT NULL,
                        updated_at TIMESTAMP NULL,
                        UNIQUE KEY uk_target_field_en (tenant_id, table_code, field_name_en),
                        UNIQUE KEY uk_target_field_cn (tenant_id, table_code, field_name_cn),
                        KEY idx_target_field_table (tenant_id, table_code, deleted),
                        CONSTRAINT fk_target_field_table_code
                            FOREIGN KEY (tenant_id, table_code) REFERENCES dm_target_table (tenant_id, table_code)
                    )
                    """);
            execute(connection, "INSERT INTO arch_physical_subsystem (id, tenant_id, code, name, short_name) VALUES (10, 1, 'SYS-1', '系统一', 'SY1'), (20, 1, 'SYS-2', '系统二', 'SY2')");
            execute(connection, "INSERT INTO pm_project (id, tenant_id, project_code, project_name) VALUES (10, 1, 'P001', '项目A')");
        }

        // 夹具按 V175（system_id 下线）+ V177（table_code/field_code 主键）收敛后的结构创建；
        // V175 的 DDL 行为由 v175MigratesPlanAndMeetingSystemToSystemCode 覆盖，此处基线即目标避免中间迁移与收敛后结构冲突。
        assertTrue(flyway("175", "175").migrate().success);
        DriverManagerDataSource dataSource = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        DataMigrationPermissionService permissions = new DataMigrationPermissionService(jdbc, StubProjectAccess.allow());
        IssueService issueService = new IssueService(jdbc, permissions);
        ProjectComponentService componentService = new ProjectComponentService(jdbc, permissions);
        TargetTableService targetTableService = new TargetTableService(jdbc, permissions);
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        transaction.executeWithoutResult(status -> {
            Map<String, Object> component = new LinkedHashMap<>();
            component.put("projectId", 10L);
            component.put("physicalSubsystemCode", "SYS-2");
            component.put("totalCheck", 1);
            componentService.createComponent(component, ADMIN);

            Map<String, Object> componentArchOne = new LinkedHashMap<>();
            componentArchOne.put("projectId", 10L);
            componentArchOne.put("physicalSubsystemCode", "SYS-1");
            componentArchOne.put("totalCheck", 0);
            componentService.createComponent(componentArchOne, ADMIN);

            Map<String, Object> componentNoArch = new LinkedHashMap<>();
            componentNoArch.put("projectId", 10L);
            componentNoArch.put("physicalSubsystemCode", "NO-ARCH");
            componentService.createComponent(componentNoArch, ADMIN);

            Map<String, Object> issue = new LinkedHashMap<>();
            issue.put("projectId", 10L);
            issue.put("issueCode", "ISS-DUAL");
            issue.put("issueName", "Dual write issue");
            issue.put("granularity", "PROJECT");
            issue.put("systemCode", "SYS-1");
            issueService.create(issue, ADMIN);

            Map<String, Object> issueNoArch = new LinkedHashMap<>();
            issueNoArch.put("projectId", 10L);
            issueNoArch.put("issueCode", "ISS-NO-ARCH");
            issueNoArch.put("issueName", "No arch issue");
            issueNoArch.put("granularity", "PROJECT");
            issueNoArch.put("systemCode", "NO-ARCH");
            issueService.create(issueNoArch, ADMIN);

            Map<String, Object> table = new LinkedHashMap<>();
            table.put("projectId", 10L);
            table.put("systemCode", "SYS-1");
            table.put("tableNameEn", "dual_tbl");
            table.put("tableNameCn", "双写表");
            targetTableService.createTable("TARGET", table, ADMIN);

            Map<String, Object> ghostIssue = new LinkedHashMap<>();
            ghostIssue.put("projectId", 10L);
            ghostIssue.put("issueCode", "ISS-GHOST");
            ghostIssue.put("issueName", "Ghost issue");
            ghostIssue.put("granularity", "PROJECT");
            ghostIssue.put("systemCode", "GHOST-CODE");
            assertThrows(BusinessException.class, () -> issueService.create(ghostIssue, ADMIN));
        });

        try (Connection connection = connection()) {
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'dm_issue' AND column_name = 'system_id'"));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'dm_target_table' AND column_name = 'system_id'"));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'dm_component' AND column_name = 'physical_subsystem_id'"));
            assertEquals("SYS-1", value(connection, "SELECT system_code FROM dm_issue WHERE issue_code = 'ISS-DUAL'"));
            assertEquals("NO-ARCH", value(connection, "SELECT system_code FROM dm_issue WHERE issue_code = 'ISS-NO-ARCH'"));
            assertEquals("SYS-1", value(connection, "SELECT system_code FROM dm_target_table WHERE table_name_en = 'dual_tbl'"));
            assertEquals("NO-ARCH", value(connection, "SELECT physical_subsystem_code FROM dm_component WHERE id = (SELECT MIN(id) FROM dm_component WHERE physical_subsystem_code = 'NO-ARCH')"));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM dm_issue WHERE issue_code = 'ISS-GHOST'"));
        }
    }

    /** V175 迁移专项：dm_plan 重建为 system_code 维度键、dm_meeting_system 改存 project_id+system_code、id 引用列全部下线。 */
    @Test
    void v175MigratesPlanAndMeetingSystemToSystemCode() throws Exception {
        cleanFixtureTables();
        try (Connection connection = connection()) {
            execute(connection, """
                    CREATE TABLE arch_physical_subsystem (
                        id BIGINT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        code VARCHAR(160) NOT NULL,
                        short_name VARCHAR(160) NULL,
                        name VARCHAR(160) NULL,
                        business_group_name VARCHAR(160) NULL,
                        deleted TINYINT NOT NULL DEFAULT 0,
                        UNIQUE KEY uk_arch_physical_code (tenant_id, code)
                    )
                    """);
            execute(connection, """
                    CREATE TABLE dm_meeting (
                        meeting_id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        tenant_id BIGINT NOT NULL DEFAULT 1,
                        project_id BIGINT NOT NULL,
                        meeting_code VARCHAR(96) NOT NULL,
                        deleted TINYINT NOT NULL DEFAULT 0
                    )
                    """);
            execute(connection, """
                    CREATE TABLE dm_plan (
                        id BIGINT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL DEFAULT 1,
                        project_id BIGINT NOT NULL,
                        granularity VARCHAR(16) NOT NULL DEFAULT 'PROJECT',
                        plan_type VARCHAR(16) NOT NULL DEFAULT 'DATA',
                        system_id BIGINT NOT NULL DEFAULT 0,
                        plan_summary VARCHAR(1000) NULL,
                        doc_code VARCHAR(96) NOT NULL,
                        doc_name VARCHAR(255) NOT NULL,
                        owner_id BIGINT NOT NULL,
                        deleted TINYINT NOT NULL DEFAULT 0,
                        active_doc_code VARCHAR(96)
                            GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN doc_code ELSE NULL END) STORED,
                        active_dimension_key VARCHAR(160)
                            GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN CONCAT_WS(':', tenant_id, project_id, granularity, plan_type, system_id) ELSE NULL END) STORED,
                        UNIQUE KEY uk_dm_plan_active_dimension (active_dimension_key),
                        KEY idx_dm_plan_system (tenant_id, system_id, deleted)
                    )
                    """);
            execute(connection, """
                    CREATE TABLE dm_meeting_system (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        tenant_id BIGINT NOT NULL DEFAULT 1,
                        meeting_id BIGINT NOT NULL,
                        subsystem_id BIGINT NOT NULL,
                        created_by BIGINT NOT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        UNIQUE KEY uk_dm_meeting_system (tenant_id, meeting_id, subsystem_id),
                        KEY idx_dm_meeting_system_subsystem (tenant_id, subsystem_id)
                    )
                    """);
            execute(connection, """
                    CREATE TABLE dm_issue (
                        id BIGINT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL DEFAULT 1,
                        project_id BIGINT NOT NULL,
                        issue_code VARCHAR(96) NOT NULL,
                        system_code VARCHAR(96) NULL,
                        system_id BIGINT NULL,
                        deleted TINYINT NOT NULL DEFAULT 0,
                        KEY idx_dm_issue_system_id (tenant_id, system_id, deleted)
                    )
                    """);
            execute(connection, """
                    CREATE TABLE dm_target_table (
                        id BIGINT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL DEFAULT 1,
                        project_id BIGINT NOT NULL,
                        table_code VARCHAR(64) NOT NULL,
                        system_code VARCHAR(64) NOT NULL,
                        system_id BIGINT NULL,
                        deleted TINYINT NOT NULL DEFAULT 0,
                        KEY idx_target_table_system_id (tenant_id, project_id, system_id, deleted)
                    )
                    """);
            execute(connection, """
                    CREATE TABLE dm_component (
                        id BIGINT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL DEFAULT 1,
                        project_id BIGINT NOT NULL,
                        physical_subsystem_code VARCHAR(64) NULL,
                        physical_subsystem_id BIGINT NULL,
                        deleted TINYINT NOT NULL DEFAULT 0,
                        KEY idx_dm_component_subsystem_id (tenant_id, project_id, physical_subsystem_id, deleted)
                    )
                    """);
            execute(connection, "INSERT INTO arch_physical_subsystem (id, tenant_id, code, name) VALUES (10, 1, 'SYS-1', '系统一'), (20, 1, 'SYS-2', '系统二')");
            execute(connection, "INSERT INTO dm_meeting (meeting_id, tenant_id, project_id, meeting_code) VALUES (1, 1, 100, 'MEET-1')");
            execute(connection, "INSERT INTO dm_meeting_system (id, tenant_id, meeting_id, subsystem_id, created_by) VALUES (1, 1, 1, 10, 1), (2, 1, 1, 20, 1)");
            execute(connection, "INSERT INTO dm_issue (id, tenant_id, project_id, issue_code, system_code, system_id) VALUES (1, 1, 100, 'ISS-1', 'SYS-1', 10)");
            execute(connection, "INSERT INTO dm_target_table (id, tenant_id, project_id, table_code, system_code, system_id) VALUES (1, 1, 100, 'TBL-1', 'SYS-2', 20)");
        }

        assertTrue(flyway("174", "175").migrate().success);
        try (Connection connection = connection()) {
            // id 引用列全部下线
            for (String[] pair : new String[][]{
                {"dm_plan", "system_id"},
                {"dm_issue", "system_id"},
                {"dm_target_table", "system_id"},
                {"dm_component", "physical_subsystem_id"},
                {"dm_meeting_system", "subsystem_id"},
            }) {
                assertEquals(0, count(connection, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = '%s' AND column_name = '%s'".formatted(pair[0], pair[1])), pair[1] + " should be dropped");
            }
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'dm_issue' AND index_name = 'idx_dm_issue_system_id'"));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'dm_target_table' AND index_name = 'idx_target_table_system_id'"));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'dm_component' AND index_name = 'idx_dm_component_subsystem_id'"));

            // dm_plan：system_code 取代 system_id，维度唯一键与系统索引重建
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'dm_plan' AND column_name = 'system_code'"));
            assertEquals("tenant_id,project_id,system_code,deleted", indexColumns(connection, "dm_plan", "idx_dm_plan_system"));
            assertEquals("active_dimension_key", indexColumns(connection, "dm_plan", "uk_dm_plan_active_dimension"));
            // 项目级空串哨兵：同项目两条项目级方案冲突；系统级按编号区分，同编号冲突
            execute(connection, "INSERT INTO dm_plan (id, tenant_id, project_id, granularity, plan_type, system_code, doc_code, doc_name, owner_id) VALUES (10, 1, 100, 'PROJECT', 'DATA', '', 'PLAN-A', '方案A', 1)");
            assertThrows(SQLException.class, () -> execute(connection,
                    "INSERT INTO dm_plan (id, tenant_id, project_id, granularity, plan_type, system_code, doc_code, doc_name, owner_id) VALUES (11, 1, 100, 'PROJECT', 'DATA', '', 'PLAN-B', '方案B', 1)"));
            execute(connection, "INSERT INTO dm_plan (id, tenant_id, project_id, granularity, plan_type, system_code, doc_code, doc_name, owner_id) VALUES (12, 1, 100, 'SYSTEM', 'DATA', 'SYS-1', 'PLAN-C', '方案C', 1)");
            assertThrows(SQLException.class, () -> execute(connection,
                    "INSERT INTO dm_plan (id, tenant_id, project_id, granularity, plan_type, system_code, doc_code, doc_name, owner_id) VALUES (13, 1, 100, 'SYSTEM', 'DATA', 'SYS-1', 'PLAN-D', '方案D', 1)"));

            // dm_meeting_system：project_id 与 system_code 回填，子系统 id 换为编号
            assertEquals(100L, valueLong(connection, "SELECT project_id FROM dm_meeting_system WHERE id = 1"));
            assertEquals(100L, valueLong(connection, "SELECT project_id FROM dm_meeting_system WHERE id = 2"));
            assertEquals("SYS-1", value(connection, "SELECT system_code FROM dm_meeting_system WHERE id = 1"));
            assertEquals("SYS-2", value(connection, "SELECT system_code FROM dm_meeting_system WHERE id = 2"));
            assertEquals("tenant_id,meeting_id,system_code", indexColumns(connection, "dm_meeting_system", "uk_dm_meeting_system"));
            assertEquals("tenant_id,project_id,system_code", indexColumns(connection, "dm_meeting_system", "idx_dm_meeting_system_project"));
        }
    }

    @Test
    void v171BackfillsSubsystemIdByCode() throws Exception {
        cleanFixtureTables();
        try (Connection connection = connection()) {
            execute(connection, """
                    CREATE TABLE arch_physical_subsystem (
                        id BIGINT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        code VARCHAR(32) NOT NULL,
                        name VARCHAR(200) NOT NULL,
                        short_name VARCHAR(100) NOT NULL,
                        deleted TINYINT NOT NULL DEFAULT 0,
                        UNIQUE KEY uk_arch_physical_code (tenant_id, code)
                    )
                    """);
            execute(connection, """
                    CREATE TABLE dm_issue (
                        id BIGINT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL DEFAULT 1,
                        project_id BIGINT NOT NULL,
                        issue_code VARCHAR(96) NOT NULL,
                        system_code VARCHAR(96) NULL,
                        deleted TINYINT NOT NULL DEFAULT 0
                    )
                    """);
            execute(connection, """
                    CREATE TABLE dm_target_table (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        tenant_id BIGINT NOT NULL DEFAULT 1,
                        project_id BIGINT NOT NULL,
                        table_code VARCHAR(64) NOT NULL,
                        system_code VARCHAR(64) NOT NULL,
                        table_name_en VARCHAR(128) NOT NULL,
                        table_name_cn VARCHAR(128) NOT NULL,
                        table_category VARCHAR(32) NOT NULL,
                        deleted TINYINT NOT NULL DEFAULT 0
                    )
                    """);
            execute(connection, """
                    CREATE TABLE dm_component (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        tenant_id BIGINT NOT NULL DEFAULT 1,
                        project_id BIGINT NOT NULL,
                        physical_subsystem_code VARCHAR(64) NULL,
                        deleted TINYINT NOT NULL DEFAULT 0
                    )
                    """);
            execute(connection, """
                    INSERT INTO arch_physical_subsystem (id, tenant_id, code, name, short_name) VALUES
                        (10, 1, 'SYS-1', '系统一', 'SY1'),
                        (20, 1, 'SYS-2', '系统二', 'SY2'),
                        (30, 2, 'SYS-3', '他租户系统', 'SY3')
                    """);
            execute(connection, """
                    INSERT INTO dm_issue (id, tenant_id, project_id, issue_code, system_code) VALUES
                        (1, 1, 100, 'ISS-1', 'SYS-1'),
                        (2, 1, 100, 'ISS-2', 'GHOST-CODE'),
                        (3, 3, 300, 'ISS-3', 'SYS-3')
                    """);
            execute(connection, """
                    INSERT INTO dm_target_table (id, tenant_id, project_id, table_code, system_code, table_name_en, table_name_cn, table_category) VALUES
                        (1, 1, 100, 'TBL-1', 'SYS-2', 'tbl_one', '表一', 'TARGET'),
                        (2, 1, 100, 'TBL-2', 'GHOST-CODE', 'tbl_two', '表二', 'TARGET')
                    """);
            execute(connection, """
                    INSERT INTO dm_component (id, tenant_id, project_id, physical_subsystem_code) VALUES
                        (1, 1, 100, 'SYS-1'),
                        (2, 1, 100, NULL)
                    """);
        }

        assertTrue(flyway("170", "171").migrate().success);
        try (Connection connection = connection()) {
            assertEquals(10L, valueLong(connection, "SELECT system_id FROM dm_issue WHERE id = 1"));
            assertEquals(null, valueLong(connection, "SELECT system_id FROM dm_issue WHERE id = 2"));
            // 跨租户 SYS-3：按 (tenant_id, code) 匹配不到，保持 NULL
            assertEquals(null, valueLong(connection, "SELECT system_id FROM dm_issue WHERE id = 3"));
            assertEquals(20L, valueLong(connection, "SELECT system_id FROM dm_target_table WHERE id = 1"));
            assertEquals(null, valueLong(connection, "SELECT system_id FROM dm_target_table WHERE id = 2"));
            assertEquals(10L, valueLong(connection, "SELECT physical_subsystem_id FROM dm_component WHERE id = 1"));
            assertEquals(null, valueLong(connection, "SELECT physical_subsystem_id FROM dm_component WHERE id = 2"));
            assertEquals(1, count(connection, "SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'dm_issue' AND index_name = 'idx_dm_issue_system_id'"));
            assertEquals(1, count(connection, "SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'dm_target_table' AND index_name = 'idx_target_table_system_id'"));
            assertEquals(1, count(connection, "SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'dm_component' AND index_name = 'idx_dm_component_subsystem_id'"));
        }
    }

    @Test
    void v172ReplacesDeletedUniqueKeysWithActiveGeneratedColumns() throws Exception {
        cleanFixtureTables();
        try (Connection connection = connection()) {
            execute(connection, """
                    CREATE TABLE dm_component (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        tenant_id BIGINT NOT NULL DEFAULT 1,
                        project_id BIGINT NOT NULL,
                        physical_subsystem_code VARCHAR(64) NULL,
                        deleted TINYINT NOT NULL DEFAULT 0,
                        UNIQUE KEY uk_dm_component_subsystem (tenant_id, project_id, physical_subsystem_code, deleted)
                    )
                    """);
            execute(connection, """
                    CREATE TABLE dm_target_table (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        tenant_id BIGINT NOT NULL DEFAULT 1,
                        project_id BIGINT NOT NULL,
                        table_code VARCHAR(64) NOT NULL,
                        system_code VARCHAR(64) NOT NULL,
                        table_name_en VARCHAR(128) NOT NULL,
                        table_name_cn VARCHAR(128) NOT NULL,
                        table_category VARCHAR(32) NOT NULL,
                        deleted TINYINT NOT NULL DEFAULT 0,
                        UNIQUE KEY uk_target_table_code (tenant_id, table_code, deleted),
                        UNIQUE KEY uk_target_table_en (tenant_id, project_id, system_code, table_name_en, deleted),
                        UNIQUE KEY uk_target_table_cn (tenant_id, project_id, system_code, table_name_cn, deleted)
                    )
                    """);
            execute(connection, """
                    CREATE TABLE dm_target_table_field (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        tenant_id BIGINT NOT NULL DEFAULT 1,
                        field_code VARCHAR(64) NOT NULL,
                        table_id BIGINT NOT NULL,
                        field_name_en VARCHAR(128) NOT NULL,
                        field_name_cn VARCHAR(128) NOT NULL,
                        deleted TINYINT NOT NULL DEFAULT 0,
                        UNIQUE KEY uk_target_field_code (tenant_id, field_code, deleted),
                        UNIQUE KEY uk_target_field_en (tenant_id, table_id, field_name_en, deleted),
                        UNIQUE KEY uk_target_field_cn (tenant_id, table_id, field_name_cn, deleted)
                    )
                    """);
            execute(connection, """
                    CREATE TABLE dm_issue (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        tenant_id BIGINT NOT NULL DEFAULT 1,
                        project_id BIGINT NOT NULL,
                        issue_code VARCHAR(96) NOT NULL,
                        deleted TINYINT NOT NULL DEFAULT 0,
                        active_issue_code VARCHAR(96)
                            GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN issue_code ELSE NULL END) STORED,
                        UNIQUE KEY uk_dm_issue_code (tenant_id, project_id, issue_code, deleted),
                        UNIQUE KEY uk_dm_issue_active_code (tenant_id, project_id, active_issue_code)
                    )
                    """);
        }

        assertTrue(flyway("171", "172").migrate().success);
        try (Connection connection = connection()) {
            // 旧含 deleted 唯一键已删除
            for (String[] pair : new String[][]{
                {"dm_component", "uk_dm_component_subsystem"},
                {"dm_target_table", "uk_target_table_code"},
                {"dm_target_table", "uk_target_table_en"},
                {"dm_target_table", "uk_target_table_cn"},
                {"dm_target_table_field", "uk_target_field_code"},
                {"dm_target_table_field", "uk_target_field_en"},
                {"dm_target_table_field", "uk_target_field_cn"},
                {"dm_issue", "uk_dm_issue_code"},
            }) {
                assertEquals(0, count(connection, "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = '%s' AND index_name = '%s'".formatted(pair[0], pair[1])), pair[1] + " should be dropped");
            }
            // 新活动生成列唯一键存在且列序精确
            assertEquals("tenant_id,project_id,active_physical_subsystem_code", indexColumns(connection, "dm_component", "uk_dm_component_active_subsystem"));
            for (String expected : new String[]{
                "tenant_id,active_table_code",
                "tenant_id,project_id,active_system_code,active_table_name_en",
                "tenant_id,project_id,active_system_code,active_table_name_cn",
            }) {
                String name = expected.startsWith("tenant_id,active_table_code") ? "uk_target_table_active_code"
                        : expected.contains("active_table_name_en") ? "uk_target_table_active_en" : "uk_target_table_active_cn";
                assertEquals(expected, indexColumns(connection, "dm_target_table", name));
            }
            for (String expected : new String[]{
                "tenant_id,active_field_code",
                "tenant_id,table_id,active_field_name_en",
                "tenant_id,table_id,active_field_name_cn",
            }) {
                String name = expected.startsWith("tenant_id,active_field_code") ? "uk_target_field_active_code"
                        : expected.contains("active_field_name_en") ? "uk_target_field_active_en" : "uk_target_field_active_cn";
                assertEquals(expected, indexColumns(connection, "dm_target_table_field", name));
            }
            assertTrue(indexColumns(connection, "dm_issue", "uk_dm_issue_active_code").startsWith("tenant_id,project_id,active_issue_code"));

            // 行为：活动行唯一、软删行允许重建、恢复冲突被拒
            execute(connection, "INSERT INTO dm_target_table (tenant_id, project_id, table_code, system_code, table_name_en, table_name_cn, table_category) VALUES (1, 100, 'TBL-A', 'SYS-1', 'tbl_a', '表A', 'TARGET')");
            assertThrows(SQLException.class, () -> execute(connection,
                    "INSERT INTO dm_target_table (tenant_id, project_id, table_code, system_code, table_name_en, table_name_cn, table_category) VALUES (1, 100, 'TBL-A', 'SYS-1', 'tbl_a', '表A', 'TARGET')"));
            execute(connection, "UPDATE dm_target_table SET deleted = 1 WHERE table_code = 'TBL-A'");
            // 软删行同值可重建（活动生成列取 NULL）
            execute(connection, "INSERT INTO dm_target_table (tenant_id, project_id, table_code, system_code, table_name_en, table_name_cn, table_category) VALUES (1, 100, 'TBL-A', 'SYS-1', 'tbl_a', '表A', 'TARGET')");
            execute(connection, "INSERT INTO dm_issue (tenant_id, project_id, issue_code) VALUES (1, 100, 'ISS-A')");
            execute(connection, "UPDATE dm_issue SET deleted = 1 WHERE issue_code = 'ISS-A'");
            execute(connection, "INSERT INTO dm_issue (tenant_id, project_id, issue_code) VALUES (1, 100, 'ISS-A')");
            // 软删行还原时若活动行同编号仍存在，生成列唯一键拒绝（恢复冲突语义）
            assertThrows(SQLException.class, () -> execute(connection,
                    "UPDATE dm_issue SET deleted = 0 WHERE issue_code = 'ISS-A' AND deleted = 1"));
        }
    }

    @Test
    void v173AddsActiveCodeColumnComments() throws Exception {
        cleanFixtureTables();
        try (Connection connection = connection()) {
            execute(connection, """
                    CREATE TABLE dm_issue (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        tenant_id BIGINT NOT NULL DEFAULT 1,
                        project_id BIGINT NOT NULL,
                        issue_code VARCHAR(96) NOT NULL,
                        deleted TINYINT NOT NULL DEFAULT 0,
                        active_issue_code VARCHAR(96)
                            GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN issue_code ELSE NULL END) STORED
                    )
                    """);
            execute(connection, """
                    CREATE TABLE dm_meeting (
                        meeting_id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        tenant_id BIGINT NOT NULL DEFAULT 1,
                        project_id BIGINT NOT NULL,
                        meeting_code VARCHAR(96) NOT NULL,
                        deleted TINYINT NOT NULL DEFAULT 0,
                        active_meeting_code VARCHAR(96)
                            GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN meeting_code ELSE NULL END) STORED
                    )
                    """);
        }

        assertTrue(flyway("172", "173").migrate().success);
        try (Connection connection = connection()) {
            assertTrue(value(connection, """
                    SELECT column_comment FROM information_schema.columns
                    WHERE table_schema = DATABASE() AND table_name = 'dm_issue' AND column_name = 'active_issue_code'
                    """).contains("仅未删除记录取值"));
            assertTrue(value(connection, """
                    SELECT column_comment FROM information_schema.columns
                    WHERE table_schema = DATABASE() AND table_name = 'dm_meeting' AND column_name = 'active_meeting_code'
                    """).contains("仅未删除记录取值"));
        }
    }

    /** 9.2 P1「审计缺项目维度」：V176 为 dm_operation_log 追加 project_id 与项目级索引；写入侧从实体项目上下文填充。 */
    @Test
    void v176AddsOperationLogProjectDimension() throws Exception {
        cleanFixtureTables();
        try (Connection connection = connection()) {
            execute(connection, """
                    CREATE TABLE dm_operation_log (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        tenant_id BIGINT NOT NULL DEFAULT 1,
                        actor_id BIGINT NOT NULL,
                        operation_code VARCHAR(64) NOT NULL,
                        entity_type VARCHAR(64) NOT NULL,
                        entity_id BIGINT NULL,
                        result_code VARCHAR(16) NOT NULL DEFAULT 'SUCCESS',
                        trace_id VARCHAR(64),
                        detail_json JSON,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        KEY idx_dm_operation_log (tenant_id, created_at, actor_id)
                    )
                    """);
            execute(connection, """
                    CREATE TABLE arch_physical_subsystem (
                        id BIGINT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        code VARCHAR(160) NOT NULL,
                        short_name VARCHAR(160) NULL,
                        name VARCHAR(160) NULL,
                        business_group_name VARCHAR(160) NULL,
                        description VARCHAR(500) NULL,
                        responsible_team_name_snapshot VARCHAR(160) NULL,
                        deleted TINYINT NOT NULL DEFAULT 0,
                        UNIQUE KEY uk_arch_physical_code (tenant_id, code)
                    )
                    """);
            execute(connection, """
                    CREATE TABLE pm_project (
                        id BIGINT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        project_code VARCHAR(64) NULL,
                        project_name VARCHAR(128) NOT NULL,
                        deleted TINYINT NOT NULL DEFAULT 0
                    )
                    """);
            execute(connection, """
                    CREATE TABLE sys_user (
                        id BIGINT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        display_name VARCHAR(128) NOT NULL,
                        deleted TINYINT NOT NULL DEFAULT 0
                    )
                    """);
            execute(connection, """
                    CREATE TABLE sys_role (
                        id BIGINT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        role_code VARCHAR(64) NOT NULL,
                        status TINYINT NOT NULL DEFAULT 1,
                        deleted TINYINT NOT NULL DEFAULT 0
                    )
                    """);
            execute(connection, """
                    CREATE TABLE sys_user_role (
                        user_id BIGINT NOT NULL,
                        role_id BIGINT NOT NULL,
                        tenant_id BIGINT NOT NULL
                    )
                    """);
            execute(connection, "INSERT INTO sys_role (id, tenant_id, role_code) VALUES (1, 1, 'ADMIN')");
            execute(connection, "INSERT INTO sys_user_role (user_id, role_id, tenant_id) VALUES (1, 1, 1)");
            execute(connection, """
                    CREATE TABLE dm_component (
                        id BIGINT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL DEFAULT 1,
                        project_id BIGINT NOT NULL,
                        physical_subsystem_code VARCHAR(64) NULL,
                        total_check TINYINT NOT NULL DEFAULT 0,
                        owner_id BIGINT NOT NULL,
                        created_by BIGINT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_by BIGINT NULL,
                        updated_at TIMESTAMP NULL,
                        deleted TINYINT NOT NULL DEFAULT 0,
                        deleted_by BIGINT NULL,
                        deleted_at TIMESTAMP NULL,
                        UNIQUE KEY uk_dm_component_subsystem (tenant_id, project_id, physical_subsystem_code, deleted)
                    )
                    """);
            execute(connection, """
                    CREATE TABLE dm_issue (
                        id BIGINT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL DEFAULT 1,
                        project_id BIGINT NOT NULL,
                        issue_code VARCHAR(96) NOT NULL,
                        issue_name VARCHAR(255) NOT NULL,
                        granularity VARCHAR(32) NOT NULL,
                        system_code VARCHAR(96) NULL,
                        issue_source VARCHAR(64) NULL,
                        defect_type VARCHAR(64) NULL,
                        issue_description TEXT NULL,
                        solution TEXT NULL,
                        meeting_conclusion TEXT NULL,
                        processing_steps TEXT NULL,
                        business_scenario TEXT NULL,
                        handler VARCHAR(128) NULL,
                        responsible_party VARCHAR(128) NULL,
                        keywords VARCHAR(255) NULL,
                        frequency VARCHAR(32) NULL,
                        owner_id BIGINT NOT NULL,
                        created_by BIGINT NOT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_by BIGINT NULL,
                        updated_at TIMESTAMP NULL,
                        deleted TINYINT NOT NULL DEFAULT 0,
                        deleted_by BIGINT NULL,
                        deleted_at TIMESTAMP NULL
                    )
                    """);
            execute(connection, """
                    CREATE TABLE dm_issue_relation (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        tenant_id BIGINT NOT NULL,
                        issue_id BIGINT NOT NULL,
                        related_type VARCHAR(32) NOT NULL,
                        related_id BIGINT NOT NULL,
                        created_by BIGINT NULL,
                        created_at TIMESTAMP NULL
                    )
                    """);
            execute(connection, """
                    CREATE TABLE dm_meeting (
                        meeting_id BIGINT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        project_id BIGINT NOT NULL,
                        meeting_code VARCHAR(96) NOT NULL,
                        meeting_title VARCHAR(500) NOT NULL,
                        deleted TINYINT NOT NULL DEFAULT 0
                    )
                    """);
            execute(connection, """
                    CREATE TABLE dm_target_table (
                        tenant_id BIGINT NOT NULL,
                        table_code BIGINT NOT NULL,
                        project_id BIGINT NOT NULL,
                        system_code VARCHAR(64) NOT NULL,
                        table_name_en VARCHAR(128) NOT NULL,
                        table_name_cn VARCHAR(128) NOT NULL,
                        table_meaning VARCHAR(512) NULL,
                        table_category VARCHAR(32) NOT NULL,
                        owner_id BIGINT NOT NULL,
                        created_by BIGINT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_by BIGINT NULL,
                        updated_at TIMESTAMP NULL,
                        deleted TINYINT NOT NULL DEFAULT 0,
                        PRIMARY KEY (table_code)
                    )
                    """);
            execute(connection, """
                    CREATE TABLE dm_target_table_field (
                        field_code BIGINT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        table_code BIGINT NOT NULL,
                        field_name_en VARCHAR(128) NOT NULL,
                        field_name_cn VARCHAR(128) NOT NULL,
                        field_meaning VARCHAR(512) NULL,
                        deleted TINYINT NOT NULL DEFAULT 0
                    )
                    """);
            execute(connection, "INSERT INTO arch_physical_subsystem (id, tenant_id, code, name, short_name) VALUES (10, 1, 'SYS-1', '系统一', 'SY1'), (20, 1, 'SYS-2', '系统二', 'SY2')");
            execute(connection, "INSERT INTO pm_project (id, tenant_id, project_code, project_name) VALUES (10, 1, 'P001', '项目A'), (20, 1, 'P002', '项目B')");
            execute(connection, "INSERT INTO dm_component (id, tenant_id, project_id, physical_subsystem_code, owner_id) VALUES (10, 1, 10, 'SYS-1', 1), (20, 1, 20, 'SYS-2', 1)");
        }

        assertTrue(flyway("175", "176").migrate().success);
        try (Connection connection = connection()) {
            // 迁移追加 project_id（BIGINT NOT NULL DEFAULT 0）与项目级审计索引
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'dm_operation_log' AND column_name = 'project_id' AND data_type = 'bigint' AND is_nullable = 'NO' AND column_default = '0'"));
            assertEquals("tenant_id,project_id,entity_type,created_at", indexColumns(connection, "dm_operation_log", "idx_dm_operation_log_project"));
            // 既有审计索引保留
            assertTrue(indexColumns(connection, "dm_operation_log", "idx_dm_operation_log").startsWith("tenant_id,created_at"));
        }

        DriverManagerDataSource dataSource = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        DataMigrationPermissionService permissions = new DataMigrationPermissionService(jdbc, StubProjectAccess.allow());
        IssueService issueService = new IssueService(jdbc, permissions);
        TargetTableService targetTableService = new TargetTableService(jdbc, permissions);
        ProjectComponentService componentService = new ProjectComponentService(jdbc, permissions);
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        transaction.executeWithoutResult(status -> {
            // 新增：项目来自入参 projectId
            Map<String, Object> issueA = new LinkedHashMap<>();
            issueA.put("projectId", 10L);
            issueA.put("issueCode", "ISS-A");
            issueA.put("issueName", "项目A问题");
            issueA.put("granularity", "PROJECT");
            issueA.put("systemCode", "SYS-1");
            Map<String, Object> createdA = issueService.create(issueA, ADMIN);

            Map<String, Object> issueB = new LinkedHashMap<>();
            issueB.put("projectId", 20L);
            issueB.put("issueCode", "ISS-B");
            issueB.put("issueName", "项目B问题");
            issueB.put("granularity", "PROJECT");
            issueB.put("systemCode", "SYS-2");
            Map<String, Object> createdB = issueService.create(issueB, ADMIN);

            // 维护：项目来自库中实体 project_id
            issueService.update(((Number) createdA.get("id")).longValue(), issueA, ADMIN);
            issueService.delete(List.of(((Number) createdA.get("id")).longValue()), ADMIN);
            issueService.restore(List.of(((Number) createdA.get("id")).longValue()), ADMIN);
            issueService.delete(List.of(((Number) createdA.get("id")).longValue()), ADMIN);
            issueService.purge(List.of(((Number) createdA.get("id")).longValue()), ADMIN);
            issueService.delete(List.of(((Number) createdB.get("id")).longValue()), ADMIN);

            // 清空回收站：无实体可回查，审计项目取操作范围 scope
            issueService.purgeAll(20L, ADMIN);

            // 目标表与组件：system_code 关联系统，审计同样落项目
            Map<String, Object> table = new LinkedHashMap<>();
            table.put("projectId", 10L);
            table.put("systemCode", "SYS-1");
            table.put("tableNameEn", "audit_tbl");
            table.put("tableNameCn", "审计表");
            targetTableService.createTable("TARGET", table, ADMIN);

            Map<String, Object> component = new LinkedHashMap<>();
            component.put("projectId", 10L);
            component.put("physicalSubsystemCode", "SYS-2");
            component.put("totalCheck", 0);
            componentService.createComponent(component, ADMIN);
        });

        try (Connection connection = connection()) {
            // 写入侧：每类操作都带实体项目
            assertEquals(1L, count(connection, "SELECT COUNT(*) FROM dm_operation_log WHERE tenant_id = 1 AND project_id = 10 AND operation_code = 'ISSUE_CREATE'"));
            assertEquals(1L, count(connection, "SELECT COUNT(*) FROM dm_operation_log WHERE tenant_id = 1 AND project_id = 10 AND operation_code = 'ISSUE_UPDATE'"));
            assertEquals(2L, count(connection, "SELECT COUNT(*) FROM dm_operation_log WHERE tenant_id = 1 AND project_id = 10 AND operation_code = 'ISSUE_DELETE'"));
            assertEquals(1L, count(connection, "SELECT COUNT(*) FROM dm_operation_log WHERE tenant_id = 1 AND project_id = 10 AND operation_code = 'ISSUE_RESTORE'"));
            assertEquals(1L, count(connection, "SELECT COUNT(*) FROM dm_operation_log WHERE tenant_id = 1 AND project_id = 10 AND operation_code = 'ISSUE_PURGE'"));
            assertEquals(1L, count(connection, "SELECT COUNT(*) FROM dm_operation_log WHERE tenant_id = 1 AND project_id = 20 AND operation_code = 'ISSUE_CREATE'"));
            assertEquals(1L, count(connection, "SELECT COUNT(*) FROM dm_operation_log WHERE tenant_id = 1 AND project_id = 20 AND operation_code = 'ISSUE_DELETE'"));
            // 清空回收站类操作：project_id = 操作范围，entity_id = 0
            assertEquals(1L, count(connection, "SELECT COUNT(*) FROM dm_operation_log WHERE tenant_id = 1 AND project_id = 20 AND operation_code = 'ISSUE_PURGE_ALL' AND entity_id = 0"));
            assertEquals(1L, count(connection, "SELECT COUNT(*) FROM dm_operation_log WHERE tenant_id = 1 AND project_id = 10 AND operation_code = 'TARGET_TABLE_CREATE'"));
            assertEquals(1L, count(connection, "SELECT COUNT(*) FROM dm_operation_log WHERE tenant_id = 1 AND project_id = 10 AND operation_code = 'COMPONENT_CREATE'"));
            // 项目级审计查询：按项目分组可直接统计，不依赖回查实体
            assertEquals(2L, count(connection, "SELECT COUNT(DISTINCT project_id) FROM dm_operation_log WHERE tenant_id = 1"));
            assertEquals(8L, count(connection, "SELECT COUNT(*) FROM dm_operation_log WHERE tenant_id = 1 AND project_id = 10"));
            assertEquals(3L, count(connection, "SELECT COUNT(*) FROM dm_operation_log WHERE tenant_id = 1 AND project_id = 20"));
        }
    }

    /** T41 迁移专项：V177 把 dm_target_table / dm_target_table_field 主键收敛为业务编号（table_code/field_code）。 */
    @Test
    void v177ConvertsTargetTablePrimaryKeyToCode() throws Exception {
        cleanFixtureTables();
        try (Connection connection = connection()) {
            execute(connection, """
                    CREATE TABLE arch_physical_subsystem (
                        id BIGINT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        code VARCHAR(160) NOT NULL,
                        short_name VARCHAR(160) NULL,
                        name VARCHAR(160) NULL,
                        business_group_name VARCHAR(160) NULL,
                        deleted TINYINT NOT NULL DEFAULT 0,
                        UNIQUE KEY uk_arch_physical_code (tenant_id, code)
                    )
                    """);
            execute(connection, """
                    CREATE TABLE pm_project (
                        id BIGINT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        project_code VARCHAR(64) NULL,
                        project_name VARCHAR(128) NOT NULL,
                        deleted TINYINT NOT NULL DEFAULT 0
                    )
                    """);
            execute(connection, """
                    CREATE TABLE sys_user (
                        id BIGINT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        display_name VARCHAR(128) NOT NULL,
                        deleted TINYINT NOT NULL DEFAULT 0
                    )
                    """);
            execute(connection, """
                    CREATE TABLE sys_role (
                        id BIGINT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        role_code VARCHAR(64) NOT NULL,
                        status TINYINT NOT NULL DEFAULT 1,
                        deleted TINYINT NOT NULL DEFAULT 0
                    )
                    """);
            execute(connection, """
                    CREATE TABLE sys_user_role (
                        user_id BIGINT NOT NULL,
                        role_id BIGINT NOT NULL,
                        tenant_id BIGINT NOT NULL
                    )
                    """);
            execute(connection, "INSERT INTO sys_role (id, tenant_id, role_code) VALUES (1, 1, 'ADMIN')");
            execute(connection, "INSERT INTO sys_user_role (user_id, role_id, tenant_id) VALUES (1, 1, 1)");
            execute(connection, """
                    CREATE TABLE dm_operation_log (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        tenant_id BIGINT NOT NULL,
                        project_id BIGINT NOT NULL DEFAULT 0,
                        actor_id BIGINT NOT NULL,
                        operation_code VARCHAR(64) NOT NULL,
                        entity_type VARCHAR(64) NOT NULL,
                        entity_id BIGINT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        KEY idx_dm_operation_log (tenant_id, created_at, actor_id),
                        KEY idx_dm_operation_log_project (tenant_id, project_id, entity_type, created_at)
                    )
                    """);
            execute(connection, """
                    CREATE TABLE dm_component (
                        id BIGINT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL DEFAULT 1,
                        project_id BIGINT NOT NULL,
                        physical_subsystem_code VARCHAR(64) NULL,
                        total_check TINYINT NOT NULL DEFAULT 0,
                        owner_id BIGINT NOT NULL,
                        created_by BIGINT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_by BIGINT NULL,
                        updated_at TIMESTAMP NULL,
                        deleted TINYINT NOT NULL DEFAULT 0,
                        UNIQUE KEY uk_dm_component_subsystem (tenant_id, project_id, physical_subsystem_code, deleted)
                    )
                    """);
            execute(connection, """
                    CREATE TABLE dm_issue_relation (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        tenant_id BIGINT NOT NULL,
                        issue_id BIGINT NOT NULL,
                        related_type VARCHAR(32) NOT NULL,
                        related_id BIGINT NOT NULL,
                        created_by BIGINT NULL,
                        created_at TIMESTAMP NULL
                    )
                    """);
            // V176 基线（旧 id 结构，含 V172 生成列与唯一键、V161 已删冗余 table_code 的字段表）
            execute(connection, """
                    CREATE TABLE dm_target_table (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        tenant_id BIGINT NOT NULL DEFAULT 1,
                        table_code VARCHAR(64) NOT NULL,
                        project_id BIGINT NOT NULL,
                        system_code VARCHAR(64) NOT NULL,
                        table_name_en VARCHAR(128) NOT NULL,
                        table_name_cn VARCHAR(128) NOT NULL,
                        table_meaning VARCHAR(500) NULL,
                        table_category VARCHAR(32) NOT NULL,
                        owner_id BIGINT NOT NULL,
                        created_by BIGINT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_by BIGINT NULL,
                        updated_at TIMESTAMP NULL,
                        deleted TINYINT NOT NULL DEFAULT 0,
                        active_table_code VARCHAR(64)
                            GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN table_code ELSE NULL END) STORED,
                        active_system_code VARCHAR(64)
                            GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN system_code ELSE NULL END) STORED,
                        active_table_name_en VARCHAR(128)
                            GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN table_name_en ELSE NULL END) STORED,
                        active_table_name_cn VARCHAR(128)
                            GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN table_name_cn ELSE NULL END) STORED,
                        UNIQUE KEY uk_target_table_active_code (tenant_id, active_table_code),
                        UNIQUE KEY uk_target_table_active_en (tenant_id, project_id, active_system_code, active_table_name_en),
                        UNIQUE KEY uk_target_table_active_cn (tenant_id, project_id, active_system_code, active_table_name_cn),
                        KEY idx_target_table_list (tenant_id, project_id, system_code, deleted, updated_at),
                        KEY idx_target_table_category (tenant_id, table_category, deleted)
                    )
                    """);
            execute(connection, """
                    CREATE TABLE dm_target_table_field (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        tenant_id BIGINT NOT NULL DEFAULT 1,
                        field_code VARCHAR(64) NOT NULL,
                        table_id BIGINT NOT NULL,
                        field_name_en VARCHAR(128) NOT NULL,
                        field_name_cn VARCHAR(128) NOT NULL,
                        field_meaning VARCHAR(500) NULL,
                        code_description VARCHAR(500) NULL,
                        is_key_field TINYINT NULL,
                        is_primary_key TINYINT NULL,
                        is_nullable TINYINT NULL,
                        oracle_type VARCHAR(64) NULL,
                        mysql_type VARCHAR(64) NULL,
                        dict_code VARCHAR(64) NULL,
                        owner_id BIGINT NOT NULL,
                        deleted TINYINT NOT NULL DEFAULT 0,
                        created_by BIGINT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_by BIGINT NULL,
                        updated_at TIMESTAMP NULL,
                        active_field_code VARCHAR(64)
                            GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN field_code ELSE NULL END) STORED,
                        active_field_name_en VARCHAR(128)
                            GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN field_name_en ELSE NULL END) STORED,
                        active_field_name_cn VARCHAR(128)
                            GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN field_name_cn ELSE NULL END) STORED,
                        UNIQUE KEY uk_target_field_active_code (tenant_id, active_field_code),
                        UNIQUE KEY uk_target_field_active_en (tenant_id, table_id, active_field_name_en),
                        UNIQUE KEY uk_target_field_active_cn (tenant_id, table_id, active_field_name_cn),
                        KEY idx_target_field_table (tenant_id, table_id, deleted),
                        KEY idx_target_field_key (tenant_id, table_id, is_key_field, deleted),
                        KEY idx_target_field_dict (tenant_id, dict_code, deleted),
                        CONSTRAINT fk_target_field_table FOREIGN KEY (table_id) REFERENCES dm_target_table (id)
                    )
                    """);
            execute(connection, "INSERT INTO arch_physical_subsystem (id, tenant_id, code, name, short_name) VALUES (10, 1, 'SYS-1', '系统一', 'SY1'), (20, 1, 'SYS-2', '系统二', 'SY2')");
            execute(connection, "INSERT INTO pm_project (id, tenant_id, project_code, project_name) VALUES (10, 1, 'P001', '项目A')");
            execute(connection, "INSERT INTO dm_component (id, tenant_id, project_id, physical_subsystem_code, owner_id) VALUES (10, 1, 10, 'SYS-1', 1)");
            execute(connection, """
                    INSERT INTO dm_target_table
                        (id, tenant_id, table_code, project_id, system_code, table_name_en, table_name_cn, table_meaning, table_category, owner_id, created_by)
                    VALUES
                        (1001, 1, 'TT1001', 10, 'SYS-1', 'legacy_tbl', '存量表', '历史', 'TARGET', 1, 1),
                        (1002, 1, 'TT1002', 10, 'SYS-1', 'legacy_tbl2', '存量表二', '历史', 'TARGET', 1, 1)
                    """);
            execute(connection, """
                    INSERT INTO dm_target_table_field
                        (id, tenant_id, field_code, table_id, field_name_en, field_name_cn, field_meaning, owner_id, created_by)
                    VALUES
                        (2001, 1, 'TF2001', 1001, 'col_a', '字段A', '字段含义', 1, 1)
                    """);
            execute(connection, """
                    INSERT INTO dm_issue_relation (tenant_id, issue_id, related_type, related_id, created_by)
                    VALUES (1, 1, 'TABLE', 1001, 1), (1, 1, 'FIELD', 2001, 1)
                    """);
        }

        assertTrue(flyway("176", "177").migrate().success);
        DriverManagerDataSource dataSource = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        DataMigrationPermissionService permissions = new DataMigrationPermissionService(jdbc, StubProjectAccess.allow());
        TargetTableService targetTableService = new TargetTableService(jdbc, permissions);
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        try (Connection connection = connection()) {
            // 主键收敛：无 id 列，主键为 table_code/field_code，编号回填为原 id 数值
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'dm_target_table' AND column_name = 'id'"));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'dm_target_table_field' AND column_name = 'id'"));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'dm_target_table_field' AND column_name = 'table_id'"));
            assertEquals(1L, count(connection, "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'dm_target_table' AND index_name = 'PRIMARY' AND column_name = 'table_code'"));
            assertEquals(1L, count(connection, "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'dm_target_table_field' AND index_name = 'PRIMARY' AND column_name = 'field_code'"));
            assertEquals(1001L, valueLong(connection, "SELECT table_code FROM dm_target_table WHERE table_name_en = 'legacy_tbl'"));
            assertEquals(2001L, valueLong(connection, "SELECT field_code FROM dm_target_table_field WHERE field_name_en = 'col_a'"));
            assertEquals(1001L, valueLong(connection, "SELECT table_code FROM dm_target_table_field WHERE field_name_en = 'col_a'"));
            // 关联查询：dm_issue_relation.related_id 数值不变，JOIN 改按业务编号
            assertEquals(1L, count(connection, "SELECT COUNT(*) FROM dm_issue_relation r JOIN dm_target_table t ON t.table_code = r.related_id AND t.tenant_id = r.tenant_id WHERE r.related_type = 'TABLE' AND r.related_id = 1001"));
            assertEquals(1L, count(connection, "SELECT COUNT(*) FROM dm_issue_relation r JOIN dm_target_table_field f ON f.field_code = r.related_id AND f.tenant_id = r.tenant_id WHERE r.related_type = 'FIELD' AND r.related_id = 2001"));
            // 生成列与唯一键：活动编号为 BIGINT，字段名唯一键随关联键改名 table_id -> table_code
            assertEquals("bigint", value(connection, "SELECT data_type FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'dm_target_table' AND column_name = 'active_table_code'"));
            assertEquals("STORED GENERATED", value(connection, "SELECT EXTRA FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'dm_target_table_field' AND column_name = 'active_field_code'"));
            assertEquals("tenant_id,active_table_code", indexColumns(connection, "dm_target_table", "uk_target_table_active_code"));
            assertEquals("tenant_id,table_code", indexColumns(connection, "dm_target_table", "uk_target_table_tenant_code"));
            assertEquals("tenant_id,table_code,active_field_name_en", indexColumns(connection, "dm_target_table_field", "uk_target_field_active_en"));
            assertEquals("tenant_id,table_code,active_field_name_cn", indexColumns(connection, "dm_target_table_field", "uk_target_field_active_cn"));
            // 组合外键：字段表按 (tenant_id, table_code) 关联主表
            assertEquals("tenant_id,table_code", value(connection, "SELECT GROUP_CONCAT(column_name ORDER BY ordinal_position) FROM information_schema.key_column_usage WHERE table_schema = DATABASE() AND table_name = 'dm_target_table_field' AND constraint_name = 'fk_target_field_table_code'"));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM information_schema.key_column_usage WHERE table_schema = DATABASE() AND table_name = 'dm_target_table_field' AND constraint_name = 'fk_target_field_table'"));
        }

        // 服务写侧：新编号直接落地 table_code / field_code（纯数字，无 TT/TF 前缀），审计 entity_id 存编号数值
        final long[] writtenTableCode = new long[1];
        transaction.executeWithoutResult(status -> {
            Map<String, Object> table = new LinkedHashMap<>();
            table.put("projectId", 10L);
            table.put("systemCode", "SYS-1");
            table.put("tableNameEn", "new_tbl");
            table.put("tableNameCn", "新表");
            Map<String, Object> created = targetTableService.createTable("TARGET", table, ADMIN);
            long tableCode = ((Number) created.get("table_code")).longValue();
            assertTrue(tableCode > 0);
            assertTrue(String.valueOf(tableCode).chars().allMatch(Character::isDigit));
            writtenTableCode[0] = tableCode;
            Map<String, Object> field = new LinkedHashMap<>();
            field.put("fieldNameEn", "new_col");
            field.put("fieldNameCn", "新字段");
            targetTableService.addField(tableCode, "TARGET", field, ADMIN);
        });
        try (Connection connection = connection()) {
            assertEquals(1L, count(connection, "SELECT COUNT(*) FROM dm_target_table WHERE table_code = " + writtenTableCode[0] + " AND table_name_en = 'new_tbl'"));
            assertEquals(1L, count(connection, "SELECT COUNT(*) FROM dm_target_table_field WHERE table_code = " + writtenTableCode[0] + " AND field_name_en = 'new_col'"));
            assertEquals(1L, count(connection, "SELECT COUNT(*) FROM dm_operation_log WHERE operation_code = 'TARGET_TABLE_CREATE' AND entity_id = " + writtenTableCode[0]));
            assertEquals(1L, count(connection, "SELECT COUNT(*) FROM dm_operation_log WHERE operation_code = 'TARGET_TABLE_FIELD_CREATE' AND entity_id = " + writtenTableCode[0]));
        }
    }

    /** T42 迁移专项：V178 全模块下线 active_* 活动生成列，唯一键直接建在业务列（方案3，软删行同样占用唯一名额）。 */
    @Test
    void v178RemovesActiveUniquenessColumnsAndUsesPlainKeys() throws Exception {
        cleanFixtureTables();
        try (Connection connection = connection()) {
            execute(connection, """
                    CREATE TABLE arch_physical_subsystem (
                        id BIGINT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        code VARCHAR(160) NOT NULL,
                        short_name VARCHAR(160) NULL,
                        name VARCHAR(160) NULL,
                        business_group_name VARCHAR(160) NULL,
                        deleted TINYINT NOT NULL DEFAULT 0,
                        UNIQUE KEY uk_arch_physical_code (tenant_id, code)
                    )
                    """);
            execute(connection, """
                    CREATE TABLE pm_project (
                        id BIGINT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        project_code VARCHAR(64) NULL,
                        project_name VARCHAR(128) NOT NULL,
                        deleted TINYINT NOT NULL DEFAULT 0
                    )
                    """);
            execute(connection, """
                    CREATE TABLE sys_user (
                        id BIGINT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        display_name VARCHAR(128) NOT NULL,
                        deleted TINYINT NOT NULL DEFAULT 0
                    )
                    """);
            execute(connection, """
                    CREATE TABLE sys_role (
                        id BIGINT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        role_code VARCHAR(64) NOT NULL,
                        status TINYINT NOT NULL DEFAULT 1,
                        deleted TINYINT NOT NULL DEFAULT 0
                    )
                    """);
            execute(connection, """
                    CREATE TABLE sys_user_role (
                        user_id BIGINT NOT NULL,
                        role_id BIGINT NOT NULL,
                        tenant_id BIGINT NOT NULL
                    )
                    """);
            execute(connection, "INSERT INTO sys_role (id, tenant_id, role_code) VALUES (1, 1, 'ADMIN')");
            execute(connection, "INSERT INTO sys_user_role (user_id, role_id, tenant_id) VALUES (1, 1, 1)");
            execute(connection, """
                    CREATE TABLE dm_operation_log (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        tenant_id BIGINT NOT NULL,
                        project_id BIGINT NOT NULL DEFAULT 0,
                        actor_id BIGINT NOT NULL,
                        operation_code VARCHAR(64) NOT NULL,
                        entity_type VARCHAR(64) NOT NULL,
                        entity_id BIGINT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            // V176 基线（V172 活动生成列 + 旧 id 主键结构，与 v177 专项一致）
            execute(connection, """
                    CREATE TABLE dm_target_table (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        tenant_id BIGINT NOT NULL DEFAULT 1,
                        table_code VARCHAR(64) NOT NULL,
                        project_id BIGINT NOT NULL,
                        system_code VARCHAR(64) NOT NULL,
                        table_name_en VARCHAR(128) NOT NULL,
                        table_name_cn VARCHAR(128) NOT NULL,
                        table_meaning VARCHAR(500) NULL,
                        table_category VARCHAR(32) NOT NULL,
                        owner_id BIGINT NOT NULL,
                        created_by BIGINT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_by BIGINT NULL,
                        updated_at TIMESTAMP NULL,
                        deleted TINYINT NOT NULL DEFAULT 0,
                        active_table_code VARCHAR(64)
                            GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN table_code ELSE NULL END) STORED,
                        active_system_code VARCHAR(64)
                            GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN system_code ELSE NULL END) STORED,
                        active_table_name_en VARCHAR(128)
                            GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN table_name_en ELSE NULL END) STORED,
                        active_table_name_cn VARCHAR(128)
                            GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN table_name_cn ELSE NULL END) STORED,
                        UNIQUE KEY uk_target_table_active_code (tenant_id, active_table_code),
                        UNIQUE KEY uk_target_table_active_en (tenant_id, project_id, active_system_code, active_table_name_en),
                        UNIQUE KEY uk_target_table_active_cn (tenant_id, project_id, active_system_code, active_table_name_cn)
                    )
                    """);
            execute(connection, """
                    CREATE TABLE dm_target_table_field (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        tenant_id BIGINT NOT NULL DEFAULT 1,
                        field_code VARCHAR(64) NOT NULL,
                        table_id BIGINT NOT NULL,
                        field_name_en VARCHAR(128) NOT NULL,
                        field_name_cn VARCHAR(128) NOT NULL,
                        is_key_field TINYINT NULL,
                        owner_id BIGINT NOT NULL,
                        deleted TINYINT NOT NULL DEFAULT 0,
                        active_field_code VARCHAR(64)
                            GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN field_code ELSE NULL END) STORED,
                        active_field_name_en VARCHAR(128)
                            GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN field_name_en ELSE NULL END) STORED,
                        active_field_name_cn VARCHAR(128)
                            GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN field_name_cn ELSE NULL END) STORED,
                        UNIQUE KEY uk_target_field_active_code (tenant_id, active_field_code),
                        UNIQUE KEY uk_target_field_active_en (tenant_id, table_id, active_field_name_en),
                        UNIQUE KEY uk_target_field_active_cn (tenant_id, table_id, active_field_name_cn),
                        CONSTRAINT fk_target_field_table FOREIGN KEY (table_id) REFERENCES dm_target_table (id)
                    )
                    """);
            execute(connection, """
                    CREATE TABLE dm_component (
                        id BIGINT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL DEFAULT 1,
                        project_id BIGINT NOT NULL,
                        physical_subsystem_code VARCHAR(64) NULL,
                        total_check TINYINT NOT NULL DEFAULT 0,
                        owner_id BIGINT NOT NULL,
                        deleted TINYINT NOT NULL DEFAULT 0,
                        active_physical_subsystem_code VARCHAR(64)
                            GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN physical_subsystem_code ELSE NULL END) STORED,
                        UNIQUE KEY uk_dm_component_active_subsystem (tenant_id, project_id, active_physical_subsystem_code)
                    )
                    """);
            execute(connection, """
                    CREATE TABLE dm_issue (
                        id BIGINT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL DEFAULT 1,
                        project_id BIGINT NOT NULL,
                        issue_code VARCHAR(96) NOT NULL,
                        issue_name VARCHAR(255) NOT NULL,
                        granularity VARCHAR(16) NULL,
                        system_code VARCHAR(96) NULL,
                        owner_id BIGINT NOT NULL,
                        deleted TINYINT NOT NULL DEFAULT 0,
                        created_by BIGINT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_by BIGINT NULL,
                        updated_at TIMESTAMP NULL,
                        active_issue_code VARCHAR(96)
                            GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN issue_code ELSE NULL END) STORED,
                        UNIQUE KEY uk_dm_issue_active_code (tenant_id, project_id, active_issue_code)
                    )
                    """);
            execute(connection, """
                    CREATE TABLE dm_meeting (
                        meeting_id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        tenant_id BIGINT NOT NULL DEFAULT 1,
                        project_id BIGINT NOT NULL,
                        meeting_code VARCHAR(96) NOT NULL,
                        deleted TINYINT NOT NULL DEFAULT 0,
                        active_meeting_code VARCHAR(96)
                            GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN meeting_code ELSE NULL END) STORED,
                        UNIQUE KEY uk_dm_meeting_active_code (tenant_id, project_id, active_meeting_code)
                    )
                    """);
            execute(connection, """
                    CREATE TABLE dm_plan (
                        id BIGINT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL DEFAULT 1,
                        project_id BIGINT NOT NULL,
                        doc_code VARCHAR(96) NOT NULL,
                        doc_name VARCHAR(255) NOT NULL,
                        granularity VARCHAR(16) NOT NULL DEFAULT 'PROJECT',
                        plan_type VARCHAR(16) NOT NULL DEFAULT 'DATA',
                        system_code VARCHAR(64) NOT NULL DEFAULT '',
                        deleted TINYINT NOT NULL DEFAULT 0,
                        active_doc_code VARCHAR(96)
                            GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN doc_code ELSE NULL END) STORED,
                        active_dimension_key VARCHAR(160)
                            GENERATED ALWAYS AS (
                                CASE WHEN deleted = 0 THEN CONCAT_WS(':', tenant_id, project_id, granularity, plan_type, system_code) ELSE NULL END
                            ) STORED,
                        UNIQUE KEY uk_dm_plan_active_code (tenant_id, project_id, active_doc_code),
                        UNIQUE KEY uk_dm_plan_active_dimension (active_dimension_key)
                    )
                    """);
            execute(connection, """
                    CREATE TABLE dm_mapping_doc (
                        id BIGINT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL DEFAULT 1,
                        project_id BIGINT NOT NULL,
                        doc_code VARCHAR(96) NOT NULL,
                        doc_name VARCHAR(255) NOT NULL,
                        deleted TINYINT NOT NULL DEFAULT 0,
                        active_doc_code VARCHAR(96)
                            GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN doc_code ELSE NULL END) STORED,
                        UNIQUE KEY uk_dm_mapping_doc_active_code (tenant_id, project_id, active_doc_code)
                    )
                    """);
            execute(connection, """
                    CREATE TABLE dm_content_attachment (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        tenant_id BIGINT NOT NULL DEFAULT 1,
                        business_type VARCHAR(32) NOT NULL,
                        business_id BIGINT NOT NULL,
                        attachment_id BIGINT NOT NULL,
                        deleted TINYINT(1) NOT NULL DEFAULT 0,
                        active_attachment_key VARCHAR(256)
                            GENERATED ALWAYS AS (
                                CASE WHEN deleted = 0 THEN CONCAT(tenant_id, ':', business_type, ':', business_id, ':', attachment_id) ELSE NULL END
                            ) STORED,
                        UNIQUE KEY uk_dm_content_att_active (active_attachment_key)
                    )
                    """);
            execute(connection, "INSERT INTO arch_physical_subsystem (id, tenant_id, code, name, short_name) VALUES (10, 1, 'SYS-1', '系统一', 'SY1')");
            execute(connection, "INSERT INTO pm_project (id, tenant_id, project_code, project_name) VALUES (10, 1, 'P001', '项目A')");
            execute(connection, "INSERT INTO dm_component (id, tenant_id, project_id, physical_subsystem_code, owner_id) VALUES (10, 1, 10, 'SYS-1', 1)");
            execute(connection, """
                    INSERT INTO dm_target_table (id, tenant_id, table_code, project_id, system_code, table_name_en, table_name_cn, table_category, owner_id)
                    VALUES (1001, 1, 'TT1001', 10, 'SYS-1', 'legacy_tbl', '存量表', 'TARGET', 1)
                    """);
            execute(connection, "INSERT INTO dm_issue (id, tenant_id, project_id, issue_code, issue_name, owner_id) VALUES (9001, 1, 10, 'ISS-1', '问题一', 1)");
            execute(connection, "INSERT INTO dm_plan (id, tenant_id, project_id, doc_code, doc_name) VALUES (8001, 1, 10, 'PLAN-1', '方案一')");
            execute(connection, "INSERT INTO dm_mapping_doc (id, tenant_id, project_id, doc_code, doc_name) VALUES (7001, 1, 10, 'MAP-1', '映射一')");
        }

        assertTrue(flyway("176", "178").migrate().success);
        DriverManagerDataSource dataSource = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        DataMigrationPermissionService permissions = new DataMigrationPermissionService(jdbc, StubProjectAccess.allow());
        TargetTableService targetTableService = new TargetTableService(jdbc, permissions);
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        String[] tables = {
                "dm_issue", "dm_meeting", "dm_plan", "dm_component",
                "dm_target_table", "dm_target_table_field",
                "dm_mapping_doc", "dm_dependency", "dm_script", "dm_topic",
                "dm_release_drill", "dm_report", "dm_rule", "dm_parameter", "dm_content_attachment"
        };
        try (Connection connection = connection()) {
            // 1. active_* 生成列全部下线
            assertEquals(0, count(connection,
                    "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND column_name LIKE 'active\\_%' AND table_name IN ('" + String.join("','", tables) + "')"));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name IN ('" + String.join("','", tables) + "') AND extra = 'STORED GENERATED'"));
            // 2. 朴素唯一键建立且列序精确
            assertEquals("tenant_id,project_id,issue_code", indexColumns(connection, "dm_issue", "uk_dm_issue_code"));
            assertEquals("tenant_id,project_id,meeting_code", indexColumns(connection, "dm_meeting", "uk_dm_meeting_code"));
            assertEquals("tenant_id,project_id,doc_code", indexColumns(connection, "dm_plan", "uk_dm_plan_code"));
            assertEquals("tenant_id,project_id,granularity,plan_type,system_code", indexColumns(connection, "dm_plan", "uk_dm_plan_dimension"));
            assertEquals("tenant_id,project_id,physical_subsystem_code", indexColumns(connection, "dm_component", "uk_dm_component_subsystem"));
            assertEquals("tenant_id,project_id,system_code,table_name_en", indexColumns(connection, "dm_target_table", "uk_target_table_en"));
            assertEquals("tenant_id,project_id,system_code,table_name_cn", indexColumns(connection, "dm_target_table", "uk_target_table_cn"));
            assertEquals("tenant_id,table_code,field_name_en", indexColumns(connection, "dm_target_table_field", "uk_target_field_en"));
            assertEquals("tenant_id,table_code,field_name_cn", indexColumns(connection, "dm_target_table_field", "uk_target_field_cn"));
            assertEquals("tenant_id,project_id,doc_code", indexColumns(connection, "dm_mapping_doc", "uk_dm_mapping_doc_code"));
            assertEquals("tenant_id,business_type,business_id,attachment_id", indexColumns(connection, "dm_content_attachment", "uk_dm_content_att"));
            // 存量编号零改写：原 table_code 字符串被 V177 回填为数值 1001，V178 不再改动数据
            assertEquals(1001L, valueLong(connection, "SELECT table_code FROM dm_target_table WHERE table_name_en = 'legacy_tbl'"));
            // 3. DB 层：软删行占唯一名额，删除后不可直接同名/同编号重建
            execute(connection, "INSERT INTO dm_issue (id, tenant_id, project_id, issue_code, issue_name, owner_id) VALUES (9002, 1, 10, 'ISS-2', '问题二', 1)");
            execute(connection, "UPDATE dm_issue SET deleted = 1 WHERE id = 9002");
            assertThrows(SQLException.class, () -> execute(connection,
                    "INSERT INTO dm_issue (id, tenant_id, project_id, issue_code, issue_name, owner_id) VALUES (9003, 1, 10, 'ISS-2', '问题二', 1)"));
            execute(connection, "INSERT INTO dm_component (id, tenant_id, project_id, physical_subsystem_code, owner_id) VALUES (11, 1, 10, 'SYS-2', 1)");
            execute(connection, "UPDATE dm_component SET deleted = 1 WHERE id = 11");
            assertThrows(SQLException.class, () -> execute(connection,
                    "INSERT INTO dm_component (id, tenant_id, project_id, physical_subsystem_code, owner_id) VALUES (12, 1, 10, 'SYS-2', 1)"));
            execute(connection, "INSERT INTO dm_content_attachment (tenant_id, business_type, business_id, attachment_id) VALUES (1, 'MEETING', 10, 100)");
            execute(connection, "UPDATE dm_content_attachment SET deleted = 1 WHERE business_type = 'MEETING' AND business_id = 10 AND attachment_id = 100");
            assertThrows(SQLException.class, () -> execute(connection,
                    "INSERT INTO dm_content_attachment (tenant_id, business_type, business_id, attachment_id) VALUES (1, 'MEETING', 10, 100)"));
            execute(connection, "INSERT INTO dm_target_table (tenant_id, table_code, project_id, system_code, table_name_en, table_name_cn, table_category, owner_id) VALUES (1, 5001, 10, 'SYS-1', 'tbl_a', '表A', 'TARGET', 1)");
            execute(connection, "UPDATE dm_target_table SET deleted = 1 WHERE table_code = 5001");
            assertThrows(SQLException.class, () -> execute(connection,
                    "INSERT INTO dm_target_table (tenant_id, table_code, project_id, system_code, table_name_en, table_name_cn, table_category, owner_id) VALUES (1, 5002, 10, 'SYS-1', 'tbl_a', '表A', 'TARGET', 1)"));
            // 4. 服务层：预校验同步含软删行，同名校重建返回业务冲突
            transaction.executeWithoutResult(status -> {
                Map<String, Object> table = new LinkedHashMap<>();
                table.put("projectId", 10L);
                table.put("systemCode", "SYS-1");
                table.put("tableNameEn", "reuse_tbl");
                table.put("tableNameCn", "重建表");
                Map<String, Object> created = targetTableService.createTable("TARGET", table, ADMIN);
                targetTableService.deleteTables(List.of(((Number) created.get("table_code")).longValue()), "TARGET", ADMIN);
            });
            BusinessException conflict = assertThrows(BusinessException.class, () -> transaction.executeWithoutResult(status -> {
                Map<String, Object> table = new LinkedHashMap<>();
                table.put("projectId", 10L);
                table.put("systemCode", "SYS-1");
                table.put("tableNameEn", "reuse_tbl");
                table.put("tableNameCn", "重建表");
                targetTableService.createTable("TARGET", table, ADMIN);
            }));
            assertEquals(ErrorCode.CONFLICT, conflict.code());
        }
    }

    /** 9.2 P2「跨表统计线性增长」：组件看板为一次 UNION ALL 分组统计，整体看板与组件看板均只按项目统计。 */
    @Test
    void dashboardComponentStatsUseSingleGroupedPass() throws Exception {
        cleanFixtureTables();
        String[] contentTables = {
                "dm_plan", "dm_mapping_doc", "dm_dependency", "dm_script", "dm_topic",
                "dm_release_drill", "dm_report", "dm_rule", "dm_parameter"
        };
        try (Connection connection = connection()) {
            execute(connection, """
                    CREATE TABLE dm_component (
                        id BIGINT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL DEFAULT 1,
                        project_id BIGINT NOT NULL,
                        physical_subsystem_code VARCHAR(64) NULL,
                        deleted TINYINT NOT NULL DEFAULT 0
                    )
                    """);
            execute(connection, """
                    CREATE TABLE arch_physical_subsystem (
                        id BIGINT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        code VARCHAR(160) NOT NULL,
                        short_name VARCHAR(160) NULL,
                        name VARCHAR(160) NULL,
                        deleted TINYINT NOT NULL DEFAULT 0
                    )
                    """);
            for (String table : contentTables) {
                execute(connection, """
                        CREATE TABLE %s (
                            id BIGINT PRIMARY KEY,
                            tenant_id BIGINT NOT NULL,
                            project_id BIGINT NOT NULL,
                            component_id BIGINT NULL,
                            deleted TINYINT NOT NULL DEFAULT 0
                        )
                        """.formatted(table));
            }
            execute(connection, "INSERT INTO arch_physical_subsystem (id, tenant_id, code, name, short_name) VALUES (10, 1, 'SYS-1', '系统一', 'SY1'), (20, 1, 'SYS-2', '系统二', 'SY2')");
            execute(connection, "INSERT INTO dm_component (id, tenant_id, project_id, physical_subsystem_code) VALUES (1, 1, 100, 'SYS-1'), (2, 1, 100, 'SYS-2')");
            execute(connection, "INSERT INTO dm_plan (id, tenant_id, project_id, component_id) VALUES (1, 1, 100, 1), (2, 1, 100, 1), (3, 1, 100, 2)");
            execute(connection, "INSERT INTO dm_report (id, tenant_id, project_id, component_id, deleted) VALUES (1, 1, 100, 1, 1)");
            execute(connection, "INSERT INTO dm_rule (id, tenant_id, project_id, component_id) VALUES (1, 1, 100, 1)");
            execute(connection, "INSERT INTO dm_parameter (id, tenant_id, project_id, component_id) VALUES (1, 1, 100, 2)");
            execute(connection, "INSERT INTO dm_mapping_doc (id, tenant_id, project_id, component_id) VALUES (1, 1, 100, NULL)");
        }

        DriverManagerDataSource dataSource = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        DataMigrationPermissionService permissions = new DataMigrationPermissionService(jdbc, StubProjectAccess.withAccessibleProjects(100L));
        DashboardService dashboard = new DashboardService(jdbc, permissions);

        List<Map<String, Object>> componentStats = dashboard.component(ADMIN, 100L);
        assertEquals(2, componentStats.size());
        assertEquals("SYS-1", componentStats.get(0).get("system_code"));
        assertEquals(3L, ((Number) componentStats.get(0).get("asset_count")).longValue());
        assertEquals("SYS-2", componentStats.get(1).get("system_code"));
        assertEquals(2L, ((Number) componentStats.get(1).get("asset_count")).longValue());

        Map<String, Object> overall = dashboard.overall(100L, ADMIN);
        assertEquals(2, ((Number) overall.get("components")).intValue());
        assertEquals(6L, ((Number) overall.get("assets")).longValue());
        Map<String, Object> byType = new java.util.LinkedHashMap<>();
        for (Object row : (List<?>) overall.get("byType")) {
            Map<String, Object> typed = (Map<String, Object>) row;
            byType.put(String.valueOf(typed.get("type")), typed.get("total"));
        }
        assertEquals(3L, ((Number) byType.get("PLAN")).longValue());
        assertEquals(1L, ((Number) byType.get("MAPPING_DOC")).longValue());
        assertEquals(1L, ((Number) byType.get("RULE")).longValue());
        assertEquals(1L, ((Number) byType.get("PARAMETER")).longValue());
    }

    private Flyway flyway(String baseline, String target) {
        return Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("filesystem:" + migrationDirectory())
                .placeholders(java.util.Map.of("bootstrap_admin_password_hash", "test-hash"))
                .baselineOnMigrate(true)
                .baselineVersion(MigrationVersion.fromVersion(baseline))
                .target(MigrationVersion.fromVersion(target))
                .cleanDisabled(false)
                .load();
    }

    private Connection connection() throws Exception {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }

    private String indexColumns(Connection connection, String table, String index) throws Exception {
        return value(connection, """
                SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = '%s' AND index_name = '%s'
                GROUP BY index_name
                """.formatted(table, index));
    }

    private String migrationDirectory() {
        Path cursor = Path.of("").toAbsolutePath();
        while (cursor != null) {
            Path candidate = cursor.resolve("server/src/platform/infrastructure/src/main/resources/db/migration");
            if (Files.isDirectory(candidate)) return candidate.toString();
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("Flyway migration directory not found");
    }

    private long count(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getLong(1);
        }
    }

    private String value(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getString(1);
        }
    }

    private Long valueLong(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            Object column = result.getObject(1);
            return column == null ? null : ((Number) column).longValue();
        }
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
