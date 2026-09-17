/*
 * 文件：server/src/modules/test-management/src/main/java/com/ccb/testmanagement/execution/TestExecutionAttachmentPolicy.java
 * 说明：测试执行的服务、策略或接口实现。
 * 用途：承载模块边界内的查询、校验、事务、权限或文件处理职责。
 * 作者：hengguan
 */
package com.ccb.testmanagement.execution;

// 关键逻辑：附件访问必须先回查所属业务实体，并以租户、测试大类和项目边界阻断越权读取。

import com.ccb.attachment.integration.AttachmentAccessPolicy;
import com.ccb.attachment.integration.AttachmentOperation;
import com.ccb.security.model.AuthUser;
import com.ccb.testmanagement.persistence.TestAttachmentAccessMapper;
import org.springframework.stereotype.Component;

/** 执行证据附件仅向同租户且拥有对应执行阅读权限的用户开放。 */
@Component
public class TestExecutionAttachmentPolicy implements AttachmentAccessPolicy {
    private final TestAttachmentAccessMapper mapper;
    public TestExecutionAttachmentPolicy(TestAttachmentAccessMapper mapper){this.mapper=mapper;}
    @Override public String businessType(){return TestExecutionService.BUSINESS_TYPE;}
    @Override public boolean canAccess(AuthUser user,String key,AttachmentOperation operation){try{long id=Long.parseLong(key);String domain=mapper.executionDomain(java.util.Map.of("id",id,"tenantId",user.tenantId()));if(!user.enabled()||domain==null)return false;var auth=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();return auth!=null&&auth.getAuthorities().stream().anyMatch(a->("test-management:"+domain+":execution").equals(a.getAuthority()));}catch(Exception ignored){return false;}}
}
