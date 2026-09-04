package com.ccb.architecture.web;

import com.ccb.architecture.model.DeploymentUnitModels.DeploymentUnitCommand;
import com.ccb.architecture.service.DeploymentUnitService;
import com.ccb.architecture.service.DeploymentUnitService.RelatedDeploymentUnitView;
import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.security.model.AuthUser;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DeploymentUnitControllerTest {
    private static final AuthUser ACTOR = new AuthUser(9, 7, "architect", "hash", "架构管理员", 11, true);
    private static final String VIEW_PERMISSION =
            "hasAnyAuthority('architecture:deployment-unit:view', 'architecture:deployment-unit:manage', "
                    + "'architecture:view', 'architecture:apply', 'architecture:manage')";
    private static final String MANAGE_PERMISSION = "hasAuthority('architecture:deployment-unit:manage')";

    private DeploymentUnitService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(DeploymentUnitService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new DeploymentUnitController(service))
                .setControllerAdvice(new ArchitectureExceptionAdvice())
                .setCustomArgumentResolvers(new AuthenticationPrincipalResolver(ACTOR))
                .build();
    }

    @AfterEach
    void clearTrace() {
        com.ccb.common.trace.TraceId.clear();
    }

    @Test
    void 关联选项转发搜索分页和排除自身参数() throws Exception {
        RelatedDeploymentUnitView option = new RelatedDeploymentUnitView(
                202L, "DU00002", "YGQL1_DB", "DATABASE", 302L, "营销数据库子系统", "ACTIVE");
        when(service.options(eq(ACTOR), eq("YGQL"), eq(201L), any(PageQuery.class)))
                .thenReturn(new PageResult<>(List.of(option), 1L, 2L, 30L));

        mockMvc.perform(get("/api/architecture/deployment-units/options")
                        .param("keyword", "YGQL").param("excludeId", "201")
                        .param("page", "2").param("size", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].name").value("YGQL1_DB"))
                .andExpect(jsonPath("$.data.records[0].physicalSubsystemName").value("营销数据库子系统"))
                .andExpect(jsonPath("$.data.records[0].tenantId").doesNotExist())
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.size").value(30));

        ArgumentCaptor<PageQuery> pageQuery = ArgumentCaptor.forClass(PageQuery.class);
        verify(service).options(eq(ACTOR), eq("YGQL"), eq(201L), pageQuery.capture());
        assertThat(pageQuery.getValue().page()).isEqualTo(2L);
        assertThat(pageQuery.getValue().size()).isEqualTo(30L);
    }

    @Test
    void 创建命令只接收完整名称类型和结构化关联() throws Exception {
        when(service.create(eq(ACTOR), any(DeploymentUnitCommand.class), any()))
                .thenReturn(mock(DeploymentUnitService.DeploymentUnitView.class));

        mockMvc.perform(post("/api/architecture/deployment-units")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "physicalSubsystemId": 301,
                                  "name": "SMSLJ_AP",
                                  "kind": "APPLICATION",
                                  "relatedDeploymentUnitIds": [202, 203],
                                  "defaultNetworkZoneId": 401,
                                  "description": "短信逻辑应用",
                                  "remark": "测试"
                                }
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<DeploymentUnitCommand> command = ArgumentCaptor.forClass(DeploymentUnitCommand.class);
        verify(service).create(eq(ACTOR), command.capture(), any());
        assertThat(command.getValue().physicalSubsystemId()).isEqualTo(301L);
        assertThat(command.getValue().name()).isEqualTo("SMSLJ_AP");
        assertThat(command.getValue().kind()).isEqualTo("APPLICATION");
        assertThat(command.getValue().relatedDeploymentUnitIds()).containsExactly(202L, 203L);

        List<String> componentNames = Arrays.stream(DeploymentUnitCommand.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertThat(componentNames)
                .doesNotContain("tenantId", "shortName", "relatedDeploymentUnitName", "deploymentUnitType");
    }

    @Test
    void 查询和写入端点保持部署单元权限契约() throws Exception {
        assertPermission("list", VIEW_PERMISSION, long.class, long.class, String.class, String.class,
                Long.class, String.class, String.class, AuthUser.class);
        assertPermission("options", VIEW_PERMISSION, String.class, long.class, long.class, Long.class, AuthUser.class);
        assertPermission("detail", VIEW_PERMISSION, long.class, AuthUser.class);
        assertPermission("versions", VIEW_PERMISSION, long.class, AuthUser.class);
        assertPermission("create", MANAGE_PERMISSION, DeploymentUnitCommand.class, AuthUser.class);
        assertPermission("update", MANAGE_PERMISSION, long.class, DeploymentUnitCommand.class, AuthUser.class);
        assertPermission("deactivate", MANAGE_PERMISSION, long.class, AuthUser.class);
        assertPermission("reactivate", MANAGE_PERMISSION, long.class, AuthUser.class);
        assertPermission("voidUnit", MANAGE_PERMISSION, long.class, AuthUser.class);
    }

    private void assertPermission(String methodName, String expectedExpression, Class<?>... parameterTypes)
            throws Exception {
        PreAuthorize annotation = DeploymentUnitController.class.getMethod(methodName, parameterTypes)
                .getAnnotation(PreAuthorize.class);
        assertThat(annotation).isNotNull();
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
