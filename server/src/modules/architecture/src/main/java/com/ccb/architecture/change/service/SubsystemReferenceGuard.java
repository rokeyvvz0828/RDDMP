package com.ccb.architecture.change.service;

import com.ccb.architecture.integration.ReferenceCheckRequest;
import com.ccb.architecture.integration.ReferenceCheckResult;
import com.ccb.architecture.integration.SubsystemReferenceChecker;
import com.ccb.architecture.repository.ArchitectureSubsystemRepository;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.ccb.architecture.integration.ReferenceCheckRequest.Operation.OFFLINE;
import static com.ccb.architecture.integration.ReferenceCheckRequest.Operation.VOID;
import static com.ccb.architecture.integration.ReferenceCheckRequest.SubsystemKind.LOGICAL;
import static com.ccb.architecture.integration.ReferenceCheckResult.Status.INDETERMINATE;
import static com.ccb.architecture.integration.ReferenceCheckResult.Status.REFERENCED;

/**
 * 汇总模块内父子约束和外部引用检查器；无法判定时一律拒绝下线或作废。
 */
@Service
public class SubsystemReferenceGuard {
    /** 由 architecture Advice 映射为 HTTP 503 的业务错误码。 */
    public static final int SERVICE_UNAVAILABLE = 50300;

    private static final String CLEAR_SUMMARY = "未发现有效引用";
    private static final String INTERNAL_CHECK_UNAVAILABLE = "模块内引用检查暂不可用";
    private static final String EXTERNAL_CHECK_UNAVAILABLE = "外部引用检查暂不可用";

    private final ArchitectureSubsystemRepository repository;
    private final List<SubsystemReferenceChecker> checkers;

    public SubsystemReferenceGuard(ArchitectureSubsystemRepository repository,
                                   List<SubsystemReferenceChecker> checkers) {
        this.repository = Objects.requireNonNull(repository, "repository 不能为空");
        this.checkers = List.copyOf(Objects.requireNonNull(checkers, "checkers 不能为空"));
    }

    /**
     * 检查 OFFLINE/VOID 是否没有有效引用。CREATE_REFERENCE 不属于状态守卫入口。
     */
    public ReferenceCheckResult check(ReferenceCheckRequest request) {
        requireSupportedRequest(request);

        List<String> indeterminateSummaries = new ArrayList<>();
        ReferenceCheckResult internal = checkInternalReferences(request);
        if (internal.status() == REFERENCED) {
            return internal;
        }
        if (internal.status() == INDETERMINATE) {
            indeterminateSummaries.add(internal.safeSummary());
        }

        for (SubsystemReferenceChecker checker : checkers) {
            ReferenceCheckResult result;
            try {
                result = checker.check(request);
            } catch (RuntimeException exception) {
                indeterminateSummaries.add(EXTERNAL_CHECK_UNAVAILABLE);
                continue;
            }
            if (result == null) {
                indeterminateSummaries.add(EXTERNAL_CHECK_UNAVAILABLE);
                continue;
            }
            if (result.status() == REFERENCED) {
                return ReferenceCheckResult.referenced(result.safeSummary());
            }
            if (result.status() == INDETERMINATE) {
                indeterminateSummaries.add(result.safeSummary());
            }
        }

        if (!indeterminateSummaries.isEmpty()) {
            return ReferenceCheckResult.indeterminate(joinSummaries(indeterminateSummaries));
        }
        return ReferenceCheckResult.clear(CLEAR_SUMMARY);
    }

    /** 非 CLEAR 结果统一转换为可供服务层处理的稳定业务异常。 */
    public void requireClear(ReferenceCheckRequest request) {
        ReferenceCheckResult result = check(request);
        if (result.status() == REFERENCED) {
            throw new BusinessException(ErrorCode.CONFLICT, result.safeSummary());
        }
        if (result.status() == INDETERMINATE) {
            throw new BusinessException(SERVICE_UNAVAILABLE, result.safeSummary());
        }
    }

    private ReferenceCheckResult checkInternalReferences(ReferenceCheckRequest request) {
        if (request.subsystemKind() != LOGICAL) {
            return ReferenceCheckResult.clear(CLEAR_SUMMARY);
        }
        try {
            if (request.operation() == OFFLINE
                    && repository.countActivePhysicalByLogical(request.tenantId(), request.subsystemId()) > 0) {
                return ReferenceCheckResult.referenced("逻辑子系统下仍有 ACTIVE 物理子系统");
            }
            if (request.operation() == VOID
                    && repository.countPhysicalHistoryByLogical(request.tenantId(), request.subsystemId()) > 0) {
                return ReferenceCheckResult.referenced("逻辑子系统存在物理子系统发布历史");
            }
            return ReferenceCheckResult.clear(CLEAR_SUMMARY);
        } catch (RuntimeException exception) {
            return ReferenceCheckResult.indeterminate(INTERNAL_CHECK_UNAVAILABLE);
        }
    }

    private void requireSupportedRequest(ReferenceCheckRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "引用检查请求不能为空");
        }
        if (request.operation() != OFFLINE && request.operation() != VOID) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "引用状态守卫只支持 OFFLINE 或 VOID");
        }
    }

    private String joinSummaries(List<String> summaries) {
        return String.join("；", summaries.stream().distinct().toList());
    }
}
