/*
 * 文件：server/src/modules/test-management/src/main/java/com/ccb/testmanagement/plan/TestPlanAttachmentPolicy.java
 * 说明：测试方案的服务、策略或接口实现。
 * 用途：承载模块边界内的查询、校验、事务、权限或文件处理职责。
 * 作者：hengguan
 */
package com.ccb.testmanagement.plan;

// 关键逻辑：附件访问必须先回查所属业务实体，并以租户、测试大类和项目边界阻断越权读取。

import com.ccb.attachment.integration.AttachmentAccessPolicy;
import com.ccb.attachment.integration.AttachmentOperation;
import com.ccb.security.model.AuthUser;
import com.ccb.testmanagement.persistence.TestAttachmentAccessMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 附件必须属于当前租户中仍有效的方案版本，且调用人具备对应大类的方案阅读权限。 */
@Component
public class TestPlanAttachmentPolicy implements AttachmentAccessPolicy {
    private final TestAttachmentAccessMapper mapper;

    public TestPlanAttachmentPolicy(TestAttachmentAccessMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String businessType() {
        return TestPlanService.BUSINESS_TYPE;
    }

    @Override
    public boolean canAccess(AuthUser user, String businessKey, AttachmentOperation operation) {
        if (user == null || !user.enabled() || businessKey == null || businessKey.isBlank()) {
            return false;
        }
        long versionId;
        try {
            versionId = Long.parseLong(businessKey.trim());
        } catch (NumberFormatException exception) {
            return false;
        }
        if (versionId <= 0) {
            return false;
        }
        String domain = mapper.planDomain(Map.of("id", versionId, "tenantId", user.tenantId()));
        return domain != null && hasAuthority("test-management:" + domain + ":plans");
    }

    private boolean hasAuthority(String authority) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities() != null
                && authentication.getAuthorities().stream().anyMatch(granted -> authority.equals(granted.getAuthority()));
    }
}
