-- REQ-20260919-076：系统/组件清单关联人员（迁移系统与人员关系表）。
-- 仅追加、幂等：一个系统可关联多个当前项目成员，每人一条关系并带职责/角色（person_role）。
-- 关系事实源为 dm_component_person，无软删；组件物理删除时由服务在事务内级联清理。

CREATE TABLE IF NOT EXISTS dm_component_person (
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户主键',
    project_id BIGINT NOT NULL COMMENT '项目主键，关联 pm_project.id',
    system_code VARCHAR(64) NOT NULL COMMENT '系统编号，关联 dm_component.system_code',
    user_id BIGINT NOT NULL COMMENT '成员用户主键，关联 sys_user.id',
    person_role VARCHAR(64) NOT NULL COMMENT '职责/角色，取值来自参数管理类别 DM_COMPONENT_PERSON_ROLE',
    created_by BIGINT NOT NULL COMMENT '创建人',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT NOT NULL COMMENT '更新人',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (tenant_id, project_id, system_code, user_id),
    KEY idx_dm_component_person_user (tenant_id, user_id),
    KEY idx_dm_component_person_project (tenant_id, project_id, system_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统/组件清单关联人员（一人一角色一条）';

-- 组件人员职责参数类别（系统管理/参数管理维护，迁移仅幂等种子默认选项，管理员配置不被覆盖）。
INSERT IGNORE INTO sys_dict_type (id, tenant_id, dict_code, dict_name, status, deleted)
VALUES (5824, 1, 'DM_COMPONENT_PERSON_ROLE', '组件人员职责', 1, 0);

INSERT IGNORE INTO sys_config (id, tenant_id, category_id, config_key, config_value, config_type, remark, status, deleted)
SELECT v.id, 1, t.id, v.config_key, v.config_value, 'string', '组件清单关联人员职责（系统管理/参数管理维护）', 1, 0
FROM (
    SELECT 58240 id, 'DM_COMPONENT_PERSON_ROLE.OWNER' config_key, '负责人' config_value
    UNION ALL SELECT 58241, 'DM_COMPONENT_PERSON_ROLE.LIAISON', '对接人'
    UNION ALL SELECT 58242, 'DM_COMPONENT_PERSON_ROLE.BUSINESS', '业务人员'
    UNION ALL SELECT 58243, 'DM_COMPONENT_PERSON_ROLE.IMPLEMENTER', '实施人员'
) v
JOIN sys_dict_type t ON t.tenant_id = 1 AND t.dict_code = 'DM_COMPONENT_PERSON_ROLE' AND t.deleted = 0;
