package com.ccb.datamigration.lifecycle.enums;

/** 生命周期状态枚举公共契约：后端枚举与前端文案 1:1（铁律 #2，唯一定义处为基线文档第 3 章）。 */
public interface LifecycleStatus {
    /** 后端枚举值（与前端 1:1）。 */
    String code();

    /** 前端显示文案（唯一，禁止同值异名 / 异名同值）。 */
    String displayName();

    /** 是否为终态（基线文档第 3 章各表「终态标记」列）。 */
    boolean terminal();
}
