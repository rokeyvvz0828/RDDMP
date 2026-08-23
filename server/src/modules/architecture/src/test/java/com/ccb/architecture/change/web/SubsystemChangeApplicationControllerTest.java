package com.ccb.architecture.change.web;

import com.ccb.architecture.change.model.SubsystemChangeModels.ActionType;
import com.ccb.architecture.change.model.SubsystemChangeModels.ApplicationStatus;
import com.ccb.architecture.change.model.SubsystemChangeModels.ChangeApplication;
import com.ccb.architecture.change.model.SubsystemChangeModels.ChangeHistoryEvent;
import com.ccb.architecture.change.model.SubsystemChangeModels.LogicalDraft;
import com.ccb.architecture.change.model.SubsystemChangeModels.PhysicalDraft;
import com.ccb.architecture.change.model.SubsystemChangeModels.TargetKind;
import com.ccb.architecture.change.service.ArchitectureSubsystemSubmissionService;
import com.ccb.architecture.change.service.SubsystemChangeService;
import com.ccb.architecture.change.service.SubsystemChangeService.AccessScope;
import com.ccb.architecture.change.service.SubsystemChangeService.ApplicationDetail;
import com.ccb.architecture.change.service.SubsystemChangeService.DraftUpdateCommand;
import com.ccb.architecture.change.service.SubsystemChangeService.LogicalApplicationCommand;
import com.ccb.architecture.change.service.SubsystemChangeService.PhysicalApplicationCommand;
import com.ccb.architecture.change.suggestion.SubsystemSuggestionProvider;
import com.ccb.architecture.change.suggestion.SubsystemSuggestionProvider.Suggestion;
import com.ccb.architecture.change.suggestion.SubsystemSuggestionProvider.SuggestionRequest;
import com.ccb.architecture.web.ArchitectureExceptionAdvice;
import com.ccb.common.exception.ErrorCode;
import com.ccb.common.trace.TraceId;
import com.ccb.security.model.AuthUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SubsystemChangeApplicationControllerTest {
    private static final String BASE = "/api/architecture/subsystem-change-applications";
    private static final AuthUser ACTOR = new AuthUser(9L, 7L, "architect", "hash", "架构师", 11L, true);

    private SubsystemChangeService service;
    private ArchitectureSubsystemSubmissionService workflowService;
    private SubsystemSuggestionProvider suggestionProvider;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(SubsystemChangeService.class);
        workflowService = mock(ArchitectureSubsystemSubmissionService.class);
        suggestionProvider = mock(SubsystemSuggestionProvider.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new SubsystemChangeApplicationController(service, workflowService, suggestionProvider))
                .setControllerAdvice(new ArchitectureExceptionAdvice())
                .setCustomArgumentResolvers(new AuthenticationPrincipalResolver(ACTOR))
                .build();
    }

    @AfterEach
    void clearTrace() {
        TraceId.clear();
    }

    @Test
    void 路径映射和权限层级保持明确且没有审批动作入口() throws Exception {
        RequestMapping root = SubsystemChangeApplicationController.class.getAnnotation(RequestMapping.class);
        assertThat(root).isNotNull();
        assertThat(root.value()).containsExactly(BASE);

        assertPermission("list", "hasAnyAuthority('architecture:view','architecture:apply','architecture:manage')",
                ApplicationStatus.class, int.class, int.class, AuthUser.class, Authentication.class);
        assertPermission("detail", "hasAnyAuthority('architecture:view','architecture:apply','architecture:manage')",
                long.class, AuthUser.class, Authentication.class);
        assertPermission("create", "hasAnyAuthority('architecture:apply','architecture:manage')",
                SubsystemChangeApplicationController.CreateApplicationRequest.class, AuthUser.class);
        assertPermission("update", "hasAnyAuthority('architecture:apply','architecture:manage')",
                long.class, SubsystemChangeApplicationController.UpdateApplicationRequest.class,
                AuthUser.class);
        assertPermission("cancel", "hasAnyAuthority('architecture:apply','architecture:manage')",
                long.class, SubsystemChangeApplicationController.CancelApplicationRequest.class,
                AuthUser.class);
        assertPermission("submit", "hasAnyAuthority('architecture:apply','architecture:manage')",
                long.class, SubsystemChangeApplicationController.SubmitApplicationRequest.class,
                AuthUser.class);
        assertPermission("suggestions", "hasAnyAuthority('architecture:apply','architecture:manage')",
                SubsystemChangeApplicationController.SuggestionPayload.class, AuthUser.class);

        assertMapping("list", GetMapping.class, "");
        assertMapping("detail", GetMapping.class, "/{id}");
        assertMapping("create", PostMapping.class, "");
        assertMapping("update", PutMapping.class, "/{id}");
        assertMapping("cancel", PostMapping.class, "/{id}/cancel");
        assertMapping("submit", PostMapping.class, "/{id}/submit");
        assertMapping("suggestions", PostMapping.class, "/suggestions");

        assertThat(SubsystemChangeApplicationController.class.getMethods())
                .noneMatch(method -> method.getName().equals("approve")
                        || method.getName().equals("returnForRevision")
                        || method.getName().equals("reject"));
    }

    @Test
    void 读取范围只从认证权限派生并忽略查询伪造范围() throws Exception {
        when(service.list(ACTOR, AccessScope.OWN, ApplicationStatus.DRAFT, 10, 3))
                .thenReturn(List.of(application(101L, 9L)));
        when(service.list(ACTOR, AccessScope.MANAGE, ApplicationStatus.DRAFT, 10, 3))
                .thenReturn(List.of(application(102L, 99L)));

        mockMvc.perform(get(BASE).principal(authentication("architecture:view"))
                        .param("status", "DRAFT").param("limit", "10").param("offset", "3")
                        .param("accessScope", "MANAGE").param("mineOnly", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(101))
                .andExpect(jsonPath("$.data[0].tenantId").doesNotExist())
                .andExpect(jsonPath("$.traceId").isNotEmpty());

        mockMvc.perform(get(BASE).principal(authentication("architecture:manage"))
                        .param("status", "DRAFT").param("limit", "10").param("offset", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(102));

        verify(service).list(ACTOR, AccessScope.OWN, ApplicationStatus.DRAFT, 10, 3);
        verify(service).list(ACTOR, AccessScope.MANAGE, ApplicationStatus.DRAFT, 10, 3);
    }

    @Test
    void 创建按目标类型分派且伪造身份字段不会生效() throws Exception {
        when(service.createLogical(eq(ACTOR), any(LogicalApplicationCommand.class))).thenReturn(logicalDetail());
        when(service.createPhysical(eq(ACTOR), any(PhysicalApplicationCommand.class))).thenReturn(physicalDetail());
        ArgumentCaptor<LogicalApplicationCommand> logicalCommand = ArgumentCaptor.forClass(LogicalApplicationCommand.class);
        ArgumentCaptor<PhysicalApplicationCommand> physicalCommand = ArgumentCaptor.forClass(PhysicalApplicationCommand.class);

        mockMvc.perform(post(BASE).principal(authentication("architecture:apply"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetKind":"LOGICAL","actionType":"CREATE","reason":"新建逻辑系统",
                                 "tenantId":999,"applicantId":998,"accessScope":"MANAGE",
                                 "logicalDraft":{"shortName":"商城","name":"商城系统","businessOrgId":11,
                                   "deploymentPlatformCode":"P2","systemTypeCode":"APPLICATION",
                                   "systemOwnershipCode":"CHANNEL","contactUserId":21}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.application.id").value(101))
                .andExpect(jsonPath("$.data.application.tenantId").doesNotExist());

        mockMvc.perform(post(BASE).principal(authentication("architecture:manage"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetKind":"PHYSICAL","actionType":"CREATE","reason":"新建物理系统",
                                 "tenantId":999,"applicantId":998,"accessScope":"OWN",
                                 "physicalDraft":{"lineNo":1,"targetLogicalSubsystemId":101,"shortName":"商城物理",
                                   "name":"商城物理系统","businessGroupName":"渠道","responsibleTeamOrgId":12,
                                   "runtimeCode":"RUNTIME","systemLevelCode":"A",
                                   "developmentFrameworkCode":"Spring"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.application.id").value(102));

        verify(service).createLogical(eq(ACTOR), logicalCommand.capture());
        verify(service).createPhysical(eq(ACTOR), physicalCommand.capture());
        assertThat(logicalCommand.getValue().actionType()).isEqualTo(ActionType.CREATE);
        assertThat(logicalCommand.getValue().targetId()).isNull();
        assertThat(physicalCommand.getValue().actionType()).isEqualTo(ActionType.CREATE);
        assertThat(physicalCommand.getValue().physicalDraft().targetLogicalSubsystemId()).isEqualTo(101L);
    }

    @Test
    void 缺失或非法目标类型返回业务参数错误且不调用草稿服务() throws Exception {
        mockMvc.perform(post(BASE).principal(authentication("architecture:apply"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST));
        mockMvc.perform(post(BASE).principal(authentication("architecture:apply"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"targetKind\":\"unknown\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST));

        verifyNoInteractions(service);
    }

    @Test
    void 管理者维护本人申请仍使用本人范围且提交取消走工作流协调器() throws Exception {
        when(service.update(eq(ACTOR), eq(AccessScope.OWN), eq(101L), eq(7L), any(DraftUpdateCommand.class)))
                .thenReturn(logicalDetail());
        when(workflowService.cancel(ACTOR, 101L, 8L)).thenReturn(cancelledDetail());
        when(workflowService.submit(ACTOR, 101L, 7L)).thenReturn(logicalDetail());
        ArgumentCaptor<DraftUpdateCommand> updateCommand = ArgumentCaptor.forClass(DraftUpdateCommand.class);

        mockMvc.perform(put(BASE + "/101").principal(authentication("architecture:manage"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rowVersion":7,"reason":"更新说明","tenantId":999,"applicantId":998,
                                 "accessScope":"OWN","logicalDraft":{"shortName":"商城","name":"商城系统",
                                 "businessOrgId":11,"deploymentPlatformCode":"P2","systemTypeCode":"APPLICATION",
                                 "systemOwnershipCode":"CHANNEL","contactUserId":21},"physicalDrafts":[]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.application.rowVersion").value(1));

        mockMvc.perform(post(BASE + "/101/cancel").principal(authentication("architecture:manage"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rowVersion\":8}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.application.status").value("CANCELLED"));

        mockMvc.perform(post(BASE + "/101/submit").principal(authentication("architecture:manage"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rowVersion\":7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.application.id").value(101));

        verify(service).update(eq(ACTOR), eq(AccessScope.OWN), eq(101L), eq(7L), updateCommand.capture());
        verify(workflowService).cancel(ACTOR, 101L, 8L);
        verify(workflowService).submit(ACTOR, 101L, 7L);
        assertThat(updateCommand.getValue().reason()).isEqualTo("更新说明");
    }

    @Test
    void 候选建议只委派Provider且不写入草稿或发起网络调用() throws Exception {
        List<Suggestion> candidates = List.of(new Suggestion("englishName", "Mall Platform", "LOCAL", "本地候选"));
        when(suggestionProvider.suggest(any(SuggestionRequest.class))).thenReturn(candidates);
        ArgumentCaptor<SuggestionRequest> requestCaptor = ArgumentCaptor.forClass(SuggestionRequest.class);

        mockMvc.perform(post(BASE + "/suggestions").principal(authentication("architecture:apply"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":999,"applicantId":998,"accessScope":"MANAGE",
                                 "fieldValues":{"shortName":"商城","tenantId":"999","access_scope":"MANAGE"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].field").value("englishName"))
                .andExpect(jsonPath("$.data[0].value").value("Mall Platform"));

        verify(suggestionProvider).suggest(requestCaptor.capture());
        assertThat(requestCaptor.getValue().fieldValues()).containsEntry("shortName", "商城")
                .doesNotContainKeys("tenantId", "access_scope");
        verifyNoInteractions(service);
    }

    private void assertPermission(String methodName, String expected, Class<?>... parameterTypes) throws Exception {
        PreAuthorize annotation = SubsystemChangeApplicationController.class.getMethod(methodName, parameterTypes)
                .getAnnotation(PreAuthorize.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo(expected);
    }

    private void assertMapping(String methodName, Class<? extends java.lang.annotation.Annotation> type,
                               String expectedPath) throws Exception {
        Method method = java.util.Arrays.stream(SubsystemChangeApplicationController.class.getMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        if (type == GetMapping.class) {
            assertPath(method.getAnnotation(GetMapping.class).value(), expectedPath);
        } else if (type == PostMapping.class) {
            assertPath(method.getAnnotation(PostMapping.class).value(), expectedPath);
        } else if (type == PutMapping.class) {
            assertPath(method.getAnnotation(PutMapping.class).value(), expectedPath);
        }
    }

    private void assertPath(String[] actualPaths, String expectedPath) {
        if (expectedPath.isEmpty()) {
            assertThat(actualPaths).isEmpty();
            return;
        }
        assertThat(actualPaths).containsExactly(expectedPath);
    }

    private Authentication authentication(String authority) {
        return new TestingAuthenticationToken(ACTOR, "N/A", authority);
    }

    private ChangeApplication application(long id, long applicantId) {
        return new ChangeApplication(id, 7L, TargetKind.LOGICAL, ActionType.CREATE, null, applicantId,
                "申请说明", ApplicationStatus.DRAFT, 0, null, null, null, null, false, 1L,
                applicantId, applicantId, LocalDateTime.of(2026, 8, 23, 10, 0),
                LocalDateTime.of(2026, 8, 23, 10, 0));
    }

    private ApplicationDetail logicalDetail() {
        ChangeApplication application = application(101L, ACTOR.id());
        LogicalDraft logicalDraft = new LogicalDraft(application.id(), application.tenantId(), null,
                "商城", "商城系统", 11L, "P2", "APPLICATION", "CHANNEL", 21L,
                null, null, 0, null, null, 1, null,
                LocalDateTime.of(2026, 8, 23, 10, 0), LocalDateTime.of(2026, 8, 23, 10, 0));
        ChangeHistoryEvent history = new ChangeHistoryEvent(500L, application.tenantId(), application.id(),
                "DRAFT_CREATED", null, ApplicationStatus.DRAFT, 0, "已创建", null, null, ACTOR.id(),
                LocalDateTime.of(2026, 8, 23, 10, 0));
        return new ApplicationDetail(application, logicalDraft, List.of(), List.of(history));
    }

    private ApplicationDetail physicalDetail() {
        ChangeApplication application = application(102L, ACTOR.id());
        PhysicalDraft physicalDraft = new PhysicalDraft(application.id(), 1, application.tenantId(), null, 101L,
                "商城物理", "商城物理系统", null, "渠道", 12L, null, "RUNTIME", "A", "Spring", null,
                null, null, null, null, 1, null,
                LocalDateTime.of(2026, 8, 23, 10, 0), LocalDateTime.of(2026, 8, 23, 10, 0));
        return new ApplicationDetail(application, null, List.of(physicalDraft), List.of());
    }

    private ApplicationDetail cancelledDetail() {
        ChangeApplication application = new ChangeApplication(101L, 7L, TargetKind.LOGICAL, ActionType.CREATE,
                null, ACTOR.id(), "已取消", ApplicationStatus.CANCELLED, 0, null, null, null, null,
                false, 2L, ACTOR.id(), ACTOR.id(), LocalDateTime.of(2026, 8, 23, 10, 0),
                LocalDateTime.of(2026, 8, 23, 10, 5));
        return new ApplicationDetail(application, null, List.of(), List.of());
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
