package com.ccb.attachment.service;

import com.ccb.attachment.integration.AttachmentAccessPolicy;
import com.ccb.attachment.integration.AttachmentOperation;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AttachmentAccessPolicyRegistry {
    private final Map<String, AttachmentAccessPolicy> policies;

    public AttachmentAccessPolicyRegistry(List<AttachmentAccessPolicy> policies) {
        Map<String, AttachmentAccessPolicy> indexed = new LinkedHashMap<>();
        for (AttachmentAccessPolicy policy : policies == null ? List.<AttachmentAccessPolicy>of() : policies) {
            String key = policy.businessType() == null ? "" : policy.businessType().trim();
            if (key.isEmpty() || indexed.putIfAbsent(key, policy) != null) throw new IllegalStateException("附件业务访问策略重复或无效: " + key);
        }
        this.policies = Map.copyOf(indexed);
    }

    public void requireAccess(String businessType, String businessKey, AttachmentOperation operation, AuthUser user) {
        AttachmentAccessPolicy policy = policies.get(businessType);
        boolean allowed = false;
        try {
            allowed = policy != null && policy.canAccess(user, businessKey, operation);
        } catch (RuntimeException ignored) {
            allowed = false;
        }
        if (!allowed) throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该业务附件");
    }
}
