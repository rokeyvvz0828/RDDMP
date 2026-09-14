/*
 * 文件：server/src/platform/system/src/test/java/com/ccb/system/service/UserProfileServiceMySqlTest.java
 * 说明：人员档案批量只读查询的租户隔离与字段范围测试。
 * 用途：在真实 MySQL 8.4 上验证跨租户不返回、软删除过滤、停用用户返回、组织与角色聚合。
 * 作者：Codex
 */
package com.ccb.system.service;

import com.ccb.security.model.AuthUser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class UserProfileServiceMySqlTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("user_profile").withUsername("test").withPassword("test");

    static final AuthUser TENANT_ONE_ACTOR = new AuthUser(1L, 1L, "actor", "hash", "调用者", 10L, true);

    static UserProfileService service;

    @BeforeAll
    static void schema() {
        var dataSource = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE sys_user(id BIGINT PRIMARY KEY, tenant_id BIGINT, username VARCHAR(60), "
                + "display_name VARCHAR(60), mobile_phone VARCHAR(20), org_id BIGINT, avatar_object_key VARCHAR(255), "
                + "status INT, deleted INT)");
        jdbc.execute("CREATE TABLE sys_org(id BIGINT PRIMARY KEY, tenant_id BIGINT, org_name VARCHAR(120), deleted INT)");
        jdbc.execute("CREATE TABLE sys_role(id BIGINT PRIMARY KEY, tenant_id BIGINT, role_code VARCHAR(60), "
                + "role_name VARCHAR(60), status INT, deleted INT)");
        jdbc.execute("CREATE TABLE sys_user_role(user_id BIGINT, role_id BIGINT, tenant_id BIGINT, "
                + "PRIMARY KEY(user_id, role_id))");

        jdbc.update("INSERT INTO sys_org(id, tenant_id, org_name, deleted) VALUES (10, 1, '平台研发一组', 0)");

        // 启用且有角色且有组织
        jdbc.update("INSERT INTO sys_user(id, tenant_id, username, display_name, mobile_phone, org_id, "
                + "avatar_object_key, status, deleted) VALUES (1, 1, 'zhangwei', '张伟', '13800138000', 10, 'avatar/1.png', 1, 0)");
        // 停用且组织不存在且无手机号
        jdbc.update("INSERT INTO sys_user(id, tenant_id, username, display_name, mobile_phone, org_id, "
                + "avatar_object_key, status, deleted) VALUES (2, 1, 'disabled', '停用人员', NULL, 99, NULL, 0, 0)");
        // 已软删除
        jdbc.update("INSERT INTO sys_user(id, tenant_id, username, display_name, mobile_phone, org_id, "
                + "avatar_object_key, status, deleted) VALUES (3, 1, 'removed', '已删除人员', NULL, 10, NULL, 1, 1)");
        // 启用但无角色
        jdbc.update("INSERT INTO sys_user(id, tenant_id, username, display_name, mobile_phone, org_id, "
                + "avatar_object_key, status, deleted) VALUES (4, 1, 'norole', '无角色人员', NULL, 10, NULL, 1, 0)");
        // 另一租户
        jdbc.update("INSERT INTO sys_user(id, tenant_id, username, display_name, mobile_phone, org_id, "
                + "avatar_object_key, status, deleted) VALUES (5, 2, 'other', '他租户人员', '13900000000', 10, NULL, 1, 0)");

        jdbc.update("INSERT INTO sys_role(id, tenant_id, role_code, role_name, status, deleted) "
                + "VALUES (1, 1, 'ARCH_ADMIN', '架构管理员', 1, 0), (2, 1, 'RETIRED', '已停用角色', 0, 0), "
                + "(3, 2, 'OTHER', '他租户角色', 1, 0), (4, 1, 'REMOVED', '已删除角色', 1, 1)");
        jdbc.update("INSERT INTO sys_user_role(user_id, role_id, tenant_id) "
                + "VALUES (1, 1, 1), (1, 2, 1), (1, 4, 1), (5, 3, 2)");

        service = new UserProfileService(jdbc);
    }

    @Test
    void 只返回当前租户未删除的用户且跨租户标识不可见() {
        List<UserProfileService.Row> rows = service.query(TENANT_ONE_ACTOR, List.of(1L, 2L, 3L, 4L, 5L));

        assertThat(rows).extracting(UserProfileService.Row::id).containsExactly(1L, 2L, 4L);
        assertThat(rows).extracting(UserProfileService.Row::id).doesNotContain(3L, 5L);
    }

    @Test
    void 跨租户标识既不返回也不报错() {
        assertThat(service.query(TENANT_ONE_ACTOR, List.of(5L))).isEmpty();
    }

    @Test
    void 空标识与重复标识都不产生重复结果() {
        assertThat(service.query(TENANT_ONE_ACTOR, List.of())).isEmpty();
        assertThat(service.query(TENANT_ONE_ACTOR, List.of(1L, 1L, 1L)))
                .extracting(UserProfileService.Row::id).containsExactly(1L);
    }

    @Test
    void 停用用户仍返回并带停用状态且缺失字段使用空值() {
        UserProfileService.Row stopped = service.query(TENANT_ONE_ACTOR, List.of(2L)).get(0);

        assertThat(stopped.status()).isZero();
        assertThat(stopped.mobilePhone()).isEmpty();
        assertThat(stopped.orgName()).isEmpty();
        assertThat(stopped.avatarObjectKey()).isNull();
        assertThat(stopped.roles()).isEmpty();
    }

    @Test
    void 角色只聚合启用且未删除的角色() {
        UserProfileService.Row active = service.query(TENANT_ONE_ACTOR, List.of(1L)).get(0);

        assertThat(active.roles()).extracting(UserProfileService.RoleItem::name).containsExactly("架构管理员");
        assertThat(active.roles().get(0).code()).isEqualTo("ARCH_ADMIN");
        assertThat(active.displayName()).isEqualTo("张伟");
        assertThat(active.mobilePhone()).isEqualTo("13800138000");
        assertThat(active.orgName()).isEqualTo("平台研发一组");
        assertThat(active.avatarObjectKey()).isEqualTo("avatar/1.png");
        assertThat(active.status()).isEqualTo(1);
    }

    @Test
    void 无角色用户返回空角色列表而不是错误() {
        assertThat(service.query(TENANT_ONE_ACTOR, List.of(4L)).get(0).roles()).isEmpty();
    }
}
