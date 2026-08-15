package com.ccb.architecture.web;

import com.ccb.architecture.model.LogicalSubsystem;
import com.ccb.architecture.model.LogicalSubsystemCommand;
import com.ccb.architecture.model.LogicalSubsystemQuery;
import com.ccb.architecture.service.LogicalSubsystemService;
import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.security.model.AuthUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LogicalSubsystemControllerTest {
    private static final AuthUser ACTOR = new AuthUser(9, 7, "architect", "hash", "架构管理员", 11, true);

    private LogicalSubsystemService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(LogicalSubsystemService.class);
        LogicalSubsystemController controller = new LogicalSubsystemController(service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ArchitectureExceptionAdvice())
                .setCustomArgumentResolvers(new AuthenticationPrincipalResolver(ACTOR))
                .build();
    }

    @AfterEach
    void clearTrace() {
        com.ccb.common.trace.TraceId.clear();
    }

    @Test
    void listReturnsTypedPageEnvelopeAndFixedFilters() throws Exception {
        when(service.list(eq(ACTOR), any(PageQuery.class), any(LogicalSubsystemQuery.class)))
                .thenReturn(new PageResult<>(List.of(logical()), 1, 2, 10));

        mockMvc.perform(get("/api/architecture/logical-subsystems")
                        .param("page", "2").param("size", "10")
                        .param("code", "AP").param("businessOrgId", "11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records[0].code").value("AP_201"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.records[0].tenantId").doesNotExist());
    }

    @Test
    void createIgnoresClientTenantAndReturnsNormalizedResource() throws Exception {
        when(service.create(eq(ACTOR), any(LogicalSubsystemCommand.class), any())).thenReturn(logical());

        mockMvc.perform(post("/api/architecture/logical-subsystems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code":"ap_201","shortName":"员工渠道","name":"员工渠道整合平台",
                                  "businessOrgId":11,"contactUserId":21,"tenantId":999
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("AP_201"))
                .andExpect(jsonPath("$.data.tenantId").doesNotExist());

        verify(service).create(eq(ACTOR), any(LogicalSubsystemCommand.class), any());
    }

    @Test
    void moduleAdviceMapsOnlyArchitectureNotFoundTo40400() throws Exception {
        when(service.detail(ACTOR, 404)).thenThrow(new ArchitectureNotFoundException("逻辑子系统不存在"));

        mockMvc.perform(get("/api/architecture/logical-subsystems/404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ArchitectureExceptionAdvice.NOT_FOUND_CODE))
                .andExpect(jsonPath("$.message").value("逻辑子系统不存在"));

        Method[] handlers = ArchitectureExceptionAdvice.class.getDeclaredMethods();
        assertThat(handlers).filteredOn(method -> method.isAnnotationPresent(
                        org.springframework.web.bind.annotation.ExceptionHandler.class))
                .singleElement().extracting(Method::getParameterTypes)
                .satisfies(types -> assertThat(types).containsExactly(ArchitectureNotFoundException.class));
    }

    @Test
    void endpointsDeclareTheFourFixedPermissions() throws Exception {
        assertPermission("list", "architecture:logical:list", long.class, long.class, String.class,
                String.class, String.class, Long.class, AuthUser.class);
        assertPermission("detail", "architecture:logical:list", long.class, AuthUser.class);
        assertPermission("create", "architecture:logical:create", LogicalSubsystemCommand.class, AuthUser.class);
        assertPermission("update", "architecture:logical:update", long.class, LogicalSubsystemCommand.class, AuthUser.class);
        assertPermission("delete", "architecture:logical:delete", long.class, AuthUser.class);
    }

    private void assertPermission(String methodName, String authority, Class<?>... parameterTypes) throws Exception {
        PreAuthorize annotation = LogicalSubsystemController.class.getMethod(methodName, parameterTypes)
                .getAnnotation(PreAuthorize.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasAuthority('" + authority + "')");
    }

    private LogicalSubsystem logical() {
        return new LogicalSubsystem(101, "AP_201", "员工渠道", "员工渠道整合平台", 11,
                null, null, null, 21, null, null, 9, 9,
                LocalDateTime.of(2026, 8, 15, 10, 0), LocalDateTime.of(2026, 8, 15, 10, 0));
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
