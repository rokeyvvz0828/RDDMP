package com.ccb.architecture.change.model;

import java.util.Objects;

/**
 * 一次编号保留请求。业务归属始终带 tenant/application/line，分配域由调用方固定为全局域 0。
 */
public record SubsystemNumberRequest(
        long tenantId,
        long applicationId,
        int lineNo,
        SubsystemNumberKind kind,
        Integer logicalSequence) {

    public static final long GLOBAL_ALLOCATION_SCOPE = 0L;
    public static final int LOGICAL_MAX_ORDINAL = 9_999;
    public static final int PHYSICAL_MAX_ORDINAL = 35;

    public SubsystemNumberRequest {
        if (tenantId <= 0) {
            throw new IllegalArgumentException("tenantId 必须为正数");
        }
        if (applicationId <= 0) {
            throw new IllegalArgumentException("applicationId 必须为正数");
        }
        kind = Objects.requireNonNull(kind, "kind 不能为空");
        if (kind == SubsystemNumberKind.LOGICAL) {
            if (lineNo != 0) {
                throw new IllegalArgumentException("逻辑编号必须使用 lineNo=0");
            }
            if (logicalSequence != null) {
                throw new IllegalArgumentException("逻辑编号请求不能携带父逻辑序号");
            }
        } else {
            if (lineNo <= 0) {
                throw new IllegalArgumentException("物理编号必须使用正数 lineNo");
            }
            if (logicalSequence == null || logicalSequence < 1 || logicalSequence > LOGICAL_MAX_ORDINAL) {
                throw new IllegalArgumentException("物理编号必须携带 1..9999 的逻辑序号");
            }
        }
    }

    public static SubsystemNumberRequest logical(long tenantId, long applicationId) {
        return new SubsystemNumberRequest(tenantId, applicationId, 0, SubsystemNumberKind.LOGICAL, null);
    }

    public static SubsystemNumberRequest physical(long tenantId, long applicationId, int lineNo, int logicalSequence) {
        return new SubsystemNumberRequest(tenantId, applicationId, lineNo, SubsystemNumberKind.PHYSICAL, logicalSequence);
    }

    public long allocationScope() {
        return GLOBAL_ALLOCATION_SCOPE;
    }

    public String namespaceCode() {
        return kind == SubsystemNumberKind.LOGICAL ? "LOGICAL" : "PHYSICAL:" + logicalSequence;
    }

    public int maximumOrdinal() {
        return kind == SubsystemNumberKind.LOGICAL ? LOGICAL_MAX_ORDINAL : PHYSICAL_MAX_ORDINAL;
    }
}
