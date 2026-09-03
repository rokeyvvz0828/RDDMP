-- REQ-20260903-063：测试报告和分析统计 V2。
-- 只追加结构：保留 V1 报告和统计快照的读取/导出能力。

CREATE TABLE tm_test_quality_threshold (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    test_domain VARCHAR(32) NOT NULL,
    project_id BIGINT NOT NULL,
    metric_code VARCHAR(64) NOT NULL,
    comparison_direction VARCHAR(16) NOT NULL COMMENT 'AT_LEAST/AT_MOST',
    qualified_threshold DECIMAL(12,4) NULL,
    risk_threshold DECIMAL(12,4) NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tm_quality_threshold (tenant_id,test_domain,project_id,metric_code),
    KEY idx_tm_quality_threshold_project (tenant_id,test_domain,project_id,enabled),
    CONSTRAINT fk_tm_quality_threshold_project FOREIGN KEY (project_id) REFERENCES pm_project(id) ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测试质量指标阈值配置';

ALTER TABLE tm_test_report_version
    ADD COLUMN report_semantic_version VARCHAR(16) NOT NULL DEFAULT 'V1' COMMENT '报告语义版本：V1/V2' AFTER version_no,
    ADD COLUMN quality_snapshot_json JSON NULL COMMENT '报告生成时冻结的质量阈值与判定' AFTER snapshot_json;
