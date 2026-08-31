package com.ccb.architecture.integration;

/**
 * 子系统外部引用检查的公开 SPI。
 *
 * <p>调用方只传递中性值对象，不暴露 Web、持久化或认证上下文类型。实现方返回的摘要必须可安全展示，
 * 不得包含异常堆栈、凭据或内部实现细节。</p>
 */
public interface SubsystemReferenceChecker {

    /**
     * 返回稳定的实现标识，便于汇总结果和审计。
     */
    String checkerKey();

    /**
     * 检查指定子系统在给定操作下是否仍被有效引用。
     */
    ReferenceCheckResult check(ReferenceCheckRequest request);
}
