package com.ccb.datamigration.service;

import com.ccb.attachment.integration.AttachmentBindingCommand;
import com.ccb.attachment.integration.AttachmentGateway;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 公共附件关系服务（dm_content_attachment）：business_type 参数化，
 * 统一承接会议多附件与文件型资产主文件（sort_order=0）的绑定、排序、
 * 附件级回收站（软删/恢复/彻底删除）逻辑。
 */
@Service
public class ContentAttachmentService {
    private final JdbcTemplate jdbc;
    private final AttachmentGateway attachmentGateway;

    public ContentAttachmentService(JdbcTemplate jdbc, AttachmentGateway attachmentGateway) {
        this.jdbc = jdbc;
        this.attachmentGateway = attachmentGateway;
    }

    /** 活动附件列表（按排序）。 */
    public List<Map<String, Object>> list(String businessType, long businessId, long tenantId) {
        return jdbc.queryForList(
                "SELECT id, attachment_id, file_name, sort_order, created_by, created_at " +
                "FROM dm_content_attachment WHERE tenant_id = ? AND business_type = ? AND business_id = ? AND deleted = 0 " +
                "ORDER BY sort_order ASC, created_at ASC",
                tenantId, businessType, businessId);
    }

    /** 附件级回收站列表。 */
    public List<Map<String, Object>> listDeleted(String businessType, long businessId, long tenantId) {
        return jdbc.queryForList(
                "SELECT a.id, a.attachment_id, a.file_name, a.sort_order, a.deleted_by, a.deleted_at, u.display_name AS deleted_by_name " +
                "FROM dm_content_attachment a LEFT JOIN sys_user u ON u.id = a.deleted_by AND u.tenant_id = a.tenant_id " +
                "WHERE a.tenant_id = ? AND a.business_type = ? AND a.business_id = ? AND a.deleted = 1 " +
                "ORDER BY a.deleted_at DESC",
                tenantId, businessType, businessId);
    }

    /**
     * 全量重设活动附件集合：保留行调序、回收站行复活并重新绑定、缺失行软删、新行插入。
     * entries 按顺序承载 attachmentId/fileName；attachmentBindingType 为 att_file 侧业务类型常量。
     */
    @Transactional
    public void replaceAll(String businessType, String attachmentBindingType, long businessId, long projectId,
                           List<Map<String, Object>> entries, AuthUser user) {
        long tenantId = user.tenantId();
        List<Long> existingIds = jdbc.queryForList(
                "SELECT attachment_id FROM dm_content_attachment WHERE tenant_id = ? AND business_type = ? AND business_id = ? AND deleted = 0",
                Long.class, tenantId, businessType, businessId);
        List<Long> deletedIds = jdbc.queryForList(
                "SELECT attachment_id FROM dm_content_attachment WHERE tenant_id = ? AND business_type = ? AND business_id = ? AND deleted = 1",
                Long.class, tenantId, businessType, businessId);

        List<Long> newAttachmentIds = new ArrayList<>();
        int sortOrder = 0;
        for (Map<String, Object> entry : entries) {
            Object rawId = entry.get("attachmentId");
            if (rawId == null) continue;
            long attachmentId;
            try { attachmentId = Long.parseLong(String.valueOf(rawId)); }
            catch (NumberFormatException ex) { throw new BusinessException(ErrorCode.BAD_REQUEST, "附件 ID 无效"); }
            String fileName = textOrNull(entry.get("fileName"));
            newAttachmentIds.add(attachmentId);
            if (existingIds.contains(attachmentId)) {
                jdbc.update("UPDATE dm_content_attachment SET sort_order = ? WHERE business_id = ? AND attachment_id = ? AND tenant_id = ? AND business_type = ? AND deleted = 0",
                        sortOrder, businessId, attachmentId, tenantId, businessType);
            } else if (deletedIds.contains(attachmentId)) {
                attachmentGateway.bind(new AttachmentBindingCommand(attachmentId, attachmentBindingType, String.valueOf(businessId), String.valueOf(projectId)), user);
                jdbc.update("UPDATE dm_content_attachment SET deleted = 0, deleted_by = NULL, deleted_at = NULL, file_name = ?, sort_order = ? WHERE business_id = ? AND attachment_id = ? AND tenant_id = ? AND business_type = ? AND deleted = 1",
                        fileName, sortOrder, businessId, attachmentId, tenantId, businessType);
            } else {
                attachmentGateway.bind(new AttachmentBindingCommand(attachmentId, attachmentBindingType, String.valueOf(businessId), String.valueOf(projectId)), user);
                jdbc.update("INSERT INTO dm_content_attachment (id, tenant_id, business_type, business_id, attachment_id, file_name, sort_order, created_by) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                        nextId(), tenantId, businessType, businessId, attachmentId, fileName, sortOrder, user.id());
            }
            sortOrder++;
        }

        for (Long existingId : existingIds) {
            if (!newAttachmentIds.contains(existingId)) {
                jdbc.update("UPDATE dm_content_attachment SET deleted = 1, deleted_by = ?, deleted_at = CURRENT_TIMESTAMP " +
                        "WHERE tenant_id = ? AND business_type = ? AND business_id = ? AND attachment_id = ? AND deleted = 0",
                        user.id(), tenantId, businessType, businessId, existingId);
            }
        }
    }

    /** 单条软删（进附件回收站）。 */
    @Transactional
    public int softDelete(String businessType, long businessId, long attachmentId, AuthUser user) {
        return jdbc.update(
                "UPDATE dm_content_attachment SET deleted = 1, deleted_by = ?, deleted_at = CURRENT_TIMESTAMP " +
                "WHERE tenant_id = ? AND business_type = ? AND business_id = ? AND attachment_id = ? AND deleted = 0",
                user.id(), user.tenantId(), businessType, businessId, attachmentId);
    }

    /** 按关系行 id 恢复。 */
    @Transactional
    public int restoreByIds(Collection<Long> ids, AuthUser user) {
        int restored = 0;
        for (Long id : ids) {
            restored += jdbc.update("UPDATE dm_content_attachment SET deleted = 0, deleted_by = NULL, deleted_at = NULL WHERE id = ? AND tenant_id = ? AND deleted = 1", id, user.tenantId());
        }
        return restored;
    }

    /** 按关系行 id 彻底删除（仅已软删行）。 */
    @Transactional
    public int purgeByIds(Collection<Long> ids, AuthUser user) {
        int purged = 0;
        for (Long id : ids) {
            purged += jdbc.update("DELETE FROM dm_content_attachment WHERE id = ? AND tenant_id = ? AND deleted = 1", id, user.tenantId());
        }
        return purged;
    }

    /** 清空回收站：删除该租户全部软删附件行。 */
    @Transactional
    public void purgeAllSoftDeleted(AuthUser user) {
        jdbc.update("DELETE FROM dm_content_attachment WHERE tenant_id = ? AND deleted = 1", user.tenantId());
    }

    /** 实体彻底删除前解绑并清空其全部附件行（含回收站行）。 */
    @Transactional
    public void unbindAndRemoveAll(String businessType, String attachmentBindingType, long businessId, AuthUser user) {
        List<Long> boundAttachmentIds = jdbc.queryForList(
                "SELECT DISTINCT a.attachment_id FROM dm_content_attachment a JOIN att_file f ON f.id = a.attachment_id AND f.tenant_id = a.tenant_id " +
                "WHERE a.tenant_id = ? AND a.business_type = ? AND a.business_id = ? AND f.status = 'BOUND' AND f.business_type = ? AND f.business_key = ?",
                Long.class, user.tenantId(), businessType, businessId, attachmentBindingType, String.valueOf(businessId));
        for (Long attachmentId : boundAttachmentIds) {
            attachmentGateway.deleteBound(attachmentId, attachmentBindingType, String.valueOf(businessId), user);
        }
        jdbc.update("DELETE FROM dm_content_attachment WHERE tenant_id = ? AND business_type = ? AND business_id = ?", user.tenantId(), businessType, businessId);
    }

    /** 软删附件的 id -> 业务实体 id 映射，供回收站恢复/删除后写审计。 */
    public Map<Long, Long> businessIdsForRows(Collection<Long> ids, AuthUser user) {
        Map<Long, Long> result = new LinkedHashMap<>();
        if (ids == null || ids.isEmpty()) return result;
        List<Long> idList = new ArrayList<>(ids);
        String placeholders = String.join(",", idList.stream().map(x -> "?").toList());
        List<Object> args = new ArrayList<>();
        args.add(user.tenantId());
        args.addAll(idList);
        jdbc.queryForList("SELECT id, business_id FROM dm_content_attachment WHERE tenant_id = ? AND deleted = 1 AND id IN (" + placeholders + ")", args.toArray())
                .forEach(row -> result.put(((Number) row.get("id")).longValue(), ((Number) row.get("business_id")).longValue()));
        return result;
    }

    /** 校验回收站行存在并返回其业务实体 id；不存在抛业务异常。 */
    public long requireSoftDeletedRow(long id, AuthUser user) {
        List<Long> rows = jdbc.queryForList("SELECT business_id FROM dm_content_attachment WHERE id = ? AND tenant_id = ? AND deleted = 1", Long.class, id, user.tenantId());
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "附件不存在或未删除");
        return rows.get(0);
    }

    private static String textOrNull(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private long nextId() { return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000); }
}
