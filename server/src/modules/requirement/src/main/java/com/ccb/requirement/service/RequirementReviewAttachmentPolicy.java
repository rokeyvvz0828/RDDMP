package com.ccb.requirement.service;

import com.ccb.attachment.integration.AttachmentAccessPolicy;
import com.ccb.attachment.integration.AttachmentOperation;
import com.ccb.security.model.AuthUser;
import org.springframework.stereotype.Component;

/**
 * 需求评审报告附件的访问策略。
 * <p>businessType = {@value #BUSINESS_TYPE}，businessKey = 需求 ID（req_requirement.id）；
 * 访问判定复用需求数据范围（统筹/管理员、提出人、当前处理人、曾流转经手）。
 */
@Component
public class RequirementReviewAttachmentPolicy implements AttachmentAccessPolicy {
    public static final String BUSINESS_TYPE = "REQUIREMENT_REVIEW";

    private final RequirementSecurityService security;

    public RequirementReviewAttachmentPolicy(RequirementSecurityService security) {
        this.security = security;
    }

    @Override
    public String businessType() {
        return BUSINESS_TYPE;
    }

    @Override
    public boolean canAccess(AuthUser user, String businessKey, AttachmentOperation operation) {
        if (user == null || !user.enabled()) {
            return false;
        }
        long requirementId;
        try {
            requirementId = Long.parseLong(String.valueOf(businessKey).trim());
        } catch (RuntimeException exception) {
            return false;
        }
        if (requirementId <= 0) {
            return false;
        }
        try {
            security.requireRequirementVisible(user, requirementId);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
