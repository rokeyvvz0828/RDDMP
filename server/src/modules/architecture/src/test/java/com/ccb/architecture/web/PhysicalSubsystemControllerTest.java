package com.ccb.architecture.web;

import com.ccb.architecture.model.PhysicalSubsystemCommand;
import com.ccb.architecture.model.PhysicalSubsystemQuery;
import com.ccb.architecture.service.PhysicalSubsystemService;
import com.ccb.architecture.service.PhysicalSubsystemService.PhysicalSubsystemView;
import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
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

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PhysicalSubsystemControllerTest {
    private static final AuthUser ACTOR = new AuthUser(9, 7, "architect", "hash", "架构管理员", 11, true);
    private static final String WORK_ORDER_MESSAGE =
            "ARCHITECTURE_WORK_ORDER_REQUIRED：请通过架构子系统变更工单发起申请";

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
    void 列表保持路径分页并返回状态筛选和可选逻辑名称及业务组件() throws Exception {
        when(service.list(eq(ACTOR), any(PageQuery.class), any(PhysicalSubsystemQuery.class)))
                .thenReturn(new PageResult<>(List.of(view()), 1L, 1L, 20L));

        mockMvc.perform(get("/api/architecture/physical-subsystems")
                        .param("code", "W0001").param("businessGroupName", "渠道")
                        .param("responsibleTeamOrgId", "12").param("logicalSubsystemName", "商城")
                        .param("businessComponentCode", "architecture.business-component.employee-portal")
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].code").value("W00011"))
                .andExpect(jsonPath("$.data.records[0].logicalSubsystemName").value("商城系统"))
                .andExpect(jsonPath("$.data.records[0].businessComponentCode").value("architecture.business-component.employee-portal"))
                .andExpect(jsonPath("$.data.records[0].englishName").value("Mall Platform"))
                .andExpect(jsonPath("$.data.records[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.records[0].rowVersion").value(4))
                .andExpect(jsonPath("$.data.records[0].tenantId").doesNotExist());
    }

    @Test
    void 旧写路径返回工单冲突响应() throws Exception {
        BusinessException conflict = new BusinessException(ErrorCode.CONFLICT, WORK_ORDER_MESSAGE);
        when(service.create(eq(ACTOR), any(PhysicalSubsystemCommand.class), any())).thenThrow(conflict);
        when(service.update(eq(ACTOR), eq(201L), any(PhysicalSubsystemCommand.class), any())).thenThrow(conflict);
        doThrow(conflict).when(service).delete(eq(ACTOR), eq(201L), any());

        mockMvc.perform(post("/api/architecture/physical-subsystems")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.CONFLICT))
                .andExpect(jsonPath("$.message", startsWith("ARCHITECTURE_WORK_ORDER_REQUIRED")));
        mockMvc.perform(put("/api/architecture/physical-subsystems/201")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", startsWith("ARCHITECTURE_WORK_ORDER_REQUIRED")));
        mockMvc.perform(delete("/api/architecture/physical-subsystems/201"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", startsWith("ARCHITECTURE_WORK_ORDER_REQUIRED")));

        verify(service).create(eq(ACTOR), any(PhysicalSubsystemCommand.class), any());
        verify(service).update(eq(ACTOR), eq(201L), any(PhysicalSubsystemCommand.class), any());
        verify(service).delete(eq(ACTOR), eq(201L), any());
    }

    @Test
    void 模块异常适配仍将未找到映射为40400() throws Exception {
        when(service.detail(ACTOR, 404L)).thenThrow(new ArchitectureNotFoundException("物理子系统不存在"));

        mockMvc.perform(get("/api/architecture/physical-subsystems/404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ArchitectureExceptionAdvice.NOT_FOUND_CODE));
    }

    @Test
    void 端点兼容既有物理权限并纳入新三级权限() throws Exception {
        assertPermission("list", "architecture:physical:list",
                "hasAnyAuthority('architecture:physical:list', 'architecture:view', 'architecture:apply', 'architecture:manage')",
                long.class, long.class, String.class,
                String.class, String.class, String.class, String.class, String.class, Long.class, String.class,
                AuthUser.class);
        assertPermission("detail", "architecture:physical:list",
                "hasAnyAuthority('architecture:physical:list', 'architecture:view', 'architecture:apply', 'architecture:manage')",
                long.class, AuthUser.class);
        assertPermission("create", "architecture:physical:create",
                "hasAnyAuthority('architecture:physical:create', 'architecture:apply', 'architecture:manage')",
                PhysicalSubsystemCommand.class, AuthUser.class);
        assertPermission("update", "architecture:physical:update",
                "hasAnyAuthority('architecture:physical:update', 'architecture:apply', 'architecture:manage')",
                long.class, PhysicalSubsystemCommand.class, AuthUser.class);
        assertPermission("delete", "architecture:physical:delete",
                "hasAnyAuthority('architecture:physical:delete', 'architecture:apply', 'architecture:manage')",
                long.class, AuthUser.class);
    }

    private void assertPermission(String methodName, String legacyAuthority, String expectedExpression,
                                  Class<?>... parameterTypes) throws Exception {
        PreAuthorize annotation = PhysicalSubsystemController.class.getMethod(methodName, parameterTypes)
                .getAnnotation(PreAuthorize.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo(expectedExpression);
        assertThat(annotation.value()).contains("'" + legacyAuthority + "'");
    }

    private PhysicalSubsystemView view() {
        return new PhysicalSubsystemView(201L, "W00011", "商城物理", "商城物理平台",
                "商城系统", "architecture.business-component.employee-portal", "渠道",
                "architecture.deployment-platform.p2", "architecture.disaster-recovery.active-active",
                12L, "平台研发团队", true,
                "architecture.runtime.7x24", "A", "Spring", 30L, "系统负责人",
                "描述", null, 9L, "架构管理员", 9L,
                LocalDateTime.of(2026, 8, 15, 10, 0), LocalDateTime.of(2026, 8, 15, 10, 0),
                "Mall Platform", "ACTIVE", 4L);
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
