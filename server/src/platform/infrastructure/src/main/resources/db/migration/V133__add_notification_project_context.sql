ALTER TABLE sys_notification
    ADD COLUMN project_ref VARCHAR(64) NULL COMMENT '项目展示标识快照' AFTER action_path,
    ADD COLUMN project_name VARCHAR(128) NULL COMMENT '项目展示名称快照' AFTER project_ref;

UPDATE sys_notification n
JOIN wf_task t
    ON t.tenant_id = n.tenant_id
    AND n.event_id = CONCAT('workflow-task:', t.id, ':assignee:', t.assignee_id)
JOIN wf_instance i
    ON i.id = t.instance_id
    AND i.tenant_id = t.tenant_id
    AND i.business_type = n.business_type
SET n.project_ref = i.project_ref,
    n.project_name = i.project_name
WHERE n.project_ref IS NULL
  AND n.project_name IS NULL
  AND i.project_ref IS NOT NULL
  AND i.project_name IS NOT NULL;

UPDATE sys_notification n
JOIN wf_lifecycle_event e
    ON e.tenant_id = n.tenant_id
    AND e.business_type = n.business_type
    AND n.event_id = CONCAT('workflow-lifecycle:', e.event_id)
SET n.project_ref = e.project_ref,
    n.project_name = e.project_name
WHERE n.project_ref IS NULL
  AND n.project_name IS NULL
  AND e.project_ref IS NOT NULL
  AND e.project_name IS NOT NULL;
