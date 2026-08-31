package com.ccb.architecture.service;

import com.ccb.architecture.integration.DeploymentUnitReferenceCheckRequest;
import com.ccb.architecture.integration.DeploymentUnitReferenceChecker;
import com.ccb.architecture.integration.ReferenceCheckResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.ccb.architecture.integration.ReferenceCheckResult.Status.INDETERMINATE;
import static com.ccb.architecture.integration.ReferenceCheckResult.Status.REFERENCED;

/**
 * 汇总部署单元外部引用检查器；无法判定时一律拒绝作废（fail-closed）。
 */
@Service
public class DeploymentUnitReferenceGuard {
    /** 由 architecture Advice 映射为 HTTP 503 的业务错误码。 */
    public static final int SERVICE_UNAVAILABLE = 50300;

    private static final String CLEAR_SUMMARY = "未发现有效引用";
    private static final String EXTERNAL_CHECK_UNAVAILABLE = "外部引用检查暂不可用";

    private final List<DeploymentUnitReferenceChecker> checkers;

    public DeploymentUnitReferenceGuard(List<DeploymentUnitReferenceChecker> checkers) {
        this.checkers = List.copyOf(Objects.requireNonNull(checkers, "checkers 不能为空"));
    }

    /**
     * 检查作废是否没有有效引用；任何外部检查器异常或不可判定都按存在引用处理。
     */
    public ReferenceCheckResult check(DeploymentUnitReferenceCheckRequest request) {
        List<String> indeterminateSummaries = new ArrayList<>();
        for (DeploymentUnitReferenceChecker checker : checkers) {
            ReferenceCheckResult result;
            try {
                result = checker.check(request);
            } catch (RuntimeException exception) {
                indeterminateSummaries.add(EXTERNAL_CHECK_UNAVAILABLE + "：" + checker.checkerKey());
                continue;
            }
            if (result == null) {
                indeterminateSummaries.add(EXTERNAL_CHECK_UNAVAILABLE + "：" + checker.checkerKey());
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
    public void requireClear(DeploymentUnitReferenceCheckRequest request) {
        ReferenceCheckResult result = check(request);
        if (result.status() == REFERENCED) {
            throw new BusinessException(ErrorCode.CONFLICT, result.safeSummary());
        }
        if (result.status() == INDETERMINATE) {
            throw new BusinessException(SERVICE_UNAVAILABLE, result.safeSummary());
        }
    }

    private String joinSummaries(List<String> summaries) {
        return String.join("；", summaries.stream().distinct().toList());
    }
}
