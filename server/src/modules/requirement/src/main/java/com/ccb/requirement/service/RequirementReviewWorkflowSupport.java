package com.ccb.requirement.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/** 需求模块评审流程公共支持：已发布流程定义解析、残留实例终结与业务摘要。 */
@Service
public class RequirementReviewWorkflowSupport {
    private final JdbcTemplate jdbc;

    public RequirementReviewWorkflowSupport(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 按流程编码取已发布流程定义 id；不存在或未发布抛业务异常。 */
    public long requirePublishedDefinitionId(long tenantId, String code) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id FROM wf_definition WHERE tenant_id = ? AND code = ? AND status = 'PUBLISHED' AND deleted = 0",
                tenantId, code);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "流程定义未发布：" + code
                            + "。请在「工作流管理」中为该编码创建并发布一条项目范围的流程"
                            + "（本地也可开启种子流程发布器后重启：ccb.workflow.seeded-definition-publisher.enabled=true）");
        }
        return ((Number) rows.get(0).get("id")).longValue();
    }

    /** 终结同 business_key 的 RUNNING/PENDING 残留实例与待办任务，为重提释放锁。 */
    public void terminateResidualInstances(long tenantId, String businessKey) {
        List<Map<String, Object>> oldInstances = jdbc.queryForList(
                "SELECT id FROM wf_instance WHERE tenant_id = ? AND business_key = ? AND status IN ('RUNNING','PENDING')",
                tenantId, businessKey);
        for (Map<String, Object> instance : oldInstances) {
            long instanceId = ((Number) instance.get("id")).longValue();
            jdbc.update("UPDATE wf_task SET status = 'COMPLETED', comment = '评审撤回重提' WHERE tenant_id = ? AND instance_id = ? AND status = 'PENDING'",
                    tenantId, instanceId);
            jdbc.update("UPDATE wf_instance SET status = 'TERMINATED' WHERE tenant_id = ? AND id = ? AND status IN ('RUNNING','PENDING')",
                    tenantId, instanceId);
        }
    }

    /** 业务摘要：规范化 SHA-256 十六进制，用于流程启动防重复。 */
    public String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
