package com.ccb.boot.audit;

import com.ccb.common.trace.TraceId;
import com.ccb.security.model.AuthUser;
import com.ccb.system.audit.OperationAuditWriteCommand;
import com.ccb.system.audit.OperationAuditWriter;
import com.ccb.common.audit.OperationAuditContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;
import java.util.Set;

@Component
public class OperationAuditInterceptor implements HandlerInterceptor {
    private static final Logger log = LoggerFactory.getLogger(OperationAuditInterceptor.class);
    private static final String START_NANOS = OperationAuditInterceptor.class.getName() + ".startNanos";
    private static final String DECISION = OperationAuditInterceptor.class.getName() + ".decision";

    private final OperationAuditPolicy policy;
    private final OperationAuditWriter writer;

    public OperationAuditInterceptor(OperationAuditPolicy policy, OperationAuditWriter writer) {
        this.policy = policy;
        this.writer = writer;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        OperationAuditPolicy.Decision decision = policy.decide(request.getMethod(), request.getRequestURI());
        if (decision.included() && actor() != null) {
            request.setAttribute(START_NANOS, System.nanoTime());
            request.setAttribute(DECISION, decision);
            OperationAuditContext.begin();
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception exception) {
        OperationAuditPolicy.Decision decision = (OperationAuditPolicy.Decision) request.getAttribute(DECISION);
        if (decision == null) {
            return;
        }
        try {
            AuthUser actor = actor();
            if (actor == null) {
                return;
            }
            OperationAuditContext.Snapshot context = OperationAuditContext.snapshot();
            int status = response.getStatus();
            boolean success = status < 400 && exception == null;
            writer.record(new OperationAuditWriteCommand(
                    actor,
                    first(context.operationCode(), decision.operationCode()),
                    decision.moduleCode(),
                    decision.moduleName(),
                    decision.operationType(),
                    first(context.targetType(), decision.targetType()),
                    first(context.targetId(), targetId(request)),
                    projectReference(request),
                    request.getMethod(),
                    request.getRequestURI(),
                    success,
                    status,
                    errorMessage(context, exception, status, success),
                    request.getRemoteAddr(),
                    request.getHeader("User-Agent"),
                    TraceId.getOrCreate(),
                    elapsedMillis(request),
                    context.changedFields() == null ? Set.of() : context.changedFields()));
        } catch (RuntimeException auditFailure) {
            log.error("Operation audit write failed traceId={}", TraceId.getOrCreate(), auditFailure);
        } finally {
            OperationAuditContext.clear();
        }
    }

    private AuthUser actor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof AuthUser user ? user : null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> pathVariables(HttpServletRequest request) {
        Object value = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        return value instanceof Map<?, ?> map ? (Map<String, String>) map : Map.of();
    }

    private String projectReference(HttpServletRequest request) {
        Map<String, String> variables = pathVariables(request);
        return first(variables.get("projectId"), variables.get("projectRef"),
                request.getParameter("projectId"), request.getParameter("projectRef"));
    }

    private String targetId(HttpServletRequest request) {
        Map<String, String> variables = pathVariables(request);
        for (String key : ListHolder.TARGET_KEYS) {
            String value = variables.get(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private long elapsedMillis(HttpServletRequest request) {
        Object value = request.getAttribute(START_NANOS);
        return value instanceof Long started ? Math.max(0, (System.nanoTime() - started) / 1_000_000) : 0;
    }

    private String errorMessage(OperationAuditContext.Snapshot context, Exception exception,
                                int status, boolean success) {
        if (success) {
            return null;
        }
        if (context.errorMessage() != null) {
            return "Business operation failed";
        }
        return exception == null ? "HTTP " + status : exception.getClass().getSimpleName();
    }

    private String first(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static final class ListHolder {
        private static final java.util.List<String> TARGET_KEYS = java.util.List.of(
                "id", "code", "applicationCode", "taskId", "definitionId", "instanceId",
                "attachmentId", "roleId", "userId", "previewId");
    }
}
