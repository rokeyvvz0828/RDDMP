package com.ccb.datamigration.service;

import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 会议纪要服务：独立存储会议纪要和问题提取纪要；
 * 附件统一经 {@link ContentAttachmentService} 落 dm_content_attachment（business_type=MEETING），
 * 关联系统落 dm_meeting_system，关联问题归一写 dm_issue_relation（REQ-20260831-050）。
 */
@Service
public class MeetingService {
    public static final String BUSINESS_TYPE = "DATA_MIGRATION_MEETING";
    private static final String CONTENT_TYPE = "MEETING";
    private static final Set<String> GRANULARITIES = Set.of("PROJECT", "COMPONENT", "TABLE", "FIELD");
    private static final Set<String> MEETING_SOURCES = Set.of("MEETING_MINUTES", "ISSUE_EXTRACT");
    private static final Set<Integer> PAGE_SIZES = Set.of(20, 50, 100);

    private final JdbcTemplate jdbc;
    private final ContentAttachmentService contentAttachments;
    private final DataMigrationPermissionService permissions;

    public MeetingService(JdbcTemplate jdbc, ContentAttachmentService contentAttachments, DataMigrationPermissionService permissions) {
        this.jdbc = jdbc;
        this.contentAttachments = contentAttachments;
        this.permissions = permissions;
    }

    /**
     * 分页查询会议纪要列表
     */
    public PageResult<Map<String, Object>> list(Long projectId, String meetingSource, String granularity,
                                                 Long systemId, String keyword, int page, int size, AuthUser user) {
        StringBuilder sql = new StringBuilder(baseSelect(false));
        List<Object> args = new ArrayList<>(List.of(user.tenantId()));
        appendFilters(sql, args, projectId, meetingSource, granularity, systemId, keyword, false);

        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM (" + sql + ") t", Long.class, args.toArray());
        int safePage = Math.max(1, page);
        int safeSize = normalizePageSize(size);
        sql.append(" ORDER BY m.meeting_code ASC, m.meeting_id ASC LIMIT ? OFFSET ?");
        args.add(safeSize);
        args.add((safePage - 1) * safeSize);

        return new PageResult<>(jdbc.queryForList(sql.toString(), args.toArray()), total == null ? 0L : total, safePage, safeSize);
    }

    /**
     * 获取单条会议纪要详情
     */
    public Map<String, Object> findById(long meetingId, AuthUser user) {
        return findByIdInternal(meetingId, user.tenantId(), false);
    }

    /**
     * 创建会议纪要
     */
    @Transactional
    public Map<String, Object> create(Map<String, Object> body, AuthUser user) {
        long projectId = number(body.get("projectId"), "projectId");
        ensureProject(projectId, user);

        String meetingTitle = text(body.get("meetingTitle"), "meetingTitle");
        String granularity = text(body.get("granularity"), "granularity");
        String meetingSource = text(body.get("meetingSource"), "meetingSource");

        validateEnums(body);

        long meetingId = nextId();
        // 会议编号：用户显式提供则校验活动域唯一；留空时后端自动生成 MEET-{meetingId}，与 REPORT-{id} 同构。
        String meetingCode = textOrNull(body.get("meetingCode"));
        if (meetingCode == null) {
            meetingCode = "MEET-" + meetingId;
        } else {
            ensureCodeAvailable(projectId, meetingCode, null, user);
        }
        try {
            jdbc.update(
                    "INSERT INTO dm_meeting (meeting_id, meeting_code, tenant_id, project_id, granularity, meeting_source, " +
                    "meeting_title, meeting_content, meeting_conclusion, business_scenario, keywords, " +
                    "created_by, updated_by) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    meetingId, meetingCode, user.tenantId(), projectId,
                    granularity, meetingSource, meetingTitle,
                    textOrNull(body.get("meetingContent")),
                    textOrNull(body.get("meetingConclusion")),
                    textOrNull(body.get("businessScenario")),
                    keywords(body.get("keywords")),
                    user.id(), user.id()
            );
        } catch (DataIntegrityViolationException ex) {
            throw meetingCodeConflict(ex);
        }

        // 保存多附件关联
        saveMeetingAttachments(meetingId, projectId, body, user);

        // 保存关联系统
        saveSystemRelations(meetingId, projectId, body, user);

        // 保存关联问题
        saveIssueRelations(meetingId, projectId, body, user);

        audit(user, "MEETING_CREATE", meetingId);
        return findByIdInternal(meetingId, user.tenantId(), false);
    }

    /**
     * 更新会议纪要
     */
    @Transactional
    public Map<String, Object> update(long meetingId, Map<String, Object> body, AuthUser user) {
        Map<String, Object> current = findByIdInternal(meetingId, user.tenantId(), false);
        permissions.requireWrite(user, ((Number) current.get("created_by")).longValue());

        long projectId = number(body.getOrDefault("projectId", current.get("project_id")), "projectId");
        ensureProject(projectId, user);

        String meetingTitle = text(body.getOrDefault("meetingTitle", current.get("meeting_title")), "meetingTitle");
        String granularity = text(body.getOrDefault("granularity", current.get("granularity")), "granularity");
        String meetingSource = text(body.getOrDefault("meetingSource", current.get("meeting_source")), "meetingSource");
        // 会议编号可修改，但活动域内仍需唯一；留空回退到当前值，避免旧前端不感知新字段时丢失。
        String meetingCode = text(body.getOrDefault("meetingCode", current.get("meeting_code")), "meetingCode");

        validateEnums(body);
        ensureCodeAvailable(projectId, meetingCode, meetingId, user);

        try {
            int changed = jdbc.update(
                    "UPDATE dm_meeting SET project_id = ?, meeting_code = ?, granularity = ?, meeting_source = ?, " +
                    "meeting_title = ?, meeting_content = ?, meeting_conclusion = ?, business_scenario = ?, " +
                    "keywords = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP " +
                    "WHERE meeting_id = ? AND tenant_id = ? AND deleted = 0",
                    projectId, meetingCode, granularity, meetingSource,
                    meetingTitle,
                    textOrNull(body.get("meetingContent")),
                    textOrNull(body.get("meetingConclusion")),
                    textOrNull(body.get("businessScenario")),
                    keywords(body.get("keywords")),
                    user.id(), meetingId, user.tenantId()
            );
            if (changed != 1) {
                throw new BusinessException(ErrorCode.CONFLICT, "会议纪要状态已变化，请刷新后重试");
            }
        } catch (DataIntegrityViolationException ex) {
            throw meetingCodeConflict(ex);
        }

        // 更新多附件关联
        saveMeetingAttachments(meetingId, projectId, body, user);

        // 更新关联系统
        saveSystemRelations(meetingId, projectId, body, user);

        // 更新关联问题
        saveIssueRelations(meetingId, projectId, body, user);

        audit(user, "MEETING_UPDATE", meetingId);
        return findByIdInternal(meetingId, user.tenantId(), false);
    }

    /**
     * 逻辑删除会议纪要
     */
    @Transactional
    public void delete(List<Long> meetingIds, AuthUser user) {
        for (Long meetingId : normalizeIds(meetingIds)) {
            Map<String, Object> row = findByIdInternal(meetingId, user.tenantId(), false);
            permissions.requireWrite(user, ((Number) row.get("created_by")).longValue());

            int changed = jdbc.update(
                    "UPDATE dm_meeting SET deleted = 1, deleted_by = ?, deleted_at = CURRENT_TIMESTAMP " +
                    "WHERE meeting_id = ? AND tenant_id = ? AND deleted = 0",
                    user.id(), meetingId, user.tenantId()
            );
            if (changed != 1) {
                throw new BusinessException(ErrorCode.CONFLICT, "会议纪要状态已变化，请刷新后重试");
            }
            audit(user, "MEETING_DELETE", meetingId);
        }
    }

    /**
     * 统一回收站原生分页：软删会议总数（SQL COUNT，不拉明细）。
     */
    public long countRecycleBin(Long projectId, String keyword, AuthUser user) {
        permissions.requireAdmin(user);
        StringBuilder sql = new StringBuilder(baseSelect(true));
        List<Object> args = new ArrayList<>(List.of(user.tenantId()));
        appendFilters(sql, args, projectId, null, null, null, keyword, true);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM (" + sql + ") t", Long.class, args.toArray());
        return total == null ? 0L : total;
    }

    /**
     * 统一回收站原生分页：按 {@code meeting_code ASC, meeting_id ASC} 取软删会议前 {@code limit} 行。
     * {@code limit} 为原生下推值（不经 {@link #normalizePageSize} 白名单），由统一层按窗口上界传入。
     */
    public List<Map<String, Object>> fetchRecycleBinPage(Long projectId, String keyword, int limit, AuthUser user) {
        permissions.requireAdmin(user);
        if (limit <= 0) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder(baseSelect(true));
        List<Object> args = new ArrayList<>(List.of(user.tenantId()));
        appendFilters(sql, args, projectId, null, null, null, keyword, true);
        sql.append(" ORDER BY m.meeting_code ASC, m.meeting_id ASC LIMIT ?");
        args.add(limit);
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    /**
     * 恢复会议纪要
     */
    @Transactional
    public void restore(List<Long> meetingIds, AuthUser user) {
        permissions.requireAdmin(user);

        for (Long meetingId : normalizeIds(meetingIds)) {
            findByIdInternal(meetingId, user.tenantId(), true);

            try {
                int changed = jdbc.update(
                        "UPDATE dm_meeting SET deleted = 0, deleted_by = NULL, deleted_at = NULL, " +
                        "updated_by = ?, updated_at = CURRENT_TIMESTAMP " +
                        "WHERE meeting_id = ? AND tenant_id = ? AND deleted = 1",
                        user.id(), meetingId, user.tenantId()
                );
                if (changed != 1) {
                    throw new BusinessException(ErrorCode.CONFLICT, "会议纪要状态已变化，请刷新后重试");
                }
            } catch (DataIntegrityViolationException ex) {
                // 恢复后活动行 meeting_code 与既有活动行冲突（uk_dm_meeting_active_code）：与 create/update 同构，翻译为 40900。
                throw meetingCodeConflict(ex);
            }
            audit(user, "MEETING_RESTORE", meetingId);
        }
    }

    /**
     * 彻底删除会议纪要
     */
    @Transactional
    public void purge(List<Long> meetingIds, AuthUser user) {
        permissions.requireAdmin(user);

        for (Long meetingId : normalizeIds(meetingIds)) {
            findByIdInternal(meetingId, user.tenantId(), true);

            purgeMeetingAttachments(meetingId, user);

            // 删除会议参与的全部问题关联与系统关联，避免留下悬挂关系。
            // 旧实现将子查询直接内嵌到 DELETE 目标表上，触发 MySQL 1093 “You can't specify target table ... for update in FROM clause”；
            // 改为先 SELECT 得到已软删且关联到本会议的 issue_id，再拆成一次 DELETE，保留原有“同时级联清理软删问题其它关联”的语义。
            List<Long> softDeletedLinkedIssueIds = jdbc.queryForList(
                    "SELECT DISTINCT r.issue_id FROM dm_issue_relation r " +
                    "JOIN dm_issue i ON i.tenant_id = r.tenant_id AND i.id = r.issue_id AND i.deleted = 1 " +
                    "WHERE r.tenant_id = ? AND r.related_type = 'MEETING' AND r.related_id = ?",
                    Long.class, user.tenantId(), meetingId);
            if (softDeletedLinkedIssueIds.isEmpty()) {
                jdbc.update("DELETE FROM dm_issue_relation WHERE tenant_id = ? AND related_type = 'MEETING' AND related_id = ?",
                        user.tenantId(), meetingId);
            } else {
                String placeholders = String.join(",", Collections.nCopies(softDeletedLinkedIssueIds.size(), "?"));
                List<Object> relationArgs = new ArrayList<>();
                relationArgs.add(user.tenantId());
                relationArgs.add(meetingId);
                relationArgs.addAll(softDeletedLinkedIssueIds);
                jdbc.update("DELETE FROM dm_issue_relation WHERE tenant_id = ? AND (related_type = 'MEETING' AND related_id = ? OR issue_id IN (" + placeholders + "))",
                        relationArgs.toArray());
            }
            jdbc.update("DELETE FROM dm_meeting_system WHERE tenant_id = ? AND meeting_id = ?", user.tenantId(), meetingId);

            int changed = jdbc.update(
                    "DELETE FROM dm_meeting WHERE meeting_id = ? AND tenant_id = ? AND deleted = 1",
                    meetingId, user.tenantId()
            );
            if (changed != 1) {
                throw new BusinessException(ErrorCode.CONFLICT, "会议纪要状态已变化，请刷新后重试");
            }
            audit(user, "MEETING_PURGE", meetingId);
        }
    }

    /**
     * 清空回收站
     */
    @Transactional
    public void purgeAll(AuthUser user) {
        permissions.requireAdmin(user);

        List<Long> meetingIds = jdbc.queryForList(
                "SELECT meeting_id FROM dm_meeting WHERE tenant_id = ? AND deleted = 1",
                Long.class, user.tenantId()
        );
        for (Long meetingId : meetingIds) {
            purgeMeetingAttachments(meetingId, user);
        }

        // 删除所有已删除会议参与的问题关联与系统关联
        jdbc.update(
                "DELETE r FROM dm_issue_relation r JOIN dm_meeting m ON m.tenant_id = r.tenant_id AND m.deleted = 1 AND r.related_type = 'MEETING' AND r.related_id = m.meeting_id " +
                "WHERE r.tenant_id = ?",
                user.tenantId()
        );
        jdbc.update(
                "DELETE s FROM dm_meeting_system s JOIN dm_meeting m ON m.tenant_id = s.tenant_id AND m.deleted = 1 AND m.meeting_id = s.meeting_id WHERE s.tenant_id = ?",
                user.tenantId()
        );

        // 删除所有已删除的会议纪要
        jdbc.update("DELETE FROM dm_meeting WHERE tenant_id = ? AND deleted = 1", user.tenantId());

        audit(user, "MEETING_PURGE_ALL", 0L);
    }

    /**
     * 获取系统选项（根据项目）
     */
    public List<Map<String, Object>> getSystemOptions(Long projectId, AuthUser user) {
        if (projectId == null) return List.of();
        return jdbc.queryForList(
                "SELECT s.id AS value, " +
                "CONCAT(s.code, ' - ', COALESCE(s.short_name, s.name, '')) AS label " +
                "FROM dm_component c " +
                "JOIN arch_physical_subsystem s ON s.tenant_id = c.tenant_id AND s.code = c.physical_subsystem_code AND s.deleted = 0 " +
                "WHERE c.tenant_id = ? AND c.project_id = ? AND c.deleted = 0 " +
                "GROUP BY s.id, s.code, s.short_name, s.name " +
                "ORDER BY s.code",
                user.tenantId(), projectId
        );
    }

    /**
     * 获取问题选项（根据项目）
     */
    public List<Map<String, Object>> getIssueOptions(Long projectId, AuthUser user) {
        if (projectId == null) return List.of();
        return jdbc.queryForList(
                "SELECT id AS value, CONCAT(issue_code, ' - ', issue_name) AS label " +
                "FROM dm_issue " +
                "WHERE tenant_id = ? AND project_id = ? AND deleted = 0 " +
                "ORDER BY issue_code",
                user.tenantId(), projectId
        );
    }

    // ============ 私有方法 ============

    private String baseSelect(boolean deleted) {
        return "SELECT m.meeting_id, m.meeting_code, m.meeting_code AS asset_code, m.tenant_id, m.project_id, p.project_name, " +
                "m.granularity, m.meeting_source, m.meeting_title, m.meeting_content, " +
                "m.meeting_conclusion, m.business_scenario, m.keywords, " +
                "(SELECT ma.attachment_id FROM dm_content_attachment ma WHERE ma.tenant_id = m.tenant_id AND ma.business_type = 'MEETING' AND ma.business_id = m.meeting_id AND ma.deleted = 0 ORDER BY ma.sort_order ASC, ma.created_at ASC LIMIT 1) AS attachment_id, " +
                "(SELECT ma.file_name FROM dm_content_attachment ma WHERE ma.tenant_id = m.tenant_id AND ma.business_type = 'MEETING' AND ma.business_id = m.meeting_id AND ma.deleted = 0 ORDER BY ma.sort_order ASC, ma.created_at ASC LIMIT 1) AS file_name, " +
                "m.created_by, m.created_at, m.updated_by, m.updated_at, " +
                "m.deleted_by, m.deleted_at, " +
                "u1.display_name AS created_by_name, " +
                "u2.display_name AS updated_by_name, " +
                "u3.display_name AS deleted_by_name, " +
                "(SELECT GROUP_CONCAT(s.name ORDER BY s.name SEPARATOR ', ') " +
                " FROM dm_meeting_system ms " +
                " JOIN arch_physical_subsystem s ON s.id = ms.subsystem_id AND s.tenant_id = ms.tenant_id AND s.deleted = 0 " +
                " WHERE ms.tenant_id = m.tenant_id AND ms.meeting_id = m.meeting_id) AS system_names, " +
                "(SELECT GROUP_CONCAT(ms.subsystem_id) " +
                " FROM dm_meeting_system ms " +
                " WHERE ms.tenant_id = m.tenant_id AND ms.meeting_id = m.meeting_id) AS system_ids, " +
                "(SELECT GROUP_CONCAT(i.issue_name ORDER BY i.issue_name SEPARATOR ', ') " +
                " FROM dm_issue_relation r " +
                " JOIN dm_issue i ON i.id = r.issue_id AND i.tenant_id = r.tenant_id AND i.deleted = 0 " +
                " WHERE r.tenant_id = m.tenant_id AND r.related_type = 'MEETING' AND r.related_id = m.meeting_id) AS related_issue_names, " +
                "(SELECT GROUP_CONCAT(r.issue_id) " +
                " FROM dm_issue_relation r " +
                " WHERE r.tenant_id = m.tenant_id AND r.related_type = 'MEETING' AND r.related_id = m.meeting_id) AS related_issue_ids, " +
                "(SELECT COUNT(*) FROM dm_content_attachment ma WHERE ma.tenant_id = m.tenant_id AND ma.business_type = 'MEETING' AND ma.business_id = m.meeting_id AND ma.deleted = 0) AS attachment_count " +
                "FROM dm_meeting m " +
                "LEFT JOIN pm_project p ON p.id = m.project_id AND p.tenant_id = m.tenant_id AND p.deleted = 0 " +
                "LEFT JOIN sys_user u1 ON u1.id = m.created_by AND u1.tenant_id = m.tenant_id " +
                "LEFT JOIN sys_user u2 ON u2.id = m.updated_by AND u2.tenant_id = m.tenant_id " +
                "LEFT JOIN sys_user u3 ON u3.id = m.deleted_by AND u3.tenant_id = m.tenant_id " +
                "WHERE m.tenant_id = ? AND m.deleted = " + (deleted ? "1" : "0");
    }

    private void appendFilters(StringBuilder sql, List<Object> args, Long projectId, String meetingSource,
                               String granularity, Long systemId, String keyword, boolean deleted) {
        if (projectId != null) {
            sql.append(" AND m.project_id = ?");
            args.add(projectId);
        }
        if (!deleted && meetingSource != null && MEETING_SOURCES.contains(meetingSource)) {
            sql.append(" AND m.meeting_source = ?");
            args.add(meetingSource);
        }
        if (!deleted && granularity != null && GRANULARITIES.contains(granularity)) {
            sql.append(" AND m.granularity = ?");
            args.add(granularity);
        }
        if (!deleted && systemId != null) {
            sql.append(" AND EXISTS (SELECT 1 FROM dm_meeting_system ms WHERE ms.tenant_id = m.tenant_id AND ms.meeting_id = m.meeting_id AND ms.subsystem_id = ?)");
            args.add(systemId);
        }
        if (keyword != null && !keyword.isBlank()) {
            String value = "%" + keyword.trim() + "%";
            sql.append(" AND (m.meeting_code LIKE ? OR m.meeting_title LIKE ? OR m.keywords LIKE ? OR EXISTS (SELECT 1 FROM dm_meeting_system ms JOIN arch_physical_subsystem s ON s.id = ms.subsystem_id AND s.tenant_id = ms.tenant_id WHERE ms.tenant_id = m.tenant_id AND ms.meeting_id = m.meeting_id AND s.name LIKE ?))");
            args.add(value);
            args.add(value);
            args.add(value);
            args.add(value);
        }
    }

    private Map<String, Object> findByIdInternal(long meetingId, long tenantId, boolean deleted) {
        List<Map<String, Object>> rows = jdbc.queryForList(baseSelect(deleted) + " AND m.meeting_id = ?", tenantId, meetingId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "会议纪要不存在");
        }
        Map<String, Object> row = rows.get(0);
        // 附加完整的附件列表（公共附件关系表）
        row.put("attachments", contentAttachments.list(CONTENT_TYPE, meetingId, tenantId));
        return row;
    }

    private void saveSystemRelations(long meetingId, long projectId, Map<String, Object> body, AuthUser user) {
        if (!body.containsKey("systemIds")) return;

        List<Long> systemIds = new ArrayList<>();
        for (Object value : asList(body.get("systemIds"))) {
            long systemId;
            try {
                systemId = Long.parseLong(String.valueOf(value));
            } catch (NumberFormatException ex) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "系统ID无效");
            }
            if (!systemIds.contains(systemId)) systemIds.add(systemId);
        }

        // 验证系统属于当前项目
        for (Long systemId : systemIds) {
            ensureSystemBelongsToProject(systemId, projectId, user);
        }

        // 删除旧的系统关联（dm_meeting_system）
        jdbc.update(
                "DELETE FROM dm_meeting_system WHERE tenant_id = ? AND meeting_id = ?",
                user.tenantId(), meetingId
        );

        // 插入新的系统关联
        for (Long systemId : systemIds) {
            jdbc.update(
                    "INSERT INTO dm_meeting_system (id, tenant_id, meeting_id, subsystem_id, created_by) " +
                    "VALUES (?, ?, ?, ?, ?)",
                    nextId(), user.tenantId(), meetingId, systemId, user.id()
            );
        }
    }

    /**
     * 保存会议纪要关联问题
     */
    private void saveIssueRelations(long meetingId, long projectId, Map<String, Object> body, AuthUser user) {
        if (!body.containsKey("issueIds")) return;

        List<Long> issueIds = new ArrayList<>();
        for (Object value : asList(body.get("issueIds"))) {
            long issueId;
            try {
                issueId = Long.parseLong(String.valueOf(value));
            } catch (NumberFormatException ex) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "问题ID无效");
            }
            if (!issueIds.contains(issueId)) issueIds.add(issueId);
        }

        // 验证问题属于当前项目
        for (Long issueId : issueIds) {
            ensureIssueBelongsToProject(issueId, projectId, user);
        }

        // 删除旧的问题关联（dm_issue_relation 中以本会议为锚点的 MEETING 行）
        jdbc.update(
                "DELETE FROM dm_issue_relation WHERE tenant_id = ? AND related_type = 'MEETING' AND related_id = ?",
                user.tenantId(), meetingId
        );

        // 插入新的问题关联：归一为 (issue_id=问题, related_type='MEETING', related_id=会议)
        for (Long issueId : issueIds) {
            jdbc.update(
                    "INSERT INTO dm_issue_relation (id, tenant_id, issue_id, related_type, related_id, created_by) " +
                    "VALUES (?, ?, ?, 'MEETING', ?, ?)",
                    nextId(), user.tenantId(), issueId, meetingId, user.id()
            );
        }
    }

    private void ensureIssueBelongsToProject(long issueId, long projectId, AuthUser user) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dm_issue WHERE id = ? AND tenant_id = ? AND project_id = ? AND deleted = 0",
                Integer.class, issueId, user.tenantId(), projectId
        );
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "问题不存在或不属于当前项目");
        }
    }

    /**
     * 保存会议纪要附件关联（多附件支持）
     */
    @SuppressWarnings("unchecked")
    private void saveMeetingAttachments(long meetingId, long projectId, Map<String, Object> body, AuthUser user) {
        if (!body.containsKey("attachments") && body.get("attachmentId") == null) return;

        List<Map<String, Object>> attachments = new ArrayList<>();
        Object attachmentsObj = body.get("attachments");
        if (attachmentsObj instanceof Collection<?> c) {
            for (Object item : c) {
                if (item instanceof Map) {
                    attachments.add((Map<String, Object>) item);
                }
            }
        }
        if (attachments.isEmpty() && body.get("attachmentId") != null) {
            Map<String, Object> legacy = new LinkedHashMap<>();
            legacy.put("attachmentId", body.get("attachmentId"));
            legacy.put("fileName", body.get("fileName"));
            attachments.add(legacy);
        }

        // 验证每个附件的 attachmentId 存在且属于当前租户
        for (Map<String, Object> att : attachments) {
            Long attachmentId = att.get("attachmentId") != null ? number(att.get("attachmentId"), "attachmentId") : null;
            if (attachmentId != null) {
                ensureAttachmentExists(attachmentId, user);
            }
        }

        // 全量重设活动附件集合（排序/复活/软删/新增）统一经 ContentAttachmentService
        contentAttachments.replaceAll(CONTENT_TYPE, BUSINESS_TYPE, meetingId, projectId, attachments, user);
    }

    /**
     * 验证附件存在
     */
    private void ensureAttachmentExists(Long attachmentId, AuthUser user) {
        if (!exists("SELECT COUNT(*) FROM att_file WHERE id = ? AND tenant_id = ?", attachmentId, user.tenantId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "附件不存在");
        }
    }

    /**
     * 获取会议纪要的附件列表
     */
    public List<Map<String, Object>> getMeetingAttachments(long meetingId, AuthUser user) {
        return contentAttachments.list(CONTENT_TYPE, meetingId, user.tenantId());
    }

    /**
     * 软删除附件（移入回收站）
     */
    @Transactional
    public void deleteAttachment(long meetingId, long attachmentId, AuthUser user) {
        Map<String, Object> current = findByIdInternal(meetingId, user.tenantId(), false);
        permissions.requireWrite(user, ((Number) current.get("created_by")).longValue());

        int changed = contentAttachments.softDelete(CONTENT_TYPE, meetingId, attachmentId, user);
        if (changed != 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "附件不存在或已删除");
        }

        audit(user, "MEETING_ATTACHMENT_DELETE", meetingId);
    }

    /**
     * 获取附件回收站列表
     */
    public List<Map<String, Object>> getAttachmentRecycleBin(long meetingId, AuthUser user) {
        return contentAttachments.listDeleted(CONTENT_TYPE, meetingId, user.tenantId());
    }

    /**
     * 恢复附件
     */
    @Transactional
    public void restoreAttachment(long meetingId, long attachmentId, AuthUser user) {
        permissions.requireAdmin(user);

        int changed = jdbc.update(
                "UPDATE dm_content_attachment SET deleted = 0, deleted_by = NULL, deleted_at = NULL " +
                "WHERE tenant_id = ? AND business_type = 'MEETING' AND business_id = ? AND attachment_id = ? AND deleted = 1",
                user.tenantId(), meetingId, attachmentId
        );
        if (changed != 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "附件不存在或未删除");
        }

        audit(user, "MEETING_ATTACHMENT_RESTORE", meetingId);
    }

    /**
     * 全局附件回收站列表（跨会议）
     */
    public PageResult<Map<String, Object>> attachmentRecycleBinList(Long projectId, String keyword, int page, int size, AuthUser user) {
        permissions.requireAdmin(user);

        StringBuilder sql = new StringBuilder(
                "SELECT a.id, a.attachment_id, a.file_name, a.sort_order, a.business_id AS meeting_id, " +
                "a.deleted_by, a.deleted_at, u.display_name AS deleted_by_name, " +
                "m.meeting_title AS meeting_title " +
                "FROM dm_content_attachment a " +
                "LEFT JOIN sys_user u ON u.id = a.deleted_by AND u.tenant_id = a.tenant_id " +
                "LEFT JOIN dm_meeting m ON m.meeting_id = a.business_id AND m.tenant_id = a.tenant_id " +
                "WHERE a.tenant_id = ? AND a.business_type = 'MEETING' AND a.deleted = 1"
        );
        List<Object> args = new ArrayList<>(List.of(user.tenantId()));

        if (projectId != null) {
            sql.append(" AND m.project_id = ?");
            args.add(projectId);
        }
        if (keyword != null && !keyword.isBlank()) {
            String value = "%" + keyword.trim() + "%";
            sql.append(" AND (a.file_name LIKE ? OR m.meeting_title LIKE ?)");
            args.add(value);
            args.add(value);
        }

        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM (" + sql + ") t", Long.class, args.toArray());
        int safePage = Math.max(1, page);
        int safeSize = normalizePageSize(size);
        sql.append(" ORDER BY a.deleted_at DESC LIMIT ? OFFSET ?");
        args.add(safeSize);
        args.add((safePage - 1) * safeSize);

        return new PageResult<>(jdbc.queryForList(sql.toString(), args.toArray()), total == null ? 0L : total, safePage, safeSize);
    }

    /**
     * 批量恢复附件
     */
    @Transactional
    public void restoreAttachments(List<Long> ids, AuthUser user) {
        permissions.requireAdmin(user);

        for (Long id : normalizeIds(ids)) {
            long meetingId = contentAttachments.requireSoftDeletedRow(id, user);
            contentAttachments.restoreByIds(List.of(id), user);
            audit(user, "MEETING_ATTACHMENT_RESTORE", meetingId);
        }
    }

    /**
     * 彻底删除附件
     */
    @Transactional
    public void purgeAttachments(List<Long> ids, AuthUser user) {
        permissions.requireAdmin(user);

        for (Long id : normalizeIds(ids)) {
            long meetingId = contentAttachments.requireSoftDeletedRow(id, user);
            contentAttachments.purgeByIds(List.of(id), user);
            audit(user, "MEETING_ATTACHMENT_PURGE", meetingId);
        }
    }

    /**
     * 清空附件回收站
     */
    @Transactional
    public void purgeAllAttachments(AuthUser user) {
        permissions.requireAdmin(user);

        contentAttachments.purgeAllSoftDeleted(user);

        audit(user, "MEETING_ATTACHMENT_PURGE_ALL", 0L);
    }

    /** 清理会议所有附件对象和关联行，仅解绑已按会议业务绑定的附件。 */
    private void purgeMeetingAttachments(long meetingId, AuthUser user) {
        contentAttachments.unbindAndRemoveAll(CONTENT_TYPE, BUSINESS_TYPE, meetingId, user);
    }

    private void ensureSystemBelongsToProject(long systemId, long projectId, AuthUser user) {
        // 验证系统存在且属于当前项目
        String sql = "SELECT COUNT(*) FROM dm_component c " +
                "JOIN arch_physical_subsystem s ON s.tenant_id = c.tenant_id AND s.code = c.physical_subsystem_code AND s.deleted = 0 " +
                "WHERE s.id = ? AND c.tenant_id = ? AND c.project_id = ? AND c.deleted = 0";
        if (!exists(sql, systemId, user.tenantId(), projectId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "系统不属于所选项目");
        }
    }

    private void validateEnums(Map<String, Object> body) {
        validateEnum(body.get("granularity"), GRANULARITIES, "颗粒度");
        validateEnum(body.get("meetingSource"), MEETING_SOURCES, "会议纪要来源");
    }

    private void validateEnum(Object value, Set<String> allowed, String label) {
        if (value != null && !String.valueOf(value).isBlank() && !allowed.contains(String.valueOf(value))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, label + "值无效");
        }
    }

    private void ensureProject(long id, AuthUser user) {
        if (!exists("SELECT COUNT(*) FROM pm_project WHERE id = ? AND tenant_id = ? AND deleted = 0", id, user.tenantId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "项目不存在");
        }
    }

    /** 与 IssueService.ensureCodeAvailable 同构：活动域内校验会议编号唯一，允许已删除记录释放编号。 */
    private void ensureCodeAvailable(long projectId, String code, Long currentMeetingId, AuthUser user) {
        String sql = "SELECT COUNT(*) FROM dm_meeting WHERE tenant_id = ? AND project_id = ? AND meeting_code = ? AND deleted = 0"
                + (currentMeetingId == null ? "" : " AND meeting_id <> ?");
        List<Object> args = new ArrayList<>(List.of(user.tenantId(), projectId, code));
        if (currentMeetingId != null) args.add(currentMeetingId);
        if (exists(sql, args.toArray())) {
            throw new BusinessException(ErrorCode.CONFLICT, "会议编号在该项目下已存在");
        }
    }

    /** 捕获唯一索引异常时统一翻译为友好提示，其他完整性冲突继续抛出。 */
    private BusinessException meetingCodeConflict(DataIntegrityViolationException ex) {
        String msg = ex.getMessage();
        if (msg != null && (msg.contains("uk_dm_meeting_active_code") || msg.contains("active_meeting_code") || msg.contains("meeting_code"))) {
            return new BusinessException(ErrorCode.CONFLICT, "会议编号在该项目下已存在");
        }
        return new BusinessException(ErrorCode.CONFLICT, "会议纪要数据冲突，请刷新后重试");
    }

    private static int normalizePageSize(int size) {
        return PAGE_SIZES.contains(size) ? size : 20;
    }

    private boolean exists(String sql, Object... args) {
        Integer count = jdbc.queryForObject(sql, Integer.class, args);
        return count != null && count > 0;
    }

    private static String text(Object value, String field) {
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, field + " 不能为空");
        }
        return String.valueOf(value).trim();
    }

    private static String textOrNull(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static long number(Object value, String field) {
        try {
            return Long.parseLong(text(value, field));
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, field + " 必须为数字");
        }
    }

    private static String keywords(Object value) {
        if (value == null) return null;
        if (value instanceof Collection<?> c) {
            List<String> list = c.stream()
                    .map(String::valueOf)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .distinct()
                    .toList();
            if (list.isEmpty()) return null;
            // 返回 JSON 数组格式
            return "[" + list.stream()
                    .map(s -> "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
                    .reduce((a, b) -> a + "," + b)
                    .orElse("") + "]";
        }
        // 如果是字符串，尝试作为 JSON 解析，否则包装为 JSON 数组
        String s = textOrNull(value);
        if (s == null) return null;
        if (s.startsWith("[")) return s; // 已经是 JSON 数组
        // 逗号分隔的旧格式，转换为 JSON 数组
        List<String> items = Arrays.stream(s.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
        if (items.isEmpty()) return null;
        return "[" + items.stream()
                .map(item -> "\"" + item.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
                .reduce((a, b) -> a + "," + b)
                .orElse("") + "]";
    }

    private static List<?> asList(Object value) {
        return value instanceof Collection<?> c ? new ArrayList<>(c) : value == null ? List.of() : List.of(value);
    }

    private static List<Long> normalizeIds(List<Long> ids) {
        return ids == null ? List.of() : ids.stream().filter(Objects::nonNull).distinct().toList();
    }

    private void audit(AuthUser user, String operation, long meetingId) {
        jdbc.update(
                "INSERT INTO dm_operation_log (tenant_id, actor_id, operation_code, entity_type, entity_id) " +
                "VALUES (?, ?, ?, 'MEETING', ?)",
                user.tenantId(), user.id(), operation, meetingId
        );
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000);
    }
}
