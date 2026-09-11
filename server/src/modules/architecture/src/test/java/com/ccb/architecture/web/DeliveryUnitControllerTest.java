package com.ccb.architecture.web;

import com.ccb.architecture.model.DeliveryUnitModels.DeliveryUnitCommand;
import com.ccb.architecture.service.DeliveryUnitService;
import com.ccb.architecture.service.DeliveryUnitService.DeliveryUnitView;
import com.ccb.architecture.service.DeploymentUnitService.RelatedDeploymentUnitView;
import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.ProjectAccess;
import com.ccb.system.capability.ProjectAccessService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DeliveryUnitControllerTest {
    private static final AuthUser ACTOR = new AuthUser(9, 7, "architect", "hash", "架构管理员", 11, true);
    private static final ProjectAccess PROJECT = new ProjectAccess(70L, "PROJECT-A", "项目 A");
    private static final String VIEW_PERMISSION =
            "hasAnyAuthority('architecture:delivery-unit:view', 'architecture:delivery-unit:manage', "
                    + "'architecture:view', 'architecture:apply', 'architecture:manage')";
    private static final String MANAGE_PERMISSION = "hasAuthority('architecture:delivery-unit:manage')";

    private DeliveryUnitService service;
    private ProjectAccessService projectAccessService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(DeliveryUnitService.class);
        projectAccessService = mock(ProjectAccessService.class);
        when(projectAccessService.requireAccessible("PROJECT-A", ACTOR)).thenReturn(PROJECT);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new DeliveryUnitController(service, projectAccessService),
                        new DeploymentUnitDeliveryUnitController(service, projectAccessService))
                .setControllerAdvice(new ArchitectureExceptionAdvice())
                .setCustomArgumentResolvers(new AuthenticationPrincipalResolver(ACTOR))
                .build();
    }

    @AfterEach
    void clearTrace() {
        com.ccb.common.trace.TraceId.clear();
    }

    @Test
    void 列表转发分页筛选且不返回租户标识() throws Exception {
        DeliveryUnitView view = new DeliveryUnitView(1L, "DUW0001A001", 501L, "W0001A", "渠道接入系统", "ACTIVE",
                "统一认证交付包", "ACTIVE", List.of(), null, null, 88L, "技术架构师", 88L, "技术架构师",
                java.time.LocalDateTime.of(2026, 9, 10, 10, 0), java.time.LocalDateTime.of(2026, 9, 10, 10, 0), 0L,
                null);
        when(service.list(eq(ACTOR), eq(PROJECT), any(PageQuery.class), any()))
                .thenReturn(new PageResult<>(List.of(view), 1L, 1L, 20L));

        mockMvc.perform(get("/api/architecture/delivery-units")
                        .param("projectRef", "PROJECT-A")
                        .param("name", "认证")
                        .param("physicalSubsystemId", "501")
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].code").value("DUW0001A001"))
                .andExpect(jsonPath("$.data.records[0].physicalSubsystemName").value("渠道接入系统"))
                .andExpect(jsonPath("$.data.records[0].tenantId").doesNotExist())
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void 创建命令只接收归属名称与结构化关联() throws Exception {
        when(service.create(eq(ACTOR), eq(PROJECT), any(DeliveryUnitCommand.class), any())).thenReturn(null);

        mockMvc.perform(post("/api/architecture/delivery-units")
                        .param("projectRef", "PROJECT-A")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "physicalSubsystemId": 501,
                                  "name": "统一认证交付包",
                                  "artifactTypeCode": "architecture.artifact-type.container",
                                  "relatedDeploymentUnitIds": [31, 32],
                                  "description": "交付内容",
                                  "remark": "测试"
                                }
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<DeliveryUnitCommand> command = ArgumentCaptor.forClass(DeliveryUnitCommand.class);
        verify(service).create(eq(ACTOR), eq(PROJECT), command.capture(), any());
        assertThat(command.getValue().physicalSubsystemId()).isEqualTo(501L);
        assertThat(command.getValue().name()).isEqualTo("统一认证交付包");
        assertThat(command.getValue().artifactTypeCode()).isEqualTo("architecture.artifact-type.container");
        assertThat(command.getValue().relatedDeploymentUnitIds()).containsExactly(31L, 32L);

        List<String> componentNames = Arrays.stream(DeliveryUnitCommand.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertThat(componentNames).doesNotContain("tenantId", "code", "status");
    }

    @Test
    void 关联覆盖式更新转发部署单元集合() throws Exception {
        when(service.replaceDeploymentUnits(eq(ACTOR), eq(PROJECT), eq(1L), any(), any())).thenReturn(null);

        mockMvc.perform(put("/api/architecture/delivery-units/1/deployment-units")
                        .param("projectRef", "PROJECT-A")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deploymentUnitIds\":[31,32]}"))
                .andExpect(status().isOk());

        ArgumentCaptor<List<Long>> ids = ArgumentCaptor.forClass(List.class);
        verify(service).replaceDeploymentUnits(eq(ACTOR), eq(PROJECT), eq(1L), ids.capture(), any());
        assertThat(ids.getValue()).containsExactly(31L, 32L);
    }

    @Test
    void 部署单元候选接口限定物理子系统() throws Exception {
        when(service.deploymentUnitOptions(eq(ACTOR), eq(PROJECT), eq(501L), eq("认证"), any(), any(PageQuery.class)))
                .thenReturn(new PageResult<>(List.of(new RelatedDeploymentUnitView(31L, "DW0001A001", "AUTH_AP",
                        "APPLICATION", 501L, "渠道接入系统", "ACTIVE")), 1L, 1L, 20L));

        mockMvc.perform(get("/api/architecture/delivery-units/deployment-unit-options")
                        .param("projectRef", "PROJECT-A")
                        .param("physicalSubsystemId", "501")
                        .param("keyword", "认证"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].name").value("AUTH_AP"));

        verify(service).deploymentUnitOptions(eq(ACTOR), eq(PROJECT), eq(501L), eq("认证"),
                eq(null), any(PageQuery.class));
    }

    @Test
    void 删除接口转发软删除() throws Exception {
        mockMvc.perform(delete("/api/architecture/delivery-units/1").param("projectRef", "PROJECT-A"))
                .andExpect(status().isOk());

        verify(service).delete(eq(ACTOR), eq(PROJECT), eq(1L), any());
    }

    @Test
    void 部署单元反查接口返回只读交付单元列表() throws Exception {
        when(service.relatedDeliveryUnits(ACTOR, PROJECT, 31L))
                .thenReturn(List.of(new DeliveryUnitService.RelatedDeliveryUnitView(1L, "DUW0001A001",
                        "统一认证交付包", "ACTIVE")));

        mockMvc.perform(get("/api/architecture/deployment-units/31/delivery-units")
                        .param("projectRef", "PROJECT-A"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("DUW0001A001"))
                .andExpect(jsonPath("$.data[0].name").value("统一认证交付包"))
                .andExpect(jsonPath("$.data[0].tenantId").doesNotExist());
    }

    @Test
    void 缺少项目上下文时拒绝请求() throws Exception {
        mockMvc.perform(get("/api/architecture/delivery-units"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 交付单元端点保持权限契约() throws Exception {
        assertPermission(DeliveryUnitController.class, "list", VIEW_PERMISSION, long.class, long.class, String.class,
                Long.class, String.class, String.class, String.class, AuthUser.class);
        assertPermission(DeliveryUnitController.class, "deploymentUnitOptions", VIEW_PERMISSION, Long.class,
                String.class, long.class, long.class, Long.class, String.class, AuthUser.class);
        assertPermission(DeliveryUnitController.class, "detail", VIEW_PERMISSION, long.class, String.class,
                AuthUser.class);
        assertPermission(DeliveryUnitController.class, "create", MANAGE_PERMISSION, DeliveryUnitCommand.class,
                String.class, AuthUser.class);
        assertPermission(DeliveryUnitController.class, "update", MANAGE_PERMISSION, long.class,
                DeliveryUnitCommand.class, String.class, AuthUser.class);
        assertPermission(DeliveryUnitController.class, "replaceDeploymentUnits", MANAGE_PERMISSION, long.class,
                DeliveryUnitController.DeliveryUnitRelationCommand.class, String.class, AuthUser.class);
        assertPermission(DeliveryUnitController.class, "deactivate", MANAGE_PERMISSION, long.class, String.class,
                AuthUser.class);
        assertPermission(DeliveryUnitController.class, "reactivate", MANAGE_PERMISSION, long.class, String.class,
                AuthUser.class);
        assertPermission(DeliveryUnitController.class, "delete", MANAGE_PERMISSION, long.class, String.class,
                AuthUser.class);
        assertPermission(DeploymentUnitDeliveryUnitController.class, "relatedDeliveryUnits",
                "hasAnyAuthority('architecture:deployment-unit:view', 'architecture:deployment-unit:manage', "
                        + "'architecture:delivery-unit:view', 'architecture:delivery-unit:manage', "
                        + "'architecture:view', 'architecture:apply', 'architecture:manage')",
                long.class, String.class, AuthUser.class);
        assertPermission(DeploymentUnitDeliveryUnitController.class, "deliveryUnitOptions",
                "hasAnyAuthority('architecture:deployment-unit:view', 'architecture:deployment-unit:manage', "
                        + "'architecture:delivery-unit:view', 'architecture:delivery-unit:manage', "
                        + "'architecture:view', 'architecture:apply', 'architecture:manage')",
                long.class, String.class, long.class, long.class, String.class, AuthUser.class);
        assertPermission(DeploymentUnitDeliveryUnitController.class, "replaceDeliveryUnits",
                "hasAuthority('architecture:delivery-unit:manage')",
                long.class, DeliveryUnitController.DeliveryUnitRelationCommand.class, String.class, AuthUser.class);
    }

    @Test
    void 部署单元侧关联保存转发集合() throws Exception {
        when(service.replaceDeploymentUnitDeliveryUnits(eq(ACTOR), eq(PROJECT), eq(31L), any(), any()))
                .thenReturn(List.of());

        mockMvc.perform(put("/api/architecture/deployment-units/31/delivery-units")
                        .param("projectRef", "PROJECT-A")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deploymentUnitIds\":[1,2]}"))
                .andExpect(status().isOk());

        ArgumentCaptor<List<Long>> ids = ArgumentCaptor.forClass(List.class);
        verify(service).replaceDeploymentUnitDeliveryUnits(eq(ACTOR), eq(PROJECT), eq(31L), ids.capture(), any());
        assertThat(ids.getValue()).containsExactly(1L, 2L);
    }

    @Test
    void 部署单元侧候选接口返回同子系统交付单元() throws Exception {
        when(service.deliveryUnitOptionsForDeploymentUnit(eq(ACTOR), eq(PROJECT), eq(31L), eq("认证"), any()))
                .thenReturn(new PageResult<>(List.of(new DeliveryUnitService.RelatedDeliveryUnitView(1L,
                        "DUW0001A001", "统一认证交付包", "ACTIVE")), 1L, 1L, 20L));

        mockMvc.perform(get("/api/architecture/deployment-units/31/delivery-unit-options")
                        .param("projectRef", "PROJECT-A")
                        .param("keyword", "认证"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].code").value("DUW0001A001"));
    }

    private void assertPermission(Class<?> controller, String methodName, String expectedExpression,
                                  Class<?>... parameterTypes) throws Exception {
        PreAuthorize annotation = controller.getMethod(methodName, parameterTypes)
                .getAnnotation(PreAuthorize.class);
        assertThat(annotation).as("%s#%s 的权限注解", controller.getSimpleName(), methodName).isNotNull();
        assertThat(annotation.value()).isEqualTo(expectedExpression);
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
