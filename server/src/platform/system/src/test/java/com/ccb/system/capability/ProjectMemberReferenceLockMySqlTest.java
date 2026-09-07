package com.ccb.system.capability;

import com.ccb.security.model.AuthUser;
import com.ccb.system.internal.capability.JdbcProjectMemberReferenceQuery;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

/** 真实两个事务验证资格锁，不以Mock的调用次数代替并发安全证据。 */
@Testcontainers
class ProjectMemberReferenceLockMySqlTest {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("qualification_lock").withUsername("test").withPassword("test");
    static JdbcTemplate jdbc;
    static TransactionTemplate tx;
    static ProjectMemberReferenceQuery query;
    static final AuthUser actor = new AuthUser(7, 1, "test", "", "测试成员", 1, true);
    @BeforeAll static void schema() {
        var ds = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        jdbc = new JdbcTemplate(ds); tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        query = new JdbcProjectMemberReferenceQuery(jdbc);
        jdbc.execute("CREATE TABLE sys_user(id BIGINT PRIMARY KEY, tenant_id BIGINT, display_name VARCHAR(30), username VARCHAR(30), status INT, deleted INT)");
        jdbc.execute("CREATE TABLE pm_project_member(id BIGINT PRIMARY KEY, tenant_id BIGINT, project_id BIGINT, user_id BIGINT, status INT, deleted INT, INDEX scope(tenant_id,project_id))");
        jdbc.update("INSERT INTO sys_user VALUES(7,1,'测试成员','test',1,0)");
        jdbc.update("INSERT INTO pm_project_member VALUES(70,1,700,7,1,0)");
    }
    @BeforeEach void reset() {
        jdbc.update("UPDATE pm_project_member SET status=1,deleted=0 WHERE id=70");
        jdbc.update("UPDATE sys_user SET status=1,deleted=0 WHERE id=7");
    }
    @Test void 执行事务持有资格后项目退出必须等待提交() throws Exception {
        assertRevocationWaits("UPDATE pm_project_member SET status=0 WHERE id=70");
    }
    @Test void 执行事务持有资格后用户停用必须等待提交() throws Exception {
        assertRevocationWaits("UPDATE sys_user SET status=0 WHERE id=7");
    }
    private void assertRevocationWaits(String sql) throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var locked = new CountDownLatch(1); var release = new CountDownLatch(1); var started = new CountDownLatch(1);
        try {
            Future<?> executing = pool.submit(() -> tx.executeWithoutResult(status -> {
                assertThat(query.findActiveMembers(actor,700)).hasSize(1);
                locked.countDown(); await(release);
            }));
            assertThat(locked.await(10,TimeUnit.SECONDS)).isTrue();
            Future<?> revoking = pool.submit(() -> { started.countDown(); jdbc.update(sql); });
            assertThat(started.await(10,TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> revoking.get(300,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown(); executing.get(10,TimeUnit.SECONDS); revoking.get(10,TimeUnit.SECONDS);
            assertThat(query.findActiveMembers(actor,700)).isEmpty();
        } finally { release.countDown(); pool.shutdownNow(); }
    }
    @Test void 已建立旧快照的事务仍读取到最新撤销资格() throws Exception {
        var pool = Executors.newSingleThreadExecutor();
        try {
            tx.executeWithoutResult(status -> {
                assertThat(jdbc.queryForObject("SELECT status FROM pm_project_member WHERE id=70",Integer.class)).isEqualTo(1);
                try { pool.submit(() -> jdbc.update("UPDATE pm_project_member SET status=0 WHERE id=70")).get(10,TimeUnit.SECONDS); }
                catch (Exception ex) { throw new AssertionError(ex); }
                assertThat(query.findActiveMembers(actor,700)).isEmpty();
            });
        } finally { pool.shutdownNow(); }
    }
    @Test void 跨租户和跨项目不能获得资格() {
        tx.executeWithoutResult(status -> {
            assertThat(query.findActiveMembers(actor,701)).isEmpty();
            assertThat(query.findActiveMembers(new AuthUser(7,2,"test","","跨租户",1,true),700)).isEmpty();
        });
    }
    private static void await(CountDownLatch latch) {
        try { if (!latch.await(10,TimeUnit.SECONDS)) throw new AssertionError("并发测试同步超时"); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new AssertionError(ex); }
    }
}
