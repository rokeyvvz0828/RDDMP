-- REQ-20260831-057：测试报告按项目、系统、专项节点归属；项目报告不绑定轮次/周期。

ALTER TABLE tm_test_report
    ADD COLUMN scope_type VARCHAR(16) NOT NULL DEFAULT 'PROJECT' COMMENT '报告范围：PROJECT/SYSTEM/SPECIAL' AFTER project_id,
    ADD COLUMN special_node_id BIGINT NULL COMMENT '专项报告所属专项节点' AFTER physical_subsystem_id,
    ADD KEY idx_tm_report_scope (tenant_id,test_domain,project_id,scope_type,physical_subsystem_id,special_node_id,generated_at),
    ADD CONSTRAINT fk_tm_report_special_node FOREIGN KEY (special_node_id) REFERENCES tm_test_plan_special_node(id) ON UPDATE RESTRICT ON DELETE RESTRICT;

UPDATE tm_test_report
SET scope_type = CASE WHEN physical_subsystem_id IS NULL THEN 'PROJECT' ELSE 'SYSTEM' END;
