package com.ccb.architecture.change.web;

import com.ccb.architecture.change.model.SubsystemChangeModels.ActionType;
import com.ccb.architecture.change.model.SubsystemChangeModels.ApplicationStatus;
import com.ccb.architecture.change.model.SubsystemChangeModels.ChangeApplication;
import com.ccb.architecture.change.model.SubsystemChangeModels.ChangeHistoryEvent;
import com.ccb.architecture.change.model.SubsystemChangeModels.PhysicalDraft;
import com.ccb.architecture.change.model.SubsystemChangeModels.TargetKind;
import com.ccb.architecture.change.service.ArchitectureSubsystemSubmissionService;
import com.ccb.architecture.change.service.SubsystemChangeService;
import com.ccb.architecture.change.service.SubsystemChangeService.AccessScope;
import com.ccb.architecture.change.service.SubsystemChangeService.ApplicationDetail;
import com.ccb.architecture.change.service.SubsystemChangeService.DraftUpdateCommand;
import com.ccb.architecture.change.service.SubsystemChangeService.PhysicalApplicationCommand;
import com.ccb.architecture.change.suggestion.SubsystemSuggestionProvider;
import com.ccb.architecture.change.suggestion.SubsystemSuggestionProvider.Suggestion;
import com.ccb.architecture.change.suggestion.SubsystemSuggestionProvider.SuggestionRequest;
import com.ccb.architecture.web.ArchitectureExceptionAdvice;
import com.ccb.common.exception.ErrorCode;
import com.ccb.common.trace.TraceId;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.SystemOperationAudit;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    private SystemOperationAudit operationAudit;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(SubsystemChangeService.class);
        workflowService = mock(ArchitectureSubsystemSubmissionService.class);
        suggestionProvider = mock(SubsystemSuggestionProvider.class);
        operationAudit = mock(SystemOperationAudit.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new SubsystemChangeApplicationController(service, workflowService, suggestionProvider, operationAudit))
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
    void 创建只接受物理目标且伪造身份和逻辑草稿字段不会生效() throws Exception {
        when(service.createPhysical(eq(ACTOR), any(PhysicalApplicationCommand.class))).thenReturn(physicalDetail());
        ArgumentCaptor<PhysicalApplicationCommand> command = ArgumentCaptor.forClass(PhysicalApplicationCommand.class);

        mockMvc.perform(post(BASE).principal(authentication("architecture:apply"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetKind":"PHYSICAL","actionType":"CREATE","reason":"新建物理系统",
                                 "tenantId":999,"applicantId":998,"accessScope":"MANAGE",
                                 "logicalDraft":{"shortName":"旧逻辑草稿"},
                                 "physicalDrafts":[{"lineNo":99,"code":"IGNORED"}],
                                 "physicalDraft":{"lineNo":1,"code":"PHY_MALL","shortName":"商城物理",
                                   "name":"商城物理系统","logicalSubsystemName":"商城逻辑域",
                                   "businessComponentCode":"architecture.business-component.employee-portal",
                                   "businessGroupName":"渠道","responsibleTeamOrgId":12,
                                   "runtimeCode":"RUNTIME","systemLevelCode":"A",
                                   "developmentFrameworkCode":"Spring"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.application.id").value(102))
                .andExpect(jsonPath("$.data.application.tenantId").doesNotExist())
                .andExpect(jsonPath("$.data.physicalDrafts[0].code").value("PHY_MALL"))
                .andExpect(jsonPath("$.data.physicalDrafts[0].logicalSubsystemName").value("商城逻辑域"))
                .andExpect(jsonPath("$.data.physicalDrafts[0].businessComponentCode").value("architecture.business-component.employee-portal"));

        verify(service).createPhysical(eq(ACTOR), command.capture());
        assertThat(command.getValue().actionType()).isEqualTo(ActionType.CREATE);
        assertThat(command.getValue().targetId()).isNull();
        assertThat(command.getValue().physicalDraft().code()).isEqualTo("PHY_MALL");
        assertThat(command.getValue().physicalDraft().logicalSubsystemName()).isEqualTo("商城逻辑域");
    }

    @Test
    void 缺失或逻辑目标类型返回业务参数错误且不调用草稿服务() throws Exception {
        mockMvc.perform(post(BASE).principal(authentication("architecture:apply"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST));
        mockMvc.perform(post(BASE).principal(authentication("architecture:apply"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"targetKind\":\"LOGICAL\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST));

        verifyNoInteractions(service);
    }

    @Test
    void 管理者维护本人申请仍使用本人范围且提交取消走工作流协调器() throws Exception {
        when(service.update(eq(ACTOR), eq(AccessScope.OWN), eq(101L), eq(7L), any(DraftUpdateCommand.class)))
                .thenReturn(physicalDetail());
        when(workflowService.cancel(ACTOR, 101L, 8L)).thenReturn(cancelledDetail());
        when(workflowService.submit(ACTOR, 101L, 7L)).thenReturn(physicalDetail());
        ArgumentCaptor<DraftUpdateCommand> updateCommand = ArgumentCaptor.forClass(DraftUpdateCommand.class);

        mockMvc.perform(put(BASE + "/101").principal(authentication("architecture:manage"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rowVersion":7,"reason":"更新说明","tenantId":999,"applicantId":998,
                                 "accessScope":"OWN","logicalDraft":{"shortName":"旧逻辑草稿"},
                                 "physicalDrafts":[{"lineNo":1,"code":"PHY_MALL","shortName":"商城物理",
                                 "name":"商城物理系统","logicalSubsystemName":"商城逻辑域",
                                 "businessComponentCode":"architecture.business-component.employee-portal",
                                 "responsibleTeamOrgId":12}]}
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
                .andExpect(jsonPath("$.data.application.id").value(102));

        verify(service).update(eq(ACTOR), eq(AccessScope.OWN), eq(101L), eq(7L), updateCommand.capture());
        verify(workflowService).cancel(ACTOR, 101L, 8L);
        verify(workflowService).submit(ACTOR, 101L, 7L);
        assertThat(updateCommand.getValue().reason()).isEqualTo("更新说明");
        assertThat(updateCommand.getValue().physicalDrafts()).singleElement()
                .satisfies(draft -> assertThat(draft.code()).isEqualTo("PHY_MALL"));
        verify(operationAudit).recordSuccess(argThat(command ->
                command.operationCode().equals("architecture.subsystem-change.update")
                        && "PUT".equals(command.requestMethod())
                        && command.requestPath().equals(BASE + "/101")));
        verify(operationAudit).recordSuccess(argThat(command ->
                command.operationCode().equals("architecture.subsystem-change.cancel")));
        verify(operationAudit).recordSuccess(argThat(command ->
                command.operationCode().equals("architecture.subsystem-change.submit")));
        verify(operationAudit, times(3)).recordSuccess(any());
        verify(operationAudit, never()).recordFailure(any());
    }

    @Test
    void 候选建议只委派Provider且不写入草稿或发起网络调用() throws Exception {
        List<Suggestion> candidates = List.of(new Suggestion("englishName", "Mall Platform", "LOCAL", "本地候选"));
        when(suggestionProvider.suggest(any(SuggestionRequest.class))).thenReturn(candidates);
        ArgumentCaptor<SuggestionRequest> requestCaptor = ArgumentCaptor.forClass(SuggestionRequest.class);

        mockMvc.perform(post(BASE + "/suggestions").principal(authentication("architecture:apply"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":999,\"fieldValues\":{\"name\":\"商城物理\",\"tenantId\":\"999\",\"accessScope\":\"MANAGE\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].field").value("englishName"))
                .andExpect(jsonPath("$.data[0].source").value("LOCAL"));

        verify(suggestionProvider).suggest(requestCaptor.capture());
        assertThat(requestCaptor.getValue().fieldValues()).containsEntry("name", "商城物理");
        assertThat(requestCaptor.getValue().fieldValues()).doesNotContainKeys("tenantId", "accessScope");
        verifyNoInteractions(service, workflowService);
    }

    private void assertPermission(String methodName, String expectedExpression,
                                  Class<?>... parameterTypes) throws Exception {
        PreAuthorize annotation = SubsystemChangeApplicationController.class.getMethod(methodName, parameterTypes)
                .getAnnotation(PreAuthorize.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo(expectedExpression);
    }

    private void assertMapping(String methodName, Class<?> annotationType, String expectedPath) {
        Method method = List.of(SubsystemChangeApplicationController.class.getDeclaredMethods()).stream()
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        Object annotation = method.getAnnotation(annotationType.asSubclass(java.lang.annotation.Annotation.class));
        assertThat(annotation).isNotNull();
        String[] paths;
        if (annotation instanceof GetMapping getMapping) {
            paths = getMapping.value();
        } else if (annotation instanceof PostMapping postMapping) {
            paths = postMapping.value();
        } else if (annotation instanceof PutMapping putMapping) {
            paths = putMapping.value();
        } else {
            throw new AssertionError("unsupported annotation " + annotationType);
        }
        assertThat(paths.length == 0 ? "" : paths[0]).isEqualTo(expectedPath);
    }

    private Authentication authentication(String authority) {
        return new TestingAuthenticationToken(ACTOR, "n/a", authority);
    }

    private ChangeApplication application(long id, long applicantId) {
        return new ChangeApplication(id, ACTOR.tenantId(), TargetKind.PHYSICAL, ActionType.CREATE, null,
                applicantId, "申请原因", ApplicationStatus.DRAFT, 1, null, null, null, null,
                false, 1, applicantId, applicantId, time(), time());
    }

    private ApplicationDetail physicalDetail() {
        ChangeApplication application = application(102L, ACTOR.id());
        return new ApplicationDetail(application, List.of(physicalDraft(application.id(), "PHY_MALL")),
                List.of(history(application.id(), ApplicationStatus.DRAFT)));
    }

    private ApplicationDetail cancelledDetail() {
        ChangeApplication application = new ChangeApplication(101L, ACTOR.tenantId(), TargetKind.PHYSICAL,
                ActionType.CREATE, null, ACTOR.id(), "申请原因", ApplicationStatus.CANCELLED, 1,
                null, null, null, null, false, 2, ACTOR.id(), ACTOR.id(), time(), time());
        return new ApplicationDetail(application, List.of(physicalDraft(application.id(), "PHY_MALL")),
                List.of(history(application.id(), ApplicationStatus.CANCELLED)));
    }

    private PhysicalDraft physicalDraft(long applicationId, String code) {
        return new PhysicalDraft(applicationId, 1, ACTOR.tenantId(), null, code,
                "商城物理", "商城物理系统", "商城逻辑域",
                "architecture.business-component.employee-portal", "Mall Platform", "渠道",
                12L, "平台研发团队", "RUNTIME", "A", "Spring", 30L,
                "描述", null, null, 0, null, time(), time());
    }

    private ChangeHistoryEvent history(long applicationId, ApplicationStatus status) {
        return new ChangeHistoryEvent(1L, ACTOR.tenantId(), applicationId, "DRAFT_SAVED",
                null, status, 1, "保存草稿", null, null, ACTOR.id(), time());
    }

    private LocalDateTime time() {
        return LocalDateTime.of(2026, 8, 22, 10, 0);
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
