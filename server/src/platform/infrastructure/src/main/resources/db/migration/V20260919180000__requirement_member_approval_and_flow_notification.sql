-- =============================================================================
-- 需求管理流转/审批人员来源统一与审批接入审批流（REQ-20260919-076）
-- -----------------------------------------------------------------------------
-- 1. 存量交付件（工作量表/软需文档）评审接入平台审批流，需要记录流程实例 ID；
-- 2. 追加全局流程定义 requirement.legacy.deliverable.review，审批节点使用动态审批人，
--    由业务层在启动变量 approverIds 中传入当前项目组织架构的有效成员。
-- 说明：审批人来源与成员资格校验由业务层完成，本迁移只补列与流程定义；
--       不修改任何历史迁移文件，回退应用代码后新增列与流程定义可保留。
-- =============================================================================

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'req_workload' AND COLUMN_NAME = 'workflow_instance_id');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE req_workload ADD COLUMN workflow_instance_id BIGINT NULL COMMENT ''审批流程实例 ID（评审接入审批流后写入）'' AFTER review_record_id',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'req_soft_doc' AND COLUMN_NAME = 'workflow_instance_id');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE req_soft_doc ADD COLUMN workflow_instance_id BIGINT NULL COMMENT ''审批流程实例 ID（评审接入审批流后写入）'' AFTER review_record_id',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT INTO wf_definition (id, tenant_id, code, name, scope_type, project_id, status, current_version, deleted)
SELECT 900000000000060, 1, 'requirement.legacy.deliverable.review', '存量需求交付件评审审批', 'GLOBAL', NULL, 'PUBLISHED', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM wf_definition WHERE tenant_id = 1 AND code = 'requirement.legacy.deliverable.review' AND deleted = 0);

INSERT INTO wf_version (id, tenant_id, definition_id, version_no, definition_json, model_schema_version, status)
SELECT 900000000000061, 1, 900000000000060, 1,
       '{"schemaVersion":1,"nodes":[{"id":"start","type":"START","label":"发起","position":{"x":100,"y":160},"config":{}},{"id":"approval-reviewer","type":"APPROVAL","label":"交付件评审","position":{"x":380,"y":160},"config":{"assigneeType":"VARIABLE","assigneeVariable":"approverIds","mode":"ANY","emptyAssigneeAction":"ERROR","actionPolicy":{"allowedActions":["APPROVE","REJECT","RETURN","ADD_SIGN","CC"]}}},{"id":"end","type":"END","label":"结束","position":{"x":660,"y":160},"config":{}}],"edges":[{"id":"edge-start-reviewer","source":"start","target":"approval-reviewer"},{"id":"edge-reviewer-end","source":"approval-reviewer","target":"end"}]}',
       1, 'PUBLISHED'
WHERE NOT EXISTS (SELECT 1 FROM wf_version WHERE tenant_id = 1 AND definition_id = 900000000000060 AND version_no = 1);

-- -----------------------------------------------------------------------------
-- 3. 演示数据补齐：需求管理示例项目（P2026-001/002/003，pm_project.id = 3001/3002/3003）
--    在项目管理中缺少项目组织架构成员。流转人与审批人改为只读项目组织架构后，
--    这些演示项目会出现"无可选人员"，本地验收无法完成。这里按 V131 的镜像口径，
--    把 V130 的 req_project_member 演示成员同步为 pm_project_member，并加入本地测试账号 admin。
--    仅覆盖上述演示项目 id，幂等；生产数据不受影响。
-- -----------------------------------------------------------------------------
INSERT INTO pm_project_member
    (id, tenant_id, project_id, user_id, status, joined_at, created_at, updated_at, deleted)
SELECT 942500 + ROW_NUMBER() OVER (ORDER BY m.project_id, m.user_id),
       m.tenant_id, m.project_id, m.user_id, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
FROM req_project_member m
JOIN pm_project p ON p.id = m.project_id AND p.tenant_id = m.tenant_id AND p.deleted = 0
JOIN sys_user u ON u.id = m.user_id AND u.tenant_id = m.tenant_id AND u.deleted = 0
WHERE m.tenant_id = 1 AND m.deleted = 0
  AND m.project_id IN (3001, 3002, 3003)
  AND NOT EXISTS (SELECT 1 FROM pm_project_member x
                  WHERE x.tenant_id = m.tenant_id AND x.project_id = m.project_id
                    AND x.user_id = m.user_id AND x.deleted = 0);

INSERT INTO pm_project_member
    (id, tenant_id, project_id, user_id, status, joined_at, created_at, updated_at, deleted)
SELECT 942491, 1, 3001, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
FROM sys_user u
WHERE u.id = 1 AND u.tenant_id = 1 AND u.deleted = 0
  AND EXISTS (SELECT 1 FROM pm_project p WHERE p.id = 3001 AND p.tenant_id = 1 AND p.deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM pm_project_member m WHERE m.id = 942491 OR (m.tenant_id = 1 AND m.project_id = 3001 AND m.user_id = 1 AND m.deleted = 0));

INSERT INTO pm_project_member
    (id, tenant_id, project_id, user_id, status, joined_at, created_at, updated_at, deleted)
SELECT 942492, 1, 3002, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
FROM sys_user u
WHERE u.id = 1 AND u.tenant_id = 1 AND u.deleted = 0
  AND EXISTS (SELECT 1 FROM pm_project p WHERE p.id = 3002 AND p.tenant_id = 1 AND p.deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM pm_project_member m WHERE m.id = 942492 OR (m.tenant_id = 1 AND m.project_id = 3002 AND m.user_id = 1 AND m.deleted = 0));

INSERT INTO pm_project_member
    (id, tenant_id, project_id, user_id, status, joined_at, created_at, updated_at, deleted)
SELECT 942493, 1, 3003, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
FROM sys_user u
WHERE u.id = 1 AND u.tenant_id = 1 AND u.deleted = 0
  AND EXISTS (SELECT 1 FROM pm_project p WHERE p.id = 3003 AND p.tenant_id = 1 AND p.deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM pm_project_member m WHERE m.id = 942493 OR (m.tenant_id = 1 AND m.project_id = 3003 AND m.user_id = 1 AND m.deleted = 0));
