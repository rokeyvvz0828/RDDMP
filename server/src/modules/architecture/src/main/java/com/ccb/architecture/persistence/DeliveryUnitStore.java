package com.ccb.architecture.persistence;

import com.ccb.architecture.model.DeliveryUnitModels.DeliveryUnit;
import com.ccb.architecture.model.DeliveryUnitModels.DeliveryUnitQuery;
import com.ccb.architecture.model.DeliveryUnitModels.DeploymentUnitRef;
import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * 交付单元数据访问：主记录、编号分配、与部署单元的无方向关联以及正反向查询。
 *
 * <p>编号分配必须运行在事务内，通过物理子系统级命名锁与序列表行锁保证并发不重号；
 * 关联的“同一物理子系统”不变量由复合外键在数据库层兜底。</p>
 */
@Repository
public class DeliveryUnitStore {
    public static final int MAX_ORDINAL_PER_PHYSICAL = 999;

    private static final String UNIT_COLUMNS = """
            id, code, physical_subsystem_id, name, status, description, remark,
            created_by, updated_by, created_at, updated_at, row_version
            """;

    private static final RowMapper<DeliveryUnit> UNIT_MAPPER = (rs, rowNum) -> new DeliveryUnit(
            rs.getLong("id"), rs.getString("code"), rs.getLong("physical_subsystem_id"),
            rs.getString("name"), rs.getString("status"), rs.getString("description"), rs.getString("remark"),
            rs.getLong("created_by"), rs.getLong("updated_by"),
            localDateTime(rs.getTimestamp("created_at")), localDateTime(rs.getTimestamp("updated_at")),
            rs.getLong("row_version"));

    private static final RowMapper<DeploymentUnitRef> DEPLOYMENT_UNIT_MAPPER = (rs, rowNum) ->
            new DeploymentUnitRef(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
                    rs.getString("kind"), rs.getString("status"), false, rs.getLong("physical_subsystem_id"));

    private final JdbcTemplate jdbc;

    public DeliveryUnitStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 物理子系统投影，用于归属校验与编号生成。 */
    public record PhysicalSubsystemProjection(
            long id,
            String code,
            String name,
            String status,
            boolean deleted) {
    }

    // ---------- 交付单元主记录 ----------

    public PageResult<DeliveryUnit> pageUnits(long tenantId, long projectId, PageQuery page,
                                              DeliveryUnitQuery query) {
        PageQuery normalizedPage = page == null ? new PageQuery(1, 20) : page;
        DeliveryUnitQuery normalized = query == null ? DeliveryUnitQuery.empty() : query;
        StringBuilder filter = new StringBuilder();
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        args.add(projectId);
        addLike(filter, args, "name", normalized.name());
        if (normalized.physicalSubsystemId() != null) {
            filter.append(" AND physical_subsystem_id = ?");
            args.add(normalized.physicalSubsystemId());
        }
        if (normalized.status() != null) {
            filter.append(" AND status = ?");
            args.add(normalized.status());
        }
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM arch_delivery_unit "
                        + "WHERE tenant_id = ? AND project_id = ? AND deleted = 0" + filter,
                Long.class, args.toArray());
        List<Object> listArgs = new ArrayList<>(args);
        listArgs.add(normalizedPage.size());
        listArgs.add((normalizedPage.page() - 1) * normalizedPage.size());
        List<DeliveryUnit> records = jdbc.query(
                "SELECT " + UNIT_COLUMNS + " FROM arch_delivery_unit "
                        + "WHERE tenant_id = ? AND project_id = ? AND deleted = 0" + filter
                        + " ORDER BY name, id LIMIT ? OFFSET ?",
                UNIT_MAPPER, listArgs.toArray());
        return new PageResult<>(records, total == null ? 0 : total, normalizedPage.page(), normalizedPage.size());
    }

    public Optional<DeliveryUnit> findUnit(long tenantId, long projectId, long id) {
        return jdbc.query("SELECT " + UNIT_COLUMNS + " FROM arch_delivery_unit "
                        + "WHERE tenant_id = ? AND project_id = ? AND id = ? AND deleted = 0",
                UNIT_MAPPER, tenantId, projectId, id).stream().findFirst();
    }

    /** 事务内锁读主记录，用于关联覆盖与状态迁移。 */
    public Optional<DeliveryUnit> lockUnit(long tenantId, long projectId, long id) {
        return jdbc.query("SELECT " + UNIT_COLUMNS + " FROM arch_delivery_unit "
                        + "WHERE tenant_id = ? AND project_id = ? AND id = ? AND deleted = 0 FOR UPDATE",
                UNIT_MAPPER, tenantId, projectId, id).stream().findFirst();
    }

    public boolean unitNameExists(long tenantId, long projectId, long physicalSubsystemId, String name,
                                  Long excludeId) {
        String exclude = excludeId == null ? "" : " AND id <> ?";
        List<Object> args = new ArrayList<>(List.of(tenantId, projectId, physicalSubsystemId, name));
        if (excludeId != null) {
            args.add(excludeId);
        }
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM arch_delivery_unit "
                        + "WHERE tenant_id = ? AND project_id = ? AND physical_subsystem_id = ? AND name = ?"
                        + " AND deleted = 0" + exclude,
                Long.class, args.toArray());
        return count != null && count > 0;
    }

    public void insertUnit(long id, long tenantId, long projectId, String code, long physicalSubsystemId,
                           String name, String description, String remark, long actorId) {
        jdbc.update("""
                INSERT INTO arch_delivery_unit
                    (id, tenant_id, project_id, code, physical_subsystem_id, name, status,
                     description, remark, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?, ?)
                """, id, tenantId, projectId, code, physicalSubsystemId, name, description, remark, actorId, actorId);
    }

    /** 乐观锁更新展示内容；返回 0 表示版本冲突或记录不可改。 */
    public int updateUnitContent(long tenantId, long projectId, long id, long expectedRowVersion, String name,
                                 String description, String remark, long actorId) {
        return jdbc.update("""
                UPDATE arch_delivery_unit
                SET name = ?, description = ?, remark = ?, updated_by = ?, row_version = row_version + 1
                WHERE tenant_id = ? AND project_id = ? AND id = ? AND deleted = 0 AND row_version = ?
                """, name, description, remark, actorId, tenantId, projectId, id, expectedRowVersion);
    }

    /** 状态迁移；返回 0 表示状态不允许或已变更。 */
    public int updateUnitStatus(long tenantId, long projectId, long id, String fromStatus, String toStatus,
                                long actorId) {
        return jdbc.update("""
                UPDATE arch_delivery_unit
                SET status = ?, updated_by = ?, row_version = row_version + 1
                WHERE tenant_id = ? AND project_id = ? AND id = ? AND deleted = 0 AND status = ?
                """, toStatus, actorId, tenantId, projectId, id, fromStatus);
    }

    /** 软删除主记录并清理其全部关联；部署单元主记录不受影响。 */
    public void softDelete(long tenantId, long projectId, long id, long actorId) {
        jdbc.update("DELETE FROM arch_delivery_unit_deployment_unit "
                + "WHERE tenant_id = ? AND project_id = ? AND delivery_unit_id = ?", tenantId, projectId, id);
        jdbc.update("""
                UPDATE arch_delivery_unit
                SET deleted = 1, updated_by = ?, row_version = row_version + 1
                WHERE tenant_id = ? AND project_id = ? AND id = ? AND deleted = 0
                """, actorId, tenantId, projectId, id);
    }

    // ---------- 编号分配 ----------

    public Optional<PhysicalSubsystemProjection> findPhysical(long tenantId, long projectId, long physicalSubsystemId) {
        return jdbc.query("SELECT id, code, name, status, deleted FROM arch_physical_subsystem "
                        + "WHERE tenant_id = ? AND project_id = ? AND id = ?",
                (rs, rowNum) -> new PhysicalSubsystemProjection(rs.getLong("id"), rs.getString("code"),
                        rs.getString("name"), rs.getString("status"), rs.getBoolean("deleted")),
                tenantId, projectId, physicalSubsystemId).stream().findFirst();
    }

    /**
     * 事务内分配交付单元编号：`DU` + 物理子系统编号 + 三位序号。
     *
     * <p>与部署单元编号策略一致，使用 MySQL 命名锁串行化同一物理子系统的分配，
     * 行锁与 code 唯一索引兜底；序号永久占用不回收，容量检查先于递增。</p>
     */
    public String allocateNumber(long tenantId, long projectId, long physicalSubsystemId, String physicalCode) {
        String lockName = "delivery-unit-alloc-" + tenantId + "-" + projectId + "-" + physicalSubsystemId;
        Integer acquired = jdbc.queryForObject("SELECT GET_LOCK(?, 10)", Integer.class, lockName);
        if (acquired == null || acquired != 1) {
            throw new IllegalStateException("交付单元编号分配繁忙，请重试");
        }
        try {
            for (int attempt = 0; attempt < 3; attempt++) {
                Integer next = jdbc.query(
                        "SELECT next_ordinal FROM arch_delivery_unit_number_seq "
                                + "WHERE tenant_id = ? AND project_id = ? AND physical_subsystem_id = ? FOR UPDATE",
                        (rs, rowNum) -> rs.getInt("next_ordinal"), tenantId, projectId, physicalSubsystemId)
                        .stream().findFirst().orElse(null);
                if (next == null) {
                    jdbc.update("INSERT INTO arch_delivery_unit_number_seq "
                                    + "(tenant_id, project_id, physical_subsystem_id, next_ordinal) VALUES (?, ?, ?, 2)",
                            tenantId, projectId, physicalSubsystemId);
                    return String.format(Locale.ROOT, "DU%s%03d", physicalCode, 1);
                }
                if (next > MAX_ORDINAL_PER_PHYSICAL) {
                    throw new DeliveryUnitNumberCapacityExceededException(
                            "物理子系统 " + physicalCode + " 的交付单元编号容量已用尽（最多 "
                                    + MAX_ORDINAL_PER_PHYSICAL + " 个）");
                }
                int updated = jdbc.update("UPDATE arch_delivery_unit_number_seq SET next_ordinal = ? "
                                + "WHERE tenant_id = ? AND project_id = ? AND physical_subsystem_id = ?",
                        next + 1, tenantId, projectId, physicalSubsystemId);
                if (updated == 1) {
                    return String.format(Locale.ROOT, "DU%s%03d", physicalCode, next);
                }
            }
            throw new IllegalStateException("交付单元编号分配失败，请重试");
        } finally {
            jdbc.queryForObject("SELECT RELEASE_LOCK(?)", Integer.class, lockName);
        }
    }

    // ---------- 交付单元与部署单元的关联 ----------

    public List<DeploymentUnitRef> findDeploymentUnitsByIds(long tenantId, long projectId, Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Object> args = new ArrayList<>(List.of(tenantId, projectId));
        args.addAll(ids);
        String placeholders = String.join(", ", ids.stream().map(id -> "?").toList());
        return jdbc.query("SELECT id, code, name, kind, status, physical_subsystem_id FROM arch_deployment_unit "
                        + "WHERE tenant_id = ? AND project_id = ? AND id IN (" + placeholders + ")",
                DEPLOYMENT_UNIT_MAPPER, args.toArray());
    }

    public List<DeploymentUnitRef> findRelatedDeploymentUnits(long tenantId, long projectId, long deliveryUnitId) {
        return jdbc.query("SELECT unit.id, unit.code, unit.name, unit.kind, unit.status, unit.physical_subsystem_id "
                        + "FROM arch_delivery_unit_deployment_unit relation "
                        + "JOIN arch_deployment_unit unit "
                        + "  ON unit.tenant_id = relation.tenant_id AND unit.id = relation.deployment_unit_id "
                        + "WHERE relation.tenant_id = ? AND relation.project_id = ? AND relation.delivery_unit_id = ? "
                        + "ORDER BY unit.name, unit.id",
                DEPLOYMENT_UNIT_MAPPER, tenantId, projectId, deliveryUnitId);
    }

    /** 反向查询：某部署单元关联的交付单元，排除已软删除的交付单元。 */
    public List<DeliveryUnit> findRelatedDeliveryUnits(long tenantId, long projectId, long deploymentUnitId) {
        return jdbc.query("SELECT " + prefixColumns("unit", UNIT_COLUMNS)
                        + " FROM arch_delivery_unit_deployment_unit relation "
                        + "JOIN arch_delivery_unit unit "
                        + "  ON unit.tenant_id = relation.tenant_id AND unit.id = relation.delivery_unit_id "
                        + "WHERE relation.tenant_id = ? AND relation.project_id = ? "
                        + "  AND relation.deployment_unit_id = ? AND unit.deleted = 0 "
                        + "ORDER BY unit.name, unit.id",
                UNIT_MAPPER, tenantId, projectId, deploymentUnitId);
    }

    public boolean hasDeliveryUnitRelation(long tenantId, long projectId, long deploymentUnitId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM arch_delivery_unit_deployment_unit "
                        + "WHERE tenant_id = ? AND project_id = ? AND deployment_unit_id = ?",
                Long.class, tenantId, projectId, deploymentUnitId);
        return count != null && count > 0;
    }

    /** 租户维度关联检查；部署单元 ID 在租户内唯一，供作废引用守卫使用。 */
    public boolean hasDeliveryUnitRelationInTenant(long tenantId, long deploymentUnitId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM arch_delivery_unit_deployment_unit "
                        + "WHERE tenant_id = ? AND deployment_unit_id = ?",
                Long.class, tenantId, deploymentUnitId);
        return count != null && count > 0;
    }

    /** 按 ID 批量读取未删除的交付单元，用于关联校验。 */
    public List<DeliveryUnit> findDeliveryUnitsByIds(long tenantId, long projectId, Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Object> args = new ArrayList<>(List.of(tenantId, projectId));
        args.addAll(ids);
        String placeholders = String.join(", ", ids.stream().map(id -> "?").toList());
        return jdbc.query("SELECT " + UNIT_COLUMNS + " FROM arch_delivery_unit "
                        + "WHERE tenant_id = ? AND project_id = ? AND deleted = 0 AND id IN (" + placeholders + ")",
                UNIT_MAPPER, args.toArray());
    }

    /** 同物理子系统下的启用交付单元候选；用于部署单元侧的关联选择。 */
    public PageResult<DeliveryUnit> searchActiveOptions(long tenantId, long projectId, long physicalSubsystemId,
                                                        String keyword, PageQuery page) {
        PageQuery normalizedPage = page == null ? new PageQuery(1, 20) : page;
        StringBuilder filter = new StringBuilder();
        List<Object> args = new ArrayList<>(List.of(tenantId, projectId, physicalSubsystemId));
        addLike(filter, args, "name", keyword);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM arch_delivery_unit "
                        + "WHERE tenant_id = ? AND project_id = ? AND physical_subsystem_id = ? "
                        + "AND deleted = 0 AND status = 'ACTIVE'" + filter,
                Long.class, args.toArray());
        List<Object> listArgs = new ArrayList<>(args);
        listArgs.add(normalizedPage.size());
        listArgs.add((normalizedPage.page() - 1) * normalizedPage.size());
        List<DeliveryUnit> records = jdbc.query("SELECT " + UNIT_COLUMNS + " FROM arch_delivery_unit "
                        + "WHERE tenant_id = ? AND project_id = ? AND physical_subsystem_id = ? "
                        + "AND deleted = 0 AND status = 'ACTIVE'" + filter
                        + " ORDER BY name, id LIMIT ? OFFSET ?",
                UNIT_MAPPER, listArgs.toArray());
        return new PageResult<>(records, total == null ? 0 : total, normalizedPage.page(), normalizedPage.size());
    }

    /**
     * 从部署单元侧覆盖式替换关联集合；与交付单元侧写同一张关系表。
     * 只增删差集，不重写未变化的关系行。
     */
    public void replaceDeploymentUnitsFromDeploymentSide(long tenantId, long projectId, long physicalSubsystemId,
                                                        long deploymentUnitId, Set<Long> deliveryUnitIds,
                                                        long actorId) {
        Set<Long> desired = deliveryUnitIds == null ? Set.of() : new HashSet<>(deliveryUnitIds);
        Set<Long> current = new HashSet<>(jdbc.query(
                "SELECT delivery_unit_id FROM arch_delivery_unit_deployment_unit "
                        + "WHERE tenant_id = ? AND project_id = ? AND deployment_unit_id = ? FOR UPDATE",
                (rs, rowNum) -> rs.getLong("delivery_unit_id"), tenantId, projectId, deploymentUnitId));

        Set<Long> additions = new HashSet<>(desired);
        additions.removeAll(current);
        Set<Long> removals = new HashSet<>(current);
        removals.removeAll(desired);

        for (Long deliveryUnitId : additions.stream().sorted().toList()) {
            jdbc.update("""
                    INSERT INTO arch_delivery_unit_deployment_unit
                        (tenant_id, project_id, physical_subsystem_id, delivery_unit_id, deployment_unit_id, created_by)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, tenantId, projectId, physicalSubsystemId, deliveryUnitId, deploymentUnitId, actorId);
        }
        for (Long deliveryUnitId : removals.stream().sorted().toList()) {
            jdbc.update("DELETE FROM arch_delivery_unit_deployment_unit "
                            + "WHERE tenant_id = ? AND project_id = ? AND delivery_unit_id = ? AND deployment_unit_id = ?",
                    tenantId, projectId, deliveryUnitId, deploymentUnitId);
        }
    }

    /** 覆盖式替换关联集合：只增删差集，避免重写未变化的关系行。 */
    public void replaceDeploymentUnits(long tenantId, long projectId, long physicalSubsystemId, long deliveryUnitId,
                                       Set<Long> deploymentUnitIds, long actorId) {
        Set<Long> desired = deploymentUnitIds == null ? Set.of() : new HashSet<>(deploymentUnitIds);
        Set<Long> current = new HashSet<>(jdbc.query(
                "SELECT deployment_unit_id FROM arch_delivery_unit_deployment_unit "
                        + "WHERE tenant_id = ? AND project_id = ? AND delivery_unit_id = ? FOR UPDATE",
                (rs, rowNum) -> rs.getLong("deployment_unit_id"), tenantId, projectId, deliveryUnitId));

        Set<Long> additions = new HashSet<>(desired);
        additions.removeAll(current);
        Set<Long> removals = new HashSet<>(current);
        removals.removeAll(desired);

        for (Long deploymentUnitId : additions.stream().sorted().toList()) {
            jdbc.update("""
                    INSERT INTO arch_delivery_unit_deployment_unit
                        (tenant_id, project_id, physical_subsystem_id, delivery_unit_id, deployment_unit_id, created_by)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, tenantId, projectId, physicalSubsystemId, deliveryUnitId, deploymentUnitId, actorId);
        }
        for (Long deploymentUnitId : removals.stream().sorted().toList()) {
            jdbc.update("DELETE FROM arch_delivery_unit_deployment_unit "
                            + "WHERE tenant_id = ? AND project_id = ? AND delivery_unit_id = ? AND deployment_unit_id = ?",
                    tenantId, projectId, deliveryUnitId, deploymentUnitId);
        }
    }

    // ---------- 通用 ----------

    private void addLike(StringBuilder filter, List<Object> args, String column, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        filter.append(" AND ").append(column).append(" LIKE ? ESCAPE '\\\\'");
        args.add("%" + escapeLike(value.trim()) + "%");
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static String prefixColumns(String alias, String columns) {
        return columns.lines()
                .flatMap(line -> java.util.Arrays.stream(line.split(",")))
                .map(String::trim)
                .filter(column -> !column.isEmpty())
                .map(column -> alias + "." + column)
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private static LocalDateTime localDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }
}
