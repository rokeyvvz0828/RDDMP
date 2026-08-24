package com.ccb.requirement.support;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 在 workflowService.start() 之后补写 wf_instance 的业务上下文字段。
 * <p>
 * 需求管理两个 service（RequirementDifferenceService、RequirementLegacyService）
 * 目前直接调用底层 WorkflowService.start(definitionId, businessKey, variables, user)，
 * 不会写入 business_module_code、business_title、action_path 等字段，
 * 导致我的代办中心查询返回的 action_path/business_title 为空 → 前端 safeRouteLocation 校验失败
 * → 点击"业务事项"原地跳回任务中心（看起来没反应），同时无法渲染业务标题。
 * <p>
 * 这个 Helper 统一封装 UPDATE 语句，确保代办列表有完整的业务标题和详情路由。
 */
public final class WorkflowBizContextHelper {

    /** 用 64 位 0 填充 data_digest，代办/已办查询不使用该列；保持 schema 约定。 */
    private static final String EMPTY_DIGEST = "0000000000000000000000000000000000000000000000000000000000000000";

    public static void fill(JdbcTemplate jdbc, long instanceId, long tenantId,
                            String moduleCode, String moduleName, String businessType,
                            String businessTitle, int round,
                            String projectRef, String projectName,
                            String actionPath) {
        Object pr = (projectRef == null || projectRef.isBlank()) ? null : projectRef;
        Object pn = (projectName == null || projectName.isBlank()) ? null : projectName;
        jdbc.update("""
                UPDATE wf_instance
                SET business_module_code = ?, business_module_name = ?, business_type = ?,
                    business_title = ?, business_round = ?, project_ref = ?, project_name = ?,
                    action_path = ?, data_digest = ?
                WHERE id = ? AND tenant_id = ?
                """, moduleCode, moduleName, businessType, businessTitle, round, pr, pn,
                actionPath, EMPTY_DIGEST, instanceId, tenantId);
    }

    private WorkflowBizContextHelper() {}
}
