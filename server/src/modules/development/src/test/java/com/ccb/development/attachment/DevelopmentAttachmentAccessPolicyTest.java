package com.ccb.development.attachment;

import com.ccb.attachment.integration.AttachmentOperation;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.development.model.DevelopmentTaskModels.TaskEntity;
import com.ccb.development.repository.DevelopmentTaskRepository;
import com.ccb.development.service.DevelopmentAccessPolicy;
import com.ccb.security.model.AuthUser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DevelopmentAttachmentAccessPolicyTest {
    @Test
    void historicalFilesCannotBeDeletedAndReadAlwaysChecksParentScope(){
        var actor=new AuthUser(9,7,"fixture","","虚构用户",1,true);
        var tasks=mock(DevelopmentTaskRepository.class);var access=mock(DevelopmentAccessPolicy.class);var task=mock(TaskEntity.class);
        when(tasks.require(7,42,false)).thenReturn(task);
        var policy=new DevelopmentAttachmentAccessPolicy(tasks,access);
        assertFalse(policy.canAccess(actor,"42",AttachmentOperation.DELETE));
        assertFalse(policy.canAccess(actor,"../42",AttachmentOperation.READ));
        assertTrue(policy.canAccess(actor,"42",AttachmentOperation.READ));
        verify(access).read(actor,task);
        doThrow(new BusinessException(ErrorCode.FORBIDDEN,"无项目权限")).when(access).read(actor,task);
        assertFalse(policy.canAccess(actor,"42",AttachmentOperation.DOWNLOAD));
    }
}
