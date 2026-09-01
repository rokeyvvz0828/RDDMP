package com.ccb.architecture.decision.integration;

import com.ccb.architecture.decision.model.DecisionModels.DecisionMatter;
import com.ccb.architecture.decision.model.DecisionModels.MatterStatus;
import com.ccb.architecture.decision.persistence.DecisionStore;
import com.ccb.architecture.decision.service.ArchitectureDecisionService;
import com.ccb.attachment.integration.AttachmentAccessPolicy;
import com.ccb.attachment.integration.AttachmentOperation;
import com.ccb.security.model.AuthUser;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 架构决策事项附件访问策略。
 *
 * <p>附件业务键为事项编号；事项必须存在于当前租户。已发布事项不再接受附件删除，
 * 读取与预览只要求事项存在（服务端权限由业务接口方法级校验兜底）。</p>
 */
@Component
public class DecisionAttachmentAccessPolicy implements AttachmentAccessPolicy {
    private final DecisionStore store;

    public DecisionAttachmentAccessPolicy(DecisionStore store) {
        this.store = store;
    }

    @Override
    public String businessType() {
        return ArchitectureDecisionService.MATTER_ATTACHMENT_BUSINESS_TYPE;
    }

    @Override
    public boolean canAccess(AuthUser user, String businessKey, AttachmentOperation operation) {
        if (user == null || !user.enabled() || businessKey == null || businessKey.isBlank()) {
            return false;
        }
        long matterId;
        try {
            matterId = Long.parseLong(businessKey.trim());
        } catch (NumberFormatException exception) {
            return false;
        }
        Optional<DecisionMatter> matter = store.findMatter(user.tenantId(), matterId);
        if (matter.isEmpty()) {
            return false;
        }
        if (operation == AttachmentOperation.DELETE) {
            return matter.get().status() != MatterStatus.PUBLISHED;
        }
        return true;
    }
}
