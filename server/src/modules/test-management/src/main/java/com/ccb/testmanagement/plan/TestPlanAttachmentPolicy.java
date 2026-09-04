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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** 附件必须属于当前租户中仍有效的方案版本，且调用人具备对应大类的方案阅读权限。 */
@Component
public class TestPlanAttachmentPolicy implements AttachmentAccessPolicy {
    private final JdbcTemplate jdbc;

    public TestPlanAttachmentPolicy(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
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
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT p.test_domain FROM tm_test_plan_version v "
                        + "JOIN tm_test_plan p ON p.id=v.plan_id AND p.tenant_id=v.tenant_id AND p.deleted=0 "
                        + "WHERE v.id=? AND v.tenant_id=? AND v.deleted=0",
                versionId, user.tenantId());
        if (rows.size() != 1) {
            return false;
        }
        Object domain = rows.get(0).get("test_domain");
        return domain != null && hasAuthority("test-management:" + domain + ":plans");
    }

    private boolean hasAuthority(String authority) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities() != null
                && authentication.getAuthorities().stream().anyMatch(granted -> authority.equals(granted.getAuthority()));
    }
}
