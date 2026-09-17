package com.ccb.datamigration.service;

import com.ccb.attachment.integration.AttachmentItem;
import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.system.model.UserDirectoryPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 投产及演练专属服务（对标迁移方案/专题材料）。
 *
 * <p>存储于 {@code dm_release_drill}（V184：新增 granularity/material_type_code/drill_round），
 * 一条资料绑定一个源文件（{@code dm_content_attachment} business_type=RELEASE_DRILL，sort_order=0）。
 * 颗粒度与资料类型统一来自系统管理/参数管理，组件级资料必选一个当前项目启用组件。
 * 支持组合筛选分页、详情、新增、编辑、下载、逻辑删除与统一回收站。
 */
@Service
public class ReleaseDrillService {
    /** att_file 平台侧绑定域，与其它文件型内容资产一致。 */
    public static final String BUSINESS_TYPE = ContentFileAssetService.BUSINESS_TYPE;
    /** dm_content_attachment.business_type 值。 */
    private static final String CONTENT_TYPE = "RELEASE_DRILL";
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;
    private static final Set<Integer> PAGE_SIZES = Set.of(20, 50, 100);

    private final ReleaseDrillRepository repository;
    private final ContentAttachmentService attachments;
    private final ContentFileAssetService fileAssets;
    private final DataMigrationPermissionService permissions;
    private final UserDirectoryPort userDirectory;
    private final ContentDocCodeGenerator docCodes;
    private final DataMigrationCodeValueService codeValues;

    @Autowired
    public ReleaseDrillService(ReleaseDrillRepository repository, ContentAttachmentService attachments, ContentFileAssetService fileAssets,
                               DataMigrationPermissionService permissions, UserDirectoryPort userDirectory,
                               ContentDocCodeGenerator docCodes, DataMigrationCodeValueService codeValues) {
        this.repository = repository;
        this.attachments = attachments;
        this.fileAssets = fileAssets;
        this.permissions = permissions;
        this.userDirectory = userDirectory;
        this.docCodes = docCodes;
        this.codeValues = codeValues;
    }

    public ReleaseDrillService(ReleaseDrillRepository repository, ContentAttachmentService attachments, ContentFileAssetService fileAssets,
                               DataMigrationPermissionService permissions, UserDirectoryPort userDirectory,
                               DataMigrationCodeValueService codeValues) {
        this(repository, attachments, fileAssets, permissions, userDirectory, new ContentDocCodeGenerator(), codeValues);
    }

    public ReleaseDrillService(ReleaseDrillRepository repository, ContentAttachmentService attachments, ContentFileAssetService fileAssets,
                               DataMigrationPermissionService permissions, DataMigrationCodeValueService codeValues) {
        this(repository, attachments, fileAssets, permissions, null, new ContentDocCodeGenerator(), codeValues);
    }

    /** 分页查询：租户 + 项目恒定过滤，支持颗粒度、资料类型、资料名称关键字组合筛选。 */
    public PageResult<Map<String, Object>> list(Long projectId, String granularity, String materialTypeCode,
                                                String systemCode, String keyword, int page, int size, AuthUser user) {
        long scope = permissions.requireProject(projectId, user);
        if (granularity != null && !granularity.isBlank()) {
            codeValues.requireActive(DataMigrationCodeValueService.DM_RELEASE_DRILL_GRANULARITY, "投产及演练颗粒度", granularity, user);
        }
        if (materialTypeCode != null && !materialTypeCode.isBlank()) {
            codeValues.requireActive(DataMigrationCodeValueService.DM_RELEASE_DRILL_TYPE, "资料类型", materialTypeCode, user);
        }

        int safePage = Math.max(1, page);
        int safeSize = normalizePageSize(size);
        long total = repository.count(user.tenantId(), scope, granularity, materialTypeCode, systemCode, keyword);
        List<Map<String, Object>> records = repository.page(user.tenantId(), scope, granularity, materialTypeCode, systemCode, keyword,
                safeSize, (long) (safePage - 1) * safeSize);
        decorateUsers(records, user.tenantId());
        return new PageResult<>(records, total, safePage, safeSize);
    }

    /** 详情（含主附件信息）。 */
    public Map<String, Object> detail(long id, AuthUser user) {
        Map<String, Object> row = repository.require(user.tenantId(), id);
        permissions.requireStoredProject(row.get("project_id"), user);
        decorateUsers(List.of(row), user.tenantId());
        row.put("attachments", attachments.list(CONTENT_TYPE, id, user.tenantId()));
        return row;
    }

    /** 新增：必填元数据 + 单个源文件；组件级必须选择系统，项目级不得传系统。 */
    @Transactional
    public Map<String, Object> create(Map<String, Object> body, AuthUser user) {
        long tenantId = user.tenantId();
        long projectId = requireLong(body.get("projectId"), "projectId 不能为空");
        permissions.requireAccessible(projectId, user);
        String granularity = requireText(body.get("granularity"), "投产及演练颗粒度不能为空");
        codeValues.requireActive(DataMigrationCodeValueService.DM_RELEASE_DRILL_GRANULARITY, "投产及演练颗粒度", granularity, user);
        String materialTypeCode = requireText(body.get("materialTypeCode"), "资料类型不能为空");
        codeValues.requireActive(DataMigrationCodeValueService.DM_RELEASE_DRILL_TYPE, "资料类型", materialTypeCode, user);
        String materialName = requireText(firstNonNull(body.get("materialName"), body.get("material_name"), body.get("docName"), body.get("doc_name")), "资料名称不能为空");
        String drillRound = optionalText(body.get("drillRound"), body.get("drill_round"));
        String systemCode = resolveSystemCode(granularity, body.get("systemCode"), projectId, user);
        Map<String, Object> file = resolveSingleFile(body, user, null);

        long id = nextId();
        try {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("id", id); values.put("tenantId", tenantId); values.put("projectId", projectId);
            values.put("docCode", docCodes.generate(CONTENT_TYPE)); values.put("docName", materialName.trim());
            values.put("granularity", granularity); values.put("materialTypeCode", materialTypeCode);
            values.put("drillRound", drillRound); values.put("systemCode", systemCode); values.put("ownerId", user.id());
            values.put("createdBy", user.id()); values.put("updatedBy", user.id());
            repository.insert(values);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "资料编号冲突，请刷新后重试");
        }
        attachments.replaceAll(CONTENT_TYPE, BUSINESS_TYPE, id, projectId, List.of(file), user);
        audit(user, "RELEASE_DRILL_CREATE", projectId, id);
        return detail(id, user);
    }

    /** 编辑：全量元数据可改；未提供文件时保留原主文件；归属与 doc_code 不可变。 */
    @Transactional
    public Map<String, Object> update(long id, Map<String, Object> body, AuthUser user) {
        Map<String, Object> existing = findRaw(id, user.tenantId());
        permissions.requireWrite(user, ((Number) existing.get("owner_id")).longValue());
        long projectId = permissions.requireStoredProject(existing.get("project_id"), user);

        String granularity = body.containsKey("granularity")
                ? requireText(body.get("granularity"), "投产及演练颗粒度不能为空") : (String) existing.get("granularity");
        codeValues.requireActive(DataMigrationCodeValueService.DM_RELEASE_DRILL_GRANULARITY, "投产及演练颗粒度", granularity, user);
        String materialTypeCode = body.containsKey("materialTypeCode")
                ? requireText(body.get("materialTypeCode"), "资料类型不能为空") : (String) existing.get("material_type_code");
        codeValues.requireActive(DataMigrationCodeValueService.DM_RELEASE_DRILL_TYPE, "资料类型", materialTypeCode, user);
        String materialName = body.containsKey("materialName") || body.containsKey("material_name") || body.containsKey("docName") || body.containsKey("doc_name")
                ? requireText(firstNonNull(body.get("materialName"), body.get("material_name"), body.get("docName"), body.get("doc_name")), "资料名称不能为空")
                : (String) existing.get("doc_name");
        String drillRound = body.containsKey("drillRound") || body.containsKey("drill_round")
                ? optionalText(body.get("drillRound"), body.get("drill_round")) : (String) existing.get("drill_round");
        String systemCode = body.containsKey("systemCode")
                ? resolveSystemCode(granularity, body.get("systemCode"), projectId, user) : (String) existing.get("system_code");
        if ("COMPONENT".equals(granularity) && (systemCode == null || systemCode.isBlank())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "组件级资料必须选择涉及物理子系统");
        }
        if ("PROJECT".equals(granularity) && systemCode != null && !systemCode.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "项目级资料不能选择涉及物理子系统");
        }

        List<Map<String, Object>> files = extractFiles(body);
        if (!files.isEmpty()) {
            Map<String, Object> file = resolveSingleFile(body, user, files);
            attachments.replaceAll(CONTENT_TYPE, BUSINESS_TYPE, id, projectId, List.of(file), user);
        }

        Map<String, Object> updateValues = new LinkedHashMap<>();
        updateValues.put("id", id); updateValues.put("tenantId", user.tenantId()); updateValues.put("docName", materialName.trim());
        updateValues.put("granularity", granularity); updateValues.put("materialTypeCode", materialTypeCode);
        updateValues.put("drillRound", drillRound); updateValues.put("systemCode", systemCode); updateValues.put("updatedBy", user.id());
        int changed = repository.update(updateValues);
        if (changed != 1) throw new BusinessException(ErrorCode.CONFLICT, "投产及演练资料状态已变化，请刷新后重试");
        audit(user, "RELEASE_DRILL_UPDATE", projectId, id);
        return detail(id, user);
    }

    /** 批量逻辑删除（进统一回收站），记录删除人/时间。 */
    @Transactional
    public void delete(List<Long> ids, AuthUser user) {
        for (Long id : normalizeIds(ids)) {
            Map<String, Object> existing = findRaw(id, user.tenantId());
            long projectId = permissions.requireStoredProject(existing.get("project_id"), user);
            permissions.requireWrite(user, ((Number) existing.get("owner_id")).longValue());
            int changed = repository.softDelete(user.tenantId(), id, user.id());
            if (changed != 1) throw new BusinessException(ErrorCode.CONFLICT, "投产及演练资料状态已变化，请刷新后重试");
            audit(user, "RELEASE_DRILL_DELETE", projectId, id);
        }
    }

    /** 下载：返回平台主附件下载路径，先校验记录项目归属。 */
    public String download(long id, AuthUser user) {
        permissions.requireStoredProject(findRaw(id, user.tenantId()).get("project_id"), user);
        List<Long> mainIds = repository.mainAttachmentIds(user.tenantId(), id);
        if (mainIds.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "投产及演练资料未绑定源文件");
        return "/api/attachments/" + mainIds.get(0) + "/download";
    }

    /** 附件列表（先校验记录项目归属）。 */
    public List<Map<String, Object>> listAttachments(long id, AuthUser user) {
        permissions.requireStoredProject(findRaw(id, user.tenantId()).get("project_id"), user);
        return attachments.list(CONTENT_TYPE, id, user.tenantId());
    }

    /** 资料类型选项：参数管理中启用的 DM_RELEASE_DRILL_TYPE。 */
    public List<Map<String, Object>> getTypeOptions(AuthUser user) {
        return codeValues.options(DataMigrationCodeValueService.DM_RELEASE_DRILL_TYPE, user);
    }

    /** 涉及物理子系统下拉：按项目返回 dm_component 启用清单，前端本地随输随筛。 */

    // ============ 统一回收站 SPI ============

    public long countRecycleBin(long projectId, String keyword, AuthUser user) {
        permissions.requireAdmin(user);
        permissions.requireAccessible(projectId, user);
        return repository.recycleCount(user.tenantId(), projectId, keyword);
    }

    public List<Map<String, Object>> fetchRecycleBinPage(long projectId, String keyword, int limit, AuthUser user) {
        permissions.requireAdmin(user);
        permissions.requireAccessible(projectId, user);
        if (limit <= 0) return List.of();
        List<Map<String, Object>> rows = repository.recyclePage(user.tenantId(), projectId, keyword, limit);
        decorateUsers(rows, user.tenantId());
        return rows;
    }

    public Map<String, Object> findRecycleBinDetail(long id, AuthUser user) {
        Map<String, Object> row = repository.requireDeleted(user.tenantId(), id);
        permissions.requireStoredProject(row.get("project_id"), user);
        decorateUsers(List.of(row), user.tenantId());
        // 添加附件列表
        List<Map<String, Object>> attachmentList = attachments.list("RELEASE_DRILL", id, user.tenantId());
        row.put("attachments", attachmentList);
        return row;
    }

    @Transactional
    public void restore(List<Long> ids, AuthUser user) {
        permissions.requireAdmin(user);
        for (Long id : normalizeIds(ids)) {
            long projectId = requireRecycleBinScope(id, user);
            try {
                int changed = repository.restore(user.tenantId(), id);
                if (changed != 1) throw new BusinessException(ErrorCode.CONFLICT, "投产及演练资料状态已变化，请刷新后重试");
            } catch (DataIntegrityViolationException ex) {
                throw new BusinessException(ErrorCode.CONFLICT, "资料编号已存在活动记录，无法恢复");
            }
            audit(user, "RELEASE_DRILL_RESTORE", projectId, id);
        }
    }

    @Transactional
    public void purge(List<Long> ids, AuthUser user) {
        permissions.requireAdmin(user);
        for (Long id : normalizeIds(ids)) {
            long projectId = requireRecycleBinScope(id, user);
            attachments.unbindAndRemoveAll(CONTENT_TYPE, BUSINESS_TYPE, id, user);
            repository.purge(user.tenantId(), id);
            audit(user, "RELEASE_DRILL_PURGE", projectId, id);
        }
    }

    // ============ 私有辅助 ============

    private String resolveSystemCode(String granularity, Object rawSystemCode, long projectId, AuthUser user) {
        String systemCode = optionalText(rawSystemCode, null);
        if ("PROJECT".equals(granularity)) {
            if (systemCode != null && !systemCode.isBlank()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "项目级资料不能选择涉及物理子系统");
            }
            return "";
        }
        if ("COMPONENT".equals(granularity)) {
            if (systemCode == null || systemCode.isBlank()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "组件级资料必须选择涉及物理子系统");
            }
            ensureSystemBelongsToProject(systemCode, projectId, user);
            return systemCode;
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, "投产及演练颗粒度无效，支持：PROJECT/COMPONENT");
    }

    private void ensureSystemBelongsToProject(String systemCode, long projectId, AuthUser user) {
        if (!repository.enabledComponent(user.tenantId(), projectId, systemCode)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "涉及物理子系统不存在或不属于当前项目");
        }
    }

    private Map<String, Object> resolveSingleFile(Map<String, Object> body, AuthUser user, List<Map<String, Object>> files) {
        List<Map<String, Object>> entries = files == null ? extractFiles(body) : files;
        if (entries.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "请上传源文件");
        if (entries.size() > 1) throw new BusinessException(ErrorCode.BAD_REQUEST, "一个投产及演练资料只能绑定一个源文件");
        Map<String, Object> entry = new LinkedHashMap<>(entries.get(0));
        long attachmentId = requireLong(entry.get("attachmentId"), "附件不能为空");
        AttachmentItem item = fileAssets.resolveAttachment(attachmentId, user);
        if (item.fileSize() <= 0 || item.fileSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件 " + item.fileName() + " 为空或超过 50 MB");
        }
        entry.put("attachmentId", item.id());
        entry.put("fileName", item.fileName());
        return entry;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractFiles(Map<String, Object> body) {
        List<Map<String, Object>> files = new java.util.ArrayList<>();
        Object rawFiles = body.get("files");
        if (rawFiles instanceof Collection<?> c) {
            for (Object item : c) if (item instanceof Map) files.add((Map<String, Object>) item);
        }
        if (files.isEmpty() && body.get("attachmentId") != null) {
            Map<String, Object> single = new LinkedHashMap<>();
            single.put("attachmentId", body.get("attachmentId"));
            single.put("fileName", body.get("fileName"));
            files.add(single);
        }
        return files;
    }

    private Map<String, Object> findRaw(long id, long tenantId) {
        return repository.requireRaw(tenantId, id);
    }

    private long requireRecycleBinScope(long id, AuthUser user) {
        long projectId = repository.requireDeletedProject(user.tenantId(), id);
        permissions.requireStoredProject(projectId, user);
        return projectId;
    }

    private List<Map<String, Object>> decorateUsers(List<Map<String, Object>> rows, long tenantId) {
        if (userDirectory == null) return rows;
        for (Map<String, Object> row : rows) {
            Object created = row.get("created_by");
            if (created instanceof Number number) userDirectory.findActive(tenantId, number.longValue())
                    .ifPresent(item -> row.put("created_by_name", item.displayName()));
            Object updated = row.get("updated_by");
            if (updated instanceof Number number) userDirectory.findActive(tenantId, number.longValue())
                    .ifPresent(item -> row.put("updated_by_name", item.displayName()));
            Object deleted = row.get("deleted_by");
            if (deleted instanceof Number number) userDirectory.findActive(tenantId, number.longValue())
                    .ifPresent(item -> row.put("deleted_by_name", item.displayName()));
        }
        return rows;
    }

    private void audit(AuthUser user, String operation, long projectId, long id) {
        repository.audit(user.tenantId(), user.id(), projectId, operation, id);
    }

    private int normalizePageSize(int size) {
        return PAGE_SIZES.contains(size) ? size : 20;
    }

    private List<Long> normalizeIds(List<Long> ids) {
        if (ids == null) return List.of();
        return ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000);
    }

    private String requireText(Object value, String message) {
        String text = optionalText(value, null);
        if (text == null) throw new BusinessException(ErrorCode.BAD_REQUEST, message);
        return text;
    }

    private String optionalText(Object value, Object otherValue) {
        Object candidate = value != null ? value : otherValue;
        if (candidate == null) return null;
        String text = String.valueOf(candidate).trim();
        return text.isEmpty() ? null : text;
    }

    private long requireLong(Object value, String message) {
        if (value == null) throw new BusinessException(ErrorCode.BAD_REQUEST, message);
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, message);
        }
    }

    private static Object firstNonNull(Object... values) {
        for (Object value : values) if (value != null) return value;
        return null;
    }
}
