-- REQ-20260822-048：架构子系统变更固定审批流程。
-- 角色处理人的运行时公开契约读取 assigneeIds；roleIds 同步保留为业务模型的显式角色语义。
-- SQL 迁移只预置草稿；必须通过平台既有发布入口生成 Flowable deployment 后才能启动。

INSERT INTO wf_definition
    (id, tenant_id, code, name, status, current_version, model_schema_version, deleted)
SELECT 900000000000030, 1, 'architecture.subsystem.change', '架构子系统变更审批', 'DRAFT', 1, 2, 0
WHERE NOT EXISTS (SELECT 1 FROM wf_definition WHERE id = 900000000000030);

INSERT INTO wf_version
    (id, tenant_id, definition_id, version_no, definition_json, model_schema_version, status)
SELECT 900000000000031, 1, 900000000000030, 1,
       '{"schemaVersion":2,"variables":[],"formBindings":[],"nodes":[{"id":"start","type":"START","label":"发起","position":{"x":100,"y":160},"config":{}},{"id":"approval-architecture-manager","type":"APPROVAL","label":"架构子系统审批","position":{"x":380,"y":160},"config":{"assigneeType":"ROLE","assigneeIds":[110],"roleIds":[110],"mode":"ANY","emptyAssigneeAction":"ERROR","actionPolicy":{"allowedActions":["APPROVE","RETURN","REJECT"]}}},{"id":"end","type":"END","label":"结束","position":{"x":660,"y":160},"config":{}}],"edges":[{"id":"edge-start-approval","source":"start","target":"approval-architecture-manager","label":null,"condition":null,"default":false},{"id":"edge-approval-end","source":"approval-architecture-manager","target":"end","label":null,"condition":null,"default":false}]}',
       2, 'DRAFT'
WHERE EXISTS (
    SELECT 1
    FROM wf_definition
    WHERE id = 900000000000030
      AND tenant_id = 1
      AND code = 'architecture.subsystem.change'
      AND deleted = 0
)
  AND NOT EXISTS (SELECT 1 FROM wf_version WHERE id = 900000000000031);

-- 稳定 ID 或流程编码若被不同记录占用，必须停止迁移，不能静默覆盖其他已发布流程。
CREATE TEMPORARY TABLE tmp_arch_v84_seed_guard (
    marker TINYINT NOT NULL,
    CONSTRAINT chk_tmp_arch_v84_seed_guard CHECK (marker = 0)
) ENGINE=InnoDB;

INSERT INTO tmp_arch_v84_seed_guard (marker)
SELECT 1
WHERE NOT EXISTS (
          SELECT 1
          FROM wf_definition
          WHERE id = 900000000000030
            AND tenant_id = 1
            AND code = 'architecture.subsystem.change'
            AND name = '架构子系统变更审批'
            AND status = 'DRAFT'
            AND current_version = 1
            AND model_schema_version = 2
            AND deleted = 0
      )
   OR NOT EXISTS (
          SELECT 1
          FROM wf_version
          WHERE id = 900000000000031
            AND tenant_id = 1
            AND definition_id = 900000000000030
            AND version_no = 1
            AND model_schema_version = 2
            AND status = 'DRAFT'
      );

DROP TEMPORARY TABLE tmp_arch_v84_seed_guard;
