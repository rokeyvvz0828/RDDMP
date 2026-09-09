-- REQ-20260905-065：架构管理剩余网络与决策业务按项目隔离。
-- 存量数据只归入同租户唯一活动 RDDMP-PLATFORM；默认项目异常时在修改结构前失败关闭。

CREATE TEMPORARY TABLE tmp_arch_v158_default_project_guard (
    marker TINYINT NOT NULL,
    CONSTRAINT chk_tmp_arch_v158_default_project_guard CHECK (marker = 0)
) ENGINE=InnoDB;

INSERT INTO tmp_arch_v158_default_project_guard (marker)
SELECT 1
FROM (
    SELECT tenant_id FROM arch_network_work_order
    UNION SELECT tenant_id FROM arch_network_zone
    UNION SELECT tenant_id FROM arch_external_network_address
    UNION SELECT tenant_id FROM arch_network_access_application
    UNION SELECT tenant_id FROM arch_network_access_relation
    UNION SELECT tenant_id FROM arch_network_access_exemption_rule
    UNION SELECT tenant_id FROM arch_decision_matter
) target_tenant
WHERE (
    SELECT COUNT(*)
    FROM pm_project project
    WHERE project.tenant_id = target_tenant.tenant_id
      AND project.project_code = 'RDDMP-PLATFORM'
      AND project.deleted = 0
) <> 1
LIMIT 1;

DROP TEMPORARY TABLE tmp_arch_v158_default_project_guard;

CREATE TEMPORARY TABLE tmp_arch_v158_default_project (
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    PRIMARY KEY (tenant_id)
) ENGINE=InnoDB;

INSERT INTO tmp_arch_v158_default_project (tenant_id, project_id)
SELECT tenant_id, MIN(id)
FROM pm_project
WHERE project_code = 'RDDMP-PLATFORM'
  AND deleted = 0
GROUP BY tenant_id
HAVING COUNT(*) = 1;

ALTER TABLE arch_network_work_order ADD COLUMN project_id BIGINT NULL COMMENT '归属项目主键' AFTER tenant_id;
ALTER TABLE arch_network_work_order_history ADD COLUMN project_id BIGINT NULL COMMENT '归属项目主键' AFTER tenant_id;
ALTER TABLE arch_network_workflow_round ADD COLUMN project_id BIGINT NULL COMMENT '归属项目主键' AFTER tenant_id;
ALTER TABLE arch_network_workflow_receipt ADD COLUMN project_id BIGINT NULL COMMENT '归属项目主键' AFTER tenant_id;

ALTER TABLE arch_network_zone ADD COLUMN project_id BIGINT NULL COMMENT '归属项目主键' AFTER tenant_id;
ALTER TABLE arch_network_zone_subnet ADD COLUMN project_id BIGINT NULL COMMENT '归属项目主键' AFTER tenant_id;
ALTER TABLE arch_external_network_address ADD COLUMN project_id BIGINT NULL COMMENT '归属项目主键' AFTER tenant_id;
ALTER TABLE arch_network_access_application ADD COLUMN project_id BIGINT NULL COMMENT '归属项目主键' AFTER tenant_id;
ALTER TABLE arch_network_access_relation ADD COLUMN project_id BIGINT NULL COMMENT '归属项目主键' AFTER tenant_id;
ALTER TABLE arch_network_access_application_history ADD COLUMN project_id BIGINT NULL COMMENT '归属项目主键' AFTER tenant_id;
ALTER TABLE arch_network_access_workflow_round ADD COLUMN project_id BIGINT NULL COMMENT '归属项目主键' AFTER tenant_id;
ALTER TABLE arch_network_access_workflow_receipt ADD COLUMN project_id BIGINT NULL COMMENT '归属项目主键' AFTER tenant_id;
ALTER TABLE arch_network_access_exemption_rule ADD COLUMN project_id BIGINT NULL COMMENT '归属项目主键' AFTER tenant_id;

ALTER TABLE arch_decision_matter ADD COLUMN project_id BIGINT NULL COMMENT '归属项目主键' AFTER tenant_id;
ALTER TABLE arch_decision_material ADD COLUMN project_id BIGINT NULL COMMENT '归属项目主键' AFTER tenant_id;
ALTER TABLE arch_decision_review ADD COLUMN project_id BIGINT NULL COMMENT '归属项目主键' AFTER tenant_id;
ALTER TABLE arch_decision_review_participant ADD COLUMN project_id BIGINT NULL COMMENT '归属项目主键' AFTER tenant_id;
ALTER TABLE arch_decision_action_item ADD COLUMN project_id BIGINT NULL COMMENT '归属项目主键' AFTER tenant_id;
ALTER TABLE arch_decision_conclusion ADD COLUMN project_id BIGINT NULL COMMENT '归属项目主键' AFTER tenant_id;
ALTER TABLE arch_decision_publication_intent ADD COLUMN project_id BIGINT NULL COMMENT '归属项目主键' AFTER tenant_id;
ALTER TABLE arch_decision_supersession ADD COLUMN project_id BIGINT NULL COMMENT '归属项目主键' AFTER tenant_id;
ALTER TABLE arch_decision_number_sequence ADD COLUMN project_id BIGINT NULL COMMENT '归属项目主键' AFTER tenant_id;
ALTER TABLE arch_decision_workflow_round ADD COLUMN project_id BIGINT NULL COMMENT '归属项目主键' AFTER tenant_id;
ALTER TABLE arch_decision_workflow_receipt ADD COLUMN project_id BIGINT NULL COMMENT '归属项目主键' AFTER tenant_id;

UPDATE arch_network_work_order row_value JOIN tmp_arch_v158_default_project scope ON scope.tenant_id = row_value.tenant_id SET row_value.project_id = scope.project_id;
UPDATE arch_network_work_order_history row_value JOIN arch_network_work_order parent ON parent.tenant_id = row_value.tenant_id AND parent.id = row_value.work_order_id SET row_value.project_id = parent.project_id;
UPDATE arch_network_workflow_round row_value JOIN arch_network_work_order parent ON parent.tenant_id = row_value.tenant_id AND parent.id = row_value.work_order_id SET row_value.project_id = parent.project_id;
UPDATE arch_network_workflow_receipt row_value JOIN arch_network_work_order parent ON parent.tenant_id = row_value.tenant_id AND parent.id = row_value.work_order_id SET row_value.project_id = parent.project_id WHERE row_value.work_order_id IS NOT NULL;
UPDATE arch_network_workflow_receipt row_value JOIN tmp_arch_v158_default_project scope ON scope.tenant_id = row_value.tenant_id SET row_value.project_id = scope.project_id WHERE row_value.project_id IS NULL;

UPDATE arch_network_zone row_value JOIN tmp_arch_v158_default_project scope ON scope.tenant_id = row_value.tenant_id SET row_value.project_id = scope.project_id;
UPDATE arch_network_zone_subnet row_value JOIN arch_network_zone parent ON parent.tenant_id = row_value.tenant_id AND parent.id = row_value.network_zone_id SET row_value.project_id = parent.project_id;
UPDATE arch_external_network_address row_value JOIN tmp_arch_v158_default_project scope ON scope.tenant_id = row_value.tenant_id SET row_value.project_id = scope.project_id;
UPDATE arch_network_access_application row_value JOIN tmp_arch_v158_default_project scope ON scope.tenant_id = row_value.tenant_id SET row_value.project_id = scope.project_id;
UPDATE arch_network_access_relation row_value JOIN arch_network_access_application parent ON parent.tenant_id = row_value.tenant_id AND parent.id = row_value.application_id SET row_value.project_id = parent.project_id;
UPDATE arch_network_access_application_history row_value JOIN arch_network_access_application parent ON parent.tenant_id = row_value.tenant_id AND parent.id = row_value.application_id SET row_value.project_id = parent.project_id;
UPDATE arch_network_access_workflow_round row_value JOIN arch_network_access_application parent ON parent.tenant_id = row_value.tenant_id AND parent.id = row_value.application_id SET row_value.project_id = parent.project_id;
UPDATE arch_network_access_workflow_receipt row_value JOIN arch_network_access_application parent ON parent.tenant_id = row_value.tenant_id AND parent.id = row_value.application_id SET row_value.project_id = parent.project_id WHERE row_value.application_id IS NOT NULL;
UPDATE arch_network_access_workflow_receipt row_value JOIN tmp_arch_v158_default_project scope ON scope.tenant_id = row_value.tenant_id SET row_value.project_id = scope.project_id WHERE row_value.project_id IS NULL;
UPDATE arch_network_access_exemption_rule row_value JOIN tmp_arch_v158_default_project scope ON scope.tenant_id = row_value.tenant_id SET row_value.project_id = scope.project_id;

UPDATE arch_decision_matter row_value JOIN tmp_arch_v158_default_project scope ON scope.tenant_id = row_value.tenant_id SET row_value.project_id = scope.project_id;
UPDATE arch_decision_material row_value JOIN arch_decision_matter parent ON parent.tenant_id = row_value.tenant_id AND parent.id = row_value.matter_id SET row_value.project_id = parent.project_id;
UPDATE arch_decision_review row_value JOIN arch_decision_matter parent ON parent.tenant_id = row_value.tenant_id AND parent.id = row_value.matter_id SET row_value.project_id = parent.project_id;
UPDATE arch_decision_review_participant row_value JOIN arch_decision_review parent ON parent.tenant_id = row_value.tenant_id AND parent.id = row_value.review_id SET row_value.project_id = parent.project_id;
UPDATE arch_decision_action_item row_value JOIN arch_decision_review parent ON parent.tenant_id = row_value.tenant_id AND parent.id = row_value.review_id SET row_value.project_id = parent.project_id;
UPDATE arch_decision_conclusion row_value JOIN arch_decision_matter parent ON parent.tenant_id = row_value.tenant_id AND parent.id = row_value.matter_id SET row_value.project_id = parent.project_id;
UPDATE arch_decision_publication_intent row_value JOIN arch_decision_matter parent ON parent.tenant_id = row_value.tenant_id AND parent.id = row_value.matter_id SET row_value.project_id = parent.project_id;
UPDATE arch_decision_supersession row_value JOIN arch_decision_conclusion parent ON parent.tenant_id = row_value.tenant_id AND parent.id = row_value.conclusion_id SET row_value.project_id = parent.project_id;
UPDATE arch_decision_number_sequence row_value JOIN tmp_arch_v158_default_project scope ON scope.tenant_id = row_value.tenant_id SET row_value.project_id = scope.project_id;
UPDATE arch_decision_workflow_round row_value JOIN arch_decision_matter parent ON parent.tenant_id = row_value.tenant_id AND parent.id = row_value.matter_id SET row_value.project_id = parent.project_id;
UPDATE arch_decision_workflow_receipt row_value JOIN arch_decision_matter parent ON parent.tenant_id = row_value.tenant_id AND parent.id = row_value.matter_id SET row_value.project_id = parent.project_id WHERE row_value.matter_id IS NOT NULL;
UPDATE arch_decision_workflow_receipt row_value JOIN tmp_arch_v158_default_project scope ON scope.tenant_id = row_value.tenant_id SET row_value.project_id = scope.project_id WHERE row_value.project_id IS NULL;

DROP TEMPORARY TABLE tmp_arch_v158_default_project;

ALTER TABLE arch_network_work_order MODIFY COLUMN project_id BIGINT NOT NULL COMMENT '归属项目主键';
ALTER TABLE arch_network_work_order_history MODIFY COLUMN project_id BIGINT NOT NULL COMMENT '归属项目主键';
ALTER TABLE arch_network_workflow_round MODIFY COLUMN project_id BIGINT NOT NULL COMMENT '归属项目主键';
ALTER TABLE arch_network_workflow_receipt MODIFY COLUMN project_id BIGINT NOT NULL COMMENT '归属项目主键';
ALTER TABLE arch_network_zone MODIFY COLUMN project_id BIGINT NOT NULL COMMENT '归属项目主键';
ALTER TABLE arch_network_zone_subnet MODIFY COLUMN project_id BIGINT NOT NULL COMMENT '归属项目主键';
ALTER TABLE arch_external_network_address MODIFY COLUMN project_id BIGINT NOT NULL COMMENT '归属项目主键';
ALTER TABLE arch_network_access_application MODIFY COLUMN project_id BIGINT NOT NULL COMMENT '归属项目主键';
ALTER TABLE arch_network_access_relation MODIFY COLUMN project_id BIGINT NOT NULL COMMENT '归属项目主键';
ALTER TABLE arch_network_access_application_history MODIFY COLUMN project_id BIGINT NOT NULL COMMENT '归属项目主键';
ALTER TABLE arch_network_access_workflow_round MODIFY COLUMN project_id BIGINT NOT NULL COMMENT '归属项目主键';
ALTER TABLE arch_network_access_workflow_receipt MODIFY COLUMN project_id BIGINT NOT NULL COMMENT '归属项目主键';
ALTER TABLE arch_network_access_exemption_rule MODIFY COLUMN project_id BIGINT NOT NULL COMMENT '归属项目主键';
ALTER TABLE arch_decision_matter MODIFY COLUMN project_id BIGINT NOT NULL COMMENT '归属项目主键';
ALTER TABLE arch_decision_material MODIFY COLUMN project_id BIGINT NOT NULL COMMENT '归属项目主键';
ALTER TABLE arch_decision_review MODIFY COLUMN project_id BIGINT NOT NULL COMMENT '归属项目主键';
ALTER TABLE arch_decision_review_participant MODIFY COLUMN project_id BIGINT NOT NULL COMMENT '归属项目主键';
ALTER TABLE arch_decision_action_item MODIFY COLUMN project_id BIGINT NOT NULL COMMENT '归属项目主键';
ALTER TABLE arch_decision_conclusion MODIFY COLUMN project_id BIGINT NOT NULL COMMENT '归属项目主键';
ALTER TABLE arch_decision_publication_intent MODIFY COLUMN project_id BIGINT NOT NULL COMMENT '归属项目主键';
ALTER TABLE arch_decision_supersession MODIFY COLUMN project_id BIGINT NOT NULL COMMENT '归属项目主键';
ALTER TABLE arch_decision_number_sequence MODIFY COLUMN project_id BIGINT NOT NULL COMMENT '归属项目主键';
ALTER TABLE arch_decision_workflow_round MODIFY COLUMN project_id BIGINT NOT NULL COMMENT '归属项目主键';
ALTER TABLE arch_decision_workflow_receipt MODIFY COLUMN project_id BIGINT NOT NULL COMMENT '归属项目主键';

ALTER TABLE arch_network_work_order
    ADD UNIQUE KEY uk_arch_network_work_order_project_id (tenant_id, project_id, id),
    ADD KEY idx_arch_network_work_order_project_list (tenant_id, project_id, status, updated_at);
ALTER TABLE arch_network_workflow_round
    ADD KEY idx_arch_network_workflow_round_order_fk (tenant_id, work_order_id);
ALTER TABLE arch_network_workflow_round
    DROP INDEX uk_arch_network_workflow_round_order,
    DROP INDEX uk_arch_network_workflow_round_instance,
    ADD UNIQUE KEY uk_arch_network_workflow_round_order (tenant_id, project_id, work_order_id, round_no),
    ADD UNIQUE KEY uk_arch_network_workflow_round_instance (tenant_id, project_id, workflow_instance_id);
ALTER TABLE arch_network_workflow_receipt
    DROP INDEX uk_arch_network_workflow_receipt,
    ADD UNIQUE KEY uk_arch_network_workflow_receipt (tenant_id, project_id, event_id, subscriber_key);

ALTER TABLE arch_network_zone
    DROP INDEX uk_arch_network_zone_code,
    DROP INDEX uk_arch_network_zone_name_parent,
    ADD UNIQUE KEY uk_arch_network_zone_project_id (tenant_id, project_id, id),
    ADD UNIQUE KEY uk_arch_network_zone_code (tenant_id, project_id, code),
    ADD UNIQUE KEY uk_arch_network_zone_name (tenant_id, project_id, name),
    ADD KEY idx_arch_network_zone_project_list (tenant_id, project_id, status, id);
ALTER TABLE arch_network_zone_subnet
    DROP INDEX uk_arch_network_zone_subnet_cidr,
    ADD UNIQUE KEY uk_arch_network_zone_subnet_project_id (tenant_id, project_id, id),
    ADD UNIQUE KEY uk_arch_network_zone_subnet_cidr (tenant_id, project_id, cidr_block);
ALTER TABLE arch_external_network_address
    DROP INDEX uk_arch_external_network_address_value,
    ADD UNIQUE KEY uk_arch_external_network_address_project_id (tenant_id, project_id, id),
    ADD UNIQUE KEY uk_arch_external_network_address_value (tenant_id, project_id, address_type, address_value);
ALTER TABLE arch_network_access_application
    DROP INDEX uk_arch_network_access_app_no,
    ADD UNIQUE KEY uk_arch_network_access_app_project_id (tenant_id, project_id, id),
    ADD UNIQUE KEY uk_arch_network_access_app_no (tenant_id, project_id, application_no),
    ADD KEY idx_arch_network_access_app_project_list (tenant_id, project_id, status, updated_at);
ALTER TABLE arch_network_access_relation
    DROP INDEX uk_arch_network_access_relation_no,
    ADD UNIQUE KEY uk_arch_network_access_relation_project_id (tenant_id, project_id, id),
    ADD UNIQUE KEY uk_arch_network_access_relation_no (tenant_id, project_id, relation_no),
    ADD KEY idx_arch_network_access_relation_project_list (tenant_id, project_id, status, updated_at);
ALTER TABLE arch_network_access_workflow_round
    ADD KEY idx_arch_network_access_workflow_round_app_fk (tenant_id, application_id);
ALTER TABLE arch_network_access_workflow_round
    DROP INDEX uk_arch_network_access_workflow_round_app,
    DROP INDEX uk_arch_network_access_workflow_round_instance,
    ADD UNIQUE KEY uk_arch_network_access_workflow_round_app (tenant_id, project_id, application_id, round_no),
    ADD UNIQUE KEY uk_arch_network_access_workflow_round_instance (tenant_id, project_id, workflow_instance_id);
ALTER TABLE arch_network_access_workflow_receipt
    DROP INDEX uk_arch_network_access_workflow_receipt,
    ADD UNIQUE KEY uk_arch_network_access_workflow_receipt (tenant_id, project_id, event_id, subscriber_key);
ALTER TABLE arch_network_access_exemption_rule
    DROP INDEX uk_arch_network_access_exemption_rule_code,
    ADD UNIQUE KEY uk_arch_network_access_exemption_rule_project_id (tenant_id, project_id, id),
    ADD UNIQUE KEY uk_arch_network_access_exemption_rule_code (tenant_id, project_id, rule_code);

ALTER TABLE arch_decision_matter
    DROP INDEX uk_arch_decision_matter_no,
    ADD UNIQUE KEY uk_arch_decision_matter_project_id (tenant_id, project_id, id),
    ADD UNIQUE KEY uk_arch_decision_matter_no (tenant_id, project_id, matter_no),
    ADD KEY idx_arch_decision_matter_project_list (tenant_id, project_id, status, updated_at);
ALTER TABLE arch_decision_material
    ADD UNIQUE KEY uk_arch_decision_material_project_id (tenant_id, project_id, id);
ALTER TABLE arch_decision_review
    DROP INDEX uk_arch_decision_review_matter_no,
    ADD UNIQUE KEY uk_arch_decision_review_project_id (tenant_id, project_id, id),
    ADD UNIQUE KEY uk_arch_decision_review_matter_no (tenant_id, project_id, matter_id, review_no);
ALTER TABLE arch_decision_review_participant
    DROP INDEX uk_arch_decision_participant_tenant_review,
    ADD UNIQUE KEY uk_arch_decision_participant_project_review (tenant_id, project_id, review_id, user_id);
ALTER TABLE arch_decision_action_item
    ADD UNIQUE KEY uk_arch_decision_action_project_id (tenant_id, project_id, id);
ALTER TABLE arch_decision_conclusion
    ADD KEY idx_arch_decision_conclusion_matter_fk (tenant_id, matter_id);
ALTER TABLE arch_decision_conclusion
    DROP INDEX uk_arch_decision_conclusion_matter,
    ADD UNIQUE KEY uk_arch_decision_conclusion_project_id (tenant_id, project_id, id),
    ADD UNIQUE KEY uk_arch_decision_conclusion_matter (tenant_id, project_id, matter_id);
ALTER TABLE arch_decision_publication_intent
    ADD KEY idx_arch_decision_intent_matter_fk (tenant_id, matter_id);
ALTER TABLE arch_decision_publication_intent
    DROP INDEX uk_arch_decision_intent_tenant,
    ADD UNIQUE KEY uk_arch_decision_intent_project (tenant_id, project_id, matter_id);
ALTER TABLE arch_decision_supersession
    ADD KEY idx_arch_decision_supersession_conclusion_fk (tenant_id, conclusion_id);
ALTER TABLE arch_decision_supersession
    DROP INDEX uk_arch_decision_supersession_pair,
    ADD UNIQUE KEY uk_arch_decision_supersession_project_id (tenant_id, project_id, id),
    ADD UNIQUE KEY uk_arch_decision_supersession_pair (tenant_id, project_id, conclusion_id, superseded_conclusion_id);
ALTER TABLE arch_decision_number_sequence
    DROP PRIMARY KEY,
    ADD PRIMARY KEY (tenant_id, project_id, seq_year);
ALTER TABLE arch_decision_workflow_round
    ADD KEY idx_arch_decision_workflow_round_matter_fk (tenant_id, matter_id);
ALTER TABLE arch_decision_workflow_round
    DROP INDEX uk_arch_decision_workflow_round_matter,
    DROP INDEX uk_arch_decision_workflow_round_instance,
    ADD UNIQUE KEY uk_arch_decision_workflow_round_matter (tenant_id, project_id, matter_id, round_no),
    ADD UNIQUE KEY uk_arch_decision_workflow_round_instance (tenant_id, project_id, workflow_instance_id);
ALTER TABLE arch_decision_workflow_receipt
    DROP INDEX uk_arch_decision_workflow_receipt,
    ADD UNIQUE KEY uk_arch_decision_workflow_receipt (tenant_id, project_id, event_id, subscriber_key);
