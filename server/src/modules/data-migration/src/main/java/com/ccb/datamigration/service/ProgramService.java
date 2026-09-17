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

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 迁移程序专属服务（对标投产及演练）。
 *
 * <p>存储于 {@code dm_script}（V185：新增 system_code / program_type / program_description），
 * 一条程序包记录绑定多个源文件（{@code dm_content_attachment} business_type=SCRIPT，sort_order 递增）。
 * 程序类型统一来自系统管理/参数管理，系统编号必须是当前项目 enabled=1 的 dm_component。
 * 支持组合筛选分页、详情、新增、编辑、单文件/打包下载、逻辑删除与统一回收站。
 */
@Service
public class ProgramService {
    /** att_file 平台侧绑定域，与其它文件型内容资产一致。 */
    public static final String BUSINESS_TYPE = ContentFileAssetService.BUSINESS_TYPE;
    /** dm_content_attachment.business_type 值。 */
    private static final String CONTENT_TYPE = "SCRIPT";
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;
    private static final Set<Integer> PAGE_SIZES = Set.of(20, 50, 100);

    private final ProgramRepository repository;
    private final ContentAttachmentService attachments;
    private final ContentFileAssetService fileAssets;
    private final DataMigrationPermissionService permissions;
    private final UserDirectoryPort userDirectory;
    private final ContentDocCodeGenerator docCodes;
    private final DataMigrationCodeValueService codeValues;

    @Autowired
    public ProgramService(ProgramRepository repository, ContentAttachmentService attachments, ContentFileAssetService fileAssets,
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

    public ProgramService(ProgramRepository repository, ContentAttachmentService attachments, ContentFileAssetService fileAssets,
                          DataMigrationPermissionService permissions, UserDirectoryPort userDirectory,
                          DataMigrationCodeValueService codeValues) {
        this(repository, attachments, fileAssets, permissions, userDirectory, new ContentDocCodeGenerator(), codeValues);
    }

    public ProgramService(ProgramRepository repository, ContentAttachmentService attachments, ContentFileAssetService fileAssets,
                          DataMigrationPermissionService permissions, DataMigrationCodeValueService codeValues) {
        this(repository, attachments, fileAssets, permissions, null, new ContentDocCodeGenerator(), codeValues);
    }

    /** 分页查询：租户 + 项目恒定过滤，支持程序类型、系统编号、程序包名称关键字组合筛选。 */
    public PageResult<Map<String, Object>> list(Long projectId, String programType, String systemCode,
                                                String keyword, int page, int size, AuthUser user) {
        long scope = permissions.requireProject(projectId, user);
        if (programType != null && !programType.isBlank()) {
            codeValues.requireActive(DataMigrationCodeValueService.DM_PROGRAM_TYPE, "程序类型", programType, user);
        }

        int safePage = Math.max(1, page);
        int safeSize = normalizePageSize(size);
        long total = repository.count(user.tenantId(), scope, programType, systemCode, keyword);
        List<Map<String, Object>> records = repository.page(user.tenantId(), scope, programType, systemCode, keyword, safeSize, (long) (safePage - 1) * safeSize);
        decorateUsers(records, user.tenantId());
        return new PageResult<>(records, total, safePage, safeSize);
    }

    /** 详情（含多附件列表）。 */
    public Map<String, Object> detail(long id, AuthUser user) {
        Map<String, Object> row = repository.require(user.tenantId(), id);
        permissions.requireStoredProject(row.get("project_id"), user);
        decorateUsers(List.of(row), user.tenantId());
        row.put("attachments", attachments.list(CONTENT_TYPE, id, user.tenantId()));
        return row;
    }

    /** 新增：必填元数据 + 至少一个源文件；系统编号必须是当前项目启用组件。 */
    @Transactional
    public Map<String, Object> create(Map<String, Object> body, AuthUser user) {
        long tenantId = user.tenantId();
        long projectId = requireLong(body.get("projectId"), "projectId 不能为空");
        permissions.requireAccessible(projectId, user);
        String programType = requireText(body.get("programType"), "程序类型不能为空");
        codeValues.requireActive(DataMigrationCodeValueService.DM_PROGRAM_TYPE, "程序类型", programType, user);
        String systemCode = requireText(body.get("systemCode"), "系统编号不能为空");
        ensureSystemBelongsToProject(systemCode, projectId, user);
        String programName = requireText(body.get("programName"), "程序包名称不能为空");
        String description = optionalText(body.get("programDescription"), body.get("program_description"));
        List<Map<String, Object>> files = resolveFiles(body, user);
        if (files.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "请上传源文件");

        long id = nextId();
        try {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("id", id); values.put("tenantId", tenantId); values.put("projectId", projectId); values.put("systemCode", systemCode.trim());
            values.put("docCode", docCodes.generate(CONTENT_TYPE)); values.put("docName", programName.trim()); values.put("programType", programType);
            values.put("programDescription", description); values.put("ownerId", user.id()); values.put("createdBy", user.id()); values.put("updatedBy", user.id());
            repository.insert(values);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "程序包编号冲突，请刷新后重试");
        }
        attachments.replaceAll(CONTENT_TYPE, BUSINESS_TYPE, id, projectId, files, user);
        audit(user, "PROGRAM_CREATE", projectId, id);
        return detail(id, user);
    }

    /** 编辑：全量元数据可改；未传 files 时保留原附件，传 files 时按提交列表重设多附件。 */
    @Transactional
    public Map<String, Object> update(long id, Map<String, Object> body, AuthUser user) {
        Map<String, Object> existing = findRaw(id, user.tenantId());
        permissions.requireWrite(user, ((Number) existing.get("owner_id")).longValue());
        long projectId = permissions.requireStoredProject(existing.get("project_id"), user);

        String programType = body.containsKey("programType")
                ? requireText(body.get("programType"), "程序类型不能为空") : (String) existing.get("program_type");
        codeValues.requireActive(DataMigrationCodeValueService.DM_PROGRAM_TYPE, "程序类型", programType, user);
        String systemCode = body.containsKey("systemCode")
                ? requireText(body.get("systemCode"), "系统编号不能为空") : (String) existing.get("system_code");
        ensureSystemBelongsToProject(systemCode, projectId, user);
        String programName = body.containsKey("programName")
                ? requireText(body.get("programName"), "程序包名称不能为空") : (String) existing.get("doc_name");
        String description;
        if (body.containsKey("programDescription") || body.containsKey("program_description")) {
            description = optionalText(body.get("programDescription"), body.get("program_description"));
        } else {
            description = (String) existing.get("program_description");
        }

        if (body.containsKey("files")) {
            List<Map<String, Object>> files = resolveFiles(body, boundAttachmentIds(id, user.tenantId()), user);
            attachments.replaceAll(CONTENT_TYPE, BUSINESS_TYPE, id, projectId, files, user);
        }

        Map<String, Object> values = new LinkedHashMap<>(); values.put("id", id); values.put("tenantId", user.tenantId()); values.put("docName", programName.trim());
        values.put("programType", programType); values.put("systemCode", systemCode); values.put("programDescription", description); values.put("updatedBy", user.id());
        int changed = repository.update(values);
        if (changed != 1) throw new BusinessException(ErrorCode.CONFLICT, "迁移程序状态已变化，请刷新后重试");
        audit(user, "PROGRAM_UPDATE", projectId, id);
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
            if (changed != 1) throw new BusinessException(ErrorCode.CONFLICT, "迁移程序状态已变化，请刷新后重试");
            audit(user, "PROGRAM_DELETE", projectId, id);
        }
    }

    /** 单文件下载：不传 attachmentId 时取第一个活动附件。 */
    public String download(long id, Long attachmentId, AuthUser user) {
        permissions.requireStoredProject(findRaw(id, user.tenantId()).get("project_id"), user);
        List<Long> attachmentIds = repository.attachmentIds(user.tenantId(), id);
        if (attachmentIds.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "迁移程序未绑定源文件");
        long target = attachmentId == null ? attachmentIds.get(0) : attachmentId;
        if (!attachmentIds.contains(target)) throw new BusinessException(ErrorCode.BAD_REQUEST, "附件不存在或不属于该迁移程序");
        return "/api/attachments/" + target + "/download";
    }

    /** 附件列表（先校验记录项目归属）。 */
    public List<Map<String, Object>> listAttachments(long id, AuthUser user) {
        permissions.requireStoredProject(findRaw(id, user.tenantId()).get("project_id"), user);
        return attachments.list(CONTENT_TYPE, id, user.tenantId());
    }

    /** 程序类型选项：参数管理中启用的 DM_PROGRAM_TYPE。 */
    public List<Map<String, Object>> getTypeOptions(AuthUser user) {
        return codeValues.options(DataMigrationCodeValueService.DM_PROGRAM_TYPE, user);
    }

    /** 系统编号下拉：按项目返回 dm_component 启用清单，前端本地随输随筛。 */

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
        return row;
    }

    @Transactional
    public void restore(List<Long> ids, AuthUser user) {
        permissions.requireAdmin(user);
        for (Long id : normalizeIds(ids)) {
            long projectId = requireRecycleBinScope(id, user);
            try {
                int changed = repository.restore(user.tenantId(), id);
                if (changed != 1) throw new BusinessException(ErrorCode.CONFLICT, "迁移程序状态已变化，请刷新后重试");
            } catch (DataIntegrityViolationException ex) {
                throw new BusinessException(ErrorCode.CONFLICT, "程序包编号已存在活动记录，无法恢复");
            }
            audit(user, "PROGRAM_RESTORE", projectId, id);
        }
    }

    @Transactional
    public void purge(List<Long> ids, AuthUser user) {
        permissions.requireAdmin(user);
        for (Long id : normalizeIds(ids)) {
            long projectId = requireRecycleBinScope(id, user);
            attachments.unbindAndRemoveAll(CONTENT_TYPE, BUSINESS_TYPE, id, user);
            repository.purge(user.tenantId(), id);
            audit(user, "PROGRAM_PURGE", projectId, id);
        }
    }

    // ============ 私有辅助 ============

    private void ensureSystemBelongsToProject(String systemCode, long projectId, AuthUser user) {
        if (!repository.enabledComponent(user.tenantId(), projectId, systemCode)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "系统编号不存在或不属于当前项目");
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> resolveFiles(Map<String, Object> body, AuthUser user) {
        return resolveFiles(body, Set.of(), user);
    }

    /** 附件解析：已绑定附件直接复用；新附件必须是当前用户上传的临时附件且不超过 50MB。 */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> resolveFiles(Map<String, Object> body, Set<Long> bound, AuthUser user) {
        List<Map<String, Object>> files = new ArrayList<>();
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
        List<Map<String, Object>> resolved = new ArrayList<>();
        for (Map<String, Object> file : files) {
            long attachmentId = requireLong(file.get("attachmentId"), "附件不能为空");
            Map<String, Object> entry = new LinkedHashMap<>();
            if (bound.contains(attachmentId)) {
                entry.put("attachmentId", attachmentId);
                entry.put("fileName", file.get("fileName"));
                resolved.add(entry);
                continue;
            }
            AttachmentItem item = fileAssets.resolveAttachment(attachmentId, user);
            if (item.fileSize() <= 0 || item.fileSize() > MAX_FILE_SIZE) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "文件 " + item.fileName() + " 为空或超过 50 MB");
            }
            entry.put("attachmentId", item.id());
            entry.put("fileName", item.fileName());
            resolved.add(entry);
        }
        return resolved;
    }

    private Set<Long> boundAttachmentIds(long programId, long tenantId) {
        return new LinkedHashSet<>(repository.attachmentIds(tenantId, programId));
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
}
