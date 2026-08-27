package com.ccb.architecture.integration;

/**
 * 部署单元外部引用检查的公开 SPI。
 *
 * <p>调用方只传递中性值对象，不暴露 Web、持久化或认证上下文类型。实现方返回的摘要必须
 * 可安全展示，不得包含异常堆栈、凭据或内部实现细节；检查异常或无法判定时必须返回
 * {@link ReferenceCheckResult.Status#INDETERMINATE}，由守卫失败关闭。</p>
 */
public interface DeploymentUnitReferenceChecker {

    /**
     * 返回稳定的实现标识，便于汇总结果和审计。
     */
    String checkerKey();

    /**
     * 检查指定部署单元是否仍被有效引用。
     */
    ReferenceCheckResult check(DeploymentUnitReferenceCheckRequest request);
}
