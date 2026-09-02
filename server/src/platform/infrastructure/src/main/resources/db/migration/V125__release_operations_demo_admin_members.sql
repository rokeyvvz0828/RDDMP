-- REQ-20260902-058：将本地测试登录用户加入投产演练示范项目。
-- V124 已执行，不能修改；本迁移只补齐 admin 的项目成员关系以满足投产接口实体授权。
INSERT INTO pm_project_member
    (id, tenant_id, project_id, user_id, status, joined_at, created_at, updated_at, deleted)
SELECT 941009, 1, 940001, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
FROM sys_user u
WHERE u.id = 1 AND u.tenant_id = 1 AND u.deleted = 0
  AND EXISTS (SELECT 1 FROM pm_project p WHERE p.id = 940001 AND p.tenant_id = 1 AND p.deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM pm_project_member m WHERE m.id = 941009 OR (m.tenant_id = 1 AND m.project_id = 940001 AND m.user_id = 1 AND m.deleted = 0));

INSERT INTO pm_project_member
    (id, tenant_id, project_id, user_id, status, joined_at, created_at, updated_at, deleted)
SELECT 941010, 1, 940002, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
FROM sys_user u
WHERE u.id = 1 AND u.tenant_id = 1 AND u.deleted = 0
  AND EXISTS (SELECT 1 FROM pm_project p WHERE p.id = 940002 AND p.tenant_id = 1 AND p.deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM pm_project_member m WHERE m.id = 941010 OR (m.tenant_id = 1 AND m.project_id = 940002 AND m.user_id = 1 AND m.deleted = 0));
