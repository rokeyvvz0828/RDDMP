-- REQ-20260824-052 纠偏：资源申请明细按现有登记表口径扩展。
-- 仅扩展申请态登记字段，不创建实际环境部署实例或资源分配。

ALTER TABLE arch_resource_request_item
    DROP CHECK chk_arch_resource_request_item_nodes;

ALTER TABLE arch_resource_request_item
    MODIFY COLUMN storage_gb DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '文件存储需求（G）',
    MODIFY COLUMN planned_node_count INT NOT NULL DEFAULT 0 COMMENT '生产环境节点数',
    ADD COLUMN business_continuity_level VARCHAR(32) NULL COMMENT '农信业务连续性等级' AFTER deployment_unit_id,
    ADD COLUMN collected_system_level VARCHAR(32) NULL COMMENT '项目组收集系统等级' AFTER business_continuity_level,
    ADD COLUMN business_group_name VARCHAR(100) NULL COMMENT '所属事业群' AFTER collected_system_level,
    ADD COLUMN deployment_platform VARCHAR(64) NULL COMMENT '部署平台' AFTER business_group_name,
    ADD COLUMN disaster_recovery_mode VARCHAR(100) NULL COMMENT '灾备模式' AFTER deployment_platform,
    ADD COLUMN related_deployment_unit_name VARCHAR(500) NULL COMMENT '关联部署单元名称' AFTER disaster_recovery_mode,
    ADD COLUMN deployment_unit_description VARCHAR(2000) NULL COMMENT '部署单元简述' AFTER related_deployment_unit_name,
    ADD COLUMN deployment_unit_type VARCHAR(100) NULL COMMENT '部署单元类型' AFTER deployment_unit_description,
    ADD COLUMN database_storage_gb DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '数据库存储需求（G）' AFTER deployment_unit_type,
    ADD COLUMN network_zone VARCHAR(100) NULL COMMENT '网络分区' AFTER storage_gb,
    ADD COLUMN server_type VARCHAR(64) NULL COMMENT '服务器类型' AFTER network_zone,
    ADD COLUMN app_web_group_count INT NOT NULL DEFAULT 0 COMMENT 'AP、WEB组数' AFTER memory_gb,
    ADD COLUMN sidecar_cpu_cores DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '总边车CPU（已乘以节点数）' AFTER planned_node_count,
    ADD COLUMN sidecar_memory_gb DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '总边车内存（已乘以节点数）' AFTER sidecar_cpu_cores,
    ADD COLUMN has_sidecar TINYINT NOT NULL DEFAULT 0 COMMENT '是否有边车' AFTER sidecar_memory_gb,
    ADD COLUMN database_name VARCHAR(100) NULL COMMENT '数据库' AFTER has_sidecar,
    ADD COLUMN database_version VARCHAR(100) NULL COMMENT '数据库版本' AFTER database_name,
    ADD COLUMN jdk_version VARCHAR(100) NULL COMMENT 'JDK' AFTER database_version,
    ADD COLUMN middleware VARCHAR(500) NULL COMMENT '中间件' AFTER jdk_version,
    ADD COLUMN operating_system VARCHAR(200) NULL COMMENT '产品化操作系统' AFTER middleware,
    ADD COLUMN extra_cbs_gb DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '额外的CBS容量C' AFTER operating_system,
    ADD COLUMN local_disk_gb DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '本地盘需求（G）' AFTER extra_cbs_gb,
    ADD COLUMN needs_nft TINYINT NOT NULL DEFAULT 0 COMMENT '是否需要NFT' AFTER local_disk_gb,
    ADD COLUMN needs_fserver TINYINT NOT NULL DEFAULT 0 COMMENT '是否需要FSever' AFTER needs_nft,
    ADD COLUMN needs_jobexecutor TINYINT NOT NULL DEFAULT 0 COMMENT '是否需要jobexecutor' AFTER needs_fserver,
    ADD COLUMN confirmer_name VARCHAR(200) NULL COMMENT '最终确认人' AFTER needs_jobexecutor,
    ADD COLUMN confirmer_contact VARCHAR(200) NULL COMMENT '联系方式' AFTER confirmer_name,
    ADD CONSTRAINT chk_arch_resource_request_item_nodes CHECK (planned_node_count >= 0),
    ADD CONSTRAINT chk_arch_resource_request_item_db_storage CHECK (database_storage_gb >= 0),
    ADD CONSTRAINT chk_arch_resource_request_item_groups CHECK (app_web_group_count >= 0),
    ADD CONSTRAINT chk_arch_resource_request_item_sidecar_cpu CHECK (sidecar_cpu_cores >= 0),
    ADD CONSTRAINT chk_arch_resource_request_item_sidecar_memory CHECK (sidecar_memory_gb >= 0),
    ADD CONSTRAINT chk_arch_resource_request_item_has_sidecar CHECK (has_sidecar IN (0, 1)),
    ADD CONSTRAINT chk_arch_resource_request_item_extra_cbs CHECK (extra_cbs_gb >= 0),
    ADD CONSTRAINT chk_arch_resource_request_item_local_disk CHECK (local_disk_gb >= 0),
    ADD CONSTRAINT chk_arch_resource_request_item_needs_nft CHECK (needs_nft IN (0, 1)),
    ADD CONSTRAINT chk_arch_resource_request_item_needs_fserver CHECK (needs_fserver IN (0, 1)),
    ADD CONSTRAINT chk_arch_resource_request_item_needs_job CHECK (needs_jobexecutor IN (0, 1));

CREATE TEMPORARY TABLE tmp_arch_v94_registration_guard (
    marker TINYINT NOT NULL,
    CONSTRAINT chk_tmp_arch_v94_registration_guard CHECK (marker = 0)
) ENGINE=InnoDB;

INSERT INTO tmp_arch_v94_registration_guard (marker)
SELECT 1
WHERE NOT EXISTS (
          SELECT 1
          FROM information_schema.columns
          WHERE table_schema = DATABASE()
            AND table_name = 'arch_resource_request_item'
            AND column_name = 'database_storage_gb'
      )
   OR NOT EXISTS (
          SELECT 1
          FROM information_schema.columns
          WHERE table_schema = DATABASE()
            AND table_name = 'arch_resource_request_item'
            AND column_name = 'needs_jobexecutor'
      );

DROP TEMPORARY TABLE tmp_arch_v94_registration_guard;
