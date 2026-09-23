package com.ccb.datamigration.lifecycle.activity;

import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.datamigration.lifecycle.LifecycleAuditService;
import com.ccb.datamigration.lifecycle.LifecyclePermissionService;
import com.ccb.datamigration.lifecycle.activity.model.ActivityView;
import com.ccb.datamigration.lifecycle.error.LifecycleErrorCode;
import com.ccb.security.model.AuthUser;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 活动基础管理：CRUD、启用/停用/作废（三不准）、颗粒度锁定、组件绑定互斥、专题聚合四道闸门、发布出口（基线第 9 章）。 */
@Service
public class ActivityService {
    private static final double MAX_ACTIVITY_NAME_LENGTH = 160;

    private final JdbcTemplate jdbc;
    private final LifecyclePermissionService permissions;
    private final LifecycleAuditService audit;
    private final ActivityCodeGenerator codeGenerator;

    public ActivityService(JdbcTemplate jdbc, LifecyclePermissionService permissions,
                           LifecycleAuditService audit, ActivityCodeGenerator codeGenerator) {
        this.jdbc = jdbc;
        this.permissions = permissions;
        this.audit = audit;
        this.codeGenerator = codeGenerator;
    }

    public PageResult<ActivityView> pageActivities(AuthUser user, String activityCode, String activityName,
                                                   Long lifecycleStageId, String granularity, String activityStatus,
                                                   String createdFrom, String createdTo, PageQuery page) {
        PageQuery normalized = page == null ? new PageQuery(1, 20) : page;
        List<Object> args = new ArrayList<>();
        args.add(user.tenantId());
        StringBuilder where = new StringBuilder("WHERE a.tenant_id = ? AND a.deleted = 0");
        applyFilter(where, args, activityCode, activityName, lifecycleStageId, granularity, activityStatus, createdFrom, createdTo);
        String select = "SELECT a.id, a.tenant_id, a.activity_code, a.activity_name, a.activity_type, "
                + "a.lifecycle_stage_id, s.stage_code, s.stage_name, a.granularity, a.activity_status, "
                + "a.scene, a.goal, a.overall_entry_cond, a.overall_exit_desc, a.overall_deliverables, "
                + "a.created_by, a.updated_by, a.created_at, a.updated_at, "
                + "(SELECT COUNT(*) FROM activity_process p WHERE p.activity_id = a.id AND p.tenant_id = a.tenant_id AND p.deleted = 0) AS process_total, "
                + "(SELECT COUNT(*) FROM activity_process p WHERE p.activity_id = a.id AND p.tenant_id = a.tenant_id AND p.deleted = 0 AND p.config_status = 'READY') AS process_ready "
                + "FROM activity a "
                + "LEFT JOIN lifecycle_stage s ON s.id = a.lifecycle_stage_id AND s.tenant_id = a.tenant_id AND s.deleted = 0 ";
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM activity a "
                + "LEFT JOIN lifecycle_stage s ON s.id = a.lifecycle_stage_id AND s.tenant_id = a.tenant_id AND s.deleted = 0 "
                + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(normalized.size());
        pageArgs.add((normalized.page() - 1) * normalized.size());
        List<ActivityView> records = jdbc.query(select + where + " ORDER BY a.updated_at DESC, a.id DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> new ActivityView(rs.getLong("id"), rs.getLong("tenant_id"), rs.getString("activity_code"),
                        rs.getString("activity_name"), rs.getString("activity_type"), getLong(rs, "lifecycle_stage_id"),
                        rs.getString("stage_code"), rs.getString("stage_name"), rs.getString("granularity"),
                        rs.getString("activity_status"), rs.getString("scene"), rs.getString("goal"),
                        rs.getString("overall_entry_cond"), rs.getString("overall_exit_desc"), rs.getString("overall_deliverables"),
                        rs.getLong("created_by"), rs.getLong("updated_by"), rs.getTimestamp("created_at").toLocalDateTime(),
                        rs.getTimestamp("updated_at").toLocalDateTime(), rs.getInt("process_total"), rs.getInt("process_ready")),
                pageArgs.toArray());
        return new PageResult<>(records, total == null ? 0 : total, normalized.page(), normalized.size());
    }

    private void applyFilter(StringBuilder where, List<Object> args, String activityCode, String activityName,
                             Long lifecycleStageId, String granularity, String activityStatus,
                             String createdFrom, String createdTo) {
        if (activityCode != null && !activityCode.isBlank()) {
            where.append(" AND a.activity_code LIKE ?");
            args.add("%" + activityCode.trim() + "%");
        }
        if (activityName != null && !activityName.isBlank()) {
            where.append(" AND a.activity_name LIKE ?");
            args.add("%" + activityName.trim() + "%");
        }
        if (lifecycleStageId != null) {
            where.append(" AND a.lifecycle_stage_id = ?");
            args.add(lifecycleStageId);
        }
        if (granularity != null && !granularity.isBlank()) {
            where.append(" AND a.granularity = ?");
            args.add(granularity.trim());
        }
        if (activityStatus != null && !activityStatus.isBlank()) {
            where.append(" AND a.activity_status = ?");
            args.add(activityStatus.trim());
        }
        if (createdFrom != null && !createdFrom.isBlank()) {
            where.append(" AND a.created_at >= ?");
            args.add(createdFrom.trim());
        }
        if (createdTo != null && !createdTo.isBlank()) {
            where.append(" AND a.created_at <= ?");
            args.add(createdTo.trim());
        }
    }

    private Long getLong(java.sql.ResultSet rs, String column) {
        try {
            long value = rs.getLong(column);
            return rs.wasNull() ? null : value;
        } catch (java.sql.SQLException ex) {
            return null;
        }
    }

    @Transactional
    public ActivityView createActivity(AuthUser user, Map<String, Object> body) {
        permissions.requireAdmin(user);
        String activityType = typeOf(body);
        String granularity = granularityOf(body);
        Long lifecycleStageId = nullableLong(body, "lifecycleStageId");
        validateBasics(body, activityType, granularity, lifecycleStageId);
        for (int attempt = 0; attempt < 3; attempt++) {
            String code = codeGenerator.candidate(user.tenantId(), activityType, granularity);
            try {
                long id = nextId();
                jdbc.update("INSERT INTO activity (id, tenant_id, activity_code, activity_name, activity_type, "
                                + "lifecycle_stage_id, granularity, activity_status, scene, goal, overall_entry_cond, "
                                + "overall_exit_desc, overall_deliverables, created_by, updated_by) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?, ?, ?, ?, ?)",
                        id, user.tenantId(), code, text(body, "activityName"), activityType, lifecycleStageId, granularity,
                        nullableText(body, "scene"), nullableText(body, "goal"), nullableText(body, "overallEntryCond"),
                        nullableText(body, "overallExitDesc"), nullableText(body, "overallDeliverables"),
                        user.id(), user.id());
                validateComponentBinding(user, id, granularity, componentIds(body));
                audit.audit(user, audit.opCode("CREATE", "ACTIVITY"), "ACTIVITY", id, "SUCCESS", null, Map.of("activityCode", code));
                return view(id, user.tenantId());
            } catch (DuplicateKeyException ex) {
                if (attempt == 2) {
                    throw new BusinessException(LifecycleErrorCode.ACTIVITY_CODE_CONFLICT, "活动编码冲突，请重试");
                }
            }
        }
        throw new BusinessException(LifecycleErrorCode.ACTIVITY_CODE_CONFLICT, "活动编码冲突，请重试");
    }

    /** 活动详情（读路径不做管理员限制，仅校验租户内存在性）。 */
    public ActivityView detail(AuthUser user, long id) {
        requireActivity(id, user.tenantId());
        return view(id, user.tenantId());
    }

    @Transactional
    public ActivityView updateActivity(AuthUser user, long id, Map<String, Object> body) {
        permissions.requireAdmin(user);
        Map<String, Object> row = requireActivity(id, user.tenantId());
        String activityType = String.valueOf(row.get("activity_type"));
        String granularity = String.valueOf(row.get("granularity"));
        rejectObsolete(row);
        Long lifecycleStageId = nullableLong(body, "lifecycleStageId");
        validateBasics(body, activityType, granularity, lifecycleStageId);
        jdbc.update("UPDATE activity SET activity_name = ?, lifecycle_stage_id = ?, scene = ?, goal = ?, "
                        + "overall_entry_cond = ?, overall_exit_desc = ?, overall_deliverables = ?, updated_by = ?, "
                        + "updated_at = CURRENT_TIMESTAMP(6) WHERE id = ? AND tenant_id = ? AND deleted = 0",
                text(body, "activityName"), lifecycleStageId, nullableText(body, "scene"), nullableText(body, "goal"),
                nullableText(body, "overallEntryCond"), nullableText(body, "overallExitDesc"),
                nullableText(body, "overallDeliverables"), user.id(), id, user.tenantId());
        validateComponentBinding(user, id, granularity, componentIds(body));
        audit.audit(user, audit.opCode("UPDATE", "ACTIVITY"), "ACTIVITY", id, "SUCCESS", null, null);
        return view(id, user.tenantId());
    }

    @Transactional
    public ActivityView setStatus(AuthUser user, long id, String targetStatus) {
        permissions.requireAdmin(user);
        Map<String, Object> row = requireActivity(id, user.tenantId());
        rejectObsolete(row);
        String current = String.valueOf(row.get("activity_status"));
        if (current.equals(targetStatus)) {
            return view(id, user.tenantId());
        }
        if (!"ACTIVE".equals(targetStatus) && !"INACTIVE".equals(targetStatus)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的状态切换");
        }
        jdbc.update("UPDATE activity SET activity_status = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP(6) "
                + "WHERE id = ? AND tenant_id = ? AND deleted = 0", targetStatus, user.id(), id, user.tenantId());
        audit.audit(user, audit.opCode("STATUS_" + targetStatus, "ACTIVITY"), "ACTIVITY", id, "SUCCESS", null,
                Map.of("from", current, "to", targetStatus));
        return view(id, user.tenantId());
    }

    private void rejectObsolete(Map<String, Object> row) {
        if ("OBSOLETE".equals(String.valueOf(row.get("activity_status")))) {
            throw new BusinessException(LifecycleErrorCode.ACTIVITY_OBSOLETE_READONLY, "已作废活动只读，禁止任何写操作");
        }
    }

    /** 作废（OBSOLETE 终态）：三不准由 rejectObsolete 拦截（不可编辑/不可重新启用/不可重复作废）。 */
    @Transactional
    public ActivityView obsoleteActivity(AuthUser user, long id) {
        permissions.requireAdmin(user);
        Map<String, Object> row = requireActivity(id, user.tenantId());
        rejectObsolete(row);
        if ("OBSOLETE".equals(String.valueOf(row.get("activity_status")))) {
            return view(id, user.tenantId());
        }
        jdbc.update("UPDATE activity SET activity_status = 'OBSOLETE', updated_by = ?, updated_at = CURRENT_TIMESTAMP(6) "
                + "WHERE id = ? AND tenant_id = ? AND deleted = 0", user.id(), id, user.tenantId());
        audit.audit(user, audit.opCode("OBSOLETE", "ACTIVITY"), "ACTIVITY", id, "SUCCESS", null,
                Map.of("from", String.valueOf(row.get("activity_status")), "to", "OBSOLETE"));
        return view(id, user.tenantId());
    }

    /** 专题聚合候选：仅同颗粒度、ACTIVE、NORMAL 的普通活动（四道闸门：类型/颗粒度/状态/范围）。 */
    public List<Map<String, Object>> topicCandidates(AuthUser user, long topicActivityId) {
        Map<String, Object> topic = requireActivity(topicActivityId, user.tenantId());
        if (!"TOPIC".equals(String.valueOf(topic.get("activity_type")))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅专题聚合活动可关联普通活动");
        }
        if (!"ACTIVE".equals(String.valueOf(topic.get("activity_status")))) {
            throw new BusinessException(LifecycleErrorCode.ACTIVITY_INACTIVE_NO_DISPATCH, "停用/作废活动不允许被聚合关联");
        }
        return jdbc.queryForList("SELECT a.id, a.activity_code, a.activity_name, s.stage_name, a.activity_status "
                + "FROM activity a LEFT JOIN lifecycle_stage s ON s.id = a.lifecycle_stage_id AND s.tenant_id = a.tenant_id AND s.deleted = 0 "
                + "WHERE a.tenant_id = ? AND a.deleted = 0 AND a.activity_type = 'NORMAL' AND a.activity_status = 'ACTIVE' "
                + "AND a.granularity = ? AND a.id <> ? AND a.id NOT IN "
                + "(SELECT member_activity_id FROM activity_topic_rel WHERE topic_activity_id = ? AND tenant_id = ? AND deleted = 0) "
                + "ORDER BY a.activity_code",
                user.tenantId(), String.valueOf(topic.get("granularity")), topicActivityId, topicActivityId, user.tenantId());
    }

    /** 专题已聚合成员（只读）。 */
    public List<Map<String, Object>> topicMembers(AuthUser user, long topicActivityId) {
        Map<String, Object> topic = requireActivity(topicActivityId, user.tenantId());
        if (!"TOPIC".equals(String.valueOf(topic.get("activity_type")))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅专题聚合活动可管理聚合成员");
        }
        return jdbc.queryForList("SELECT a.id, a.activity_code, a.activity_name, s.stage_name, a.activity_status "
                + "FROM activity_topic_rel r "
                + "JOIN activity a ON a.id = r.member_activity_id AND a.tenant_id = r.tenant_id AND a.deleted = 0 "
                + "LEFT JOIN lifecycle_stage s ON s.id = a.lifecycle_stage_id AND s.tenant_id = a.tenant_id AND s.deleted = 0 "
                + "WHERE r.topic_activity_id = ? AND r.tenant_id = ? AND r.deleted = 0 ORDER BY a.activity_code",
                topicActivityId, user.tenantId());
    }

    /** 添加专题聚合成员：类型/颗粒度/状态/范围四道闸门。 */
    @Transactional
    public void addTopicMember(AuthUser user, long topicActivityId, long memberActivityId) {
        permissions.requireAdmin(user);
        Map<String, Object> topic = requireActivity(topicActivityId, user.tenantId());
        if (!"TOPIC".equals(String.valueOf(topic.get("activity_type")))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅专题聚合活动可关联普通活动");
        }
        if (!"ACTIVE".equals(String.valueOf(topic.get("activity_status")))) {
            throw new BusinessException(LifecycleErrorCode.ACTIVITY_INACTIVE_NO_DISPATCH, "停用/作废活动不允许被聚合关联");
        }
        Map<String, Object> member = requireActivity(memberActivityId, user.tenantId());
        if (!"NORMAL".equals(String.valueOf(member.get("activity_type")))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "专题聚合仅允许关联普通基础活动");
        }
        if (!String.valueOf(topic.get("granularity")).equals(String.valueOf(member.get("granularity")))) {
            throw new BusinessException(LifecycleErrorCode.TOPIC_GRANULARITY_MISMATCH, "专题聚合活动仅可聚合同颗粒度普通活动");
        }
        if (!"ACTIVE".equals(String.valueOf(member.get("activity_status")))) {
            throw new BusinessException(LifecycleErrorCode.ACTIVITY_INACTIVE_NO_DISPATCH, "仅启用普通活动可被聚合关联");
        }
        if (exists("SELECT COUNT(*) FROM activity_topic_rel WHERE topic_activity_id = ? AND member_activity_id = ? "
                + "AND tenant_id = ? AND deleted = 0", topicActivityId, memberActivityId, user.tenantId())) {
            throw new BusinessException(ErrorCode.CONFLICT, "该普通活动已在专题聚合中");
        }
        jdbc.update("INSERT INTO activity_topic_rel (id, tenant_id, topic_activity_id, member_activity_id, created_by) "
                + "VALUES (?, ?, ?, ?, ?)", nextId(), user.tenantId(), topicActivityId, memberActivityId, user.id());
        audit.audit(user, audit.opCode("TOPIC_ADD", "ACTIVITY"), "ACTIVITY_TOPIC", topicActivityId, "SUCCESS", null,
                Map.of("memberActivityId", memberActivityId));
    }

    @Transactional
    public void removeTopicMember(AuthUser user, long topicActivityId, long memberActivityId) {
        permissions.requireAdmin(user);
        Map<String, Object> topic = requireActivity(topicActivityId, user.tenantId());
        rejectObsolete(topic);
        jdbc.update("UPDATE activity_topic_rel SET deleted = 1 WHERE topic_activity_id = ? AND member_activity_id = ? "
                + "AND tenant_id = ? AND deleted = 0", topicActivityId, memberActivityId, user.tenantId());
        audit.audit(user, audit.opCode("TOPIC_REMOVE", "ACTIVITY"), "ACTIVITY_TOPIC", topicActivityId, "SUCCESS", null,
                Map.of("memberActivityId", memberActivityId));
    }

    private boolean exists(String sql, Object... args) {
        Integer count = jdbc.queryForObject(sql, Integer.class, args);
        return count != null && count > 0;
    }

    private Map<String, Object> requireActivity(long id, long tenantId) {
        try {
            Map<String, Object> row = jdbc.queryForMap("SELECT id, tenant_id, activity_type, granularity, activity_status, "
                    + "lifecycle_stage_id FROM activity WHERE id = ? AND tenant_id = ? AND deleted = 0", id, tenantId);
            return row;
        } catch (Exception ex) {
            throw new BusinessException(LifecycleErrorCode.ACTIVITY_NOT_FOUND, "活动不存在");
        }
    }

    private ActivityView view(long id, long tenantId) {
        try {
            return jdbc.queryForObject("SELECT a.id, a.tenant_id, a.activity_code, a.activity_name, a.activity_type, "
                            + "a.lifecycle_stage_id, s.stage_code, s.stage_name, a.granularity, a.activity_status, "
                            + "a.scene, a.goal, a.overall_entry_cond, a.overall_exit_desc, a.overall_deliverables, "
                            + "a.created_by, a.updated_by, a.created_at, a.updated_at, "
                            + "(SELECT COUNT(*) FROM activity_process p WHERE p.activity_id = a.id AND p.tenant_id = a.tenant_id AND p.deleted = 0) AS process_total, "
                            + "(SELECT COUNT(*) FROM activity_process p WHERE p.activity_id = a.id AND p.tenant_id = a.tenant_id AND p.deleted = 0 AND p.config_status = 'READY') AS process_ready "
                            + "FROM activity a "
                            + "LEFT JOIN lifecycle_stage s ON s.id = a.lifecycle_stage_id AND s.tenant_id = a.tenant_id AND s.deleted = 0 "
                            + "WHERE a.id = ? AND a.tenant_id = ? AND a.deleted = 0",
                    (rs, rowNum) -> new ActivityView(rs.getLong("id"), rs.getLong("tenant_id"), rs.getString("activity_code"),
                            rs.getString("activity_name"), rs.getString("activity_type"), getLong(rs, "lifecycle_stage_id"),
                            rs.getString("stage_code"), rs.getString("stage_name"), rs.getString("granularity"),
                            rs.getString("activity_status"), rs.getString("scene"), rs.getString("goal"),
                            rs.getString("overall_entry_cond"), rs.getString("overall_exit_desc"), rs.getString("overall_deliverables"),
                            rs.getLong("created_by"), rs.getLong("updated_by"), rs.getTimestamp("created_at").toLocalDateTime(),
                            rs.getTimestamp("updated_at").toLocalDateTime(), rs.getInt("process_total"), rs.getInt("process_ready")),
                    id, tenantId);
        } catch (Exception ex) {
            throw new BusinessException(LifecycleErrorCode.ACTIVITY_NOT_FOUND, "活动不存在");
        }
    }

    private void validateBasics(Map<String, Object> body, String activityType, String granularity, Long lifecycleStageId) {
        String name = text(body, "activityName");
        if (name.length() > MAX_ACTIVITY_NAME_LENGTH) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "活动名称过长");
        }
        if (!"NORMAL".equals(activityType) && !"TOPIC".equals(activityType)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的活动类型");
        }
        if (!"PROJECT".equals(granularity) && !"COMPONENT".equals(granularity)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的颗粒度");
        }
        if ("NORMAL".equals(activityType) && lifecycleStageId == null) {
            throw new BusinessException(LifecycleErrorCode.LIFE_STAGE_REQUIRED_FOR_NORMAL, "普通基础活动必须归属生命周期阶段");
        }
    }

    private void validateComponentBinding(AuthUser user, long activityId, String granularity, List<Long> componentIds) {
        List<Long> normalized = componentIds == null ? List.of() : componentIds.stream().distinct().toList();
        if ("COMPONENT".equals(granularity) && normalized.isEmpty()) {
            throw new BusinessException(LifecycleErrorCode.COMPONENT_BINDING_MISMATCH, "组件级活动必须绑定至少 1 个组件");
        }
        if ("PROJECT".equals(granularity) && !normalized.isEmpty()) {
            throw new BusinessException(LifecycleErrorCode.COMPONENT_BINDING_MISMATCH, "项目级活动禁止绑定组件");
        }
        if (normalized.isEmpty()) {
            return;
        }
        Integer available = jdbc.queryForObject("SELECT COUNT(*) FROM dm_component WHERE id IN (" + placeholders(normalized.size()) + ") "
                + "AND tenant_id = ? AND deleted = 0", Integer.class, join(normalized, user.tenantId()));
        if (available == null || available != normalized.size()) {
            throw new BusinessException(LifecycleErrorCode.COMPONENT_BINDING_MISMATCH, "绑定组件必须为可用组件（deleted=0）");
        }
        jdbc.update("DELETE FROM activity_component_rel WHERE activity_id = ? AND tenant_id = ? AND deleted = 0",
                activityId, user.tenantId());
        for (Long componentId : normalized) {
            jdbc.update("INSERT INTO activity_component_rel (id, tenant_id, activity_id, component_id, created_by) "
                    + "VALUES (?, ?, ?, ?, ?)", nextId(), user.tenantId(), activityId, componentId, user.id());
        }
    }

    private String placeholders(int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append("?");
        }
        return sb.toString();
    }

    private Object[] join(List<Long> ids, long tenantId) {
        List<Object> values = new ArrayList<>(ids);
        values.add(tenantId);
        return values.toArray();
    }

    private static String typeOf(Map<String, Object> body) {
        return String.valueOf(body.get("activityType"));
    }

    private static String granularityOf(Map<String, Object> body) {
        return String.valueOf(body.get("granularity"));
    }

    private static String text(Map<String, Object> body, String key) {
        if (body.get(key) == null || String.valueOf(body.get(key)).trim().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, key + " 不能为空");
        }
        return String.valueOf(body.get(key)).trim();
    }

    private static String nullableText(Map<String, Object> body, String key) {
        return body.get(key) == null ? null : String.valueOf(body.get(key));
    }

    private static Long nullableLong(Map<String, Object> body, String key) {
        Object raw = body.get(key);
        if (raw == null || String.valueOf(raw).trim().isEmpty() || "null".equals(String.valueOf(raw))) {
            return null;
        }
        return Long.parseLong(String.valueOf(raw));
    }

    private static List<Long> componentIds(Map<String, Object> body) {
        Object raw = body.get("componentIds");
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (Object item : list) {
            try {
                ids.add(Long.valueOf(String.valueOf(item)));
            } catch (NumberFormatException ignored) {
                // 忽略非法项，绑定校验会拒绝数量不足或超范围
            }
        }
        return ids;
    }

    private static long nextId() {
        return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000);
    }
}
