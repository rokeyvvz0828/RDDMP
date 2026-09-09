package com.ccb.development.attachment;

import com.ccb.attachment.integration.AttachmentAccessPolicy;
import com.ccb.attachment.integration.AttachmentOperation;
import com.ccb.common.exception.BusinessException;
import com.ccb.development.repository.DevelopmentTaskRepository;
import com.ccb.development.service.DevelopmentAccessPolicy;
import com.ccb.security.model.AuthUser;
import org.springframework.stereotype.Component;

@Component
public class DevelopmentAttachmentAccessPolicy implements AttachmentAccessPolicy {
    private final DevelopmentTaskRepository tasks;
    private final DevelopmentAccessPolicy access;
    public DevelopmentAttachmentAccessPolicy(DevelopmentTaskRepository tasks,DevelopmentAccessPolicy access){this.tasks=tasks;this.access=access;}
    @Override public String businessType(){return "development-task";}
    @Override public boolean canAccess(AuthUser actor,String key,AttachmentOperation operation){
        if(actor==null||!actor.enabled()||operation==AttachmentOperation.DELETE||key==null)return false;
        try{
            long id=Long.parseLong(key);if(id<=0)return false;
            access.read(actor,tasks.require(actor.tenantId(),id,false));return true;
        }catch(NumberFormatException|BusinessException error){return false;}
    }
}
