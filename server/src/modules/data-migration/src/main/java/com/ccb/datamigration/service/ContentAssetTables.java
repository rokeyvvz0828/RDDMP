package com.ccb.datamigration.service;

import java.util.List;
import java.util.stream.Stream;

/**
 * 内容一菜单一表的表名登记表（REQ-20260831-050）。
 * 文件型 7 张表共享 MD5 查重域；结构化 3 张表仅承载 structured_data。
 * 表名为模块内部常量，不接受外部输入拼接，杜绝注入面。
 */
public final class ContentAssetTables {
    public static final List<String> FILE_TABLES = List.of(
            "dm_plan", "dm_mapping_doc", "dm_dependency", "dm_script", "dm_topic", "dm_release_drill", "dm_report");
    public static final List<String> STRUCTURED_TABLES = List.of("dm_rule", "dm_parameter", "dm_intermediate_table");
    public static final List<String> ALL_TABLES = Stream.concat(FILE_TABLES.stream(), STRUCTURED_TABLES.stream()).toList();

    private ContentAssetTables() {}

    /** 资产类型 -> 内容表；未登记类型返回 null 由调用方拒绝。 */
    public static String tableFor(String assetType) {
        return switch (assetType) {
            case "PLAN" -> "dm_plan";
            case "MAPPING_DOC" -> "dm_mapping_doc";
            case "DEPENDENCY" -> "dm_dependency";
            case "SCRIPT" -> "dm_script";
            case "TOPIC" -> "dm_topic";
            case "RELEASE_DRILL" -> "dm_release_drill";
            case "REPORT" -> "dm_report";
            case "RULE" -> "dm_rule";
            case "PARAMETER" -> "dm_parameter";
            case "INTERMEDIATE_TABLE" -> "dm_intermediate_table";
            default -> null;
        };
    }

    /** 内容表 -> 资产类型标签（看板与回收站保持原 asset_type 语义）。 */
    public static String typeFor(String table) {
        return switch (table) {
            case "dm_plan" -> "PLAN";
            case "dm_mapping_doc" -> "MAPPING_DOC";
            case "dm_dependency" -> "DEPENDENCY";
            case "dm_script" -> "SCRIPT";
            case "dm_topic" -> "TOPIC";
            case "dm_release_drill" -> "RELEASE_DRILL";
            case "dm_report" -> "REPORT";
            case "dm_rule" -> "RULE";
            case "dm_parameter" -> "PARAMETER";
            case "dm_intermediate_table" -> "INTERMEDIATE_TABLE";
            default -> null;
        };
    }

    /** 跨 7 张文件表按 MD5 统计活动记录，附来源表标签；参数依次为 tenant、md5。 */
    public static String md5UnionSql() {
        StringBuilder sql = new StringBuilder();
        for (String table : FILE_TABLES) {
            if (sql.length() > 0) sql.append(" UNION ALL ");
            sql.append("SELECT '").append(typeFor(table)).append("' AS asset_type, id FROM ").append(table)
               .append(" WHERE tenant_id = ? AND checksum_md5 = ? AND deleted = 0");
        }
        return sql.toString();
    }

    /** 与 md5UnionSql 配对的位置参数：每张文件表一组 (tenant_id, checksum_md5)，共 2×FILE_TABLES.length 个。 */
    public static Object[] md5UnionArgs(long tenantId, String checksumMd5) {
        Object[] args = new Object[FILE_TABLES.size() * 2];
        for (int i = 0; i < FILE_TABLES.size(); i++) {
            args[i * 2] = tenantId;
            args[i * 2 + 1] = checksumMd5;
        }
        return args;
    }

    /** 跨内容表的活动行计数 UNION；占位符为每个表一组 args。 */
    public static String activeCountUnionSql(String wherePerTable, List<String> tables) {
        StringBuilder sql = new StringBuilder();
        for (String table : tables) {
            if (sql.length() > 0) sql.append(" UNION ALL ");
            sql.append("SELECT COUNT(*) AS cnt FROM ").append(table).append(" WHERE ").append(wherePerTable);
        }
        return sql.toString();
    }

    /** 跨内容表按类型分组计数 UNION，列为 type/total；占位符为每个表一组 args。 */
    public static String typeCountUnionSql(String wherePerTable, List<String> tables) {
        StringBuilder sql = new StringBuilder();
        for (String table : tables) {
            if (sql.length() > 0) sql.append(" UNION ALL ");
            sql.append("SELECT '").append(typeFor(table)).append("' AS type, COUNT(*) AS total FROM ")
               .append(table).append(" WHERE ").append(wherePerTable);
        }
        return sql.toString();
    }

    /** 跨内容表按 (tenant_id, project_id) 分组的活动行计数 UNION，列为 tenant_id/project_id/cnt。 */
    public static String tenantProjectCountUnionSql(List<String> tables) {
        StringBuilder sql = new StringBuilder();
        for (String table : tables) {
            if (sql.length() > 0) sql.append(" UNION ALL ");
            sql.append("SELECT tenant_id, project_id, COUNT(*) AS cnt FROM ").append(table)
               .append(" WHERE deleted = 0 GROUP BY tenant_id, project_id");
        }
        return sql.toString();
    }

    /** 关联到外层实体（别名 outerAlias）的跨内容表活动行计数之和表达式；占位符为每个表一组 args。 */
    public static String correlatedCountSumSql(String outerAlias, String joinColumn, String wherePerTable, List<String> tables) {
        StringBuilder sql = new StringBuilder();
        for (String table : tables) {
            if (sql.length() > 0) sql.append(" + ");
            sql.append("(SELECT COUNT(*) FROM ").append(table).append(" ca WHERE ca.").append(joinColumn)
               .append(" = ").append(outerAlias).append(".id AND ").append(wherePerTable).append(")");
        }
        return sql.toString();
    }
}
