-- REQ-20260902-058：修正 V124 演示问题记录的后端枚举值。
-- V124 已执行，不能修改；仅更新本任务固定演示记录，不影响用户创建的问题。
UPDATE rel_release_issue
SET issue_status = 'ANALYZING'
WHERE tenant_id = 1
  AND id IN (942301, 943302)
  AND issue_status = 'IN_PROGRESS'
  AND deleted = 0;
