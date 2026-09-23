package com.ccb.datamigration.lifecycle.snapshot;

import java.time.LocalDateTime;

/**
 * 固化载荷自描述契约（铁律 #15）：任何冻结载荷必须自带版本号、归属主体标识、生成时间，
 * 且载荷版本号与外层记录版本号恒等（1:1），在同一次发布事务中一并写入，禁止事后补齐或反查。
 * 批次 3 起由任务快照/模板包嵌入业务载荷（payload）实现深拷贝固化。
 */
public record LifecycleSnapshotEnvelope(int schemaVersion, String entityType, long entityId, LocalDateTime frozenAt) {
    /** 当前载荷契约版本；与各快照/模板外层记录版本号 1:1 恒等。 */
    public static final int SCHEMA_VERSION = 1;

    public static LifecycleSnapshotEnvelope freeze(String entityType, long entityId, LocalDateTime frozenAt) {
        return new LifecycleSnapshotEnvelope(SCHEMA_VERSION, entityType, entityId, frozenAt);
    }
}
