package com.ccb.architecture.network.web;

import com.ccb.architecture.network.model.NetworkAccessModels.AccessDecision;
import com.ccb.architecture.network.model.NetworkAccessModels.AccessProtocol;
import com.ccb.architecture.network.model.NetworkAccessModels.DecisionBasis;
import com.ccb.architecture.network.model.NetworkAccessModels.ExemptionRuleStatus;
import com.ccb.architecture.network.model.NetworkAccessModels.NetworkAccessExemptionRule;
import com.ccb.architecture.network.model.NetworkAccessModels.ValidityType;
import com.ccb.architecture.network.service.NetworkAccessApplicationSubmissionService;
import com.ccb.architecture.network.service.NetworkAccessService;
import com.ccb.architecture.network.service.NetworkAccessService.ExemptionRuleCommand;
import com.ccb.architecture.network.service.NetworkAccessService.NetworkAccessCommand;
import com.ccb.architecture.network.service.NetworkAccessService.NetworkAccessDecisionCommand;
import com.ccb.architecture.network.service.NetworkAccessService.NetworkAccessDecisionResult;
import com.ccb.architecture.web.ArchitectureExceptionAdvice;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.SystemOperationAudit;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NetworkAccessControllerTest {
    private static final AuthUser ACTOR = new AuthUser(9L, 7L, "applicant", "hash", "申请人", 11L, true);
    private static final LocalDateTime TIME = LocalDateTime.of(2026, 8, 28, 10, 0);

    private NetworkAccessService service;
    private NetworkAccessApplicationSubmissionService submissionService;
    private SystemOperationAudit operationAudit;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(NetworkAccessService.class);
        submissionService = mock(NetworkAccessApplicationSubmissionService.class);
        operationAudit = mock(SystemOperationAudit.class);
        NetworkAccessController controller = new NetworkAccessController(service, submissionService, operationAudit);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ArchitectureExceptionAdvice())
                .setCustomArgumentResolvers(new AuthenticationPrincipalResolver())
                .build();
    }

    @Test
    void 新增端点权限注解覆盖判定规则和生命周期() throws Exception {
        assertThat(authorities("decideNetworkAccess")).contains(
                "architecture:network-access:view",
                "architecture:network-access:apply",
                "architecture:network-access:manage");
        assertThat(authorities("listExemptionRules")).contains(
                "architecture:network-access:view",
                "architecture:network-access:manage");
        assertThat(authorities("createExemptionRule")).containsExactly(
                "architecture:network-access:manage",
                "architecture:manage");
        assertThat(authorities("updateExemptionRule")).containsExactly(
                "architecture:network-access:manage",
                "architecture:manage");
        assertThat(authorities("disableExemptionRule")).containsExactly(
                "architecture:network-access:manage",
                "architecture:manage");
        assertThat(authorities("createApplication")).contains(
                "architecture:network-access:apply",
                "architecture:network-access:manage");
        assertThat(authorities("submitApplication")).contains(
                "architecture:network-access:apply",
                "architecture:network-access:manage");
        assertThat(authorities("cancelApplication")).contains(
                "architecture:network-access:apply",
                "architecture:network-access:manage");
    }

    @Test
    void 判定端点调用服务并写审计() throws Exception {
        when(service.decideAccess(eq(ACTOR), any(NetworkAccessDecisionCommand.class)))
                .thenReturn(new NetworkAccessDecisionResult(AccessDecision.NEEDS_APPLICATION, true,
                        DecisionBasis.STRICT_REQUIRED, List.of("NO_FULL_COVERAGE"), List.of(), List.of()));

        mvc.perform(post("/api/architecture/network-access/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"source":{"kind":"MANAGED","physicalSubsystemId":1,"environmentId":2,
                                  "deploymentUnitId":3,"instanceIds":[4]},
                                 "target":{"kind":"EXTERNAL","externalAddressId":5},
                                 "protocol":"TCP","ports":"443",
                                 "validFrom":"2026-08-28T10:00:00",
                                 "validUntil":"2026-08-29T10:00:00",
                                 "validityType":"LIMITED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.decision").value("NEEDS_APPLICATION"))
                .andExpect(jsonPath("$.data.reasonCodes[0]").value("NO_FULL_COVERAGE"));

        verify(service).decideAccess(eq(ACTOR), any(NetworkAccessDecisionCommand.class));
        verify(operationAudit).recordSuccess(any());
    }

    @Test
    void 停用免申请规则调用服务并写审计() throws Exception {
        NetworkAccessExemptionRule rule = new NetworkAccessExemptionRule(55L, 7L, "EXEMPT_1",
                "默认免申请", 10L, "应用区", 11L, "服务区", AccessProtocol.TCP, "443",
                TIME, null, ValidityType.LONG_TERM, ExemptionRuleStatus.DISABLED, null, 2L,
                9L, 9L, TIME, TIME);
        when(service.updateExemptionRuleStatus(ACTOR, 55L, 1L, ExemptionRuleStatus.DISABLED))
                .thenReturn(rule);

        mvc.perform(post("/api/architecture/network-access-exemption-rules/55/disable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rowVersion\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));

        verify(service).updateExemptionRuleStatus(ACTOR, 55L, 1L, ExemptionRuleStatus.DISABLED);
        verify(operationAudit).recordSuccess(any());
    }

    private List<String> authorities(String methodName) throws Exception {
        Method method = NetworkAccessController.class.getDeclaredMethod(methodName, params(methodName));
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile("'([^']+)'").matcher(annotation.value());
        List<String> result = new java.util.ArrayList<>();
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
        return result;
    }

    private Class<?>[] params(String methodName) {
        return switch (methodName) {
            case "decideNetworkAccess" -> new Class<?>[]{NetworkAccessDecisionCommand.class, AuthUser.class};
            case "listExemptionRules" -> new Class<?>[]{ExemptionRuleStatus.class, AuthUser.class};
            case "createExemptionRule" -> new Class<?>[]{ExemptionRuleCommand.class, AuthUser.class};
            case "updateExemptionRule" -> new Class<?>[]{long.class, ExemptionRuleCommand.class, AuthUser.class};
            case "enableExemptionRule", "disableExemptionRule" ->
                    new Class<?>[]{long.class, NetworkAccessController.RowVersionRequest.class, AuthUser.class};
            case "createApplication" -> new Class<?>[]{NetworkAccessCommand.class, AuthUser.class};
            case "submitApplication", "approveApplication", "rejectApplication", "cancelApplication" ->
                    new Class<?>[]{long.class, NetworkAccessController.RowVersionRequest.class, AuthUser.class};
            default -> throw new IllegalArgumentException(methodName);
        };
    }

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
