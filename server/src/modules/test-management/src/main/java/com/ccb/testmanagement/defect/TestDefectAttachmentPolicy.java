/*
 * 文件：server/src/modules/test-management/src/main/java/com/ccb/testmanagement/defect/TestDefectAttachmentPolicy.java
 * 说明：测试缺陷的服务、策略或接口实现。
 * 用途：承载模块边界内的查询、校验、事务、权限或文件处理职责。
 * 作者：hengguan
 */
package com.ccb.testmanagement.defect;

// 关键逻辑：附件访问必须先回查所属业务实体，并以租户、测试大类和项目边界阻断越权读取。

import com.ccb.attachment.integration.AttachmentAccessPolicy;
import com.ccb.attachment.integration.AttachmentOperation;
import com.ccb.security.model.AuthUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 缺陷附件访问必须回到缺陷所属项目及测试大类校验。 */
@Component
public class TestDefectAttachmentPolicy implements AttachmentAccessPolicy {
    private final JdbcTemplate jdbc;
    public TestDefectAttachmentPolicy(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public String businessType() { return TestDefectService.BUSINESS_TYPE; }
    @Override public boolean canAccess(AuthUser user, String businessId, AttachmentOperation operation) { try { long id = Long.parseLong(businessId); var rows = jdbc.queryForList("SELECT test_domain FROM tm_test_defect WHERE id=? AND tenant_id=? AND deleted=0", id, user.tenantId()); if (!user.enabled() || rows.isEmpty()) return false; String domain = String.valueOf(rows.get(0).get("test_domain")); var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication(); return auth != null && auth.getAuthorities().stream().anyMatch(value -> ("test-management:" + domain + ":defects").equals(value.getAuthority())); } catch (Exception exception) { return false; } }
}
