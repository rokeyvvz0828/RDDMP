-- V97: 数据迁移模块全表注释补齐 + 专题类型字典表
-- 1) 为所有缺少 COMMENT 的表和列补充中文注释（幂等，不影响已有注释）
-- 2) 新建 dm_topic_type 专题类型字典表

-- ============================================================
-- 一、新建 dm_topic_type 专题类型字典表
-- ============================================================
CREATE TABLE IF NOT EXISTS dm_topic_type (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    tenant_id       BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
    project_id      BIGINT NOT NULL COMMENT '所属项目（pm_project.id）',
    component_id    BIGINT NULL COMMENT '所属组件（dm_component.id），NULL 表示项目级',
    type_name       VARCHAR(128) NOT NULL COMMENT '专题类型名称',
    type_code       VARCHAR(64) NOT NULL COMMENT '专题类型编码，项目内唯一',
    description     VARCHAR(500) NULL COMMENT '类型说明',
    level           VARCHAR(16) NOT NULL DEFAULT 'PROJECT' COMMENT '层级 PROJECT/COMPONENT',
    sort_no         INT NOT NULL DEFAULT 0 COMMENT '排序序号',
    owner_id        BIGINT NOT NULL COMMENT '负责人',
    deleted         TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    created_by      BIGINT NULL COMMENT '创建人',
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by      BIGINT NULL COMMENT '更新人',
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_dm_topic_type_code (tenant_id, project_id, type_code, deleted),
    KEY idx_dm_topic_type_project (tenant_id, project_id, deleted),
    KEY idx_dm_topic_type_component (tenant_id, component_id, deleted)
) COMMENT '数据迁移专题类型字典（管理员维护的项目级/组件级专题分类）';

-- ============================================================
-- 二、dm_asset 表级 + 列级注释补齐
-- ============================================================
ALTER TABLE dm_asset COMMENT '数据迁移内容资产表（文件型资产、规则/参数等11类）';

ALTER TABLE dm_asset
    MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    MODIFY COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
    MODIFY COLUMN project_id BIGINT NOT NULL COMMENT '所属项目（pm_project.id）',
    MODIFY COLUMN component_id BIGINT COMMENT '所属组件（dm_component.id）',
    MODIFY COLUMN asset_type VARCHAR(32) NOT NULL COMMENT '资产类型 PLAN/MAPPING/VALIDATION_RULE/PARAMETER/DEPENDENCY/PROGRAM/TOPIC/RELEASE_DRILL/REPORT/TABLE_STRUCTURE/INTERMEDIATE_TABLE',
    MODIFY COLUMN asset_code VARCHAR(96) NOT NULL COMMENT '资产编号，项目内同类型唯一',
    MODIFY COLUMN asset_name VARCHAR(255) NOT NULL COMMENT '资产名称',
    MODIFY COLUMN content_type VARCHAR(160) COMMENT 'MIME类型',
    MODIFY COLUMN file_size BIGINT COMMENT '文件大小（字节）',
    MODIFY COLUMN object_key VARCHAR(512) COMMENT '对象存储Key',
    MODIFY COLUMN checksum_md5 CHAR(32) COMMENT '文件MD5校验值',
    MODIFY COLUMN structured_data JSON COMMENT '结构化数据JSON',
    MODIFY COLUMN owner_id BIGINT NOT NULL COMMENT '负责人',
    MODIFY COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    MODIFY COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    MODIFY COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

-- ============================================================
-- 三、dm_operation_log 表级 + 列级注释补齐
-- ============================================================
ALTER TABLE dm_operation_log COMMENT '数据迁移模块写操作审计表';

ALTER TABLE dm_operation_log
    MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    MODIFY COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
    MODIFY COLUMN actor_id BIGINT NOT NULL COMMENT '操作人ID',
    MODIFY COLUMN operation_code VARCHAR(64) NOT NULL COMMENT '操作码',
    MODIFY COLUMN entity_type VARCHAR(64) NOT NULL COMMENT '实体类型',
    MODIFY COLUMN entity_id BIGINT COMMENT '实体ID',
    MODIFY COLUMN result_code VARCHAR(16) NOT NULL DEFAULT 'SUCCESS' COMMENT '结果码 SUCCESS/FAIL',
    MODIFY COLUMN trace_id VARCHAR(64) COMMENT '链路追踪ID',
    MODIFY COLUMN detail_json JSON COMMENT '操作详情JSON',
    MODIFY COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

-- ============================================================
-- 四、dm_dashboard_snapshot 表级 + 列级注释补齐
-- ============================================================
ALTER TABLE dm_dashboard_snapshot COMMENT '数据迁移每日看板快照表';

ALTER TABLE dm_dashboard_snapshot
    MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    MODIFY COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
    MODIFY COLUMN snapshot_date DATE NOT NULL COMMENT '快照日期',
    MODIFY COLUMN project_id BIGINT COMMENT '所属项目（pm_project.id）',
    MODIFY COLUMN component_id BIGINT COMMENT '所属组件（dm_component.id）',
    MODIFY COLUMN metric_code VARCHAR(64) NOT NULL COMMENT '指标编码',
    MODIFY COLUMN metric_value DECIMAL(20,4) NOT NULL DEFAULT 0 COMMENT '指标值',
    MODIFY COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

-- ============================================================
-- 五、dm_project 表级 + 列级注释补齐
-- ============================================================
ALTER TABLE dm_project COMMENT '数据迁移项目表（历史保留，新项目使用 pm_project）';

ALTER TABLE dm_project
    MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    MODIFY COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
    MODIFY COLUMN project_code VARCHAR(64) NOT NULL COMMENT '项目编码',
    MODIFY COLUMN project_name VARCHAR(160) NOT NULL COMMENT '项目名称',
    MODIFY COLUMN description VARCHAR(500) COMMENT '项目说明',
    MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态 ACTIVE/INACTIVE',
    MODIFY COLUMN owner_id BIGINT NOT NULL COMMENT '负责人',
    MODIFY COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    MODIFY COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    MODIFY COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

-- ============================================================
-- 六、dm_component 表级 + 缺失列级注释补齐
-- ============================================================
ALTER TABLE dm_component COMMENT '数据迁移系统/组件清单表';

ALTER TABLE dm_component
    MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    MODIFY COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
    MODIFY COLUMN project_id BIGINT NOT NULL COMMENT '所属项目（pm_project.id）',
    MODIFY COLUMN owner_id BIGINT NOT NULL COMMENT '负责人',
    MODIFY COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    MODIFY COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    MODIFY COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

-- ============================================================
-- 七、dm_target_table 表级 + 缺失列级注释补齐
-- ============================================================
ALTER TABLE dm_target_table COMMENT '数据迁移目标表结构主表';

ALTER TABLE dm_target_table
    MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    MODIFY COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
    MODIFY COLUMN owner_id BIGINT NOT NULL COMMENT '负责人',
    MODIFY COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    MODIFY COLUMN created_by BIGINT COMMENT '创建人',
    MODIFY COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    MODIFY COLUMN updated_by BIGINT COMMENT '更新人',
    MODIFY COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是';

-- ============================================================
-- 八、dm_target_table_field 表级 + 缺失列级注释补齐
-- ============================================================
ALTER TABLE dm_target_table_field COMMENT '数据迁移目标表字段明细表';

ALTER TABLE dm_target_table_field
    MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    MODIFY COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
    MODIFY COLUMN owner_id BIGINT NOT NULL COMMENT '负责人',
    MODIFY COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    MODIFY COLUMN created_by BIGINT COMMENT '创建人',
    MODIFY COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    MODIFY COLUMN updated_by BIGINT COMMENT '更新人',
    MODIFY COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是';

-- ============================================================
-- 九、dm_asset_relation 缺失列级注释补齐（表级已有）
-- ============================================================
ALTER TABLE dm_asset_relation
    MODIFY COLUMN id BIGINT NOT NULL COMMENT '主键ID',
    MODIFY COLUMN tenant_id BIGINT NOT NULL COMMENT '租户ID',
    MODIFY COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    MODIFY COLUMN created_by BIGINT NOT NULL COMMENT '创建人';

-- ============================================================
-- 十、dm_issue 列级注释补齐（表级已有）
-- ============================================================
ALTER TABLE dm_issue
    MODIFY COLUMN id BIGINT NOT NULL COMMENT '主键ID',
    MODIFY COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
    MODIFY COLUMN project_id BIGINT NOT NULL COMMENT '所属项目（pm_project.id）',
    MODIFY COLUMN issue_code VARCHAR(96) NOT NULL COMMENT '问题编号，项目内唯一',
    MODIFY COLUMN issue_name VARCHAR(255) NOT NULL COMMENT '问题名称',
    MODIFY COLUMN granularity VARCHAR(16) COMMENT '粒度 PROJECT/COMPONENT/TABLE/FIELD',
    MODIFY COLUMN system_code VARCHAR(96) COMMENT '系统编号（arch_physical_subsystem.code）',
    MODIFY COLUMN system_name VARCHAR(160) COMMENT '系统名称',
    MODIFY COLUMN issue_source VARCHAR(32) COMMENT '问题来源',
    MODIFY COLUMN defect_type VARCHAR(32) COMMENT '缺陷类型',
    MODIFY COLUMN issue_description TEXT COMMENT '问题描述',
    MODIFY COLUMN solution TEXT COMMENT '解决方案',
    MODIFY COLUMN meeting_conclusion TEXT COMMENT '会议结论',
    MODIFY COLUMN processing_steps TEXT COMMENT '处理步骤',
    MODIFY COLUMN business_scenario VARCHAR(500) COMMENT '所属业务场景',
    MODIFY COLUMN handler VARCHAR(160) COMMENT '处理人',
    MODIFY COLUMN responsible_party VARCHAR(160) COMMENT '责任方',
    MODIFY COLUMN keywords VARCHAR(500) COMMENT '关键字（英文逗号分隔）',
    MODIFY COLUMN frequency VARCHAR(16) COMMENT '发生频率',
    MODIFY COLUMN owner_id BIGINT NOT NULL COMMENT '负责人',
    MODIFY COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    MODIFY COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    MODIFY COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    MODIFY COLUMN created_by BIGINT COMMENT '创建人',
    MODIFY COLUMN updated_by BIGINT COMMENT '更新人',
    MODIFY COLUMN deleted_by BIGINT COMMENT '删除人',
    MODIFY COLUMN deleted_at TIMESTAMP COMMENT '删除时间';

-- ============================================================
-- 十一、dm_meeting 缺失列级注释补齐（表级已有）
-- ============================================================
ALTER TABLE dm_meeting
    MODIFY COLUMN meeting_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    MODIFY COLUMN tenant_id BIGINT NOT NULL COMMENT '租户ID',
    MODIFY COLUMN project_id BIGINT NOT NULL COMMENT '所属项目（pm_project.id）',
    MODIFY COLUMN project_name VARCHAR(200) COMMENT '项目名称（冗余）',
    MODIFY COLUMN keywords JSON COMMENT '关键字（JSON数组）',
    MODIFY COLUMN deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    MODIFY COLUMN deleted_by BIGINT COMMENT '删除人',
    MODIFY COLUMN deleted_at DATETIME(6) COMMENT '删除时间',
    MODIFY COLUMN created_by BIGINT NOT NULL COMMENT '创建人',
    MODIFY COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    MODIFY COLUMN updated_by BIGINT COMMENT '更新人',
    MODIFY COLUMN updated_at DATETIME(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间';

-- ============================================================
-- 十二、dm_meeting_attachment 缺失列级注释补齐（表级已有）
-- ============================================================
ALTER TABLE dm_meeting_attachment
    MODIFY COLUMN id BIGINT NOT NULL COMMENT '主键ID',
    MODIFY COLUMN tenant_id BIGINT NOT NULL COMMENT '租户ID',
    MODIFY COLUMN meeting_id BIGINT NOT NULL COMMENT '所属会议（dm_meeting.meeting_id）',
    MODIFY COLUMN deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    MODIFY COLUMN deleted_by BIGINT COMMENT '删除人',
    MODIFY COLUMN deleted_at DATETIME(6) COMMENT '删除时间',
    MODIFY COLUMN created_by BIGINT NOT NULL COMMENT '创建人',
    MODIFY COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间';
