package com.ccb.datamigration.service;

import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 统一内容回收站：聚合所有 {@link RecycleBinSource} 认领类型的软删记录，按内容类型筛选、分类恢复与彻底删除。
 *
 * <p>类型到来源的映射由注入的 {@link RecycleBinSource} 列表按 {@code supports()} 构建；新增一类内容
 * 只需新增一个来源实现，本类无需改动。恢复/彻底删除按类型薄分发到对应来源，管理员权限、唯一性校验、
 * 关系级联与审计均由来源及其下游服务负责，统一层不枚举业务字段。
 *
 * <p>当前来源覆盖：六种文件型 + 三种结构化型（{@link ContentAssetRecycleBinSource}）与汇报材料 REPORT
 * （{@link ReportRecycleBinSource}）。会议附件级回收站仍保留在 {@code /meetings} 下。
 */
@Service
public class ContentRecycleBinService {

    /** 内容类型 -> 来源注册表，按各来源 supports() 构建。 */
    private final Map<String, RecycleBinSource> registry;

    public ContentRecycleBinService(List<RecycleBinSource> sources) {
        Map<String, RecycleBinSource> map = new LinkedHashMap<>();
        for (RecycleBinSource source : sources) {
            for (String type : source.supports()) {
                if (map.putIfAbsent(type, source) != null) {
                    throw new IllegalStateException("回收站内容类型被多个来源重复认领：" + type);
                }
            }
        }
        this.registry = Map.copyOf(map);
    }

    /** 统一回收站当前覆盖的全部内容类型。 */
    public Set<String> supportedTypes() {
        return registry.keySet();
    }

    /**
     * 聚合软删列表；contentTypes 为空表示全部覆盖类型。
     *
     * <p>统一排序：跨类型合并后按业务编号 {@code asset_code} 字典序升序，与会议列表 {@code meeting_code ASC}、
     * 汇报列表 {@code doc_code ASC} 一致。空 {@code asset_code} 行归到末尾（回退到 {@code deleted_at DESC} 保证同编号行内部可预测）。
     *
     * <p>原生分页（T27）：{@code total} 由各来源 {@link RecycleBinSource#countDeleted} 的 SQL {@code COUNT(*)} 相加得到；
     * 取数时对各已排序来源仅拉取前 {@code page*size} 行（{@link RecycleBinSource#listDeletedPage} 在 SQL 层 {@code ORDER BY asset_code + LIMIT}），
     * 因为全局第 {@code page*size} 小行必然落在每个单源的前 {@code page*size} 行内，取并集后排序切页严格无损。单次拉取上界为
     * {@code 来源数 × page × size}，内存有界，不再全量加载。{@code size} 上限 100。
     *
     * <p>T32 项目隔离：{@code projectId} 必填，缺失直接 {@code BAD_REQUEST}，不回退为全项目回收站；
     * 可访问性（管理员或项目活动成员）由各来源下游服务统一按 {@link DataMigrationPermissionService#requireAccessible} 判定。
     */
    public PageResult<Map<String, Object>> list(Set<String> contentTypes, Long projectId, String keyword, int page, int size, AuthUser user) {
        if (projectId == null || projectId <= 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "projectId is required");
        long scope = projectId;
        Set<String> requested = (contentTypes == null || contentTypes.isEmpty())
                ? new LinkedHashSet<>(supportedTypes()) : new LinkedHashSet<>(contentTypes);
        for (String type : requested) {
            if (!registry.containsKey(type)) throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的内容类型：" + type);
        }
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        long total = 0L;
        for (String type : requested) {
            total += registry.get(type).countDeleted(type, scope, keyword, user);
        }
        long offset = (long) (safePage - 1) * safeSize;
        if (offset >= total) {
            return new PageResult<>(new ArrayList<>(), total, safePage, safeSize);
        }
        // 有界 k 路归并：每源只需提供其编号升序序偶的前 page*size 行（SQL 层 LIMIT），就能保证全局前 page*size 行完整覆盖。
        int window = (int) Math.min((long) safePage * safeSize, (long) Integer.MAX_VALUE);
        List<Map<String, Object>> merged = new ArrayList<>();
        for (String type : requested) {
            merged.addAll(registry.get(type).listDeletedPage(type, scope, keyword, window, user));
        }
        merged.sort(Comparator
                .comparing((Map<String, Object> row) -> {
                    Object code = row.get("asset_code");
                    return code == null || String.valueOf(code).isEmpty();
                })
                .thenComparing(row -> String.valueOf(row.getOrDefault("asset_code", "")))
                .thenComparing(row -> String.valueOf(row.getOrDefault("deleted_at", "")), Comparator.reverseOrder()));
        int from = Math.min(offset > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) offset, merged.size());
        int to = Math.min(from + safeSize, merged.size());
        List<Map<String, Object>> records = new ArrayList<>(merged.subList(from, to));
        return new PageResult<>(records, total, safePage, safeSize);
    }

    /** 按内容类型分发单条软删除详情，不改变记录状态；项目归属由来源校验（T32）。 */
    public Map<String, Object> detail(String type, long id, AuthUser user) {
        if (id <= 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "无效的内容 ID");
        return resolve(type).detail(type, id, user);
    }

    /** 按内容类型分发恢复（管理员权限、实体与项目归属校验由来源下游负责，T32）。 */
    @Transactional
    public void restore(String type, List<Long> ids, AuthUser user) {
        resolve(type).restore(type, ids, user);
    }

    /** 按内容类型分发彻底删除（管理员权限、级联与项目归属校验由来源下游负责，T32）。 */
    @Transactional
    public void purge(String type, List<Long> ids, AuthUser user) {
        resolve(type).purge(type, ids, user);
    }

    private RecycleBinSource resolve(String type) {
        RecycleBinSource source = type == null ? null : registry.get(type);
        if (source == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的内容类型");
        return source;
    }
}
