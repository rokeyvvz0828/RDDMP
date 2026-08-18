package com.ccb.requirement.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ccb.requirement.support.RequirementIds;
import com.ccb.requirement.support.RequirementSql;
import com.ccb.requirement.support.RequirementValues;

/** 业务附件绑定：文件上传/预览由平台 file-preview 能力完成，业务只保存平台返回的受控引用。 */
@Service
public class RequirementAttachmentService {
    private static final List<String> BIZ_TYPES = List.of("NEW_PROJECT_DIFF", "LEGACY_REQUIREMENT");

    private final JdbcTemplate jdbc;
    private final RequirementSecurityService security;

    public RequirementAttachmentService(JdbcTemplate jdbc, RequirementSecurityService security) {
        this.jdbc = jdbc;
        this.security = security;
    }

    public List<Map<String, Object>> list(String bizType, long bizId, AuthUser user) {
        requireAccess(bizType, bizId, user);
        return jdbc.queryForList("""
                SELECT id, biz_type, biz_id, file_name, file_size, content_type, preview_id, preview_url, created_at
                FROM req_attachment WHERE tenant_id = ? AND biz_type = ? AND biz_id = ? AND deleted = 0
                ORDER BY created_at DESC, id DESC
                """, user.tenantId(), bizType, bizId);
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
        RequirementSql.insert(jdbc, "req_attachment", values);
        return list(bizType, bizId, user).stream()
                .filter(row -> ((Number) row.get("id")).longValue() == id)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR, "附件保存失败"));
    }

    @Transactional
    public void delete(long id, AuthUser user) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, biz_type, biz_id FROM req_attachment WHERE tenant_id = ? AND id = ? AND deleted = 0",
                user.tenantId(), id);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "附件不存在");
        }
        Map<String, Object> row = rows.get(0);
        requireAccess(String.valueOf(row.get("biz_type")), ((Number) row.get("biz_id")).longValue(), user);
        jdbc.update("UPDATE req_attachment SET deleted = 1 WHERE tenant_id = ? AND id = ?", user.tenantId(), id);
    }

    private void requireAccess(String bizType, long bizId, AuthUser user) {
        if ("NEW_PROJECT_DIFF".equals(bizType)) {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT project_id FROM req_difference WHERE tenant_id = ? AND id = ? AND deleted = 0",
                    user.tenantId(), bizId);
            if (rows.isEmpty()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "差异不存在");
            }
            security.requireProjectAccess(user, ((Number) rows.get(0).get("project_id")).longValue());
        } else if ("LEGACY_REQUIREMENT".equals(bizType)) {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT business_group FROM req_legacy_requirement WHERE tenant_id = ? AND id = ? AND deleted = 0",
                    user.tenantId(), bizId);
            if (rows.isEmpty()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "存量需求不存在");
            }
            security.requireLegacyAccess(user, String.valueOf(rows.get(0).get("business_group")));
        } else {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "附件业务类型不受支持：" + bizType);
        }
    }
}
