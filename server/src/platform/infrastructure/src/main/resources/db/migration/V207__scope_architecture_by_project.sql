-- REQ-20260905-064：架构与环境能力按项目隔离。
-- 存量数据统一归入同租户唯一未删除项目 RDDMP-PLATFORM；存在待迁移数据但默认项目缺失或不唯一时失败关闭。

CREATE TEMPORARY TABLE tmp_arch_v156_default_project_guard (
    marker TINYINT NOT NULL,
    CONSTRAINT chk_tmp_arch_v156_default_project_guard CHECK (marker = 0)
) ENGINE=InnoDB;

INSERT INTO tmp_arch_v156_default_project_guard (marker)
SELECT 1
FROM (
    SELECT tenant_id FROM arch_physical_subsystem
    UNION SELECT tenant_id FROM arch_deployment_unit
    UNION SELECT tenant_id FROM arch_environment
    UNION SELECT tenant_id FROM arch_resource_request
    UNION SELECT tenant_id FROM arch_environment_instance
    UNION SELECT tenant_id FROM arch_setup_plan
) architecture_tenant
WHERE (
    SELECT COUNT(*)
    FROM pm_project project
    WHERE project.tenant_id = architecture_tenant.tenant_id
      AND project.project_code = 'RDDMP-PLATFORM'
      AND project.deleted = 0
) <> 1
LIMIT 1;

DROP TEMPORARY TABLE tmp_arch_v156_default_project_guard;

CREATE TEMPORARY TABLE tmp_arch_v156_default_project (
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    PRIMARY KEY (tenant_id)
) ENGINE=InnoDB;

INSERT INTO tmp_arch_v156_default_project (tenant_id, project_id)
SELECT tenant_id, MIN(id)
FROM pm_project
WHERE project_code = 'RDDMP-PLATFORM'
  AND deleted = 0
GROUP BY tenant_id
HAVING COUNT(*) = 1;

ALTER TABLE arch_physical_subsystem ADD COLUMN project_id BIGINT NULL COMMENT '归属项目主键' AFTER tenant_id;
ALTER TABLE arch_subsystem_change_application ADD COLUMN project_id BIGINT NULL COMMENT '归属项目主键' AFTER tenant_id;
ALTER TABLE arch_subsystem_physical_draft ADD COLUMN project_id BIGINT NULL COMMENT '归属项目主键' AFTER tenant_id;
ALTER TABLE arch_subsystem_change_history ADD COLUMN project_id BIGINT NULL COMMENT '归属项目主键' AFTER tenant_id;
ALTER TABLE arch_subsystem_change_lock ADD COLUMN project_id BIGINT NULL COMMENT '归属项目主键' AFTER tenant_id;
ALTER TABLE arch_subsystem_value_reservation ADD COLUMN project_id BIGINT NULL COMMENT '归属项目主键' AFTER tenant_id;
ALTER TABLE arch_subsystem_replacement ADD COLUMN project_id BIGINT NULL COMMENT '归属项目主键' AFTER tenant_id;
ALTER TABLE arch_subsystem_workflow_round ADD COLUMN project_id BIGINT NULL COMMENT '归属项目主键' AFTER tenant_id;
ALTER TABLE arch_subsystem_workflow_receipt ADD COLUMN project_id BIGINT NULL COMMENT '归属项目主键' AFTER tenant_id;

ALTER TABLE arch_deployment_unit ADD COLUMN project_id BIGINT NULL COMMENT '归属项目主键' AFTER tenant_id;
ALTER TABLE arch_deployment_unit_version ADD COLUMN project_id BIGINT NULL COMMENT '归属项目主键' AFTER tenant_id;
ALTER TABLE arch_deployment_unit_number_seq ADD COLUMN project_id BIGINT NULL COMMENT '归属项目主键' AFTER tenant_id;
ALTER TABLE arch_deployment_unit_import_batch ADD COLUMN project_id BIGINT NULL COMMENT '归属项目主键' AFTER tenant_id;
ALTER TABLE arch_deployment_unit_import_item ADD COLUMN project_id BIGINT NULL COMMENT '归属项目主键' AFTER tenant_id;
ALTER TABLE arch_deployment_unit_relation ADD COLUMN project_id BIGINT NULL COMMENT '归属项目主键' AFTER tenant_id;
ALTER TABLE arch_deployment_unit_relation_history ADD COLUMN project_id BIGINT NULL COMMENT '归属项目主键' AFTER tenant_id;

ALTER TABLE arch_environment ADD COLUMN project_id BIGINT NULL COMMENT '归属项目主键' AFTER tenant_id;
ALTER TABLE arch_resource_request ADD COLUMN project_id BIGINT NULL COMMENT '归属项目主键' AFTER tenant_id;
ALTER TABLE arch_resource_request_item ADD COLUMN project_id BIGINT NULL COMMENT '归属项目主键' AFTER tenant_id;
ALTER TABLE arch_resource_request_history ADD COLUMN project_id BIGINT NULL COMMENT '归属项目主键' AFTER tenant_id;
ALTER TABLE arch_resource_request_workflow_round ADD COLUMN project_id BIGINT NULL COMMENT '归属项目主键' AFTER tenant_id;
ALTER TABLE arch_resource_request_workflow_receipt ADD COLUMN project_id BIGINT NULL COMMENT '归属项目主键' AFTER tenant_id;
ALTER TABLE arch_environment_instance ADD COLUMN project_id BIGINT NULL COMMENT '归属项目主键' AFTER tenant_id;
ALTER TABLE arch_instance_disaster_recovery ADD COLUMN project_id BIGINT NULL COMMENT '归属项目主键' AFTER tenant_id;

ALTER TABLE arch_setup_plan ADD COLUMN project_id BIGINT NULL COMMENT '归属项目主键' AFTER tenant_id;
ALTER TABLE arch_plan_target ADD COLUMN project_id BIGINT NULL COMMENT '归属项目主键' AFTER tenant_id;
ALTER TABLE arch_plan_stage ADD COLUMN project_id BIGINT NULL COMMENT '归属项目主键' AFTER tenant_id;
ALTER TABLE arch_plan_task ADD COLUMN project_id BIGINT NULL COMMENT '归属项目主键' AFTER tenant_id;
ALTER TABLE arch_plan_task_participant ADD COLUMN project_id BIGINT NULL COMMENT '归属项目主键' AFTER tenant_id;
ALTER TABLE arch_plan_check_item ADD COLUMN project_id BIGINT NULL COMMENT '归属项目主键' AFTER tenant_id;
ALTER TABLE arch_plan_task_dependency ADD COLUMN project_id BIGINT NULL COMMENT '归属项目主键' AFTER tenant_id;
ALTER TABLE arch_plan_block ADD COLUMN project_id BIGINT NULL COMMENT '归属项目主键' AFTER tenant_id;
ALTER TABLE arch_plan_check_item_cancel_suggestion ADD COLUMN project_id BIGINT NULL COMMENT '归属项目主键' AFTER tenant_id;
ALTER TABLE arch_plan_event ADD COLUMN project_id BIGINT NULL COMMENT '归属项目主键' AFTER tenant_id;
ALTER TABLE arch_plan_work_order ADD COLUMN project_id BIGINT NULL COMMENT '归属项目主键' AFTER tenant_id;
ALTER TABLE arch_plan_stage_dependency ADD COLUMN project_id BIGINT NULL COMMENT '归属项目主键' AFTER tenant_id;
ALTER TABLE arch_setup_plan_activity ADD COLUMN project_id BIGINT NULL COMMENT '计划操作归属项目；模板操作为空' AFTER tenant_id;

UPDATE arch_physical_subsystem row_value JOIN tmp_arch_v156_default_project scope ON scope.tenant_id = row_value.tenant_id SET row_value.project_id = scope.project_id;
UPDATE arch_subsystem_change_application row_value JOIN tmp_arch_v156_default_project scope ON scope.tenant_id = row_value.tenant_id SET row_value.project_id = scope.project_id;
UPDATE arch_subsystem_physical_draft row_value JOIN tmp_arch_v156_default_project scope ON scope.tenant_id = row_value.tenant_id SET row_value.project_id = scope.project_id;
UPDATE arch_subsystem_change_history row_value JOIN tmp_arch_v156_default_project scope ON scope.tenant_id = row_value.tenant_id SET row_value.project_id = scope.project_id;
UPDATE arch_subsystem_change_lock row_value JOIN tmp_arch_v156_default_project scope ON scope.tenant_id = row_value.tenant_id SET row_value.project_id = scope.project_id;
UPDATE arch_subsystem_value_reservation row_value JOIN tmp_arch_v156_default_project scope ON scope.tenant_id = row_value.tenant_id SET row_value.project_id = scope.project_id;
UPDATE arch_subsystem_replacement row_value JOIN tmp_arch_v156_default_project scope ON scope.tenant_id = row_value.tenant_id SET row_value.project_id = scope.project_id;
UPDATE arch_subsystem_workflow_round row_value JOIN tmp_arch_v156_default_project scope ON scope.tenant_id = row_value.tenant_id SET row_value.project_id = scope.project_id;
UPDATE arch_subsystem_workflow_receipt row_value JOIN tmp_arch_v156_default_project scope ON scope.tenant_id = row_value.tenant_id SET row_value.project_id = scope.project_id;

UPDATE arch_deployment_unit row_value JOIN tmp_arch_v156_default_project scope ON scope.tenant_id = row_value.tenant_id SET row_value.project_id = scope.project_id;
UPDATE arch_deployment_unit_version row_value JOIN tmp_arch_v156_default_project scope ON scope.tenant_id = row_value.tenant_id SET row_value.project_id = scope.project_id;
UPDATE arch_deployment_unit_number_seq row_value JOIN tmp_arch_v156_default_project scope ON scope.tenant_id = row_value.tenant_id SET row_value.project_id = scope.project_id;
UPDATE arch_deployment_unit_import_batch row_value JOIN tmp_arch_v156_default_project scope ON scope.tenant_id = row_value.tenant_id SET row_value.project_id = scope.project_id;
UPDATE arch_deployment_unit_import_item row_value JOIN tmp_arch_v156_default_project scope ON scope.tenant_id = row_value.tenant_id SET row_value.project_id = scope.project_id;
UPDATE arch_deployment_unit_relation row_value JOIN tmp_arch_v156_default_project scope ON scope.tenant_id = row_value.tenant_id SET row_value.project_id = scope.project_id;
UPDATE arch_deployment_unit_relation_history row_value JOIN tmp_arch_v156_default_project scope ON scope.tenant_id = row_value.tenant_id SET row_value.project_id = scope.project_id;

UPDATE arch_environment row_value JOIN tmp_arch_v156_default_project scope ON scope.tenant_id = row_value.tenant_id SET row_value.project_id = scope.project_id;
UPDATE arch_resource_request row_value JOIN tmp_arch_v156_default_project scope ON scope.tenant_id = row_value.tenant_id SET row_value.project_id = scope.project_id;
UPDATE arch_resource_request_item row_value JOIN tmp_arch_v156_default_project scope ON scope.tenant_id = row_value.tenant_id SET row_value.project_id = scope.project_id;
UPDATE arch_resource_request_history row_value JOIN tmp_arch_v156_default_project scope ON scope.tenant_id = row_value.tenant_id SET row_value.project_id = scope.project_id;
UPDATE arch_resource_request_workflow_round row_value JOIN tmp_arch_v156_default_project scope ON scope.tenant_id = row_value.tenant_id SET row_value.project_id = scope.project_id;
UPDATE arch_resource_request_workflow_receipt row_value JOIN tmp_arch_v156_default_project scope ON scope.tenant_id = row_value.tenant_id SET row_value.project_id = scope.project_id;
UPDATE arch_environment_instance row_value JOIN tmp_arch_v156_default_project scope ON scope.tenant_id = row_value.tenant_id SET row_value.project_id = scope.project_id;
UPDATE arch_instance_disaster_recovery row_value JOIN tmp_arch_v156_default_project scope ON scope.tenant_id = row_value.tenant_id SET row_value.project_id = scope.project_id;

UPDATE arch_setup_plan row_value JOIN tmp_arch_v156_default_project scope ON scope.tenant_id = row_value.tenant_id SET row_value.project_id = scope.project_id;
UPDATE arch_plan_target row_value JOIN tmp_arch_v156_default_project scope ON scope.tenant_id = row_value.tenant_id SET row_value.project_id = scope.project_id;
UPDATE arch_plan_stage row_value JOIN tmp_arch_v156_default_project scope ON scope.tenant_id = row_value.tenant_id SET row_value.project_id = scope.project_id;
UPDATE arch_plan_task row_value JOIN tmp_arch_v156_default_project scope ON scope.tenant_id = row_value.tenant_id SET row_value.project_id = scope.project_id;
UPDATE arch_plan_task_participant row_value JOIN tmp_arch_v156_default_project scope ON scope.tenant_id = row_value.tenant_id SET row_value.project_id = scope.project_id;
UPDATE arch_plan_check_item row_value JOIN tmp_arch_v156_default_project scope ON scope.tenant_id = row_value.tenant_id SET row_value.project_id = scope.project_id;
UPDATE arch_plan_task_dependency row_value JOIN tmp_arch_v156_default_project scope ON scope.tenant_id = row_value.tenant_id SET row_value.project_id = scope.project_id;
UPDATE arch_plan_block row_value JOIN tmp_arch_v156_default_project scope ON scope.tenant_id = row_value.tenant_id SET row_value.project_id = scope.project_id;
UPDATE arch_plan_check_item_cancel_suggestion row_value JOIN tmp_arch_v156_default_project scope ON scope.tenant_id = row_value.tenant_id SET row_value.project_id = scope.project_id;
UPDATE arch_plan_event row_value JOIN tmp_arch_v156_default_project scope ON scope.tenant_id = row_value.tenant_id SET row_value.project_id = scope.project_id;
UPDATE arch_plan_work_order row_value JOIN tmp_arch_v156_default_project scope ON scope.tenant_id = row_value.tenant_id SET row_value.project_id = scope.project_id;
UPDATE arch_plan_stage_dependency row_value JOIN tmp_arch_v156_default_project scope ON scope.tenant_id = row_value.tenant_id SET row_value.project_id = scope.project_id;
UPDATE arch_setup_plan_activity row_value JOIN tmp_arch_v156_default_project scope ON scope.tenant_id = row_value.tenant_id SET row_value.project_id = scope.project_id WHERE row_value.scope_type = 'PLAN';

DROP TEMPORARY TABLE tmp_arch_v156_default_project;

ALTER TABLE arch_physical_subsystem MODIFY COLUMN project_id BIGINT NOT NULL COMMENT '归属项目主键';
ALTER TABLE arch_subsystem_change_application MODIFY COLUMN project_id BIGINT NOT NULL COMMENT '归属项目主键';
ALTER TABLE arch_subsystem_physical_draft MODIFY COLUMN project_id BIGINT NOT NULL COMMENT '归属项目主键';
ALTER TABLE arch_subsystem_change_history MODIFY COLUMN project_id BIGINT NOT NULL COMMENT '归属项目主键';
ALTER TABLE arch_subsystem_change_lock MODIFY COLUMN project_id BIGINT NOT NULL COMMENT '归属项目主键';
ALTER TABLE arch_subsystem_value_reservation MODIFY COLUMN project_id BIGINT NOT NULL COMMENT '归属项目主键';
ALTER TABLE arch_subsystem_replacement MODIFY COLUMN project_id BIGINT NOT NULL COMMENT '归属项目主键';
ALTER TABLE arch_subsystem_workflow_round MODIFY COLUMN project_id BIGINT NOT NULL COMMENT '归属项目主键';
ALTER TABLE arch_subsystem_workflow_receipt MODIFY COLUMN project_id BIGINT NOT NULL COMMENT '归属项目主键';
ALTER TABLE arch_deployment_unit MODIFY COLUMN project_id BIGINT NOT NULL COMMENT '归属项目主键';
ALTER TABLE arch_deployment_unit_version MODIFY COLUMN project_id BIGINT NOT NULL COMMENT '归属项目主键';
ALTER TABLE arch_deployment_unit_number_seq MODIFY COLUMN project_id BIGINT NOT NULL COMMENT '归属项目主键';
ALTER TABLE arch_deployment_unit_import_batch MODIFY COLUMN project_id BIGINT NOT NULL COMMENT '归属项目主键';
ALTER TABLE arch_deployment_unit_import_item MODIFY COLUMN project_id BIGINT NOT NULL COMMENT '归属项目主键';
ALTER TABLE arch_deployment_unit_relation MODIFY COLUMN project_id BIGINT NOT NULL COMMENT '归属项目主键';
ALTER TABLE arch_deployment_unit_relation_history MODIFY COLUMN project_id BIGINT NOT NULL COMMENT '归属项目主键';
ALTER TABLE arch_environment MODIFY COLUMN project_id BIGINT NOT NULL COMMENT '归属项目主键';
ALTER TABLE arch_resource_request MODIFY COLUMN project_id BIGINT NOT NULL COMMENT '归属项目主键';
ALTER TABLE arch_resource_request_item MODIFY COLUMN project_id BIGINT NOT NULL COMMENT '归属项目主键';
ALTER TABLE arch_resource_request_history MODIFY COLUMN project_id BIGINT NOT NULL COMMENT '归属项目主键';
ALTER TABLE arch_resource_request_workflow_round MODIFY COLUMN project_id BIGINT NOT NULL COMMENT '归属项目主键';
ALTER TABLE arch_resource_request_workflow_receipt MODIFY COLUMN project_id BIGINT NOT NULL COMMENT '归属项目主键';
ALTER TABLE arch_environment_instance MODIFY COLUMN project_id BIGINT NOT NULL COMMENT '归属项目主键';
ALTER TABLE arch_instance_disaster_recovery MODIFY COLUMN project_id BIGINT NOT NULL COMMENT '归属项目主键';
ALTER TABLE arch_setup_plan MODIFY COLUMN project_id BIGINT NOT NULL COMMENT '归属项目主键';
ALTER TABLE arch_plan_target MODIFY COLUMN project_id BIGINT NOT NULL COMMENT '归属项目主键';
ALTER TABLE arch_plan_stage MODIFY COLUMN project_id BIGINT NOT NULL COMMENT '归属项目主键';
ALTER TABLE arch_plan_task MODIFY COLUMN project_id BIGINT NOT NULL COMMENT '归属项目主键';
ALTER TABLE arch_plan_task_participant MODIFY COLUMN project_id BIGINT NOT NULL COMMENT '归属项目主键';
ALTER TABLE arch_plan_check_item MODIFY COLUMN project_id BIGINT NOT NULL COMMENT '归属项目主键';
ALTER TABLE arch_plan_task_dependency MODIFY COLUMN project_id BIGINT NOT NULL COMMENT '归属项目主键';
ALTER TABLE arch_plan_block MODIFY COLUMN project_id BIGINT NOT NULL COMMENT '归属项目主键';
ALTER TABLE arch_plan_check_item_cancel_suggestion MODIFY COLUMN project_id BIGINT NOT NULL COMMENT '归属项目主键';
ALTER TABLE arch_plan_event MODIFY COLUMN project_id BIGINT NOT NULL COMMENT '归属项目主键';
ALTER TABLE arch_plan_work_order MODIFY COLUMN project_id BIGINT NOT NULL COMMENT '归属项目主键';
ALTER TABLE arch_plan_stage_dependency MODIFY COLUMN project_id BIGINT NOT NULL COMMENT '归属项目主键';

ALTER TABLE arch_setup_plan_activity
    ADD CONSTRAINT chk_arch_setup_plan_activity_project
        CHECK ((scope_type = 'TEMPLATE' AND project_id IS NULL) OR (scope_type = 'PLAN' AND project_id IS NOT NULL));

ALTER TABLE arch_physical_subsystem
    DROP INDEX uk_arch_physical_code,
    DROP INDEX uk_arch_physical_name,
    DROP INDEX uk_arch_physical_english_name,
    ADD UNIQUE KEY uk_arch_physical_code (tenant_id, project_id, code),
    ADD UNIQUE KEY uk_arch_physical_name (tenant_id, project_id, name),
    ADD UNIQUE KEY uk_arch_physical_english_name (tenant_id, project_id, english_name),
    ADD UNIQUE KEY uk_arch_physical_project_id (tenant_id, project_id, id),
    ADD KEY idx_arch_physical_project_list (tenant_id, project_id, deleted, id);

ALTER TABLE arch_subsystem_value_reservation
    ADD KEY idx_arch_subsystem_value_reservation_application (tenant_id, application_id),
    DROP PRIMARY KEY,
    DROP INDEX uk_arch_subsystem_value_reservation_application_line,
    ADD PRIMARY KEY (tenant_id, project_id, reservation_scope, normalized_value),
    ADD UNIQUE KEY uk_arch_subsystem_value_reservation_application_line
        (tenant_id, project_id, application_id, reservation_scope, line_no);

ALTER TABLE arch_subsystem_change_lock
    DROP PRIMARY KEY,
    ADD PRIMARY KEY (tenant_id, project_id, target_kind, target_id);

ALTER TABLE arch_subsystem_workflow_receipt
    DROP INDEX uk_arch_subsystem_workflow_receipt,
    ADD UNIQUE KEY uk_arch_subsystem_workflow_receipt
        (tenant_id, project_id, event_id, subscriber_key);

ALTER TABLE arch_deployment_unit
    DROP INDEX uk_arch_deployment_unit_code,
    DROP INDEX uk_arch_deployment_unit_name,
    ADD UNIQUE KEY uk_arch_deployment_unit_code (tenant_id, project_id, code),
    ADD UNIQUE KEY uk_arch_deployment_unit_name (tenant_id, project_id, name),
    ADD UNIQUE KEY uk_arch_deployment_unit_project_id (tenant_id, project_id, id),
    ADD KEY idx_arch_deployment_unit_project_list (tenant_id, project_id, status, id);

ALTER TABLE arch_environment
    DROP INDEX uk_arch_environment_code,
    DROP INDEX uk_arch_environment_name,
    ADD UNIQUE KEY uk_arch_environment_code (tenant_id, project_id, code),
    ADD UNIQUE KEY uk_arch_environment_name (tenant_id, project_id, name),
    ADD UNIQUE KEY uk_arch_environment_project_id (tenant_id, project_id, id),
    ADD KEY idx_arch_environment_project_list (tenant_id, project_id, status, id);

ALTER TABLE arch_resource_request
    DROP INDEX uk_arch_resource_request_no,
    ADD UNIQUE KEY uk_arch_resource_request_no (tenant_id, project_id, request_no),
    ADD UNIQUE KEY uk_arch_resource_request_project_id (tenant_id, project_id, id),
    ADD KEY idx_arch_resource_request_project_list (tenant_id, project_id, status, updated_at);

ALTER TABLE arch_environment_instance
    DROP INDEX uk_arch_env_instance_no,
    ADD UNIQUE KEY uk_arch_env_instance_no (tenant_id, project_id, instance_no),
    ADD UNIQUE KEY uk_arch_env_instance_project_id (tenant_id, project_id, id),
    ADD KEY idx_arch_env_instance_project_list (tenant_id, project_id, status, id);

ALTER TABLE arch_setup_plan
    DROP INDEX uk_arch_setup_plan_no,
    ADD UNIQUE KEY uk_arch_setup_plan_no (tenant_id, project_id, plan_no),
    ADD UNIQUE KEY uk_arch_setup_plan_project_id (tenant_id, project_id, id),
    ADD KEY idx_arch_setup_plan_project_list (tenant_id, project_id, status, id);

ALTER TABLE arch_subsystem_change_application ADD KEY idx_arch_subsystem_application_project (tenant_id, project_id, status, updated_at);
ALTER TABLE arch_deployment_unit_import_batch ADD KEY idx_arch_deployment_import_project (tenant_id, project_id, created_at);
ALTER TABLE arch_plan_target ADD KEY idx_arch_plan_target_project (tenant_id, project_id, plan_id, status);
ALTER TABLE arch_plan_stage ADD KEY idx_arch_plan_stage_project (tenant_id, project_id, plan_id, status, sort_no);
ALTER TABLE arch_plan_task ADD KEY idx_arch_plan_task_project (tenant_id, project_id, plan_id, status);
ALTER TABLE arch_plan_event ADD KEY idx_arch_plan_event_project (tenant_id, project_id, plan_id, occurred_at);
