-- 阶段资料只追加版本引用；任务回退或应用回退不得删除历史附件和日历快照。
ALTER TABLE dev_task ADD COLUMN status_before_cancel VARCHAR(24) NULL COMMENT '取消前任务状态';

CREATE TABLE dev_task_stage (
    tenant_id BIGINT NOT NULL COMMENT '租户',
    task_id BIGINT NOT NULL COMMENT '所属任务',
    design_plan_start DATE NULL COMMENT '设计计划开始',
    design_plan_end DATE NULL COMMENT '设计计划结束',
    design_document_path VARCHAR(2000) NOT NULL DEFAULT '' COMMENT '设计文档路径文本',
    implementation_actual_start DATE NULL COMMENT '实施实际开始',
    implementation_actual_end DATE NULL COMMENT '实施实际结束',
    not_applicable_design BOOLEAN NOT NULL DEFAULT FALSE COMMENT '设计不适用',
    not_applicable_implementation BOOLEAN NOT NULL DEFAULT FALSE COMMENT '实施不适用',
    design_registered_at DATETIME(3) NULL COMMENT '设计资料登记时间',
    test_registered_at DATETIME(3) NULL COMMENT '测试资料登记时间',
    row_version BIGINT NOT NULL DEFAULT 0 COMMENT '阶段行版本',
    updated_by BIGINT NOT NULL COMMENT '更新人',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (tenant_id, task_id),
    CONSTRAINT fk_dev_stage_task FOREIGN KEY (tenant_id, task_id) REFERENCES dev_task (tenant_id, id),
    CONSTRAINT ck_dev_stage_design_dates CHECK (design_plan_end >= design_plan_start),
    CONSTRAINT ck_dev_stage_actual_dates CHECK (implementation_actual_end >= implementation_actual_start)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='开发任务阶段资料';

CREATE TABLE dev_stage_attachment_ref (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '引用标识',
    tenant_id BIGINT NOT NULL COMMENT '租户',
    task_id BIGINT NOT NULL COMMENT '所属任务',
    stage_version BIGINT NOT NULL COMMENT '资料版本',
    kind VARCHAR(24) NOT NULL COMMENT '设计、代码走查或测试报告',
    attachment_id BIGINT NOT NULL COMMENT '平台附件标识',
    created_by BIGINT NOT NULL COMMENT '登记人',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '登记时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_dev_stage_ref (tenant_id, task_id, stage_version, kind, attachment_id),
    CONSTRAINT fk_dev_stage_ref_task FOREIGN KEY (tenant_id, task_id) REFERENCES dev_task (tenant_id, id),
    CONSTRAINT ck_dev_stage_ref_kind CHECK (kind IN ('DESIGN','CODE_WALK','TEST_REPORT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='阶段附件历史引用';

CREATE TABLE dev_calendar_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '快照标识',
    tenant_id BIGINT NOT NULL COMMENT '租户',
    task_id BIGINT NOT NULL COMMENT '所属任务',
    object_type VARCHAR(24) NOT NULL COMMENT '任务或工作项',
    object_id BIGINT NOT NULL COMMENT '完成对象标识',
    object_version BIGINT NOT NULL COMMENT '完成或改期后的对象版本',
    calendar_version CHAR(64) NOT NULL COMMENT '规范化日历摘要',
    calendar_json JSON NOT NULL COMMENT '计算日历快照',
    created_by BIGINT NOT NULL COMMENT '确认人',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '冻结时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_dev_calendar_object (tenant_id, object_type, object_id, object_version),
    KEY idx_dev_calendar_task (tenant_id, task_id, id),
    CONSTRAINT fk_dev_calendar_task FOREIGN KEY (tenant_id, task_id) REFERENCES dev_task (tenant_id, id),
    CONSTRAINT ck_dev_calendar_type CHECK (object_type IN ('TASK','WORK_ITEM'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='完成耗时计算日历快照';
