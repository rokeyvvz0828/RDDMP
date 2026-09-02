-- 工作流定义、实例和业务场景增加真实项目范围。
-- 存量定义保持租户全局；历史实例只按稳定项目编号回填，不按名称猜测。

ALTER TABLE wf_definition
    DROP INDEX uk_wf_definition_code,
    ADD COLUMN scope_type VARCHAR(16) NOT NULL DEFAULT 'GLOBAL' COMMENT '流程范围：GLOBAL租户全局、PROJECT项目' AFTER name,
    ADD COLUMN project_id BIGINT NULL COMMENT '项目流程所属项目主键' AFTER scope_type,
    ADD COLUMN scope_project_key BIGINT GENERATED ALWAYS AS (IFNULL(project_id, 0)) STORED COMMENT '范围唯一键辅助列' AFTER project_id,
    ADD UNIQUE KEY uk_wf_definition_scope_code (tenant_id, scope_type, scope_project_key, code, deleted),
    ADD KEY idx_wf_definition_project (tenant_id, project_id, status, deleted);

UPDATE wf_definition
SET scope_type = 'GLOBAL', project_id = NULL
WHERE scope_type IS NULL OR scope_type NOT IN ('GLOBAL', 'PROJECT');

ALTER TABLE wf_instance
    ADD COLUMN project_id BIGINT NULL COMMENT '实例所属真实项目主键' AFTER business_round,
    ADD KEY idx_wf_instance_project (tenant_id, project_id, status, deleted);

UPDATE wf_instance i
JOIN pm_project p
  ON p.tenant_id = i.tenant_id
 AND p.project_code COLLATE utf8mb4_unicode_ci = i.project_ref COLLATE utf8mb4_unicode_ci
 AND p.deleted = 0
SET i.project_id = p.id
WHERE i.project_id IS NULL
  AND i.project_ref IS NOT NULL
  AND i.project_ref <> '';

CREATE TABLE wf_business_binding (
    id BIGINT PRIMARY KEY COMMENT '业务流程绑定主键',
    tenant_id BIGINT NOT NULL COMMENT '租户主键',
    project_id BIGINT NOT NULL COMMENT '项目主键',
    module_code VARCHAR(64) NOT NULL COMMENT '业务模块编码',
    scene_code VARCHAR(64) NOT NULL COMMENT '模块内业务场景编码',
    definition_id BIGINT NOT NULL COMMENT '流程定义主键',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_by BIGINT NOT NULL COMMENT '创建人',
    updated_by BIGINT NOT NULL COMMENT '最后修改人',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_wf_business_binding_scene (tenant_id, project_id, module_code, scene_code),
    KEY idx_wf_business_binding_definition (tenant_id, definition_id, enabled)
) COMMENT='项目业务场景工作流绑定';
