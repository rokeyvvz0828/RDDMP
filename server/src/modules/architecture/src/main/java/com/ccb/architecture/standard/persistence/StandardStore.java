package com.ccb.architecture.standard.persistence;

import com.ccb.architecture.standard.model.StandardModels.DocumentStatus;
import com.ccb.architecture.standard.model.StandardModels.StandardCommand;
import com.ccb.architecture.standard.model.StandardModels.StandardDocument;
import com.ccb.architecture.standard.model.StandardModels.StandardQuery;
import com.ccb.architecture.standard.model.StandardModels.StandardVersion;
import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 架构规范文档数据访问：主记录、版本快照与发布事务。 */
@Repository
public class StandardStore {

    private static final String DOCUMENT_COLUMNS = """
            id, tenant_id, title, category_code, summary, content, status, current_version,
            published_at, published_by, published_by_name, row_version,
            created_by, created_by_name, created_at, updated_at
            """;
    private static final String VERSION_COLUMNS = """
            id, tenant_id, document_id, version_no, title, category_code, summary, content,
            published_at, published_by, published_by_name
            """;

    private static final RowMapper<StandardDocument> DOCUMENT_MAPPER = (rs, rowNum) -> new StandardDocument(
            rs.getLong("id"), rs.getLong("tenant_id"), rs.getString("title"),
            rs.getString("category_code"), rs.getString("summary"), rs.getString("content"),
            DocumentStatus.valueOf(rs.getString("status")), rs.getInt("current_version"),
            localDateTime(rs.getTimestamp("published_at")), nullableLong(rs, "published_by"),
            rs.getString("published_by_name"), rs.getLong("row_version"),
            rs.getLong("created_by"), rs.getString("created_by_name"),
            localDateTime(rs.getTimestamp("created_at")), localDateTime(rs.getTimestamp("updated_at")));

    private static final RowMapper<StandardVersion> VERSION_MAPPER = (rs, rowNum) -> new StandardVersion(
            rs.getLong("id"), rs.getLong("tenant_id"), rs.getLong("document_id"),
            rs.getInt("version_no"), rs.getString("title"), rs.getString("category_code"),
            rs.getString("summary"), rs.getString("content"),
            localDateTime(rs.getTimestamp("published_at")), rs.getLong("published_by"),
            rs.getString("published_by_name"));

    private final JdbcTemplate jdbc;

    public StandardStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PageResult<StandardDocument> pageDocuments(long tenantId, PageQuery page, StandardQuery query) {
        PageQuery normalizedPage = page == null ? new PageQuery(1, 20) : page;
        StandardQuery normalized = query == null ? StandardQuery.empty() : query;
        StringBuilder filter = new StringBuilder();
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        addLike(filter, args, "title", normalized.title());
        if (normalized.categoryCode() != null) {
            filter.append(" AND category_code = ?");
            args.add(normalized.categoryCode());
        }
        if (normalized.status() != null) {
            filter.append(" AND status = ?");
            args.add(normalized.status());
        }
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM arch_standard_document WHERE tenant_id = ? AND deleted = 0" + filter,
                Long.class, args.toArray());
        List<Object> listArgs = new ArrayList<>(args);
        listArgs.add(normalizedPage.size());
        listArgs.add((normalizedPage.page() - 1) * normalizedPage.size());
        List<StandardDocument> records = jdbc.query(
                "SELECT " + DOCUMENT_COLUMNS + " FROM arch_standard_document"
                        + " WHERE tenant_id = ? AND deleted = 0" + filter
                        + " ORDER BY updated_at DESC, id DESC LIMIT ? OFFSET ?",
                DOCUMENT_MAPPER, listArgs.toArray());
        return new PageResult<>(records, total == null ? 0 : total, normalizedPage.page(), normalizedPage.size());
    }

    public Optional<StandardDocument> findDocument(long tenantId, long id) {
        return jdbc.query(
                "SELECT " + DOCUMENT_COLUMNS + " FROM arch_standard_document"
                        + " WHERE tenant_id = ? AND id = ? AND deleted = 0",
                DOCUMENT_MAPPER, tenantId, id).stream().findFirst();
    }

    /** 乐观锁读取：行版本不一致返回 empty。 */
    public Optional<StandardDocument> lockDocument(long tenantId, long id, long expectedRowVersion) {
        List<StandardDocument> rows = jdbc.query(
                "SELECT " + DOCUMENT_COLUMNS + " FROM arch_standard_document"
                        + " WHERE tenant_id = ? AND id = ? AND deleted = 0 AND row_version = ? FOR UPDATE",
                DOCUMENT_MAPPER, tenantId, id, expectedRowVersion);
        return rows.stream().findFirst();
    }

    public long createDocument(long id, long tenantId, StandardCommand command,
                               long operatorId, String operatorName) {
        jdbc.update("""
                INSERT INTO arch_standard_document
                    (id, tenant_id, title, category_code, summary, content, status, current_version,
                     row_version, created_by, created_by_name, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, 'DRAFT', 0, 0, ?, ?, ?)
                """, id, tenantId, command.title(), command.categoryCode(), command.summary(),
                command.content(), operatorId, operatorName, operatorId);
        return id;
    }

    public void updateDocument(long tenantId, long id, long expectedRowVersion, StandardCommand command,
                               long operatorId) {
        int updated = jdbc.update("""
                UPDATE arch_standard_document
                SET title = ?, category_code = ?, summary = ?, content = ?,
                    row_version = row_version + 1, updated_by = ?
                WHERE tenant_id = ? AND id = ? AND deleted = 0 AND row_version = ?
                """, command.title(), command.categoryCode(), command.summary(), command.content(),
                operatorId, tenantId, id, expectedRowVersion);
        if (updated != 1) {
            throw new IllegalStateException("架构规范文档行版本冲突");
        }
    }

    /** 发布：状态、版本与发布信息在同一事务更新，并追加不可变版本快照。 */
    public StandardVersion publish(long tenantId, long id, long expectedRowVersion,
                                   long operatorId, String operatorName) {
        StandardDocument document = lockDocument(tenantId, id, expectedRowVersion)
                .orElseThrow(() -> new IllegalStateException("架构规范文档不存在或行版本冲突"));
        if (document.status() != DocumentStatus.DRAFT && document.status() != DocumentStatus.OFFLINE) {
            throw new IllegalStateException("只有草稿或已下线文档可以发布");
        }
        int nextVersion = document.currentVersion() + 1;
        long snapshotId = System.currentTimeMillis() * 1_000 + (id % 1_000);
        jdbc.update("""
                UPDATE arch_standard_document
                SET status = 'PUBLISHED', current_version = ?, published_at = NOW(3),
                    published_by = ?, published_by_name = ?, row_version = row_version + 1,
                    updated_by = ?
                WHERE tenant_id = ? AND id = ? AND deleted = 0 AND row_version = ?
                """, nextVersion, operatorId, operatorName, operatorId,
                tenantId, id, expectedRowVersion);
        jdbc.update("""
                INSERT INTO arch_standard_document_version
                    (id, tenant_id, document_id, version_no, title, category_code, summary, content,
                     published_at, published_by, published_by_name)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(3), ?, ?)
                """, snapshotId, tenantId, id, nextVersion, document.title(), document.categoryCode(),
                document.summary(), document.content(), operatorId, operatorName);
        return new StandardVersion(snapshotId, tenantId, id, nextVersion, document.title(),
                document.categoryCode(), document.summary(), document.content(),
                LocalDateTime.now(), operatorId, operatorName);
    }

    /** 下线：已发布文档进入 OFFLINE，保留历史快照。 */
    public void offline(long tenantId, long id, long expectedRowVersion, long operatorId) {
        int updated = jdbc.update("""
                UPDATE arch_standard_document
                SET status = 'OFFLINE', row_version = row_version + 1, updated_by = ?
                WHERE tenant_id = ? AND id = ? AND deleted = 0 AND status = 'PUBLISHED' AND row_version = ?
                """, operatorId, tenantId, id, expectedRowVersion);
        if (updated != 1) {
            throw new IllegalStateException("架构规范文档不是已发布状态或行版本冲突");
        }
    }

    /** 删除仅允许从未发布的草稿。 */
    public void deleteDraft(long tenantId, long id, long expectedRowVersion, long operatorId) {
        int updated = jdbc.update("""
                UPDATE arch_standard_document
                SET deleted = 1, row_version = row_version + 1, updated_by = ?
                WHERE tenant_id = ? AND id = ? AND deleted = 0 AND status = 'DRAFT' AND row_version = ?
                """, operatorId, tenantId, id, expectedRowVersion);
        if (updated != 1) {
            throw new IllegalStateException("只有未发布的草稿可以删除");
        }
    }

    public List<StandardVersion> listVersions(long tenantId, long documentId) {
        return jdbc.query("""
                SELECT %s FROM arch_standard_document_version
                WHERE tenant_id = ? AND document_id = ?
                ORDER BY version_no DESC
                """.formatted(VERSION_COLUMNS), VERSION_MAPPER, tenantId, documentId);
    }

    private void addLike(StringBuilder filter, List<Object> args, String column, String value) {
        if (value != null && !value.isBlank()) {
            filter.append(" AND ").append(column).append(" LIKE ?");
            args.add("%" + value.trim() + "%");
        }
    }

    private static LocalDateTime localDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private static Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
