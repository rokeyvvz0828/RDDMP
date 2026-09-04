/*
 * 文件：server/src/modules/test-management/src/main/java/com/ccb/testmanagement/casework/TestCaseAttachmentPolicy.java
 * 说明：测试案例的服务、策略或接口实现。
 * 用途：承载模块边界内的查询、校验、事务、权限或文件处理职责。
 * 作者：hengguan
 */
package com.ccb.testmanagement.casework;

// 关键逻辑：附件访问必须先回查所属业务实体，并以租户、测试大类和项目边界阻断越权读取。

import com.ccb.attachment.integration.AttachmentAccessPolicy;
import com.ccb.attachment.integration.AttachmentOperation;
import com.ccb.security.model.AuthUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** 案例附件仅允许当前租户中拥有对应大类案例阅读权限的用户访问。 */
@Component
public class TestCaseAttachmentPolicy implements AttachmentAccessPolicy {
    private final JdbcTemplate jdbc;
    public TestCaseAttachmentPolicy(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    @Override public String businessType() { return TestCaseService.BUSINESS_TYPE; }
    @Override public boolean canAccess(AuthUser user,String key,AttachmentOperation operation) {
        if(user==null||!user.enabled()||key==null)return false;
        try { long caseId=Long.parseLong(key); ListRow row=row(caseId,user.tenantId()); return row != null && authority("test-management:"+row.domain+":cases"); } catch(Exception ignored){return false;}
    }
    private ListRow row(long id,long tenant){return jdbc.query("SELECT test_domain FROM tm_test_case WHERE id=? AND tenant_id=? AND deleted=0",rs->rs.next()?new ListRow(rs.getString(1)):null,id,tenant);}
    private boolean authority(String expected){Authentication a=SecurityContextHolder.getContext().getAuthentication();return a!=null&&a.getAuthorities()!=null&&a.getAuthorities().stream().anyMatch(x->expected.equals(x.getAuthority()));}
    private record ListRow(String domain) {}
}
