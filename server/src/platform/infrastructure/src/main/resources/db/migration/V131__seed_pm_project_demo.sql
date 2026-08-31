-- =============================================================================
-- V131：补 pm_project 演示数据（与需求模块 req_project 对齐）
-- 顶部项目下拉（project-context）数据源为 pm_project，需求模块差异/存量按
-- project_code 绑定；补齐同一批项目，保证下拉有数据且两边可匹配。
-- 幂等：按 project_code 唯一键存在性判断，不覆盖已有项目。
-- 仅追加，不修改历史迁移。
-- =============================================================================

INSERT INTO pm_project
    (id, tenant_id, project_code, project_name, description, status, owner_id,
     planned_start_date, planned_end_date, actual_end_date, created_by, deleted)
SELECT rp.id, rp.tenant_id, rp.project_code, rp.project_name, rp.description,
       CASE
           WHEN rp.status COLLATE utf8mb4_0900_ai_ci IN ('BASELINED', '已基线', 'COMPLETED', '已完成') THEN 'COMPLETED'
           WHEN rp.status COLLATE utf8mb4_0900_ai_ci IN ('SUSPENDED', '已停用') THEN 'SUSPENDED'
           ELSE 'RUNNING'
       END AS status,
       1,
       rp.start_time, NULL, NULL, 1, 0
FROM req_project rp
WHERE rp.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM pm_project p
      WHERE p.tenant_id = rp.tenant_id AND p.id = rp.id AND p.deleted = 0);
