package com.ccb.datamigration.service;

import com.ccb.security.model.AuthUser;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 统一回收站来源契约（REQ-20260820-031 演进：回收站可扩展化）。
 *
 * <p>每一类软删内容表由一个 Provider 实现，负责自身的列表、恢复与彻底删除，并保留其业务规则
 * （编码/MD5 唯一性、关系级联、附件绑定/解绑、审计）。统一入口
 * {@link ContentRecycleBinService} 仅按内容类型薄分发，不枚举业务字段。
 *
 * <p>设计意图：未来各菜单功能增加字段或新增关联表时，改动只落在对应来源实现内部，统一回收站
 * 与列表信封无需回改；新增一类内容只需实现并注册本接口，主流程零改动。
 */
public interface RecycleBinSource {

    /** 该来源认领的内容类型标签（如 {@code PLAN}、{@code REPORT}、{@code RULE}）。 */
    Set<String> supports();

    /**
     * 该来源软删记录的总数（原生分页 {@code total} 组成部分）；实现应在 SQL 层用 {@code COUNT(*)} 统计，
     * 不拉取明细行。{@code keyword} 为空表示不限关键字。
     *
     * <p>T32：{@code projectId} 为必填的项目隔离范围，实现必须在 SQL 层恒定附加 {@code project_id = ?}，
     * 不得回退为全项目统计。
     */
    long countDeleted(String type, long projectId, String keyword, AuthUser user);

    /**
     * 该来源软删列表的“原生分页取数”：在 SQL 层按统一信封业务编号 {@code asset_code}（即各自的
     * doc_code/meeting_code）升序排序，并 {@code LIMIT limit} 只取前 {@code limit} 行，返回公共信封列
     * （asset_type/asset_code/asset_name/deleted_by/deleted_at 等），允许携带类型专属附加列。
     *
     * <p>统一入口 {@link ContentRecycleBinService} 依据各来源已排序的前 N 行做有界 k 路归并后再切页，
     * 因此每个来源必须保证返回其编号升序序偶的前 {@code limit} 行（排序口径与统一层 comparator 一致）。
     *
     * <p>T32：取数同样限定在 {@code projectId} 单项目范围内。
     */
    List<Map<String, Object>> listDeletedPage(String type, long projectId, String keyword, int limit, AuthUser user);

    /** 查询单条软删除详情；实现必须保持租户隔离并限定 deleted=1，并按库中 {@code project_id} 做项目可访问校验（T32）。 */
    Map<String, Object> detail(String type, long id, AuthUser user);

    /** 恢复（管理员权限、实体校验与项目归属校验由实现负责，T32）。 */
    void restore(String type, List<Long> ids, AuthUser user);

    /** 彻底删除（关系级联与附件解绑由实现负责；跨项目 id 必须拒绝，T32）。 */
    void purge(String type, List<Long> ids, AuthUser user);
}
