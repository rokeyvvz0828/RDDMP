package com.ccb.architecture.standard.integration;

import com.ccb.architecture.standard.model.StandardModels.DocumentStatus;
import com.ccb.architecture.standard.model.StandardModels.StandardDocument;
import com.ccb.architecture.standard.persistence.StandardStore;
import com.ccb.architecture.standard.service.ArchitectureStandardService;
import com.ccb.attachment.integration.AttachmentAccessPolicy;
import com.ccb.attachment.integration.AttachmentOperation;
import com.ccb.security.model.AuthUser;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 架构规范附件访问策略。
 *
 * <p>附件业务键为文档编号；文档必须存在于当前租户。已下线文档不再接受附件删除，
 * 读取与预览只要求文档存在（服务端权限由业务接口方法级校验兜底）。</p>
 */
@Component
public class StandardAttachmentAccessPolicy implements AttachmentAccessPolicy {
    private final StandardStore store;

    public StandardAttachmentAccessPolicy(StandardStore store) {
        this.store = store;
    }

    @Override
    public String businessType() {
        return ArchitectureStandardService.BUSINESS_TYPE;
    }

    @Override
    public boolean canAccess(AuthUser user, String businessKey, AttachmentOperation operation) {
        if (user == null || !user.enabled() || businessKey == null || businessKey.isBlank()) {
            return false;
        }
        long documentId;
        try {
            documentId = Long.parseLong(businessKey.trim());
        } catch (NumberFormatException exception) {
            return false;
        }
        Optional<StandardDocument> document = store.findDocument(user.tenantId(), documentId);
        if (document.isEmpty()) {
            return false;
        }
        if (operation == AttachmentOperation.DELETE) {
            return document.get().status() == DocumentStatus.DRAFT
                    || document.get().status() == DocumentStatus.PUBLISHED;
        }
        return true;
    }
}
