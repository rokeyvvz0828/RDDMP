package com.ccb.architecture.plan.web;

import com.ccb.architecture.plan.service.*;
import com.ccb.architecture.service.ArchitectureOptionsService;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.lang.reflect.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 通过真实方法安全代理检查入口RBAC，服务资格拒绝不得被入口吞掉。 */
class PlanParticipationControllerTest {
    private static final AuthUser ACTOR = new AuthUser(9, 7, "tester", "", "参与验收用户", 1, true);
    private static final BusinessException ENTITY_DENIED = new BusinessException(ErrorCode.FORBIDDEN, "模拟实时参与资格拒绝");
    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean ProjectAccessService projects() {
            var service = mock(ProjectAccessService.class);
            when(service.requireAccessible("P70", ACTOR)).thenReturn(new ProjectAccess(70, "P70", "验收项目"));
            return service;
        }
        // 用实体层拒绝作为哨兵：只有通过真实RBAC才会到达，不能用无安全代理的standalone测试替代。
        private <T> T guarded(Class<T> type) { return mock(type, invocation -> { throw ENTITY_DENIED; }); }
        @Bean PlanController controller(ProjectAccessService projects) {
            return new PlanController(guarded(PlanGenerationService.class), guarded(PlanExecutionService.class),
                    guarded(PlanDependencyService.class), guarded(PlanBlockService.class), guarded(PlanTimeService.class),
                    guarded(PlanWorkOrderService.class), guarded(PlanQueryService.class), guarded(PlanEngine.class),
                    mock(ArchitectureOptionsService.class), mock(SystemOperationAudit.class), projects);
        }
    }
    private AnnotationConfigApplicationContext context;
    private PlanController controller;
    @BeforeEach void setup() {
        context = new AnnotationConfigApplicationContext(Config.class);
        controller = context.getBean(PlanController.class);
    }
    @AfterEach void cleanup() {
        SecurityContextHolder.clearContext(); context.close(); com.ccb.common.trace.TraceId.clear();
    }
    private void login(String authority) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(ACTOR, null,
                List.of(new SimpleGrantedAuthority(authority))));
    }
    private void invoke(String name) throws Throwable {
        Method method = Arrays.stream(PlanController.class.getDeclaredMethods()).filter(m -> m.getName().equals(name)).findFirst().orElseThrow();
        Object[] args = Arrays.stream(method.getParameterTypes()).map(type -> {
            if (type == long.class) return (Object) 1L;
            if (type == String.class) return "P70";
            if (type == AuthUser.class) return ACTOR;
            if (type == Authentication.class) return SecurityContextHolder.getContext().getAuthentication();
            if (type == PlanController.WorkOrderRequest.class) return new PlanController.WorkOrderRequest(com.ccb.architecture.plan.model.PlanModels.WorkOrderType.RESOURCE_REQUEST, List.of(1L), "验收");
            if (type == PlanController.BlockRequest.class) return new PlanController.BlockRequest("验收阻塞", "验收影响", 9L, null);
            if (type == PlanController.ScheduleRequest.class) return new PlanController.ScheduleRequest(null, null, "验收");
            return null;
        }).toArray();
        try { method.invoke(controller, args); } catch (InvocationTargetException error) { throw error.getCause(); }
    }
    @ParameterizedTest
    @ValueSource(strings = {"startTask", "completeCheckItem", "reopenCheckItem", "suggestCancel", "addBlock", "updateBlock", "resolveBlock", "updateTaskSchedule", "attachWorkOrders", "detachWorkOrder"})
    void 普通查看角色可到达参与校验且实时拒绝原样传播(String method) {
        login("architecture:plan:view");
        assertThatThrownBy(() -> invoke(method)).isSameAs(ENTITY_DENIED);
    }
    @ParameterizedTest
    @ValueSource(strings = {"startTask", "completeCheckItem", "reopenCheckItem", "suggestCancel", "addBlock", "updateBlock", "resolveBlock", "updateTaskSchedule", "attachWorkOrders", "detachWorkOrder"})
    void 无计划权限不能进入执行服务(String method) {
        login("unrelated:view");
        assertThatThrownBy(() -> invoke(method)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(context.getBean(ProjectAccessService.class));
    }
    @ParameterizedTest
    @ValueSource(strings = {"newTaskAssignment", "assign", "cancelPlan", "cancelTask", "cancelCheckItem", "restoreCheckItem", "acceptSuggestion", "rejectSuggestion", "updatePlanSchedule", "updateStageSchedule", "correctEvent"})
    void 普通参与角色不能获得计划管理权限(String method) {
        login("architecture:plan:view");
        assertThatThrownBy(() -> invoke(method)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(context.getBean(ProjectAccessService.class));
    }
    @Test void 管理角色也不能跳过服务端实时参与拒绝() {
        login("architecture:plan:manage");
        assertThatThrownBy(() -> invoke("startTask")).isSameAs(ENTITY_DENIED);
    }
}

