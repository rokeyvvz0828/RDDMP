package com.ccb.requirement.service;

import com.ccb.attachment.integration.AttachmentBindingCommand;
import com.ccb.attachment.integration.AttachmentGateway;
import com.ccb.attachment.integration.AttachmentItem;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 评审报告文件（REQ-20260919-078，Owner 2026-09-19 追加要求）：
 * 新建项目差异评审、存量工作量评审与软需评审都必须上传评审报告文件。
 * <p>文件由平台附件能力托管：前端上传得到附件 ID，此处校验归属并绑定到需求业务，
 * 需求侧只落文件名称快照与附件 ID，避免临时附件被清理。
 */
@Service
public class RequirementReviewReportService {
    private final AttachmentGateway attachmentGateway;

    /**
     * 评审报告是否必传：默认必传；本地/联调在平台附件能力未启用时可临时关闭
     * （ccb.requirement.review-report-required=false），关闭后上传入口保留但不再拦截提交。
     */
    @Value("${ccb.requirement.review-report-required:true}")
    private boolean reviewReportRequired = true;

    public RequirementReviewReportService(AttachmentGateway attachmentGateway) {
        this.attachmentGateway = attachmentGateway;
    }

    /** 当前是否强制要求上传评审报告文件。 */
    public boolean required() {
        return reviewReportRequired;
    }

    /** 校验评审报告附件并绑定到需求；未上传时拒绝提交评审。 */
    @Transactional
    public BoundReport requireBound(AuthUser user, long requirementId, String projectRef, Long attachmentId) {
        return requireBound(user, requirementId, projectRef, attachmentId, "请先上传评审报告文件，再提交评审");
    }

    /** 校验并绑定附件；未上传且开关要求必传时按 missingMessage 拒绝。 */
    @Transactional
    public BoundReport requireBound(AuthUser user, long requirementId, String projectRef, Long attachmentId,
                                    String missingMessage) {
        if (attachmentId == null || attachmentId <= 0) {
            if (reviewReportRequired) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, missingMessage);
            }
            return new BoundReport(null, null);
        }
        AttachmentItem item = attachmentGateway.get(attachmentId, user);
        if (item == null || item.fileName() == null || item.fileName().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "评审报告文件无效，请重新上传");
        }
        attachmentGateway.bind(new AttachmentBindingCommand(attachmentId,
                RequirementReviewAttachmentPolicy.BUSINESS_TYPE, String.valueOf(requirementId), projectRef), user);
        return new BoundReport(attachmentId, item.fileName());
    }

    /** 绑定结果：附件 ID + 文件名称快照；未上传且未强制时两者为空。 */
    public record BoundReport(Long attachmentId, String fileName) {
    }
}
