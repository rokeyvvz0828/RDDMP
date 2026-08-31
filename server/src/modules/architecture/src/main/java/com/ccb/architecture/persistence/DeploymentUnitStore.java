package com.ccb.architecture.persistence;

import com.ccb.architecture.model.DeploymentUnitModels.DeploymentUnit;
import com.ccb.architecture.model.DeploymentUnitModels.DeploymentUnitImportBatch;
import com.ccb.architecture.model.DeploymentUnitModels.DeploymentUnitImportItem;
import com.ccb.architecture.model.DeploymentUnitModels.DeploymentUnitQuery;
import com.ccb.architecture.model.DeploymentUnitModels.DeploymentUnitVersion;
import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 部署单元数据访问：主记录、版本、编号分配与导入批次/明细。
 * 编号分配必须运行在事务内，通过物理子系统级行锁保证并发不重号。
 */
@Repository
public class DeploymentUnitStore {
    public static final int MAX_ORDINAL_PER_PHYSICAL = 999;
    public static final int MAX_IMPORT_ROWS = 5000;

    private static final String UNIT_COLUMNS = """
            id, code, physical_subsystem_id, short_name, name, related_deployment_unit_name,
            deployment_unit_type, kind, status, current_version,
            default_network_zone_id, default_network_zone_name,
            description, remark, created_by, updated_by, created_at, updated_at, row_version
            """;
    private static final String VERSION_COLUMNS = """
            id, unit_id, version_no, short_name, name, related_deployment_unit_name, deployment_unit_type,
            kind, default_network_zone_id, default_network_zone_name, description, remark, published_by, published_at
            """;
    private static final String BATCH_COLUMNS = """
            id, file_name, file_size, total_rows, valid_rows, success_rows, failed_rows, skipped_rows,
            status, error_message, created_by, created_at, completed_at
            """;
    private static final String ITEM_COLUMNS = """
            id, batch_id, line_no, raw_json, row_status, error_message, note, unit_id, created_at
            """;

    private static final RowMapper<DeploymentUnit> UNIT_MAPPER = (rs, rowNum) -> new DeploymentUnit(
            rs.getLong("id"), rs.getString("code"), rs.getLong("physical_subsystem_id"),
            rs.getString("short_name"), rs.getString("name"), rs.getString("related_deployment_unit_name"),
            rs.getString("deployment_unit_type"), rs.getString("kind"),
            nullableLong(rs, "default_network_zone_id"), rs.getString("default_network_zone_name"),
            rs.getString("status"), rs.getInt("current_version"), rs.getString("description"), rs.getString("remark"),
            rs.getLong("created_by"), rs.getLong("updated_by"),
            localDateTime(rs.getTimestamp("created_at")), localDateTime(rs.getTimestamp("updated_at")),
            rs.getLong("row_version"));

    private static final RowMapper<DeploymentUnitVersion> VERSION_MAPPER = (rs, rowNum) -> new DeploymentUnitVersion(
            rs.getLong("id"), rs.getLong("unit_id"), rs.getInt("version_no"),
            rs.getString("short_name"), rs.getString("name"), rs.getString("related_deployment_unit_name"),
            rs.getString("deployment_unit_type"), rs.getString("kind"),
            nullableLong(rs, "default_network_zone_id"), rs.getString("default_network_zone_name"),
            rs.getString("description"), rs.getString("remark"), rs.getLong("published_by"),
            localDateTime(rs.getTimestamp("published_at")));

    private static final RowMapper<DeploymentUnitImportBatch> BATCH_MAPPER = (rs, rowNum) ->
            new DeploymentUnitImportBatch(
                    rs.getLong("id"), rs.getString("file_name"), rs.getLong("file_size"),
                    rs.getInt("total_rows"), rs.getInt("valid_rows"), rs.getInt("success_rows"),
                    rs.getInt("failed_rows"), rs.getInt("skipped_rows"), rs.getString("status"),
                    rs.getString("error_message"), rs.getLong("created_by"),
                    localDateTime(rs.getTimestamp("created_at")), localDateTime(rs.getTimestamp("completed_at")));

    private static final RowMapper<DeploymentUnitImportItem> ITEM_MAPPER = (rs, rowNum) ->
            new DeploymentUnitImportItem(
                    rs.getLong("id"), rs.getLong("batch_id"), rs.getInt("line_no"), rs.getString("raw_json"),
                    rs.getString("row_status"), rs.getString("error_message"), rs.getString("note"),
                    nullableLong(rs, "unit_id"), localDateTime(rs.getTimestamp("created_at")));

    /** 物理子系统引用投影，用于关系校验与编号生成。 */
    public record PhysicalSubsystemRef(long id, String code, String name, String status, boolean deleted) {
    }

    private final JdbcTemplate jdbc;

    public DeploymentUnitStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---------- 部署单元主记录 ----------

    public PageResult<DeploymentUnit> pageUnits(long tenantId, PageQuery page, DeploymentUnitQuery query) {
        PageQuery normalizedPage = page == null ? new PageQuery(1, 20) : page;
        DeploymentUnitQuery normalized = query == null ? DeploymentUnitQuery.empty() : query;
        StringBuilder filter = new StringBuilder();
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        addLike(filter, args, "code", normalized.code());
        addLike(filter, args, "short_name", normalized.shortName());
        addLike(filter, args, "name", normalized.name());
        if (normalized.physicalSubsystemId() != null) {
            filter.append(" AND physical_subsystem_id = ?");
            args.add(normalized.physicalSubsystemId());
        }
        if (normalized.kind() != null) {
            filter.append(" AND kind = ?");
            args.add(normalized.kind());
        }
        if (normalized.status() != null) {
            filter.append(" AND status = ?");
            args.add(normalized.status());
        }
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM arch_deployment_unit WHERE tenant_id = ?" + filter, Long.class, args.toArray());
        List<Object> listArgs = new ArrayList<>(args);
        listArgs.add(normalizedPage.size());
        listArgs.add((normalizedPage.page() - 1) * normalizedPage.size());
        List<DeploymentUnit> records = jdbc.query(
                "SELECT " + UNIT_COLUMNS + " FROM arch_deployment_unit WHERE tenant_id = ?" + filter
                        + " ORDER BY id DESC LIMIT ? OFFSET ?",
                UNIT_MAPPER, listArgs.toArray());
        return new PageResult<>(records, total == null ? 0 : total, normalizedPage.page(), normalizedPage.size());
    }

    public Optional<DeploymentUnit> findUnit(long tenantId, long id) {
        return jdbc.query("SELECT " + UNIT_COLUMNS + " FROM arch_deployment_unit WHERE tenant_id = ? AND id = ?",
                UNIT_MAPPER, tenantId, id).stream().findFirst();
    }

    /** 事务内锁读主记录，用于状态迁移与版本发布。 */
    public Optional<DeploymentUnit> lockUnit(long tenantId, long id) {
        return jdbc.query("SELECT " + UNIT_COLUMNS + " FROM arch_deployment_unit "
                        + "WHERE tenant_id = ? AND id = ? FOR UPDATE",
                UNIT_MAPPER, tenantId, id).stream().findFirst();
    }

    public Optional<DeploymentUnit> findUnitByPhysicalAndName(long tenantId, long physicalSubsystemId, String name) {
        return jdbc.query("SELECT " + UNIT_COLUMNS + " FROM arch_deployment_unit "
                        + "WHERE tenant_id = ? AND physical_subsystem_id = ? AND name = ?",
                UNIT_MAPPER, tenantId, physicalSubsystemId, name).stream().findFirst();
    }

    public boolean unitNameExists(long tenantId, long physicalSubsystemId, String name, Long excludeUnitId) {
        String exclude = excludeUnitId == null ? "" : " AND id <> ?";
        List<Object> args = new ArrayList<>(List.of(tenantId, physicalSubsystemId, name));
        if (excludeUnitId != null) {
            args.add(excludeUnitId);
        }
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM arch_deployment_unit WHERE tenant_id = ? AND physical_subsystem_id = ?"
                        + " AND name = ?" + exclude,
                Long.class, args.toArray());
        return count != null && count > 0;
    }

    public void insertUnit(long id, long tenantId, String code, long physicalSubsystemId, String shortName,
                           String name, String relatedDeploymentUnitName, String deploymentUnitType,
                           String kind, Long defaultNetworkZoneId, String defaultNetworkZoneName,
                           String description, String remark, long actorId) {
        jdbc.update("""
                INSERT INTO arch_deployment_unit
                    (id, tenant_id, code, physical_subsystem_id, short_name, name,
                     related_deployment_unit_name, deployment_unit_type, kind, status,
                     current_version, default_network_zone_id, default_network_zone_name,
                     description, remark, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', 1, ?, ?, ?, ?, ?, ?)
                """, id, tenantId, code, physicalSubsystemId, shortName, name,
                relatedDeploymentUnitName, deploymentUnitType, kind,
                defaultNetworkZoneId, defaultNetworkZoneName, description, remark, actorId, actorId);
    }

    public void insertUnit(long id, long tenantId, String code, long physicalSubsystemId, String shortName,
                           String name, String kind, String description, String remark, long actorId) {
        insertUnit(id, tenantId, code, physicalSubsystemId, shortName, name, null,
                defaultDeploymentUnitType(kind), kind, null, null, description, remark, actorId);
    }

    public void insertUnit(long id, long tenantId, String code, long physicalSubsystemId, String shortName,
                           String name, String relatedDeploymentUnitName, String deploymentUnitType,
                           String kind, String description, String remark, long actorId) {
        insertUnit(id, tenantId, code, physicalSubsystemId, shortName, name, relatedDeploymentUnitName,
                deploymentUnitType, kind, null, null, description, remark, actorId);
    }

    /** 乐观锁更新展示内容；返回 0 表示版本冲突或状态不允许。 */
    public int updateUnitContent(long tenantId, long id, long expectedRowVersion, String shortName, String name,
                                 String relatedDeploymentUnitName, String deploymentUnitType,
                                 String kind, Long defaultNetworkZoneId, String defaultNetworkZoneName,
                                 String description, String remark, long actorId) {
        return jdbc.update("""
                UPDATE arch_deployment_unit
                SET short_name = ?, name = ?, related_deployment_unit_name = ?, deployment_unit_type = ?,
                    kind = ?, default_network_zone_id = ?, default_network_zone_name = ?,
                    description = ?, remark = ?, updated_by = ?,
                    row_version = row_version + 1
                WHERE tenant_id = ? AND id = ? AND status = 'ACTIVE' AND row_version = ?
                """, shortName, name, relatedDeploymentUnitName, deploymentUnitType, kind,
                defaultNetworkZoneId, defaultNetworkZoneName, description, remark, actorId,
                tenantId, id, expectedRowVersion);
    }

    public int updateUnitContent(long tenantId, long id, long expectedRowVersion, String shortName, String name,
                                 String kind, String description, String remark, long actorId) {
        return updateUnitContent(tenantId, id, expectedRowVersion, shortName, name, null,
                defaultDeploymentUnitType(kind), kind, null, null, description, remark, actorId);
    }

    public int updateUnitContent(long tenantId, long id, long expectedRowVersion, String shortName, String name,
                                 String relatedDeploymentUnitName, String deploymentUnitType,
                                 String kind, String description, String remark, long actorId) {
        return updateUnitContent(tenantId, id, expectedRowVersion, shortName, name,
                relatedDeploymentUnitName, deploymentUnitType, kind, null, null, description, remark, actorId);
    }

    /** 状态迁移；返回 0 表示状态不允许或已变更。 */
    public int updateUnitStatus(long tenantId, long id, String fromStatus, String toStatus, long actorId) {
        return jdbc.update("""
                UPDATE arch_deployment_unit
                SET status = ?, updated_by = ?, row_version = row_version + 1
                WHERE tenant_id = ? AND id = ? AND status = ?
                """, toStatus, actorId, tenantId, id, fromStatus);
    }

    public void updateUnitCurrentVersion(long tenantId, long id, int versionNo, long actorId) {
        jdbc.update("UPDATE arch_deployment_unit SET current_version = ?, updated_by = ? "
                + "WHERE tenant_id = ? AND id = ?", versionNo, actorId, tenantId, id);
    }

    // ---------- 编号分配 ----------

    /** 物理子系统投影（含 deleted，用于区分普通无效与并发删除）。 */
    public Optional<PhysicalSubsystemRef> findPhysical(long tenantId, long physicalSubsystemId) {
        return jdbc.query("SELECT id, code, name, status, deleted FROM arch_physical_subsystem "
                        + "WHERE tenant_id = ? AND id = ?",
                (rs, rowNum) -> new PhysicalSubsystemRef(rs.getLong("id"), rs.getString("code"),
                        rs.getString("name"), rs.getString("status"), rs.getBoolean("deleted")),
                tenantId, physicalSubsystemId).stream().findFirst();
    }

    public Optional<PhysicalSubsystemRef> findPhysicalByCode(long tenantId, String code) {
        return jdbc.query("SELECT id, code, name, status, deleted FROM arch_physical_subsystem "
                        + "WHERE tenant_id = ? AND code = ?",
                (rs, rowNum) -> new PhysicalSubsystemRef(rs.getLong("id"), rs.getString("code"),
                        rs.getString("name"), rs.getString("status"), rs.getBoolean("deleted")),
                tenantId, code).stream().findFirst();
    }

    /**
     * 事务内分配部署单元编号：`D` + 物理子系统编号 + 三位序号。
     *
     * <p>使用 MySQL 命名锁（GET_LOCK）在连接上串行化同一物理子系统的分配，避免并发
     * 首行 INSERT 与 FOR UPDATE 的死锁；行锁与 code 唯一索引兜底。序号永久占用不回收，
     * 容量检查先于递增，避免 CHECK 约束被违反。</p>
     */
    public String allocateNumber(long tenantId, long physicalSubsystemId, String physicalCode) {
        String lockName = "du-alloc-" + tenantId + "-" + physicalSubsystemId;
        Integer acquired = jdbc.queryForObject("SELECT GET_LOCK(?, 10)", Integer.class, lockName);
        if (acquired == null || acquired != 1) {
            throw new IllegalStateException("部署单元编号分配繁忙，请重试");
        }
        try {
            for (int attempt = 0; attempt < 3; attempt++) {
                Integer next = jdbc.query(
                        "SELECT next_ordinal FROM arch_deployment_unit_number_seq "
                                + "WHERE tenant_id = ? AND physical_subsystem_id = ? FOR UPDATE",
                        (rs, rowNum) -> rs.getInt("next_ordinal"), tenantId, physicalSubsystemId)
                        .stream().findFirst().orElse(null);
                if (next == null) {
                    jdbc.update("INSERT INTO arch_deployment_unit_number_seq "
                            + "(tenant_id, physical_subsystem_id, next_ordinal) VALUES (?, ?, 2)",
                            tenantId, physicalSubsystemId);
                    return String.format(Locale.ROOT, "D%s%03d", physicalCode, 1);
                }
                if (next > MAX_ORDINAL_PER_PHYSICAL) {
                    throw new DeploymentUnitNumberCapacityExceededException(
                            "物理子系统 " + physicalCode + " 的部署单元编号容量已用尽（最多 "
                                    + MAX_ORDINAL_PER_PHYSICAL + " 个）");
                }
                int updated = jdbc.update("UPDATE arch_deployment_unit_number_seq SET next_ordinal = ? "
                        + "WHERE tenant_id = ? AND physical_subsystem_id = ?",
                        next + 1, tenantId, physicalSubsystemId);
                if (updated == 1) {
                    return String.format(Locale.ROOT, "D%s%03d", physicalCode, next);
                }
            }
            throw new IllegalStateException("部署单元编号分配失败，请重试");
        } finally {
            jdbc.queryForObject("SELECT RELEASE_LOCK(?)", Integer.class, lockName);
        }
    }

    // ---------- 版本 ----------

    public void insertVersion(long id, long tenantId, long unitId, int versionNo, String shortName, String name,
                              String relatedDeploymentUnitName, String deploymentUnitType,
                              String kind, Long defaultNetworkZoneId, String defaultNetworkZoneName,
                              String description, String remark, long actorId) {
        jdbc.update("""
                INSERT INTO arch_deployment_unit_version
                    (id, tenant_id, unit_id, version_no, short_name, name, related_deployment_unit_name,
                     deployment_unit_type, kind, default_network_zone_id, default_network_zone_name,
                     description, remark, published_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, tenantId, unitId, versionNo, shortName, name, relatedDeploymentUnitName,
                deploymentUnitType, kind, defaultNetworkZoneId, defaultNetworkZoneName,
                description, remark, actorId);
    }

    public void insertVersion(long id, long tenantId, long unitId, int versionNo, String shortName, String name,
                              String kind, String description, String remark, long actorId) {
        insertVersion(id, tenantId, unitId, versionNo, shortName, name, null,
                defaultDeploymentUnitType(kind), kind, null, null, description, remark, actorId);
    }

    public void insertVersion(long id, long tenantId, long unitId, int versionNo, String shortName, String name,
                              String relatedDeploymentUnitName, String deploymentUnitType,
                              String kind, String description, String remark, long actorId) {
        insertVersion(id, tenantId, unitId, versionNo, shortName, name, relatedDeploymentUnitName,
                deploymentUnitType, kind, null, null, description, remark, actorId);
    }

    public List<DeploymentUnitVersion> findVersions(long tenantId, long unitId) {
        return jdbc.query("SELECT " + VERSION_COLUMNS + " FROM arch_deployment_unit_version "
                        + "WHERE tenant_id = ? AND unit_id = ? ORDER BY version_no ASC",
                VERSION_MAPPER, tenantId, unitId);
    }

    public int countVersions(long tenantId, long unitId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM arch_deployment_unit_version "
                + "WHERE tenant_id = ? AND unit_id = ?", Long.class, tenantId, unitId);
        return count == null ? 0 : count.intValue();
    }

    // ---------- 导入批次与明细 ----------

    public void insertBatch(long id, long tenantId, String fileName, long fileSize, int totalRows, int validRows,
                            long actorId) {
        jdbc.update("""
                INSERT INTO arch_deployment_unit_import_batch
                    (id, tenant_id, file_name, file_size, total_rows, valid_rows, status, created_by)
                VALUES (?, ?, ?, ?, ?, ?, 'PREVIEW', ?)
                """, id, tenantId, fileName, fileSize, totalRows, validRows, actorId);
    }

    public void insertItem(long id, long tenantId, long batchId, int lineNo, String rawJson, String rowStatus,
                           String errorMessage, String note, Long unitId) {
        jdbc.update("""
                INSERT INTO arch_deployment_unit_import_item
                    (id, tenant_id, batch_id, line_no, raw_json, row_status, error_message, note, unit_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, tenantId, batchId, lineNo, rawJson, rowStatus, errorMessage, note, unitId);
    }

    public void updateItemResult(long tenantId, long itemId, String rowStatus, String errorMessage, String note,
                                 Long unitId) {
        jdbc.update("""
                UPDATE arch_deployment_unit_import_item
                SET row_status = ?, error_message = ?, note = ?, unit_id = ?
                WHERE tenant_id = ? AND id = ?
                """, rowStatus, errorMessage, note, unitId, tenantId, itemId);
    }

    public void updateBatchResult(long tenantId, long batchId, String status, int successRows, int failedRows,
                                  int skippedRows, String errorMessage) {
        jdbc.update("""
                UPDATE arch_deployment_unit_import_batch
                SET status = ?, success_rows = ?, failed_rows = ?, skipped_rows = ?, error_message = ?,
                    completed_at = CURRENT_TIMESTAMP
                WHERE tenant_id = ? AND id = ?
                """, status, successRows, failedRows, skippedRows, errorMessage, tenantId, batchId);
    }

    public Optional<DeploymentUnitImportBatch> findBatch(long tenantId, long batchId) {
        return jdbc.query("SELECT " + BATCH_COLUMNS + " FROM arch_deployment_unit_import_batch "
                        + "WHERE tenant_id = ? AND id = ?",
                BATCH_MAPPER, tenantId, batchId).stream().findFirst();
    }

    public PageResult<DeploymentUnitImportBatch> pageBatches(long tenantId, PageQuery page) {
        PageQuery normalizedPage = page == null ? new PageQuery(1, 20) : page;
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM arch_deployment_unit_import_batch WHERE tenant_id = ?",
                Long.class, tenantId);
        List<DeploymentUnitImportBatch> records = jdbc.query(
                "SELECT " + BATCH_COLUMNS + " FROM arch_deployment_unit_import_batch WHERE tenant_id = ? "
                        + "ORDER BY id DESC LIMIT ? OFFSET ?",
                BATCH_MAPPER, tenantId, normalizedPage.size(),
                (normalizedPage.page() - 1) * normalizedPage.size());
        return new PageResult<>(records, total == null ? 0 : total, normalizedPage.page(), normalizedPage.size());
    }

    public List<DeploymentUnitImportItem> findItems(long tenantId, long batchId, int limit) {
        return jdbc.query("SELECT " + ITEM_COLUMNS + " FROM arch_deployment_unit_import_item "
                        + "WHERE tenant_id = ? AND batch_id = ? ORDER BY line_no ASC LIMIT ?",
                ITEM_MAPPER, tenantId, batchId, limit);
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

    private String defaultDeploymentUnitType(String kind) {
        return "DATABASE".equalsIgnoreCase(kind) ? "DB" : "AP";
    }

    private static Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static LocalDateTime localDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }
}
