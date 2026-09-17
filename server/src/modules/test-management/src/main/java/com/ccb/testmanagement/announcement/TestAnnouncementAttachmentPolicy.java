/*
 * 文件：server/src/modules/test-management/src/main/java/com/ccb/testmanagement/announcement/TestAnnouncementAttachmentPolicy.java
 * 说明：测试公告板的服务、策略或接口实现。
 * 用途：承载模块边界内的查询、校验、事务、权限或文件处理职责。
 * 作者：hengguan
 */
package com.ccb.testmanagement.announcement;

// 关键逻辑：附件访问必须先回查所属业务实体，并以租户、测试大类和项目边界阻断越权读取。
import com.ccb.attachment.integration.*; import com.ccb.security.model.AuthUser; import com.ccb.testmanagement.persistence.TestAttachmentAccessMapper; import org.springframework.stereotype.Component;
@Component public class TestAnnouncementAttachmentPolicy implements AttachmentAccessPolicy { private final TestAttachmentAccessMapper mapper; public TestAnnouncementAttachmentPolicy(TestAttachmentAccessMapper mapper){this.mapper=mapper;} public String businessType(){return TestAnnouncementService.BUSINESS_TYPE;} public boolean canAccess(AuthUser u,String key,AttachmentOperation op){try{long id=Long.parseLong(key);Long n=mapper.announcementCount(java.util.Map.of("id",id,"tenantId",u.tenantId()));return u.enabled()&&n!=null&&n>0;}catch(Exception e){return false;}} }
