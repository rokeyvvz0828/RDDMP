-- REQ-20260826-054：网络分区网段，用于资源下发实例 IP 与所属分区子网匹配。

CREATE TABLE arch_network_zone_subnet (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    network_zone_id BIGINT NOT NULL,
    cidr_block VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    gateway_ip VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    purpose VARCHAR(500) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
    remark VARCHAR(1000) NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_arch_network_zone_subnet_tenant_id (tenant_id, id),
    UNIQUE KEY uk_arch_network_zone_subnet_cidr (tenant_id, cidr_block),
    KEY idx_arch_network_zone_subnet_zone (tenant_id, network_zone_id, status, id),
    CONSTRAINT fk_arch_network_zone_subnet_zone
        FOREIGN KEY (tenant_id, network_zone_id)
        REFERENCES arch_network_zone (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_arch_network_zone_subnet_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT chk_arch_network_zone_subnet_row_version CHECK (row_version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='网络分区网段';

-- 本地默认分区网段，用于空库演示和 Mock 自动下发；生产可停用后维护真实网段。
INSERT INTO arch_network_zone_subnet
    (id, tenant_id, network_zone_id, cidr_block, gateway_ip, purpose, status, remark, created_by, updated_by)
SELECT 100000000000111, zone.tenant_id, zone.id, '10.250.10.0/24', '10.250.10.1',
       '默认应用区 Mock 下发网段', 'ACTIVE', '本地演示数据，可按真实网络规划停用或替换', 1, 1
FROM arch_network_zone zone
WHERE zone.tenant_id = 1
  AND zone.code = 'ZONE_APP'
  AND NOT EXISTS (
      SELECT 1 FROM arch_network_zone_subnet subnet
      WHERE subnet.tenant_id = zone.tenant_id AND subnet.cidr_block = '10.250.10.0/24'
  );

INSERT INTO arch_network_zone_subnet
    (id, tenant_id, network_zone_id, cidr_block, gateway_ip, purpose, status, remark, created_by, updated_by)
SELECT 100000000000112, zone.tenant_id, zone.id, '10.250.20.0/24', '10.250.20.1',
       '默认数据区 Mock 下发网段', 'ACTIVE', '本地演示数据，可按真实网络规划停用或替换', 1, 1
FROM arch_network_zone zone
WHERE zone.tenant_id = 1
  AND zone.code = 'ZONE_DATA'
  AND NOT EXISTS (
      SELECT 1 FROM arch_network_zone_subnet subnet
      WHERE subnet.tenant_id = zone.tenant_id AND subnet.cidr_block = '10.250.20.0/24'
  );
