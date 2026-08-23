package com.ccb.architecture.web;

import com.ccb.architecture.model.LogicalSubsystem;
import com.ccb.architecture.model.LogicalSubsystemCommand;
import com.ccb.architecture.model.LogicalSubsystemQuery;
import com.ccb.architecture.model.PhysicalSubsystemSummary;
import com.ccb.architecture.service.LogicalSubsystemService;
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

class LogicalSubsystemControllerTest {
    private static final AuthUser ACTOR = new AuthUser(9, 7, "architect", "hash", "架构管理员", 11, true);
    private static final String WORK_ORDER_MESSAGE =
            "ARCHITECTURE_WORK_ORDER_REQUIRED：请通过架构子系统变更工单发起申请";

    private LogicalSubsystemService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(LogicalSubsystemService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new LogicalSubsystemController(service))
                .setControllerAdvice(new ArchitectureExceptionAdvice())
                .setCustomArgumentResolvers(new AuthenticationPrincipalResolver(ACTOR))
                .build();
    }

    @AfterEach
    void clearTrace() {
        com.ccb.common.trace.TraceId.clear();
    }

    @Test
    void 列表保持路径分页并返回状态筛选和V82字段() throws Exception {
        when(service.list(eq(ACTOR), any(PageQuery.class), any(LogicalSubsystemQuery.class)))
                .thenReturn(new PageResult<>(List.of(logical()), 1L, 2L, 10L));

        mockMvc.perform(get("/api/architecture/logical-subsystems")
                        .param("page", "2").param("size", "10")
                        .param("code", "A0001").param("businessOrgId", "11").param("status", "OFFLINE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records[0].code").value("A0001"))
                .andExpect(jsonPath("$.data.records[0].numberSequence").value(1))
                .andExpect(jsonPath("$.data.records[0].status").value("OFFLINE"))
                .andExpect(jsonPath("$.data.records[0].sortNo").value(8))
                .andExpect(jsonPath("$.data.records[0].rowVersion").value(3))
                .andExpect(jsonPath("$.data.records[0].tenantId").doesNotExist());
    }

    @Test
    void 详情返回物理子系统摘要及其发布状态() throws Exception {
        when(service.detail(ACTOR, 101L)).thenReturn(logical());

        mockMvc.perform(get("/api/architecture/logical-subsystems/101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.physicalSubsystems[0].code").value("W00011"))
                .andExpect(jsonPath("$.data.physicalSubsystems[0].numberSlot").value("1"))
                .andExpect(jsonPath("$.data.physicalSubsystems[0].englishName").value("Mall Platform"))
                .andExpect(jsonPath("$.data.physicalSubsystems[0].status").value("ACTIVE"));
    }

    @Test
    void 旧写路径返回工单冲突响应() throws Exception {
        BusinessException conflict = new BusinessException(ErrorCode.CONFLICT, WORK_ORDER_MESSAGE);
        when(service.create(eq(ACTOR), any(LogicalSubsystemCommand.class), any())).thenThrow(conflict);
        when(service.update(eq(ACTOR), eq(101L), any(LogicalSubsystemCommand.class), any())).thenThrow(conflict);
        doThrow(conflict).when(service).delete(eq(ACTOR), eq(101L), any());

        mockMvc.perform(post("/api/architecture/logical-subsystems")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.CONFLICT))
                .andExpect(jsonPath("$.message", startsWith("ARCHITECTURE_WORK_ORDER_REQUIRED")));
        mockMvc.perform(put("/api/architecture/logical-subsystems/101")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", startsWith("ARCHITECTURE_WORK_ORDER_REQUIRED")));
        mockMvc.perform(delete("/api/architecture/logical-subsystems/101"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", startsWith("ARCHITECTURE_WORK_ORDER_REQUIRED")));

        verify(service).create(eq(ACTOR), any(LogicalSubsystemCommand.class), any());
        verify(service).update(eq(ACTOR), eq(101L), any(LogicalSubsystemCommand.class), any());
        verify(service).delete(eq(ACTOR), eq(101L), any());
    }

    @Test
    void 模块异常适配仍将未找到映射为40400() throws Exception {
        when(service.detail(ACTOR, 404L)).thenThrow(new ArchitectureNotFoundException("逻辑子系统不存在"));

        mockMvc.perform(get("/api/architecture/logical-subsystems/404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ArchitectureExceptionAdvice.NOT_FOUND_CODE))
                .andExpect(jsonPath("$.message").value("逻辑子系统不存在"));
    }

    @Test
    void 引用检查无法判定时映射为503且保留安全摘要() throws Exception {
        when(service.detail(ACTOR, 503L)).thenThrow(new BusinessException(
                com.ccb.architecture.change.service.SubsystemReferenceGuard.SERVICE_UNAVAILABLE,
                "外部引用检查暂不可用"));

        mockMvc.perform(get("/api/architecture/logical-subsystems/503"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(
                        com.ccb.architecture.change.service.SubsystemReferenceGuard.SERVICE_UNAVAILABLE))
                .andExpect(jsonPath("$.message").value("外部引用检查暂不可用"));
    }

    @Test
    void 端点兼容既有逻辑权限并纳入新三级权限() throws Exception {
        assertPermission("list", "architecture:logical:list",
                "hasAnyAuthority('architecture:logical:list', 'architecture:view', 'architecture:apply', 'architecture:manage')",
                long.class, long.class, String.class,
                String.class, String.class, Long.class, String.class, AuthUser.class);
        assertPermission("detail", "architecture:logical:list",
                "hasAnyAuthority('architecture:logical:list', 'architecture:view', 'architecture:apply', 'architecture:manage')",
                long.class, AuthUser.class);
        assertPermission("create", "architecture:logical:create",
                "hasAnyAuthority('architecture:logical:create', 'architecture:apply', 'architecture:manage')",
                LogicalSubsystemCommand.class, AuthUser.class);
        assertPermission("update", "architecture:logical:update",
                "hasAnyAuthority('architecture:logical:update', 'architecture:apply', 'architecture:manage')",
                long.class, LogicalSubsystemCommand.class, AuthUser.class);
        assertPermission("delete", "architecture:logical:delete",
                "hasAnyAuthority('architecture:logical:delete', 'architecture:apply', 'architecture:manage')",
                long.class, AuthUser.class);
    }

    private void assertPermission(String methodName, String legacyAuthority, String expectedExpression,
                                  Class<?>... parameterTypes) throws Exception {
        PreAuthorize annotation = LogicalSubsystemController.class.getMethod(methodName, parameterTypes)
                .getAnnotation(PreAuthorize.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo(expectedExpression);
        assertThat(annotation.value()).contains("'" + legacyAuthority + "'");
    }

    private LogicalSubsystem logical() {
        return new LogicalSubsystem(101L, "A0001", "商城", "商城系统", 11L,
                "P2", "APPLICATION", "CHANNEL", 21L, "系统描述", null,
                9L, 9L, LocalDateTime.of(2026, 8, 15, 10, 0), LocalDateTime.of(2026, 8, 15, 10, 0),
                1, "OFFLINE", 8, 3L,
                List.of(new PhysicalSubsystemSummary(201L, "W00011", "商城物理", "商城物理平台",
                        "1", "Mall Platform", "ACTIVE", 4L)));
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
