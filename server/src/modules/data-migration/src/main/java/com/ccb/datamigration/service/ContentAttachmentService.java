package com.ccb.datamigration.service;

import com.ccb.attachment.integration.AttachmentBindingCommand;
import com.ccb.attachment.integration.AttachmentGateway;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.system.model.UserDirectoryPort;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final ContentAttachmentRepository repository;
    private final AttachmentGateway attachmentGateway;
    private final UserDirectoryPort userDirectory;

    @Autowired
    public ContentAttachmentService(ContentAttachmentRepository repository, AttachmentGateway attachmentGateway, UserDirectoryPort userDirectory) {
        this.repository = repository;
        this.attachmentGateway = attachmentGateway;
        this.userDirectory = userDirectory;
    }

    /** 活动附件列表（按排序）。 */
    public List<Map<String, Object>> list(String businessType, long businessId, long tenantId) {
        return repository.listActive(tenantId, businessType, businessId);
    }

    /** 附件级回收站列表。 */
    public List<Map<String, Object>> listDeleted(String businessType, long businessId, long tenantId) {
        List<Map<String, Object>> rows = repository.listDeleted(tenantId, businessType, businessId);
        addDeletedByNames(rows, tenantId);
        return rows;
    }

    /**
     * 全量重设活动附件集合：保留行调序、回收站行复活并重新绑定、缺失行软删、新行插入。
     * entries 按顺序承载 attachmentId/fileName；attachmentBindingType 为 att_file 侧业务类型常量。
     */
    @Transactional
    public void replaceAll(String businessType, String attachmentBindingType, long businessId, long projectId,
                           List<Map<String, Object>> entries, AuthUser user) {
        long tenantId = user.tenantId();
        List<Long> existingIds = repository.activeAttachmentIds(tenantId, businessType, businessId);
        List<Long> deletedIds = repository.deletedAttachmentIds(tenantId, businessType, businessId);

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
                repository.updateSort(sortOrder, businessId, attachmentId, tenantId, businessType);
            } else if (deletedIds.contains(attachmentId)) {
                attachmentGateway.bind(new AttachmentBindingCommand(attachmentId, attachmentBindingType, String.valueOf(businessId), String.valueOf(projectId)), user);
                repository.restoreAttachment(fileName, sortOrder, businessId, attachmentId, tenantId, businessType);
            } else {
                attachmentGateway.bind(new AttachmentBindingCommand(attachmentId, attachmentBindingType, String.valueOf(businessId), String.valueOf(projectId)), user);
                repository.insert(nextId(), tenantId, businessType, businessId, attachmentId, fileName, sortOrder, user.id());
            }
            sortOrder++;
        }

        for (Long existingId : existingIds) {
            if (!newAttachmentIds.contains(existingId)) {
                repository.softDeleteAttachment(user.id(), tenantId, businessType, businessId, existingId);
            }
        }
    }

    /** 单条软删（进附件回收站）。 */
    @Transactional
    public int softDelete(String businessType, long businessId, long attachmentId, AuthUser user) {
        return repository.softDeleteAttachment(user.id(), user.tenantId(), businessType, businessId, attachmentId);
    }

    /** 按关系行 id 恢复。 */
    @Transactional
    public int restoreByIds(Collection<Long> ids, AuthUser user) {
        int restored = 0;
        for (Long id : ids) {
            restored += repository.restoreById(id, user.tenantId());
        }
        return restored;
    }

    /** 按关系行 id 彻底删除（仅已软删行）。 */
    @Transactional
    public int purgeByIds(Collection<Long> ids, AuthUser user) {
        int purged = 0;
        for (Long id : ids) {
            purged += repository.purgeById(id, user.tenantId());
        }
        return purged;
    }

    /** 实体彻底删除前解绑并清空其全部附件行（含回收站行）。 */
    @Transactional
    public void unbindAndRemoveAll(String businessType, String attachmentBindingType, long businessId, AuthUser user) {
        List<Long> candidateIds = repository.attachmentIds(user.tenantId(), businessType, businessId);
        List<Long> boundAttachmentIds = new ArrayList<>();
        for (Long attachmentId : candidateIds) {
            try {
                var item = attachmentGateway.get(attachmentId, user);
                if ("BOUND".equals(item.status()) && attachmentBindingType.equals(item.businessType())
                        && String.valueOf(businessId).equals(item.businessKey())) {
                    boundAttachmentIds.add(attachmentId);
                }
            } catch (BusinessException ignored) {
                // 关系行可能已经对应平台侧删除附件，继续清理本模块关系行。
            }
        }
        for (Long attachmentId : boundAttachmentIds) {
            attachmentGateway.deleteBound(attachmentId, attachmentBindingType, String.valueOf(businessId), user);
        }
        repository.deleteAll(user.tenantId(), businessType, businessId);
    }

    /** 软删附件的 id -> 业务实体 id 映射，供回收站恢复/删除后写审计。 */
    public Map<Long, Long> businessIdsForRows(Collection<Long> ids, AuthUser user) {
        Map<Long, Long> result = new LinkedHashMap<>();
        if (ids == null || ids.isEmpty()) return result;
        repository.softDeletedRows(user.tenantId(), new ArrayList<>(ids))
                .forEach(row -> result.put(((Number) row.get("id")).longValue(), ((Number) row.get("business_id")).longValue()));
        return result;
    }

    /** 校验回收站行存在并返回其业务实体 id；不存在抛业务异常。 */
    public long requireSoftDeletedRow(long id, AuthUser user) {
        List<Long> rows = repository.softDeletedBusinessIds(id, user.tenantId());
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "附件不存在或未删除");
        return rows.get(0);
    }

    /** 通过附件公开契约校验当前租户附件，不读取平台附件表。 */
    public void ensureAttachmentExists(long attachmentId, AuthUser user) {
        if (attachmentId <= 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "附件不存在");
        attachmentGateway.get(attachmentId, user);
    }

    private void addDeletedByNames(List<Map<String, Object>> rows, long tenantId) {
        if (userDirectory == null) return;
        for (Map<String, Object> row : rows) {
            Object raw = row.get("deleted_by");
            if (raw == null) continue;
            userDirectory.findActive(tenantId, ((Number) raw).longValue())
                    .ifPresent(item -> row.put("deleted_by_name", item.displayName()));
        }
    }

    private static String textOrNull(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private long nextId() { return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000); }
}
