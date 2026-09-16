package com.ccb.architecture.integration;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.system.capability.ProjectDeletionGuard;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/** 存在有效架构主数据时阻止删除项目。 */
@Component
public class ArchitectureProjectDeletionGuard implements ProjectDeletionGuard {
    private static final List<String> REFERENCE_QUERIES = List.of(
            "SELECT EXISTS(SELECT 1 FROM arch_physical_subsystem WHERE tenant_id = ? AND project_id = ? AND deleted = 0)",
            "SELECT EXISTS(SELECT 1 FROM arch_deployment_unit WHERE tenant_id = ? AND project_id = ?)",
            "SELECT EXISTS(SELECT 1 FROM arch_environment WHERE tenant_id = ? AND project_id = ?)",
            "SELECT EXISTS(SELECT 1 FROM arch_resource_request WHERE tenant_id = ? AND project_id = ?)",
            "SELECT EXISTS(SELECT 1 FROM arch_environment_instance WHERE tenant_id = ? AND project_id = ?)",
            "SELECT EXISTS(SELECT 1 FROM arch_setup_plan WHERE tenant_id = ? AND project_id = ?)",
            "SELECT EXISTS(SELECT 1 FROM arch_network_work_order WHERE tenant_id = ? AND project_id = ?)",
            "SELECT EXISTS(SELECT 1 FROM arch_network_zone WHERE tenant_id = ? AND project_id = ?)",
            "SELECT EXISTS(SELECT 1 FROM arch_external_network_address WHERE tenant_id = ? AND project_id = ?)",
            "SELECT EXISTS(SELECT 1 FROM arch_network_access_application WHERE tenant_id = ? AND project_id = ?)",
            "SELECT EXISTS(SELECT 1 FROM arch_network_access_relation WHERE tenant_id = ? AND project_id = ?)",
            "SELECT EXISTS(SELECT 1 FROM arch_network_access_exemption_rule WHERE tenant_id = ? AND project_id = ?)",
            "SELECT EXISTS(SELECT 1 FROM arch_decision_matter WHERE tenant_id = ? AND project_id = ? AND deleted = 0)"
    );

    private final JdbcTemplate jdbc;

    public ArchitectureProjectDeletionGuard(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void requireNoReferences(long tenantId, long projectId) {
        for (String sql : REFERENCE_QUERIES) {
            if (Boolean.TRUE.equals(jdbc.queryForObject(sql, Boolean.class, tenantId, projectId))) {
                throw new BusinessException(ErrorCode.CONFLICT, "项目仍有关联的架构数据，不能删除");
            }
        }
    }
}
