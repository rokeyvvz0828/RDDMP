package com.ccb.datamigration.lifecycle.error;

import java.util.LinkedHashMap;
import java.util.Map;

/** 错误码 1:1 文案注册表（铁律 #12：枚举值与前端文案 1:1，禁止同值异名 / 异名同值）。 */
public final class LifecycleErrorCatalog {
    private static final Map<Integer, String> MESSAGES = new LinkedHashMap<>();

    static {
        register(LifecycleErrorCode.DATA_SCOPE_FORBIDDEN, "无该数据范围的操作权限");
        register(LifecycleErrorCode.OPTION_SOURCE_UNAVAILABLE, "选项数据源不可用");
        register(LifecycleErrorCode.MEMBER_INACTIVE, "人员必须为已激活成员");
        register(LifecycleErrorCode.COMPONENT_DISABLED, "停用组件不得参与新任务下发");
        register(LifecycleErrorCode.ACTIVITY_OBSOLETE_READONLY, "已作废活动只读，禁止任何写操作");
        register(LifecycleErrorCode.EXPORT_INSTANCE_DATA_LEAK, "导出模板包不得携带项目/组件/人员实例数据");
        register(LifecycleErrorCode.OWNER_INACTIVE, "负责人必须为已激活成员且仅可绑定 1 名");
    }

    private LifecycleErrorCatalog() {
    }

    private static void register(int code, String message) {
        if (MESSAGES.containsKey(code)) {
            throw new IllegalStateException("错误码重复定义：" + code);
        }
        if (MESSAGES.containsValue(message)) {
            throw new IllegalStateException("错误码文案重复：" + message);
        }
        MESSAGES.put(code, message);
    }

    /** 返回错误码唯一文案；未注册时抛出（强制 1:1，禁止静默返回 null）。 */
    public static String message(int code) {
        String message = MESSAGES.get(code);
        if (message == null) {
            throw new IllegalArgumentException("错误码未注册文案：" + code);
        }
        return message;
    }

    public static Map<Integer, String> all() {
        return Map.copyOf(MESSAGES);
    }
}
