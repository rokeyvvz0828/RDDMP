package com.ccb.architecture.environment.web;

import com.ccb.architecture.environment.service.EnvironmentResourceService;
import com.ccb.architecture.environment.service.ResourceRequestSubmissionService;
import com.ccb.architecture.plan.service.PlanWorkOrderService;
import com.ccb.architecture.web.ArchitectureExceptionAdvice;
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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EnvironmentResourceControllerTest {
    private static final AuthUser ACTOR =
            new AuthUser(9L, 7L, "architect", "hash", "架构师", 11L, true);
    private static final ProjectAccess PROJECT = new ProjectAccess(70L, "PROJECT-A", "项目 A");

    private EnvironmentResourceService service;
    private ProjectAccessService projectAccessService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(EnvironmentResourceService.class);
        projectAccessService = mock(ProjectAccessService.class);
        EnvironmentResourceController controller = new EnvironmentResourceController(
                service,
                mock(ResourceRequestSubmissionService.class),
                mock(SystemOperationAudit.class),
                mock(PlanWorkOrderService.class),
                projectAccessService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ArchitectureExceptionAdvice())
                .setCustomArgumentResolvers(new AuthenticationPrincipalResolver(ACTOR))
                .build();
    }

    @Test
    void 项目私有入口缺少projectRef时返回四百且不访问业务数据() throws Exception {
        mockMvc.perform(get("/api/architecture/environments"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/architecture/resource-requests"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/architecture/instances"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(projectAccessService, service);
    }

    @Test
    void 环境列表使用解析后的可信项目上下文() throws Exception {
        when(projectAccessService.requireAccessible(PROJECT.projectRef(), ACTOR)).thenReturn(PROJECT);
        when(service.listEnvironments(ACTOR, PROJECT, null, null, null, 20, 0)).thenReturn(List.of());

        mockMvc.perform(get("/api/architecture/environments")
                        .param("projectRef", PROJECT.projectRef()))
                .andExpect(status().isOk());
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
