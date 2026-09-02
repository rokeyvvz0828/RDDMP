package com.ccb.datamigration.service;

import com.ccb.security.model.AuthUser;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 现有九类内容资产（六种文件型 + 三种结构化型）的统一回收站来源。
 *
 * <p>把 {@link ContentRecycleBinService} 原先内联的 file/structured 分发原样封装为一个来源，
 * 行为完全等价：文件型走 {@link ContentFileAssetService}，结构化型走 {@link StructuredAssetService}。
 */
@Component
public class ContentAssetRecycleBinSource implements RecycleBinSource {

    private final ContentFileAssetService fileAssets;
    private final StructuredAssetService structured;

    public ContentAssetRecycleBinSource(ContentFileAssetService fileAssets, StructuredAssetService structured) {
        this.fileAssets = fileAssets;
        this.structured = structured;
    }

    @Override
    public Set<String> supports() {
        Set<String> types = new LinkedHashSet<>(ContentFileAssetService.MANAGED_TYPES);
        for (String table : ContentAssetTables.STRUCTURED_TABLES) types.add(ContentAssetTables.typeFor(table));
        return Set.copyOf(types);
    }

    private static boolean isFile(String type) {
        return ContentFileAssetService.MANAGED_TYPES.contains(type);
    }

    @Override
    public long countDeleted(String type, String keyword, AuthUser user) {
        return isFile(type) ? fileAssets.countDeleted(type, keyword, user) : structured.countDeleted(type, keyword, user);
    }

    @Override
    public List<Map<String, Object>> listDeletedPage(String type, String keyword, int limit, AuthUser user) {
        return isFile(type) ? fileAssets.listDeletedPage(type, keyword, limit, user) : structured.listDeletedPage(type, keyword, limit, user);
    }

    @Override
    public Map<String, Object> detail(String type, long id, AuthUser user) {
        return isFile(type) ? fileAssets.findDeletedDetail(type, id, user) : structured.findDeletedDetail(type, id, user);
    }

    @Override
    public void restore(String type, List<Long> ids, AuthUser user) {
        if (isFile(type)) fileAssets.restore(type, ids, user);
        else structured.restore(type, ids, user);
    }

    @Override
    public void purge(String type, List<Long> ids, AuthUser user) {
        if (isFile(type)) fileAssets.purge(type, ids, user);
        else structured.purge(type, ids, user);
    }
}
