package com.ccb.release.reporting.service;

import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.release.reporting.model.ReleaseAnalyticsModels.Summary;
import com.ccb.release.reporting.persistence.ReleaseAnalyticsStore;
import com.ccb.release.window.model.ReleaseWindow;
import com.ccb.release.window.persistence.ReleaseWindowStore;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.ProjectAccess;
import com.ccb.system.capability.ProjectAccessService;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

@Service
public class ReleaseAnalyticsService {
    private final ReleaseAnalyticsStore store;
    private final ReleaseWindowStore windows;
    private final ProjectAccessService projectAccessService;
    public ReleaseAnalyticsService(ReleaseAnalyticsStore store, ReleaseWindowStore windows,
                                   ProjectAccessService projectAccessService) {
        this.store = store;
        this.windows = windows;
        this.projectAccessService = projectAccessService;
    }
    public Summary summary(String projectId, Long windowId, AuthUser user) {
        ProjectAccess project = requireScope(projectId, windowId, user);
        return store.summary(user.tenantId(), project.projectRef(), windowId);
    }
    public PageResult<Map<String, Object>> drilldown(long page, long size, String projectId, Long windowId,
                                                     String dimension, String value, AuthUser user) {
        if (dimension != null && !dimension.isBlank() && !Set.of("versionType", "status", "productionResult").contains(dimension)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "统计下钻维度无效");
        }
        if (dimension != null && !dimension.isBlank() && (value == null || value.isBlank())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "统计下钻值不能为空");
        }
        ProjectAccess project = requireScope(projectId, windowId, user);
        return store.drilldown(user.tenantId(), project.projectRef(), windowId, dimension, value, new PageQuery(page, size));
    }

    private ProjectAccess requireScope(String projectId, Long windowId, AuthUser user) {
        ProjectAccess project = projectAccessService.requireAccessible(projectId, user);
        if (windowId != null) {
            ReleaseWindow window = windows.findById(windowId, user.tenantId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "投产窗口不存在"));
            if (!project.projectRef().equals(window.projectId())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "投产窗口与当前项目不一致");
            }
        }
        return project;
    }
}
