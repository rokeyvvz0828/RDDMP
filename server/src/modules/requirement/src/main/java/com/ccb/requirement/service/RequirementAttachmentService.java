package com.ccb.requirement.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ccb.requirement.support.RequirementIds;
import com.ccb.requirement.support.RequirementValues;

/** 业务附件绑定：文件上传/预览由平台 file-preview 能力完成，业务只保存平台返回的受控引用。 */
@Service
public class RequirementAttachmentService {
    private static final List<String> BIZ_TYPES = List.of("NEW_PROJECT_DIFF", "LEGACY_REQUIREMENT");

    private final RequirementAttachmentRepository repository;
    private final RequirementSecurityService security;

    public RequirementAttachmentService(RequirementAttachmentRepository repository, RequirementSecurityService security) {
        this.repository = repository;
        this.security = security;
    }

    public List<Map<String, Object>> list(String bizType, long bizId, AuthUser user) {
        requireAccess(bizType, bizId, user);
        return repository.list(user.tenantId(), bizType, bizId);
    }

    @Transactional
    public Map<String, Object> create(String bizType, long bizId, Map<String, Object> body, AuthUser user) {
        if (!BIZ_TYPES.contains(bizType)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "附件业务类型不受支持：" + bizType);
        }
        requireAccess(bizType, bizId, user);
        String fileName = RequirementValues.requireText(body, "fileName", "文件名不能为空");
        long id = RequirementIds.next();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", id);
        values.put("tenant_id", user.tenantId());
        values.put("biz_type", bizType);
        values.put("biz_id", bizId);
        values.put("file_name", fileName);
        values.put("file_size", body.get("fileSize"));
        values.put("content_type", body.get("contentType"));
        values.put("preview_id", body.get("previewId"));
        values.put("preview_url", body.get("previewUrl"));
        values.put("operator_id", user.id());
        values.put("deleted", 0);
        repository.insert(values);
        return list(bizType, bizId, user).stream()
                .filter(row -> ((Number) row.get("id")).longValue() == id)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR, "附件保存失败"));
    }

    @Transactional
    public void delete(long id, AuthUser user) {
        Map<String, Object> row = repository.findActive(user.tenantId(), id);
        if (row == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "附件不存在");
        }
        requireAccess(String.valueOf(row.get("biz_type")), ((Number) row.get("biz_id")).longValue(), user);
        repository.softDelete(user.tenantId(), id);
    }

    private void requireAccess(String bizType, long bizId, AuthUser user) {
        if ("NEW_PROJECT_DIFF".equals(bizType)) {
            long projectId = repository.findDifferenceProjectId(user.tenantId(), bizId);
            if (projectId == 0L) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "差异不存在");
            }
            security.requireProjectAccess(user, projectId);
        } else if ("LEGACY_REQUIREMENT".equals(bizType)) {
            String businessGroup = repository.findLegacyBusinessGroup(user.tenantId(), bizId);
            if (businessGroup == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "存量需求不存在");
            }
            security.requireLegacyAccess(user, businessGroup);
        } else {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "附件业务类型不受支持：" + bizType);
        }
    }
}
