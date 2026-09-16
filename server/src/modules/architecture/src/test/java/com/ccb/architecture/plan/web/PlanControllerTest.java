package com.ccb.architecture.plan.web;

import com.ccb.architecture.plan.service.PlanBlockService;
import com.ccb.architecture.plan.service.PlanDependencyService;
import com.ccb.architecture.plan.service.PlanEngine;
import com.ccb.architecture.plan.service.PlanExecutionService;
import com.ccb.architecture.plan.service.PlanGenerationService;
import com.ccb.architecture.plan.service.PlanQueryService;
import com.ccb.architecture.plan.service.PlanTimeService;
import com.ccb.architecture.plan.service.PlanWorkOrderService;
import com.ccb.architecture.service.ArchitectureOptionsService;
import com.ccb.architecture.web.ArchitectureExceptionAdvice;
import com.ccb.common.api.PageResult;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.ProjectAccess;
import com.ccb.system.capability.ProjectAccessService;
import com.ccb.system.capability.SystemOperationAudit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlanControllerTest {
    private static final AuthUser ACTOR =
            new AuthUser(9L, 7L, "architect", "hash", "架构师", 11L, true);
    private static final ProjectAccess PROJECT = new ProjectAccess(70L, "PROJECT-A", "项目 A");

    private PlanQueryService queryService;
    private ProjectAccessService projectAccessService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        queryService = mock(PlanQueryService.class);
        projectAccessService = mock(ProjectAccessService.class);
        PlanController controller = new PlanController(
                mock(PlanGenerationService.class),
                mock(PlanExecutionService.class),
                mock(PlanDependencyService.class),
                mock(PlanBlockService.class),
                mock(PlanTimeService.class),
                mock(PlanWorkOrderService.class),
                queryService,
                mock(PlanEngine.class),
                mock(ArchitectureOptionsService.class),
                mock(SystemOperationAudit.class),
                projectAccessService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ArchitectureExceptionAdvice())
                .setCustomArgumentResolvers(new AuthenticationPrincipalResolver(ACTOR))
                .build();
    }

    @Test
    void 项目私有入口缺少projectRef时返回四百且不访问业务数据() throws Exception {
        mockMvc.perform(get("/api/architecture/plans"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/architecture/plans/1"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/architecture/tasks/1/work-orders"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(projectAccessService, queryService);
    }

    @Test
    void 计划列表使用解析后的可信项目主键() throws Exception {
        when(projectAccessService.requireAccessible(PROJECT.projectRef(), ACTOR)).thenReturn(PROJECT);
        when(queryService.list(eq(ACTOR), eq(PROJECT.id()), any(), eq(1L), eq(20L)))
                .thenReturn(new PageResult<>(List.of(), 0, 1, 20));

        mockMvc.perform(get("/api/architecture/plans")
                        .param("projectRef", PROJECT.projectRef()))
                .andExpect(status().isOk());

        verify(projectAccessService).requireAccessible(PROJECT.projectRef(), ACTOR);
        verify(queryService).list(eq(ACTOR), eq(PROJECT.id()), any(), eq(1L), eq(20L));
    }

    private record AuthenticationPrincipalResolver(AuthUser actor) implements HandlerMethodArgumentResolver {
        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
        }

        @Override
        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                      NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
            return actor;
        }
    }
}
