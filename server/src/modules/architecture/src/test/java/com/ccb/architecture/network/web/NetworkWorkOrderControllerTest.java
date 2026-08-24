package com.ccb.architecture.network.web;

import com.ccb.architecture.network.model.NetworkWorkOrderModels.ActionType;
import com.ccb.architecture.network.model.NetworkWorkOrderModels.HistoryEvent;
import com.ccb.architecture.network.model.NetworkWorkOrderModels.Kind;
import com.ccb.architecture.network.model.NetworkWorkOrderModels.WorkOrder;
import com.ccb.architecture.network.model.NetworkWorkOrderModels.WorkOrderStatus;
import com.ccb.architecture.network.service.NetworkWorkOrderService;
import com.ccb.architecture.network.service.NetworkWorkOrderService.AccessScope;
import com.ccb.architecture.network.service.NetworkWorkOrderService.CreateCommand;
import com.ccb.architecture.network.service.NetworkWorkOrderService.UpdateCommand;
import com.ccb.architecture.network.service.NetworkWorkOrderService.WorkOrderDetail;
import com.ccb.architecture.network.service.NetworkWorkOrderSubmissionService;
import com.ccb.architecture.web.ArchitectureExceptionAdvice;
import com.ccb.common.exception.ErrorCode;
import com.ccb.common.trace.TraceId;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.SystemOperationAudit;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NetworkWorkOrderControllerTest {
    private static final String BASE = "/api/architecture/network-work-orders";
    private static final AuthUser ACTOR = new AuthUser(9L, 7L, "applicant", "hash", "申请人", 11L, true);

    private NetworkWorkOrderService service;
    private NetworkWorkOrderSubmissionService workflowService;
    private SystemOperationAudit operationAudit;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(NetworkWorkOrderService.class);
        workflowService = mock(NetworkWorkOrderSubmissionService.class);
        operationAudit = mock(SystemOperationAudit.class);
        NetworkWorkOrderController controller =
                new NetworkWorkOrderController(service, workflowService, operationAudit,
                        new com.fasterxml.jackson.databind.ObjectMapper());
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ArchitectureExceptionAdvice())
                .setCustomArgumentResolvers(new AuthenticationPrincipalResolver())
                .build();
    }

    @Test
    void 端点权限注解覆盖全部写操作() throws Exception {
        assertThat(authorities("list")).containsExactly(
                "architecture:network-work-order:view",
                "architecture:network-work-order:apply",
                "architecture:network-work-order:manage");
        assertThat(authorities("create")).containsExactly(
                "architecture:network-work-order:apply",
                "architecture:network-work-order:manage");
        assertThat(authorities("update")).containsExactly(
                "architecture:network-work-order:apply",
                "architecture:network-work-order:manage");
        assertThat(authorities("submit")).containsExactly(
                "architecture:network-work-order:apply",
                "architecture:network-work-order:manage");
        assertThat(authorities("cancel")).containsExactly(
                "architecture:network-work-order:apply",
                "architecture:network-work-order:manage");
        assertThat(authorities("registerHandlingResult")).containsExactly(
                "architecture:network-work-order:manage");
        assertThat(authorities("removeAttachment")).containsExactly(
                "architecture:network-work-order:apply",
                "architecture:network-work-order:manage");
    }

    @Test
    void 创建工单调用服务并写审计() throws Exception {
        WorkOrder order = new WorkOrder(77001L, 7L, Kind.CLB, ActionType.OPEN, "渠道接入CLB",
                9L, "原因", WorkOrderStatus.DRAFT, "{}", "[]", null, null, "[]", null, null,
                0, null, null, null, null, false, 0, 9L, 9L, null, null);
        when(service.create(eq(ACTOR), any(CreateCommand.class)))
                .thenReturn(new WorkOrderDetail(order, List.of()));

        mvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kind":"CLB","actionType":"OPEN","reason":"新环境开通",
                                 "payload":{"clbName":"渠道接入CLB","purpose":"渠道流量接入"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workOrder.subject").value("渠道接入CLB"));

        ArgumentCaptor<CreateCommand> commandCaptor = ArgumentCaptor.forClass(CreateCommand.class);
        verify(service).create(eq(ACTOR), commandCaptor.capture());
        assertThat(commandCaptor.getValue().kind()).isEqualTo(Kind.CLB);
        assertThat(commandCaptor.getValue().actionType()).isEqualTo(ActionType.OPEN);
        verify(operationAudit).recordSuccess(any());
    }

    @Test
    void 非法kind返回400且不写库() throws Exception {
        mvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"LOADBALANCER\",\"actionType\":\"OPEN\",\"payload\":{}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST));
        verify(service, org.mockito.Mockito.never()).create(any(), any());
    }

    @Test
    void 更新提交取消登记结果端点接线() throws Exception {
        WorkOrder order = order(77002L, Kind.DNS, ActionType.ADD);
        WorkOrderDetail detail = new WorkOrderDetail(order, List.of());
        when(service.update(eq(ACTOR), eq(77002L), any(UpdateCommand.class))).thenReturn(detail);
        when(workflowService.submit(ACTOR, 77002L, 1L)).thenReturn(detail);
        when(workflowService.cancel(ACTOR, 77002L, 1L)).thenReturn(detail);
        when(service.registerHandlingResult(eq(ACTOR), eq(77002L), eq(1L), any())).thenReturn(detail);

        mvc.perform(put(BASE + "/77002").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rowVersion\":1,\"reason\":\"调整\",\"payload\":{\"domainName\":\"a.test\",\"purpose\":\"x\"}}"))
                .andExpect(status().isOk());
        mvc.perform(post(BASE + "/77002/submit").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rowVersion\":1}"))
                .andExpect(status().isOk());
        mvc.perform(post(BASE + "/77002/cancel").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rowVersion\":1}"))
                .andExpect(status().isOk());
        mvc.perform(post(BASE + "/77002/handling-result").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rowVersion\":1,\"resultStatus\":\"SUCCESS\",\"resultDescription\":\"完成\"}"))
                .andExpect(status().isOk());

        verify(operationAudit, org.mockito.Mockito.times(4)).recordSuccess(any());
    }

    @Test
    void 详情返回解析载荷附件与历史() throws Exception {
        WorkOrder order = order(77003L, Kind.CERT, ActionType.APPLY);
        WorkOrder withResult = new WorkOrder(77003L, 7L, Kind.CERT, ActionType.APPLY, "demo.example.test",
                9L, "原因", WorkOrderStatus.COMPLETED,
                "{\"certType\":\"SSL\",\"subjectName\":\"demo.example.test\",\"purpose\":\"上线准备\",\"description\":null}",
                "[55001]", com.ccb.architecture.network.model.NetworkWorkOrderModels.HandlingResultStatus.SUCCESS,
                "外部已办理", "[55002]", 12L, LocalDateTime.of(2026, 8, 23, 12, 0), 1, null, null, null,
                "f".repeat(64), false, 2, 9L, 12L, null, null);
        when(service.detail(ACTOR, AccessScope.MANAGE, 77003L))
                .thenReturn(new WorkOrderDetail(withResult, List.of(
                        new HistoryEvent(1L, 7L, 77003L, "COMPLETED", WorkOrderStatus.IN_REVIEW,
                                WorkOrderStatus.COMPLETED, 1, "审批通过", "{}", null, 12L,
                                LocalDateTime.of(2026, 8, 23, 12, 1)))));

        mvc.perform(get(BASE + "/77003").principal(authentication(MANAGE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workOrder.kind").value("CERT"))
                .andExpect(jsonPath("$.data.payload.certType").value("SSL"))
                .andExpect(jsonPath("$.data.attachmentIds[0]").value(55001))
                .andExpect(jsonPath("$.data.resultAttachmentIds[0]").value(55002))
                .andExpect(jsonPath("$.data.history[0].eventType").value("COMPLETED"));
    }

    @Test
    void 不存在工单返回40400() throws Exception {
        when(service.detail(ACTOR, AccessScope.MANAGE, 999999L))
                .thenThrow(new com.ccb.architecture.web.ArchitectureNotFoundException("网络专项工单不存在"));
        mvc.perform(get(BASE + "/999999").principal(authentication(MANAGE)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400));
    }

    @Test
    void 业务冲突映射为409() throws Exception {
        when(service.detail(ACTOR, AccessScope.MANAGE, 77005L))
                .thenThrow(new com.ccb.common.exception.BusinessException(ErrorCode.CONFLICT, "行版本冲突"));
        mvc.perform(get(BASE + "/77005").principal(authentication(MANAGE)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.CONFLICT));
    }

    private List<String> authorities(String methodName) throws Exception {
        Method method = NetworkWorkOrderController.class.getDeclaredMethod(methodName,
                params(methodName));
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        String value = annotation.value();
        java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile("'([^']+)'").matcher(value);
        List<String> result = new java.util.ArrayList<>();
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
        return result;
    }

    private Class<?>[] params(String methodName) {
        return switch (methodName) {
            case "list" -> new Class<?>[]{Kind.class, WorkOrderStatus.class, int.class, int.class,
                    AuthUser.class, Authentication.class};
            case "create" -> new Class<?>[]{NetworkWorkOrderController.CreateWorkOrderRequest.class, AuthUser.class};
            case "update" -> new Class<?>[]{long.class, NetworkWorkOrderController.UpdateWorkOrderRequest.class,
                    AuthUser.class};
            case "submit" -> new Class<?>[]{long.class,
                    NetworkWorkOrderController.SubmitWorkOrderRequest.class, AuthUser.class};
            case "cancel" -> new Class<?>[]{long.class,
                    NetworkWorkOrderController.CancelWorkOrderRequest.class, AuthUser.class};
            case "registerHandlingResult" -> new Class<?>[]{long.class,
                    NetworkWorkOrderController.RegisterHandlingResultRequest.class, AuthUser.class};
            case "removeAttachment" -> new Class<?>[]{long.class, long.class,
                    NetworkWorkOrderController.CancelWorkOrderRequest.class, AuthUser.class};
            case "detail" -> new Class<?>[]{long.class, AuthUser.class, Authentication.class};
            default -> throw new IllegalArgumentException(methodName);
        };
    }

    private WorkOrder order(long id, Kind kind, ActionType actionType) {
        return new WorkOrder(id, 7L, kind, actionType, "subject-" + id, 9L, "原因",
                WorkOrderStatus.DRAFT, "{}", "[]", null, null, "[]", null, null,
                0, null, null, null, null, false, 0, 9L, 9L, null, null);
    }

    private static java.security.Principal authentication(String... authorities) {
        return new TestingAuthenticationToken(ACTOR, "n/a", authorities);
    }

    private static final String MANAGE = "architecture:network-work-order:manage";

    /** 从认证主体解析 @AuthenticationPrincipal AuthUser。 */
    private static final class AuthenticationPrincipalResolver implements HandlerMethodArgumentResolver {
        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
        }

        @Override
        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                      NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
            return ACTOR;
        }
    }
}
