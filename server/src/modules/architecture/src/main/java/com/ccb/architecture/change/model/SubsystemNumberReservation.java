package com.ccb.architecture.change.model;

import java.util.Objects;

/**
 * 活动编号保留。code 由策略格式化；持久化实现只需保存全局分配键和业务归属。
 */
public record SubsystemNumberReservation(
        long allocationScope,
        String namespaceCode,
        int ordinal,
        long tenantId,
        long applicationId,
        int lineNo,
        SubsystemNumberKind kind,
        Integer logicalSequence,
        String code) {

    public SubsystemNumberReservation {
        if (allocationScope != SubsystemNumberRequest.GLOBAL_ALLOCATION_SCOPE) {
            throw new IllegalArgumentException("当前策略只支持全局 allocationScope=0");
        }
        namespaceCode = Objects.requireNonNull(namespaceCode, "namespaceCode 不能为空");
        kind = Objects.requireNonNull(kind, "kind 不能为空");
        new SubsystemNumberRequest(tenantId, applicationId, lineNo, kind, logicalSequence);
        if (ordinal <= 0) {
            throw new IllegalArgumentException("ordinal 必须为正数");
        }
    }

    public static SubsystemNumberReservation unformatted(SubsystemNumberRequest request, int ordinal) {
        Objects.requireNonNull(request, "request 不能为空");
        return new SubsystemNumberReservation(
                request.allocationScope(),
                request.namespaceCode(),
                ordinal,
                request.tenantId(),
                request.applicationId(),
                request.lineNo(),
                request.kind(),
                request.logicalSequence(),
                null);
    }

    public Identity identity() {
        return new Identity(tenantId, applicationId, lineNo, kind);
    }

    public SubsystemNumberReservation withCode(String formattedCode) {
        return new SubsystemNumberReservation(
                allocationScope,
                namespaceCode,
                ordinal,
                tenantId,
                applicationId,
                lineNo,
                kind,
                logicalSequence,
                Objects.requireNonNull(formattedCode, "formattedCode 不能为空"));
    }

    /** application/line/kind 是幂等保留的业务身份。 */
    public record Identity(long tenantId, long applicationId, int lineNo, SubsystemNumberKind kind) {
        public static Identity from(SubsystemNumberRequest request) {
            Objects.requireNonNull(request, "request 不能为空");
            return new Identity(request.tenantId(), request.applicationId(), request.lineNo(), request.kind());
        }
    }
}
