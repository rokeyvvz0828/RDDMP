package com.ccb.development.repository;

import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.development.config.DevelopmentSettings;
import com.ccb.development.integration.DevelopmentSourceDirectory;
import com.ccb.development.integration.DevelopmentSystemDirectory;
import com.ccb.development.model.DevelopmentTaskModels.*;
import com.ccb.development.service.*;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Testcontainers
public class DevelopmentTaskMySqlTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("development_task_test").withUsername("test").withPassword("test");
    public static final AuthUser OWNER = new AuthUser(9, 7, "fixture.owner", "", "虚构负责人", 1, true);
    public static final AuthUser OTHER = new AuthUser(10, 7, "fixture.other", "", "虚构执行人", 1, true);
    protected Fixture fixture;

    @BeforeEach
    void prepare() {
        var ds = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        var jdbc = new JdbcTemplate(ds);
        jdbc.execute("SET FOREIGN_KEY_CHECKS=0");
        for (String table : List.of("dev_stage_attachment_ref", "dev_task_stage", "dev_calendar_snapshot", "dev_task_source_binding",
                "dev_task_change", "dev_work_item", "dev_task", "dev_task_number_sequence")) jdbc.execute("DROP TABLE IF EXISTS " + table);
        jdbc.execute("SET FOREIGN_KEY_CHECKS=1");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V199__development_task_schema.sql")).execute(ds);
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V200__development_stage_calendar_schema.sql")).execute(ds);
        fixture = new Fixture(jdbc);
    }

    @AfterEach
    void clearActor() { SecurityContextHolder.clearContext(); }

    @Test
    void standaloneDefaultsToSystemOwnerAndCreatesNoPlaceholderWorkItem() {
        var task = as(OWNER, () -> fixture.create(OWNER, standalone(null)));
        assertEquals("9", task.owner().id());
        assertTrue(task.number().matches("RW_ZZ_\\d{8}_001"));
        assertEquals(0, task.workItemCount());
        assertNull(task.source());
        assertEquals(0L, fixture.jdbc.queryForObject("SELECT COUNT(*) FROM dev_work_item", Long.class));
        assertEquals(1L, fixture.jdbc.queryForObject("SELECT COUNT(*) FROM dev_task_change", Long.class));
        assertEquals(1, as(OWNER, () -> fixture.tasks.list(OWNER,
                new TaskQuery("PROJECT-A", null, null, null, null, new PageQuery(1, 20)))).total());
    }

    @Test
    void requestRetryReturnsSameTaskButDifferentPayloadWithSameRequestIdConflicts() {
        CreateTask request = standalone(null);
        var one = as(OWNER, () -> fixture.create(OWNER, request));
        var two = as(OWNER, () -> fixture.create(OWNER, request));
        assertEquals(one.id(), two.id());
        CreateTask changed = new CreateTask("PROJECT-A", SourceMode.STANDALONE, null, null, 42L,
                "另一任务", "内容", null, null, null, null, null, request.requestId());
        assertEquals(ErrorCode.CONFLICT, assertThrows(BusinessException.class,
                () -> as(OWNER, () -> fixture.create(OWNER, changed))).code());
        assertEquals(1L, fixture.jdbc.queryForObject("SELECT COUNT(*) FROM dev_task", Long.class));
    }

    @Test
    void thirtyTwoConcurrentClaimsProduceOneTaskAndOneHistory() throws Exception {
        var executor = Executors.newFixedThreadPool(8);
        try {
            var calls = new java.util.ArrayList<java.util.concurrent.Callable<Boolean>>();
            for (int i = 0; i < 32; i++) calls.add(() -> as(OWNER, () -> {
                try { fixture.create(OWNER, linked(42)); return true; }
                catch (BusinessException error) { assertEquals(ErrorCode.CONFLICT, error.code()); return false; }
            }));
            int successes = 0;
            for (var result : executor.invokeAll(calls)) if (result.get()) successes++;
            assertEquals(1, successes);
        } finally { executor.shutdownNow(); }
        assertEquals(1L, fixture.jdbc.queryForObject("SELECT COUNT(*) FROM dev_task", Long.class));
        assertEquals(1L, fixture.jdbc.queryForObject("SELECT COUNT(*) FROM dev_task_change", Long.class));
        as(OWNER, () -> fixture.create(OWNER, linked(43)));
        assertEquals(2L, fixture.jdbc.queryForObject("SELECT COUNT(*) FROM dev_task", Long.class));
    }

    @Test
    void unrelatedActorCannotCreateReadOrSeeCountsAndAdminStillNeedsProject() {
        var task = as(OWNER, () -> fixture.create(OWNER, standalone(null)));
        assertEquals(ErrorCode.FORBIDDEN, assertThrows(BusinessException.class,
                () -> as(OTHER, () -> fixture.create(OTHER, standalone(null)))).code());
        assertThrows(BusinessException.class, () -> as(OTHER, () -> fixture.tasks.detail(OTHER, Long.parseLong(task.id()))));
        assertEquals(0, as(OTHER, () -> fixture.tasks.list(OTHER,
                new TaskQuery("PROJECT-A", null, null, null, null, new PageQuery(1, 20)))).total());
        assertThrows(BusinessException.class, () -> as(OWNER, () -> fixture.tasks.list(OWNER,
                new TaskQuery("PROJECT-B", null, null, null, null, new PageQuery(1, 20)))));
    }

    @Test
    void staleVersionCannotOverwriteTaskAndTenantNeverLeaks() {
        var task = as(OWNER, () -> fixture.create(OWNER, standalone(null)));
        var update = new UpdateTask("修改后", "内容", 10L, null, null, null, null, 0);
        as(OWNER, () -> fixture.tx.execute(s -> fixture.tasks.update(OWNER, Long.parseLong(task.id()), update)));
        assertEquals(ErrorCode.CONFLICT, assertThrows(BusinessException.class,
                () -> as(OWNER, () -> fixture.tx.execute(s -> fixture.tasks.update(OWNER, Long.parseLong(task.id()), update)))).code());
        AuthUser tenantB = new AuthUser(9, 8, "tenant-b", "", "另一租户", 1, true);
        assertThrows(BusinessException.class, () -> as(tenantB, () -> fixture.tasks.detail(tenantB, Long.parseLong(task.id()))));
    }

    @Test
    void invalidDatesAndMissingOwnerFailWithoutAllocatingTasks() {
        CreateTask badDates = new CreateTask("PROJECT-A", SourceMode.STANDALONE, null, null, 42L, "任务", "", null,
                LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 7), null, null, UUID.randomUUID().toString());
        assertThrows(BusinessException.class, () -> as(OWNER, () -> fixture.create(OWNER, badDates)));
        when(fixture.systems.find(any(), eq(31L), eq(42L))).thenReturn(Optional.of(new DevelopmentSystemDirectory.SystemRef(42, "PHY-A", "系统", null, "ACTIVE", 1)));
        assertThrows(BusinessException.class, () -> as(OWNER, () -> fixture.create(OWNER, standalone(null))));
        assertEquals(0L, fixture.jdbc.queryForObject("SELECT COUNT(*) FROM dev_task", Long.class));
    }

    @Test
    void pendingCountsExcludeOnlyClaimedSystemAndRespectActorScope() {
        assertEquals(2, as(OWNER, () -> fixture.tasks.pending(OWNER,"PROJECT-A",null,null,new PageQuery(1,20))).total());
        as(OWNER, () -> fixture.create(OWNER,linked(42)));
        var pending=as(OWNER, () -> fixture.tasks.pending(OWNER,"PROJECT-A",null,null,new PageQuery(1,20)));
        assertEquals(1,pending.total());
        assertEquals("43",pending.records().get(0).system().id());
        assertEquals(List.of("TEST"),pending.records().get(0).roles());
        assertEquals(0,as(OTHER, () -> fixture.tasks.pending(OTHER,"PROJECT-A",null,null,new PageQuery(1,20))).total());
    }

    @Test
    void assignedActorDoesNotSeeOtherPeoplesWorkItemCountsInParentSummary() {
        var task=as(OWNER,()->fixture.create(OWNER,standalone(null)));
        fixture.jdbc.update("INSERT INTO dev_work_item (tenant_id,task_id,title,description,assignee_id,status,created_by,updated_by) VALUES (7,?,'自己的工作','',10,'TODO',9,9),(7,?,'他人的工作','',11,'DONE',9,9)",task.id(),task.id());
        var visible=as(OTHER,()->fixture.tasks.detail(OTHER,Long.parseLong(task.id())));
        assertEquals(1,visible.workItemCount());
        assertEquals(0,visible.completedWorkItemCount());
        var owner=as(OWNER,()->fixture.tasks.detail(OWNER,Long.parseLong(task.id())));
        assertEquals(2,owner.workItemCount());
        assertEquals(1,owner.completedWorkItemCount());
    }

    @Test
    void staleSourceAndReboundSystemCannotCreateDuplicateBusinessClaim() {
        CreateTask stale=new CreateTask("PROJECT-A",SourceMode.LINKED,100L,"old-revision",42L,"任务","",null,
                null,null,null,null,UUID.randomUUID().toString());
        assertEquals(ErrorCode.CONFLICT,assertThrows(BusinessException.class,()->as(OWNER,()->fixture.create(OWNER,stale))).code());
        as(OWNER,()->fixture.create(OWNER,linked(42)));
        when(fixture.references.activeParameters(any(),eq("DEVELOPMENT_SYSTEM_MAPPING"))).thenReturn(List.of(
                new SystemParameterReference("SYS-A","43"),new SystemParameterReference("SYS-B","43")));
        assertEquals(ErrorCode.CONFLICT,assertThrows(BusinessException.class,()->as(OWNER,()->fixture.create(OWNER,linked(43)))).code());
        assertEquals(ErrorCode.CONFLICT,assertThrows(BusinessException.class,()->as(OWNER,()->fixture.tasks.pending(OWNER,"PROJECT-A",null,null,new PageQuery(1,20)))).code());
        assertEquals(1L,fixture.jdbc.queryForObject("SELECT COUNT(*) FROM dev_task",Long.class));
        assertEquals(1L,fixture.jdbc.queryForObject("SELECT COUNT(*) FROM dev_task_change",Long.class));
    }

    public static CreateTask standalone(Long ownerId) {
        return new CreateTask("PROJECT-A", SourceMode.STANDALONE, null, null, 42L, "虚构开发任务", "内容", ownerId,
                null, null, null, null, UUID.randomUUID().toString());
    }

    public static CreateTask linked(long systemId) {
        return new CreateTask("PROJECT-A", SourceMode.LINKED, 100L, "revision-1", systemId, "关联任务", "内容", null,
                null, null, null, null, UUID.randomUUID().toString());
    }

    public static <T> T as(AuthUser user, Supplier<T> action) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null,
                List.of("development:task:read", "development:task:create", "development:task:update",
                        "development:work-item:update", "development:work-item:accept", "development:task:complete")
                        .stream().map(SimpleGrantedAuthority::new).toList()));
        try { return action.get(); } finally { SecurityContextHolder.clearContext(); }
    }

    public static class Fixture {
        public final JdbcTemplate jdbc;
        public final TransactionTemplate tx;
        public final DevelopmentTaskRepository repository;
        public final DevelopmentAccessPolicy access;
        public final DevelopmentChangeRepository changes;
        public final DevelopmentTaskService tasks;
        public final DevelopmentSettings settings;
        public final DevelopmentSystemDirectory systems = mock(DevelopmentSystemDirectory.class);
        public final DevelopmentSourceDirectory sources = mock(DevelopmentSourceDirectory.class);
        public final SystemReferenceQuery references = mock(SystemReferenceQuery.class);
        public final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

        public Fixture(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
            tx = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
            repository = new DevelopmentTaskRepository(jdbc, json);
            when(references.activeParameters(any(), anyString())).thenReturn(List.of());
            when(references.activeParameters(any(), eq("DEVELOPMENT_SYSTEM_MAPPING"))).thenReturn(List.of(
                    new SystemParameterReference("SYS-A", "42"), new SystemParameterReference("SYS-B", "43")));
            when(references.findUser(any(), anyLong(), anyBoolean())).thenAnswer(a -> Optional.of(
                    new SystemUserReference(a.getArgument(1), "虚构用户", "fixture", null, true)));
            var a = new DevelopmentSystemDirectory.SystemRef(42, "PHY-A", "虚构系统 A", 9L, "ACTIVE", 1);
            var b = new DevelopmentSystemDirectory.SystemRef(43, "PHY-B", "虚构系统 B", 9L, "ACTIVE", 1);
            when(systems.find(any(), eq(31L), eq(42L))).thenReturn(Optional.of(a));
            when(systems.find(any(), eq(31L), eq(43L))).thenReturn(Optional.of(b));
            when(systems.searchManageable(any(), eq(31L), any(), any())).thenAnswer(call -> {
                AuthUser actor = call.getArgument(0);
                return new PageResult<>(actor.id() == 9 ? List.of(a, b) : List.of(), actor.id() == 9 ? 2 : 0, 1, 100);
            });
            var source = new DevelopmentSourceDirectory.SourceRequirement(100, "REQ-FIXTURE", "虚构需求", "内容", 31,
                    "revision-1", true, List.of(new DevelopmentSourceDirectory.SourceSystem("SYS-A", Set.of(DevelopmentSourceDirectory.Role.LEAD), null),
                    new DevelopmentSourceDirectory.SourceSystem("SYS-B", Set.of(DevelopmentSourceDirectory.Role.TEST), null)));
            when(sources.requireCurrent(any(), eq("PROJECT-A"), eq(100L))).thenReturn(source);
            when(sources.search(any(), any())).thenReturn(new PageResult<>(List.of(source), 1, 1, 100));
            ProjectAccessService projects = (ref, actor) -> {
                if (!"PROJECT-A".equals(ref)) throw new BusinessException(ErrorCode.FORBIDDEN, "无项目权限");
                return new ProjectAccess(31, ref, "虚构项目");
            };
            settings = new DevelopmentSettings(references, json);
            access = new DevelopmentAccessPolicy(repository, systems, references, projects);
            changes = new DevelopmentChangeRepository(jdbc, json, mock(SystemOperationAudit.class));
            tasks = new DevelopmentTaskService(repository, access, new DevelopmentSourceResolver(sources, settings),
                    new DevelopmentNumberService(jdbc, settings), changes, json);
        }

        public TaskView create(AuthUser user, CreateTask request) {
            return tx.execute(status -> tasks.create(user, request));
        }
    }
}
