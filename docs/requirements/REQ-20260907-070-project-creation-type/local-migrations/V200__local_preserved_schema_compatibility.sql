-- Local compatibility lane: preserve legacy data, tables and permissions.
ALTER TABLE `dm_component` ADD COLUMN `system_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL;

ALTER TABLE `dm_component` ADD COLUMN `enabled` tinyint NOT NULL DEFAULT '1';

ALTER TABLE `dm_component` MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT;

ALTER TABLE `dm_dashboard_snapshot` ADD COLUMN `system_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '';

ALTER TABLE `dm_operation_log` ADD COLUMN `project_id` bigint NOT NULL DEFAULT '0';

ALTER TABLE `dm_target_table` MODIFY COLUMN `table_code` bigint NOT NULL;

ALTER TABLE `dm_target_table` MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT;

ALTER TABLE `dm_target_table_field` MODIFY COLUMN `field_code` bigint NOT NULL;

ALTER TABLE `dm_target_table_field` MODIFY COLUMN `table_code` bigint NOT NULL;

ALTER TABLE `dm_target_table_field` MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT;

ALTER TABLE `dm_target_table_field` MODIFY COLUMN `table_id` bigint NULL;

ALTER TABLE `sys_operation_log` ADD COLUMN `operator_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL;

ALTER TABLE `sys_operation_log` ADD COLUMN `module_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL;

ALTER TABLE `sys_operation_log` ADD COLUMN `module_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL;

ALTER TABLE `sys_operation_log` ADD COLUMN `operation_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL;

ALTER TABLE `sys_operation_log` ADD COLUMN `target_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL;

ALTER TABLE `sys_operation_log` ADD COLUMN `target_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL;

ALTER TABLE `sys_operation_log` ADD COLUMN `project_id` bigint NULL;

ALTER TABLE `sys_operation_log` ADD COLUMN `project_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL;

ALTER TABLE `sys_operation_log` ADD COLUMN `http_status` int NULL;

ALTER TABLE `sys_operation_log` ADD COLUMN `duration_ms` bigint NULL;

ALTER TABLE `sys_operation_log` ADD COLUMN `user_agent` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL;

ALTER TABLE `sys_operation_log` ADD COLUMN `changed_fields` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL;

ALTER TABLE `dm_component` ADD INDEX `idx_dm_component_enabled` (`tenant_id`,`project_id`,`enabled`);

ALTER TABLE `dm_component` ADD INDEX `idx_dm_component_list_req070` (`tenant_id`,`project_id`,`enabled`,`updated_at`);

ALTER TABLE `dm_component` ADD UNIQUE INDEX `req070_primary` (`tenant_id`,`project_id`,`system_code`);

ALTER TABLE `dm_dashboard_snapshot` ADD UNIQUE INDEX `uk_dm_snapshot_req070` (`tenant_id`,`snapshot_date`,`project_id`,`system_code`,`metric_code`);

ALTER TABLE `dm_operation_log` ADD INDEX `idx_dm_operation_log_project` (`tenant_id`,`project_id`,`entity_type`,`created_at`);

ALTER TABLE `dm_target_table` ADD UNIQUE INDEX `req070_primary` (`table_code`);

ALTER TABLE `dm_target_table` ADD UNIQUE INDEX `uk_target_table_cn_req070` (`tenant_id`,`project_id`,`system_code`,`table_name_cn`);

ALTER TABLE `dm_target_table` ADD UNIQUE INDEX `uk_target_table_en_req070` (`tenant_id`,`project_id`,`system_code`,`table_name_en`);

ALTER TABLE `dm_target_table` ADD UNIQUE INDEX `uk_target_table_tenant_code` (`tenant_id`,`table_code`);

ALTER TABLE `dm_target_table_field` ADD INDEX `fk_target_field_table_req070` (`table_code`);

ALTER TABLE `dm_target_table_field` ADD INDEX `idx_target_field_key_req070` (`tenant_id`,`table_code`,`is_key_field`,`deleted`);

ALTER TABLE `dm_target_table_field` ADD INDEX `idx_target_field_table_req070` (`tenant_id`,`table_code`,`deleted`);

ALTER TABLE `dm_target_table_field` ADD UNIQUE INDEX `req070_primary` (`field_code`);

ALTER TABLE `dm_target_table_field` ADD UNIQUE INDEX `uk_target_field_cn_req070` (`tenant_id`,`table_code`,`field_name_cn`);

ALTER TABLE `dm_target_table_field` ADD UNIQUE INDEX `uk_target_field_en_req070` (`tenant_id`,`table_code`,`field_name_en`);

ALTER TABLE `sys_operation_log` ADD INDEX `idx_sys_operation_log_project_created` (`tenant_id`,`project_id`,`created_at`);

ALTER TABLE `sys_operation_log` ADD INDEX `idx_sys_operation_log_type_created` (`tenant_id`,`operation_type`,`created_at`);

CREATE TABLE `dm_content_attachment` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '租户ID',
  `business_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '业务类型 PLAN/MAPPING_DOC/DEPENDENCY/SCRIPT/TOPIC/RELEASE_DRILL/REPORT/MEETING',
  `business_id` bigint NOT NULL COMMENT '业务实体ID（内容表主键或会议主键）',
  `attachment_id` bigint NOT NULL COMMENT '关联 att_file.id',
  `file_name` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '附件原始文件名',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序序号，主文件为0',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除（附件回收站）0否 1是',
  `deleted_by` bigint DEFAULT NULL COMMENT '删除人',
  `deleted_at` datetime(6) DEFAULT NULL COMMENT '删除时间',
  `created_by` bigint NOT NULL COMMENT '创建人',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dm_content_att` (`tenant_id`,`business_type`,`business_id`,`attachment_id`),
  KEY `idx_dm_content_att_business` (`tenant_id`,`business_type`,`business_id`,`deleted`,`sort_order`),
  KEY `idx_dm_content_att_attachment` (`tenant_id`,`attachment_id`,`deleted`),
  KEY `idx_dm_content_att_tenant` (`tenant_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数迁内容公共附件关系表';

CREATE TABLE `dm_dependency` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID（V100 迁移保留原 dm_asset.id）',
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '租户ID',
  `project_id` bigint NOT NULL COMMENT '所属项目',
  `system_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '关联系统编号(dm_component.system_code)，项目级为空串',
  `doc_code` varchar(96) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文档编号，项目内活动记录唯一',
  `doc_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文档名称',
  `owner_id` bigint NOT NULL COMMENT '负责人',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除 0否 1是',
  `deleted_by` bigint DEFAULT NULL COMMENT '删除人',
  `deleted_at` timestamp NULL DEFAULT NULL COMMENT '删除时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '最后编辑人',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dm_dependency_code` (`tenant_id`,`project_id`,`doc_code`),
  KEY `idx_dm_dependency_query` (`tenant_id`,`project_id`,`deleted`,`updated_at`),
  KEY `idx_dm_dependency_owner` (`tenant_id`,`owner_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='迁移过程依赖文件内容表';

CREATE TABLE `dm_issue` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '租户ID',
  `project_id` bigint NOT NULL COMMENT '所属项目（pm_project.id）',
  `issue_code` varchar(96) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '问题编号，项目内唯一',
  `issue_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '问题名称',
  `granularity` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '粒度 PROJECT/COMPONENT/TABLE/FIELD',
  `system_code` varchar(96) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '系统编号（arch_physical_subsystem.code）',
  `issue_source` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '问题来源',
  `defect_type` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '缺陷类型',
  `issue_description` text COLLATE utf8mb4_unicode_ci COMMENT '问题描述',
  `solution` text COLLATE utf8mb4_unicode_ci COMMENT '解决方案',
  `meeting_conclusion` text COLLATE utf8mb4_unicode_ci COMMENT '会议结论',
  `processing_steps` text COLLATE utf8mb4_unicode_ci COMMENT '处理步骤',
  `business_scenario` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '所属业务场景',
  `handler` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '处理人',
  `responsible_party` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '责任方',
  `keywords` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关键字（英文逗号分隔）',
  `frequency` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '发生频率',
  `owner_id` bigint NOT NULL COMMENT '负责人',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除 0否 1是',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `deleted_by` bigint DEFAULT NULL COMMENT '删除人',
  `deleted_at` timestamp NULL DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dm_issue_code` (`tenant_id`,`project_id`,`issue_code`),
  KEY `idx_dm_issue_query` (`tenant_id`,`project_id`,`deleted`,`updated_at`),
  KEY `idx_dm_issue_owner` (`tenant_id`,`owner_id`,`deleted`),
  KEY `idx_dm_issue_filters` (`tenant_id`,`project_id`,`granularity`,`issue_source`,`defect_type`,`frequency`,`deleted`),
  KEY `idx_dm_issue_system` (`tenant_id`,`project_id`,`system_code`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据迁移问题清单';

CREATE TABLE `dm_issue_relation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '租户ID',
  `issue_id` bigint NOT NULL COMMENT '关联 dm_issue.id',
  `related_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '关联对象类型 MEETING/TABLE/FIELD',
  `related_id` bigint NOT NULL COMMENT '关联对象ID（会议主键/目标表ID/目标字段ID）',
  `created_by` bigint NOT NULL COMMENT '创建人',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dm_issue_relation` (`tenant_id`,`issue_id`,`related_type`,`related_id`),
  KEY `idx_dm_issue_relation_target` (`tenant_id`,`related_type`,`related_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数迁问题关联公共关系表';

CREATE TABLE `dm_mapping_doc` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID（V100 迁移保留原 dm_asset.id）',
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '租户ID',
  `project_id` bigint NOT NULL COMMENT '所属项目',
  `system_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '关联系统编号(dm_component.system_code)，项目级为空串',
  `mapping_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '映射类型码值（DM_MAPPING_TYPE，系统管理/参数管理维护）',
  `doc_code` varchar(96) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文档编号，项目内活动记录唯一',
  `doc_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文档名称',
  `owner_id` bigint NOT NULL COMMENT '负责人',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除 0否 1是',
  `deleted_by` bigint DEFAULT NULL COMMENT '删除人',
  `deleted_at` timestamp NULL DEFAULT NULL COMMENT '删除时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '最后编辑人',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dm_mapping_doc_code` (`tenant_id`,`project_id`,`doc_code`),
  KEY `idx_dm_mapping_doc_query` (`tenant_id`,`project_id`,`deleted`,`updated_at`),
  KEY `idx_dm_mapping_doc_owner` (`tenant_id`,`owner_id`,`deleted`),
  KEY `idx_dm_mapping_doc_mapping_type` (`tenant_id`,`project_id`,`mapping_type`,`system_code`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='迁移映射内容表';

CREATE TABLE `dm_meeting` (
  `meeting_id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `meeting_code` varchar(96) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '会议编号',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `project_id` bigint NOT NULL COMMENT '所属项目（pm_project.id）',
  `granularity` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'PROJECT/COMPONENT/TABLE/FIELD',
  `meeting_source` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'MEETING_MINUTES/ISSUE_EXTRACT',
  `meeting_title` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '会议主题',
  `meeting_content` text COLLATE utf8mb4_unicode_ci COMMENT '会议内容',
  `meeting_conclusion` text COLLATE utf8mb4_unicode_ci COMMENT '会议结论',
  `business_scenario` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '所属业务场景',
  `keywords` json DEFAULT NULL COMMENT '关键字（JSON数组）',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除 0否 1是',
  `deleted_by` bigint DEFAULT NULL COMMENT '删除人',
  `deleted_at` datetime(6) DEFAULT NULL COMMENT '删除时间',
  `created_by` bigint NOT NULL COMMENT '创建人',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime(6) DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
  PRIMARY KEY (`meeting_id`),
  UNIQUE KEY `uk_dm_meeting_code` (`tenant_id`,`project_id`,`meeting_code`),
  KEY `idx_dm_meeting_project` (`tenant_id`,`project_id`,`deleted`,`updated_at`),
  KEY `idx_dm_meeting_source` (`tenant_id`,`meeting_source`,`deleted`),
  KEY `idx_dm_meeting_granularity` (`tenant_id`,`granularity`,`deleted`),
  KEY `idx_dm_meeting_created` (`tenant_id`,`created_at`),
  KEY `idx_dm_meeting_deleted` (`tenant_id`,`deleted`,`deleted_at`),
  KEY `idx_dm_meeting_code` (`tenant_id`,`meeting_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据迁移会议纪要';

CREATE TABLE `dm_meeting_system` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '租户ID',
  `meeting_id` bigint NOT NULL COMMENT '关联 dm_meeting.meeting_id',
  `project_id` bigint NOT NULL DEFAULT '0' COMMENT '所属项目(dm_meeting.project_id)',
  `system_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '项目内系统编号(dm_component活动记录)',
  `created_by` bigint NOT NULL COMMENT '创建人',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dm_meeting_system` (`tenant_id`,`meeting_id`,`system_code`),
  KEY `idx_dm_meeting_system_project` (`tenant_id`,`project_id`,`system_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会议纪要关联系统表';

CREATE TABLE `dm_parameter` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID（V100 迁移保留原 dm_asset.id）',
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '租户ID',
  `project_id` bigint NOT NULL COMMENT '所属项目',
  `system_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '关联系统编号(dm_component.system_code)，项目级为空串',
  `parameter_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '参数名称',
  `parameter_description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '参数说明（用途/取值规则/适用场景）',
  `parameter_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '参数类型（系统参数管理码值：业务/技术参数）',
  `parameter_scope` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '参数范围分类（系统参数管理码值：公共/自有参数）',
  `owner_id` bigint NOT NULL COMMENT '负责人',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除 0否 1是',
  `deleted_by` bigint DEFAULT NULL COMMENT '删除人',
  `deleted_at` timestamp NULL DEFAULT NULL COMMENT '删除时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '最后编辑人',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dm_parameter_name` (`tenant_id`,`project_id`,`system_code`,`parameter_name`),
  KEY `idx_dm_parameter_query` (`tenant_id`,`project_id`,`parameter_type`,`parameter_scope`,`system_code`,`deleted`),
  KEY `idx_dm_parameter_keyword` (`tenant_id`,`project_id`,`parameter_name`(80),`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='迁移参数内容表';

CREATE TABLE `dm_plan` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID（V100 迁移保留原 dm_asset.id）',
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '租户ID',
  `project_id` bigint NOT NULL COMMENT '所属项目',
  `granularity` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PROJECT' COMMENT '资产颗粒度 PROJECT=项目级/SYSTEM=系统级',
  `plan_type` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DATA' COMMENT '迁移方案类型 BUSINESS=业务迁移方案/DATA=数据迁移方案',
  `system_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '关联系统(项目内dm_component活动记录编号)，项目级用空串哨兵',
  `plan_summary` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '方案简介',
  `doc_code` varchar(96) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文档编号，项目内活动记录唯一',
  `doc_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文档名称',
  `owner_id` bigint NOT NULL COMMENT '负责人',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除 0否 1是',
  `deleted_by` bigint DEFAULT NULL COMMENT '删除人',
  `deleted_at` timestamp NULL DEFAULT NULL COMMENT '删除时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '最后编辑人',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dm_plan_code` (`tenant_id`,`project_id`,`doc_code`),
  UNIQUE KEY `uk_dm_plan_dimension` (`tenant_id`,`project_id`,`granularity`,`plan_type`,`system_code`),
  KEY `idx_dm_plan_query` (`tenant_id`,`project_id`,`deleted`,`updated_at`),
  KEY `idx_dm_plan_owner` (`tenant_id`,`owner_id`,`deleted`),
  KEY `idx_dm_plan_dimension` (`tenant_id`,`project_id`,`granularity`,`plan_type`,`deleted`),
  KEY `idx_dm_plan_system` (`tenant_id`,`project_id`,`system_code`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='迁移方案内容表';

CREATE TABLE `dm_release_drill` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID（V100 迁移保留原 dm_asset.id）',
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '租户ID',
  `project_id` bigint NOT NULL COMMENT '所属项目',
  `granularity` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PROJECT' COMMENT '颗粒度 PROJECT/COMPONENT（参数管理）',
  `material_type_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'RELEASE_PLAN' COMMENT '资料类型参数编码（系统管理/参数管理）',
  `drill_round` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '所属轮次（手工输入）',
  `system_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '关联系统编号(dm_component.system_code)，项目级为空串',
  `doc_code` varchar(96) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文档编号，项目内活动记录唯一',
  `doc_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文档名称',
  `owner_id` bigint NOT NULL COMMENT '负责人',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除 0否 1是',
  `deleted_by` bigint DEFAULT NULL COMMENT '删除人',
  `deleted_at` timestamp NULL DEFAULT NULL COMMENT '删除时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '最后编辑人',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dm_release_drill_code` (`tenant_id`,`project_id`,`doc_code`),
  KEY `idx_dm_release_drill_query` (`tenant_id`,`project_id`,`deleted`,`updated_at`),
  KEY `idx_dm_release_drill_owner` (`tenant_id`,`owner_id`,`deleted`),
  KEY `idx_dm_release_drill_granularity` (`tenant_id`,`project_id`,`granularity`,`deleted`,`updated_at`),
  KEY `idx_dm_release_drill_type` (`tenant_id`,`project_id`,`material_type_code`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='投产及演练内容表';

CREATE TABLE `dm_report` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID（V100 迁移保留原 dm_asset.id）',
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '租户ID',
  `project_id` bigint NOT NULL COMMENT '所属项目',
  `system_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '关联系统编号(dm_component.system_code)，项目级为空串',
  `doc_code` varchar(96) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '材料编号，项目内活动记录唯一',
  `doc_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '材料名称',
  `report_period` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '汇报周期',
  `report_date` date DEFAULT NULL COMMENT '汇报日期',
  `keywords` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关键字，逗号分隔',
  `owner_id` bigint NOT NULL COMMENT '负责人',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除 0否 1是',
  `deleted_by` bigint DEFAULT NULL COMMENT '删除人',
  `deleted_at` timestamp NULL DEFAULT NULL COMMENT '删除时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '最后编辑人',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dm_report_code` (`tenant_id`,`project_id`,`doc_code`),
  KEY `idx_dm_report_query` (`tenant_id`,`project_id`,`deleted`,`updated_at`),
  KEY `idx_dm_report_period` (`tenant_id`,`project_id`,`report_period`,`deleted`),
  KEY `idx_dm_report_owner` (`tenant_id`,`owner_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='汇报材料内容表';

CREATE TABLE `dm_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID（V100 迁移保留原 dm_asset.id）',
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '租户ID',
  `project_id` bigint NOT NULL COMMENT '所属项目',
  `system_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '关联系统编号(dm_component.system_code)，项目级为空串',
  `check_target_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '检核目标类型码值（DM_RULE_TARGET_TYPE）',
  `rule_category` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '检核规则大类码值（DM_RULE_CATEGORY）',
  `rule_code` varchar(96) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '规则编码（全局唯一，用户录入）',
  `rule_code_desc` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '规则编码说明',
  `rule_description` text COLLATE utf8mb4_unicode_ci COMMENT '检核规则说明',
  `table_name_en` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '表英文名',
  `table_name_cn` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '表中文名',
  `field_name_en` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '字段英文名称',
  `field_name_cn` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '字段中文名称',
  `owner_id` bigint NOT NULL COMMENT '负责人',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除 0否 1是',
  `deleted_by` bigint DEFAULT NULL COMMENT '删除人',
  `deleted_at` timestamp NULL DEFAULT NULL COMMENT '删除时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '最后编辑人',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dm_rule_code` (`tenant_id`,`rule_code`),
  KEY `idx_dm_rule_owner` (`tenant_id`,`owner_id`,`deleted`),
  KEY `idx_dm_rule_query` (`tenant_id`,`project_id`,`check_target_type`,`rule_category`,`system_code`,`deleted`),
  KEY `idx_dm_rule_keyword` (`tenant_id`,`project_id`,`table_name_en`(80),`table_name_cn`(80),`field_name_en`(80),`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='迁移检核规则内容表';

CREATE TABLE `dm_script` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID（V100 迁移保留原 dm_asset.id）',
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '租户ID',
  `project_id` bigint NOT NULL COMMENT '所属项目',
  `system_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '关联系统编号(dm_component.system_code)，项目级为空串',
  `program_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '程序类型码值（DM_PROGRAM_TYPE，系统管理/参数管理维护）',
  `program_description` varchar(2000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '程序包说明（功能、执行逻辑、适用场景）',
  `doc_code` varchar(96) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文档编号，项目内活动记录唯一',
  `doc_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文档名称',
  `owner_id` bigint NOT NULL COMMENT '负责人',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除 0否 1是',
  `deleted_by` bigint DEFAULT NULL COMMENT '删除人',
  `deleted_at` timestamp NULL DEFAULT NULL COMMENT '删除时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '最后编辑人',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dm_script_code` (`tenant_id`,`project_id`,`doc_code`),
  KEY `idx_dm_script_query` (`tenant_id`,`project_id`,`deleted`,`updated_at`),
  KEY `idx_dm_script_owner` (`tenant_id`,`owner_id`,`deleted`),
  KEY `idx_dm_script_program_type` (`tenant_id`,`project_id`,`program_type`,`system_code`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='迁移程序内容表';

CREATE TABLE `dm_topic` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID（V100 迁移保留原 dm_asset.id）',
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '租户ID',
  `project_id` bigint NOT NULL COMMENT '所属项目',
  `granularity` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PROJECT' COMMENT '专题颗粒度 PROJECT/SYSTEM',
  `topic_type_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'LEGACY' COMMENT '专题类型参数编码（系统管理/参数管理）',
  `system_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '关联系统编号(dm_component.system_code)，项目级为空串',
  `doc_code` varchar(96) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文档编号，项目内活动记录唯一',
  `doc_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文档名称',
  `topic_summary` varchar(2000) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '专题材料简述',
  `owner_id` bigint NOT NULL COMMENT '负责人',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除 0否 1是',
  `deleted_by` bigint DEFAULT NULL COMMENT '删除人',
  `deleted_at` timestamp NULL DEFAULT NULL COMMENT '删除时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '最后编辑人',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dm_topic_code` (`tenant_id`,`project_id`,`doc_code`),
  KEY `idx_dm_topic_query` (`tenant_id`,`project_id`,`deleted`,`updated_at`),
  KEY `idx_dm_topic_owner` (`tenant_id`,`owner_id`,`deleted`),
  KEY `idx_dm_topic_granularity` (`tenant_id`,`project_id`,`granularity`,`deleted`,`updated_at`),
  KEY `idx_dm_topic_type` (`tenant_id`,`project_id`,`topic_type_code`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='专题材料内容表';

CREATE TABLE `dm_topic_system` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '租户ID',
  `topic_id` bigint NOT NULL COMMENT '专题材料ID（dm_topic.id）',
  `project_id` bigint NOT NULL COMMENT '项目ID（pm_project.id）',
  `system_code` varchar(96) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '系统/组件编号（项目内有效）',
  `created_by` bigint NOT NULL COMMENT '创建人',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dm_topic_system` (`tenant_id`,`topic_id`,`system_code`),
  KEY `idx_dm_topic_system_project` (`tenant_id`,`project_id`,`system_code`),
  KEY `idx_dm_topic_system_topic` (`tenant_id`,`topic_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='专题材料涉及系统关系表';
