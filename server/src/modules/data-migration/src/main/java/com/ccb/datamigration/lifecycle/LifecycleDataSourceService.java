package com.ccb.datamigration.lifecycle;

import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.datamigration.lifecycle.model.LifecycleComponentOption;
import com.ccb.datamigration.lifecycle.model.LifecycleMemberOption;
import com.ccb.datamigration.lifecycle.model.LifecycleRoleOption;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.SystemReferenceQuery;
import com.ccb.system.capability.SystemUserReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

/** 生命周期平台数据源复用接入：成员/组件/角色选择器全部复用既有能力，人员与组件仅返回启用/可用数据。 */
@Service
public class LifecycleDataSourceService {
    private static final RowMapper<LifecycleComponentOption> COMPONENT_MAPPER = (rs, rowNum) ->
            new LifecycleComponentOption(rs.getLong("id"), rs.getLong("tenant_id"), rs.getLong("project_id"),
                    rs.getString("project_code"), rs.getString("project_name"), rs.getString("physical_subsystem_code"),
                    rs.getString("system_short_name"), rs.getString("system_name"), rs.getString("business_group_name"),
                    rs.getLong("owner_id"));
    private static final RowMapper<LifecycleRoleOption> ROLE_MAPPER = (rs, rowNum) ->
            new LifecycleRoleOption(rs.getLong("id"), rs.getLong("tenant_id"), rs.getString("role_code"),
                    rs.getString("role_name"));

    private final JdbcTemplate jdbc;
    private final SystemReferenceQuery referenceQuery;

    public LifecycleDataSourceService(JdbcTemplate jdbc, SystemReferenceQuery referenceQuery) {
        this.jdbc = jdbc;
        this.referenceQuery = referenceQuery;
    }

    /** 成员选择器：平台/system 只读契约，仅返回启用成员（deleted=0 AND status=1）。 */
    public PageResult<LifecycleMemberOption> memberOptions(AuthUser actor, PageQuery page, String keyword) {
        PageResult<SystemUserReference> active = referenceQuery.searchActiveUsers(actor,
                page == null ? new PageQuery(1, 20) : page, keyword);
        List<LifecycleMemberOption> records = active.records().stream()
                .map(user -> new LifecycleMemberOption(user.id(), user.displayName(), user.username(), user.phone(), user.active()))
                .toList();
        return new PageResult<>(records, active.total(), active.page(), active.size());
    }

    /** 组件选择器：契约视图 v_data_migration_lifecycle_component_option，仅返回可用组件（deleted=0）。 */
    public List<LifecycleComponentOption> componentOptions(AuthUser actor, Long projectId, String keyword) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, tenant_id, project_id, project_code, project_name, physical_subsystem_code, "
                        + "system_short_name, system_name, business_group_name, owner_id "
                        + "FROM v_data_migration_lifecycle_component_option WHERE tenant_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(actor.tenantId());
        if (projectId != null) {
            sql.append(" AND project_id = ?");
            args.add(projectId);
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (project_name LIKE ? OR system_name LIKE ? OR system_short_name LIKE ?)");
            String pattern = "%" + keyword.trim() + "%";
            args.add(pattern);
            args.add(pattern);
            args.add(pattern);
        }
        sql.append(" ORDER BY project_code, id LIMIT 100");
        return jdbc.query(sql.toString(), COMPONENT_MAPPER, args.toArray());
    }

    /** 角色选择器：契约视图 v_data_migration_lifecycle_role_option，仅返回启用角色。 */
    public List<LifecycleRoleOption> roleOptions(AuthUser actor) {
        return jdbc.query("SELECT id, tenant_id, role_code, role_name FROM v_data_migration_lifecycle_role_option "
                + "WHERE tenant_id = ? ORDER BY id", ROLE_MAPPER, actor.tenantId());
    }

    /** 生命周期阶段下拉：仅启用阶段（lifecycle_stage.status=1 AND deleted=0）。 */
    public List<Map<String, Object>> stageOptions(AuthUser actor) {
        return jdbc.queryForList("SELECT id, stage_code, stage_name, sort_no FROM lifecycle_stage "
                + "WHERE tenant_id = ? AND status = 1 AND deleted = 0 ORDER BY sort_no, id", actor.tenantId());
    }
}
