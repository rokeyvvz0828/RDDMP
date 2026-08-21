package com.ccb.release.reporting.service;

import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.release.reporting.model.ReleaseAnalyticsModels.Summary;
import com.ccb.release.reporting.persistence.ReleaseAnalyticsStore;
import com.ccb.security.model.AuthUser;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

@Service
public class ReleaseAnalyticsService {
    private final ReleaseAnalyticsStore store;
    public ReleaseAnalyticsService(ReleaseAnalyticsStore store) { this.store = store; }
    public Summary summary(String projectId, Long windowId, AuthUser user) {
        return store.summary(user.tenantId(), projectId, windowId);
    }
    public PageResult<Map<String, Object>> drilldown(long page, long size, String projectId, Long windowId,
                                                     String dimension, String value, AuthUser user) {
        if (dimension != null && !dimension.isBlank() && !Set.of("versionType", "status", "productionResult").contains(dimension)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "统计下钻维度无效");
        }
        if (dimension != null && !dimension.isBlank() && (value == null || value.isBlank())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "统计下钻值不能为空");
        }
        return store.drilldown(user.tenantId(), projectId, windowId, dimension, value, new PageQuery(page, size));
    }
}
