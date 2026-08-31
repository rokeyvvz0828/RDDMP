-- REQ-20260830-056：前置依赖、阻塞记录、检查项取消建议、执行事件、任务工单关联与操作历史。
-- 执行事件是实际时间事实源；取消、恢复、事件更正与操作历史全部留痕。

CREATE TABLE arch_plan_task_dependency (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL COMMENT '后续任务',
    predecessor_id BIGINT NOT NULL COMMENT '前置任务',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/REMOVED',
    removed_by BIGINT NULL,
    removed_at TIMESTAMP NULL,
    removed_reason VARCHAR(1000) NULL,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_arch_plan_task_dep_tenant_id (tenant_id, id),
    UNIQUE KEY uk_arch_plan_task_dep_pair (tenant_id, task_id, predecessor_id),
    KEY idx_arch_plan_task_dep_task (tenant_id, task_id, status),
    KEY idx_arch_plan_task_dep_pred (tenant_id, predecessor_id, status),
    CONSTRAINT fk_arch_plan_task_dep_task
        FOREIGN KEY (tenant_id, task_id)
        REFERENCES arch_plan_task (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_arch_plan_task_dep_pred
        FOREIGN KEY (tenant_id, predecessor_id)
        REFERENCES arch_plan_task (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_arch_plan_task_dep_status CHECK (status IN ('ACTIVE', 'REMOVED')),
    CONSTRAINT chk_arch_plan_task_dep_distinct CHECK (task_id <> predecessor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务前置依赖';

CREATE TABLE arch_plan_block (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    description VARCHAR(2000) NOT NULL,
    impact VARCHAR(2000) NULL,
    owner_user_id BIGINT NOT NULL COMMENT '阻塞责任人',
    expected_resolve_at TIMESTAMP NULL COMMENT '预计解决时间',
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/RESOLVED',
    resolved_note VARCHAR(1000) NULL,
    resolved_by BIGINT NULL,
    resolved_at TIMESTAMP NULL,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_arch_plan_block_tenant_id (tenant_id, id),
    KEY idx_arch_plan_block_task (tenant_id, task_id, status),
    CONSTRAINT fk_arch_plan_block_task
        FOREIGN KEY (tenant_id, task_id)
        REFERENCES arch_plan_task (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_arch_plan_block_status CHECK (status IN ('OPEN', 'RESOLVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务阻塞记录';

CREATE TABLE arch_plan_check_item_cancel_suggestion (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    check_item_id BIGINT NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    submitter_user_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/ACCEPTED/REJECTED',
    handled_by_user_id BIGINT NULL,
    handled_at TIMESTAMP NULL,
    handler_note VARCHAR(1000) NULL,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_arch_plan_check_item_sug_tenant_id (tenant_id, id),
    KEY idx_arch_plan_check_item_sug_item (tenant_id, check_item_id, status),
    CONSTRAINT fk_arch_plan_check_item_sug_item
        FOREIGN KEY (tenant_id, check_item_id)
        REFERENCES arch_plan_check_item (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_arch_plan_check_item_sug_status CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='检查项取消建议';

CREATE TABLE arch_plan_event (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    plan_id BIGINT NOT NULL,
    object_type VARCHAR(24) NOT NULL COMMENT 'PLAN/STAGE/TASK/CHECK_ITEM',
    object_id BIGINT NOT NULL,
    event_type VARCHAR(24) NOT NULL COMMENT 'START/COMPLETE/REOPEN/CANCEL/RESTORE/TIME_CORRECT',
    occurred_at TIMESTAMP NOT NULL COMMENT '事件发生时间（实际时间事实源）',
    operator_user_id BIGINT NOT NULL,
    reason VARCHAR(1000) NULL,
    correct_of_event_id BIGINT NULL COMMENT '被更正的原事件，为空表示原始事件',
    before_json JSON NULL,
    after_json JSON NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_arch_plan_event_tenant_id (tenant_id, id),
    KEY idx_arch_plan_event_plan (tenant_id, plan_id, object_type, object_id, occurred_at),
    KEY idx_arch_plan_event_correct (tenant_id, correct_of_event_id),
    CONSTRAINT chk_arch_plan_event_object_type CHECK (object_type IN ('PLAN', 'STAGE', 'TASK', 'CHECK_ITEM')),
    CONSTRAINT chk_arch_plan_event_type CHECK (event_type IN ('START', 'COMPLETE', 'REOPEN', 'CANCEL', 'RESTORE', 'TIME_CORRECT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='搭建执行事件（实际时间事实源与更正链）';

CREATE TABLE arch_plan_work_order (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    plan_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    work_order_type VARCHAR(32) NOT NULL COMMENT 'RESOURCE_REQUEST/NETWORK_CLB/NETWORK_DNS/NETWORK_CERT/CRYPTO_POOL(预留)',
    work_order_id BIGINT NOT NULL,
    source VARCHAR(24) NOT NULL COMMENT 'CREATED_FROM_TASK/ATTACHED_LATER',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/REMOVED',
    removed_reason VARCHAR(1000) NULL,
    removed_by BIGINT NULL,
    removed_at TIMESTAMP NULL,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_arch_plan_work_order_tenant_id (tenant_id, id),
    UNIQUE KEY uk_arch_plan_work_order_binding (tenant_id, task_id, work_order_type, work_order_id),
    KEY idx_arch_plan_work_order_plan (tenant_id, plan_id, status),
    KEY idx_arch_plan_work_order_task (tenant_id, task_id, status),
    KEY idx_arch_plan_work_order_type (tenant_id, work_order_type, work_order_id),
    CONSTRAINT fk_arch_plan_work_order_plan
        FOREIGN KEY (tenant_id, plan_id)
        REFERENCES arch_setup_plan (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_arch_plan_work_order_task
        FOREIGN KEY (tenant_id, task_id)
        REFERENCES arch_plan_task (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_arch_plan_work_order_type CHECK (work_order_type IN ('RESOURCE_REQUEST', 'NETWORK_CLB', 'NETWORK_DNS', 'NETWORK_CERT', 'CRYPTO_POOL')),
    CONSTRAINT chk_arch_plan_work_order_source CHECK (source IN ('CREATED_FROM_TASK', 'ATTACHED_LATER')),
    CONSTRAINT chk_arch_plan_work_order_status CHECK (status IN ('ACTIVE', 'REMOVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务-工单关联';

CREATE TABLE arch_setup_plan_activity (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    scope_type VARCHAR(16) NOT NULL COMMENT 'TEMPLATE/PLAN',
    scope_id BIGINT NOT NULL,
    object_type VARCHAR(24) NOT NULL COMMENT 'TEMPLATE/STAGE_TEMPLATE/TASK_TEMPLATE/PLAN/STAGE/TASK/CHECK_ITEM/TARGET/DEPENDENCY/BLOCK/WORK_ORDER/SUGGESTION/SCHEDULE/EVENT',
    object_id BIGINT NULL,
    action VARCHAR(40) NOT NULL,
    operator_user_id BIGINT NOT NULL,
    reason VARCHAR(1000) NULL,
    before_json JSON NULL,
    after_json JSON NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_arch_setup_plan_activity_tenant_id (tenant_id, id),
    KEY idx_arch_setup_plan_activity_scope (tenant_id, scope_type, scope_id, created_at),
    CONSTRAINT chk_arch_setup_plan_activity_scope CHECK (scope_type IN ('TEMPLATE', 'PLAN')),
    CONSTRAINT chk_arch_setup_plan_activity_action CHECK (LENGTH(action) BETWEEN 1 AND 40)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='搭建模板与计划操作历史';
