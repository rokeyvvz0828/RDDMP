package com.ccb.datamigration.lifecycle.enums;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 状态枚举可达性注册表（基线文档 3.11，铁律 #11/#18）：每一个在目录中登记的状态值必须登记可达性证据，
 * 证据可为「当前态实体 / 用例构造 / 流转留痕」三者之一；本期业务不产生时必须显式登记
 * NOT_YET_AUTHORIZED 并写明原因，禁止沉默。批次任务落地种子或用例时，将本条从 NOT_YET_AUTHORIZED 翻转。
 */
public final class LifecycleReachabilityRegistry {
    public enum Evidence {
        CURRENT_ENTITY,
        CASE_CONSTRUCTION,
        FLOW_LOG,
        NOT_YET_AUTHORIZED
    }

    private static final String NOT_YET_REASON = "批次1无业务表；由批次2~4对应模块在种子/用例中登记可达性证据，禁用沉默死枚举";
    private static final Map<String, Evidence> EVIDENCE = new LinkedHashMap<>();
    private static final Map<String, String> REASONS = new LinkedHashMap<>();

    static {
        for (LifecycleStatusCatalog.Category category : LifecycleStatusCatalog.all()) {
            for (LifecycleStatusCatalog.StatusEntry entry : category.entries()) {
                register(category.name(), entry.code(), Evidence.NOT_YET_AUTHORIZED, NOT_YET_REASON);
            }
        }
    }

    private LifecycleReachabilityRegistry() {
    }

    private static void register(String category, String code, Evidence evidence, String reason) {
        String key = key(category, code);
        EVIDENCE.put(key, evidence);
        REASONS.put(key, reason == null ? "" : reason);
    }

    private static String key(String category, String code) {
        return category + "|" + code;
    }

    /** 状态值是否已登记可达性证据（未登记即死枚举，自检必须失败）。 */
    public static boolean isRegistered(String category, String code) {
        return EVIDENCE.containsKey(key(category, code));
    }

    public static Evidence evidenceOf(String category, String code) {
        Evidence evidence = EVIDENCE.get(key(category, code));
        if (evidence == null) {
            throw new IllegalArgumentException("状态值未登记可达性：" + key(category, code));
        }
        return evidence;
    }

    public static String reasonOf(String category, String code) {
        return REASONS.getOrDefault(key(category, code), "");
    }
}
