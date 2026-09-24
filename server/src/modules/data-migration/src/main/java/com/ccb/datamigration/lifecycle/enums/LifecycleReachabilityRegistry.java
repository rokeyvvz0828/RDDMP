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
        // 批次1/2：活动、问题、风险、工序配置暂无可达实例，保持 NOT_YET_AUTHORIZED 登记。
        // 批次3：工单 9 态 / 工序 5 态 / 审核 4 态 / 时效 3 档由任务下发→流转→反馈→审核链路用例与留痕构造可达（T4/T5/T6）。
        String orderCategory = "工单整体状态（9 态，3.2）";
        String processCategory = "工序节点状态（5 态，3.3）";
        String auditCategory = "审核状态（4 态，3.7）";
        String timingCategory = "时效标记（3 档，3.8）";
        register(orderCategory, "WAIT_PRE", Evidence.CASE_CONSTRUCTION, "专题跨活动阻塞承接：初始态 WAIT_PRE（D-09，T4 用例）");
        register(orderCategory, "WAIT_ACCEPT", Evidence.CASE_CONSTRUCTION, "无跨活动阻塞承接（T4 用例）；流转留痕 FLOW_LOG");
        register(orderCategory, "EXECUTING", Evidence.CASE_CONSTRUCTION, "承接即执行中（T4 用例）；工序闭环重算派生（D-13）");
        register(orderCategory, "SUSPENDED", Evidence.FLOW_LOG, "管理员暂停（18.4 分支1，T5 用例/留痕）");
        register(orderCategory, "REVIEWING", Evidence.FLOW_LOG, "需审核工序提审后主状态派生（D-11/D-13，T5 用例）");
        register(orderCategory, "REVIEW_REJECTED", Evidence.FLOW_LOG, "审核打回优先于执行中（D-11，T5 用例）");
        register(orderCategory, "CLOSED", Evidence.FLOW_LOG, "四象限闭环（A0 自动/A1 审核通过，18.2，T5 用例/留痕）");
        register(orderCategory, "ARCHIVED", Evidence.FLOW_LOG, "全部闭环且无打回未整改后归档（11.4，T5 用例/留痕）");
        register(orderCategory, "CANCELLED", Evidence.FLOW_LOG, "任务作废联动（10.2.1，T5 用例/留痕）");
        register(processCategory, "LOCKED", Evidence.CASE_CONSTRUCTION, "前置未闭环强制锁止（D-09，T4 承接用例）");
        register(processCategory, "EXECUTING", Evidence.CASE_CONSTRUCTION, "无前置工序承接即解锁（T4 用例）");
        register(processCategory, "REVIEWING", Evidence.FLOW_LOG, "提审进入审核（T5 用例/留痕）");
        register(processCategory, "REJECTED", Evidence.FLOW_LOG, "审核打回拒绝写审核结果（D-14，T5 用例/留痕）");
        register(processCategory, "CLOSED", Evidence.FLOW_LOG, "四象限闭环/审核通过关闭（18.2，T5 用例/留痕）");
        register(auditCategory, "WAIT_REVIEW", Evidence.CASE_CONSTRUCTION, "工序承接默认待审核（T4 用例）");
        register(auditCategory, "PASSED", Evidence.FLOW_LOG, "审核通过驱动解锁（T5 用例/留痕）");
        register(auditCategory, "REJECTED", Evidence.FLOW_LOG, "审核打回（T5 用例/留痕）");
        register(auditCategory, "RECHECK", Evidence.FLOW_LOG, "打回整改后闭环复审（T5 用例/留痕）");
        register(timingCategory, "NORMAL", Evidence.CASE_CONSTRUCTION, "承接默认 NORMAL（T4 用例）");
        register(timingCategory, "NEAR_OVERDUE", Evidence.CASE_CONSTRUCTION, "剩余≤24h 自动分档（18.3，T5 用例）");
        register(timingCategory, "OVERDUE", Evidence.CASE_CONSTRUCTION, "剩余≤0 自动分档（18.3，T5 用例）");
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
