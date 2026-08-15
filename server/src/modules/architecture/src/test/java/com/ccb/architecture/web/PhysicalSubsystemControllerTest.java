package com.ccb.architecture.web;

import com.ccb.architecture.model.PhysicalSubsystemCommand;
import com.ccb.architecture.model.PhysicalSubsystemQuery;
import com.ccb.architecture.service.PhysicalSubsystemService;
import com.ccb.architecture.service.PhysicalSubsystemService.PhysicalSubsystemView;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

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

class PhysicalSubsystemControllerTest {
    private static final AuthUser ACTOR = new AuthUser(9, 7, "architect", "hash", "架构管理员", 11, true);

    private PhysicalSubsystemService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(PhysicalSubsystemService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new PhysicalSubsystemController(service))
                .setControllerAdvice(new ArchitectureExceptionAdvice())
                .setCustomArgumentResolvers(new AuthenticationPrincipalResolver(ACTOR))
                .build();
    }

    @AfterEach
    void clearTrace() {
        com.ccb.common.trace.TraceId.clear();
    }

    @Test
    void listReturnsTypedProjectionAndFixedFilters() throws Exception {
        when(service.list(eq(ACTOR), any(PageQuery.class), any(PhysicalSubsystemQuery.class)))
                .thenReturn(new PageResult<>(List.of(view()), 1, 1, 20));

        mockMvc.perform(get("/api/architecture/physical-subsystems")
                        .param("code", "WP").param("businessGroupName", "渠道")
                        .param("responsibleTeamOrgId", "12").param("logicalSubsystemId", "101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].responsibleTeamDisplayName").value("平台研发团队"))
                .andExpect(jsonPath("$.data.records[0].responsibleTeamValid").value(true))
                .andExpect(jsonPath("$.data.records[0].contactPhone").value("13800000000"))
                .andExpect(jsonPath("$.data.records[0].tenantId").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].responsibleTeamNameSnapshot").doesNotExist());
    }

    @Test
    void createCannotAcceptTenantPhoneStatusOrTeamSnapshotFields() throws Exception {
        when(service.create(eq(ACTOR), any(PhysicalSubsystemCommand.class), any())).thenReturn(view());

        mockMvc.perform(post("/api/architecture/physical-subsystems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code":"WP_201","shortName":"员工渠道物理","name":"员工渠道物理平台",
                                  "logicalSubsystemId":101,"responsibleTeamOrgId":12,
                                  "tenantId":999,"status":0,"contactPhone":"secret",
                                  "responsibleTeamNameSnapshot":"伪造团队"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.responsibleTeamDisplayName").value("平台研发团队"))
                .andExpect(jsonPath("$.data.tenantId").doesNotExist())
                .andExpect(jsonPath("$.data.status").doesNotExist())
                .andExpect(jsonPath("$.data.responsibleTeamNameSnapshot").doesNotExist());

        verify(service).create(eq(ACTOR), any(PhysicalSubsystemCommand.class), any());
    }

    @Test
    void moduleAdviceAlsoMapsPhysicalNotFoundTo40400() throws Exception {
        when(service.detail(ACTOR, 404)).thenThrow(new ArchitectureNotFoundException("物理子系统不存在"));

        mockMvc.perform(get("/api/architecture/physical-subsystems/404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ArchitectureExceptionAdvice.NOT_FOUND_CODE));
    }

    @Test
    void endpointsDeclareTheFourFixedPermissions() throws Exception {
        assertPermission("list", "architecture:physical:list", long.class, long.class, String.class,
                String.class, String.class, String.class, Long.class, Long.class, AuthUser.class);
        assertPermission("detail", "architecture:physical:list", long.class, AuthUser.class);
        assertPermission("create", "architecture:physical:create", PhysicalSubsystemCommand.class, AuthUser.class);
        assertPermission("update", "architecture:physical:update", long.class, PhysicalSubsystemCommand.class, AuthUser.class);
        assertPermission("delete", "architecture:physical:delete", long.class, AuthUser.class);
    }

    private void assertPermission(String methodName, String authority, Class<?>... parameterTypes) throws Exception {
        PreAuthorize annotation = PhysicalSubsystemController.class.getMethod(methodName, parameterTypes)
                .getAnnotation(PreAuthorize.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasAuthority('" + authority + "')");
    }

    private PhysicalSubsystemView view() {
        return new PhysicalSubsystemView(201, "WP_201", "员工渠道物理", "员工渠道物理平台",
                101, "AP_201", "员工渠道整合平台", null, 12, "平台研发团队", true,
                "24H", "A_PLUS", "P2", 30L, "系统负责人", 31L, "联系人", "13800000000",
                "描述", null, 9, 9, LocalDateTime.of(2026, 8, 15, 10, 0),
                LocalDateTime.of(2026, 8, 15, 10, 0));
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
