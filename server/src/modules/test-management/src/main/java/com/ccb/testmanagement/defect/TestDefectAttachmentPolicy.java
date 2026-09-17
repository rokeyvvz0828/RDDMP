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
import com.ccb.testmanagement.persistence.TestAttachmentAccessMapper;
import org.springframework.stereotype.Component;
import java.util.Map;

/** 缺陷附件访问必须回到缺陷所属项目及测试大类校验。 */
@Component
public class TestDefectAttachmentPolicy implements AttachmentAccessPolicy {
    private final TestAttachmentAccessMapper mapper;
    public TestDefectAttachmentPolicy(TestAttachmentAccessMapper mapper) { this.mapper = mapper; }
    @Override public String businessType() { return TestDefectService.BUSINESS_TYPE; }
    @Override public boolean canAccess(AuthUser user, String businessId, AttachmentOperation operation) { try { long id = Long.parseLong(businessId); String domain = mapper.defectDomain(Map.of("id", id, "tenantId", user.tenantId())); if (!user.enabled() || domain == null) return false; var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication(); return auth != null && auth.getAuthorities().stream().anyMatch(value -> ("test-management:" + domain + ":defects").equals(value.getAuthority())); } catch (Exception exception) { return false; } }
}
