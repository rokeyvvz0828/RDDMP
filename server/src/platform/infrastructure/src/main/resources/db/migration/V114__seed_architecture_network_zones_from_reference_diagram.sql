-- REQ-20260826-054：按用户确认的网络架构图初始化 UAT 网络分区与网段。
-- 只追加图中 P1/P2/P5/P8 分区和 CIDR，不删除 V101/V102 的默认演示分区。

INSERT INTO arch_network_zone
    (id, tenant_id, parent_id, code, name, restriction_level, status, description, remark, created_by, updated_by)
SELECT 100000000001000, 1, NULL, 'P1', 'P1 互联网接入域', 0, 'ACTIVE',
       '网络架构图 P1 区域根节点', '2026-08-28 UAT 网络架构图初始化', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM arch_network_zone WHERE tenant_id = 1 AND code = 'P1');

INSERT INTO arch_network_zone
    (id, tenant_id, parent_id, code, name, restriction_level, status, description, remark, created_by, updated_by)
SELECT 100000000001001, 1, parent.id, 'P1_INTERNET_DMZ_IN', 'P1 互联网DMZ区-入向', 2, 'ACTIVE',
       '图中编号 1：互联网 DMZ 入向分区', '2026-08-28 UAT 网络架构图初始化', 1, 1
FROM arch_network_zone parent
WHERE parent.tenant_id = 1 AND parent.code = 'P1'
  AND NOT EXISTS (SELECT 1 FROM arch_network_zone WHERE tenant_id = 1 AND code = 'P1_INTERNET_DMZ_IN');

INSERT INTO arch_network_zone
    (id, tenant_id, parent_id, code, name, restriction_level, status, description, remark, created_by, updated_by)
SELECT 100000000001002, 1, parent.id, 'P1_INTERNET_DMZ_OUT', 'P1 互联网DMZ区-出向', 2, 'ACTIVE',
       '图中编号 2：互联网 DMZ 出向分区', '2026-08-28 UAT 网络架构图初始化', 1, 1
FROM arch_network_zone parent
WHERE parent.tenant_id = 1 AND parent.code = 'P1'
  AND NOT EXISTS (SELECT 1 FROM arch_network_zone WHERE tenant_id = 1 AND code = 'P1_INTERNET_DMZ_OUT');

INSERT INTO arch_network_zone
    (id, tenant_id, parent_id, code, name, restriction_level, status, description, remark, created_by, updated_by)
SELECT 100000000001003, 1, parent.id, 'P1_INTERNET_ISOLATION', 'P1 互联网隔离区', 3, 'ACTIVE',
       '图中编号 3：互联网隔离区，按图示需要 CLB', '2026-08-28 UAT 网络架构图初始化', 1, 1
FROM arch_network_zone parent
WHERE parent.tenant_id = 1 AND parent.code = 'P1'
  AND NOT EXISTS (SELECT 1 FROM arch_network_zone WHERE tenant_id = 1 AND code = 'P1_INTERNET_ISOLATION');

INSERT INTO arch_network_zone
    (id, tenant_id, parent_id, code, name, restriction_level, status, description, remark, created_by, updated_by)
SELECT 100000000002000, 1, NULL, 'P2', 'P2 终端接入域', 0, 'ACTIVE',
       '网络架构图 P2 区域根节点', '2026-08-28 UAT 网络架构图初始化', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM arch_network_zone WHERE tenant_id = 1 AND code = 'P2');

INSERT INTO arch_network_zone
    (id, tenant_id, parent_id, code, name, restriction_level, status, description, remark, created_by, updated_by)
SELECT 100000000002001, 1, parent.id, 'P2_OFFICE_TERMINAL', 'P2 办公电脑区', 1, 'ACTIVE',
       '图中编号 4：办公电脑，原图仅标注 20 开头，未给明确 CIDR', '待补充准确办公电脑 CIDR 后再维护网段', 1, 1
FROM arch_network_zone parent
WHERE parent.tenant_id = 1 AND parent.code = 'P2'
  AND NOT EXISTS (SELECT 1 FROM arch_network_zone WHERE tenant_id = 1 AND code = 'P2_OFFICE_TERMINAL');

INSERT INTO arch_network_zone
    (id, tenant_id, parent_id, code, name, restriction_level, status, description, remark, created_by, updated_by)
SELECT 100000000002002, 1, parent.id, 'P2_BUSINESS_TERMINAL', 'P2 业务终端区', 1, 'ACTIVE',
       '图中编号 5：业务终端区', '2026-08-28 UAT 网络架构图初始化', 1, 1
FROM arch_network_zone parent
WHERE parent.tenant_id = 1 AND parent.code = 'P2'
  AND NOT EXISTS (SELECT 1 FROM arch_network_zone WHERE tenant_id = 1 AND code = 'P2_BUSINESS_TERMINAL');

INSERT INTO arch_network_zone
    (id, tenant_id, parent_id, code, name, restriction_level, status, description, remark, created_by, updated_by)
SELECT 100000000005000, 1, NULL, 'P5', 'P5 外联接入域', 0, 'ACTIVE',
       '网络架构图 P5 区域根节点', '2026-08-28 UAT 网络架构图初始化', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM arch_network_zone WHERE tenant_id = 1 AND code = 'P5');

INSERT INTO arch_network_zone
    (id, tenant_id, parent_id, code, name, restriction_level, status, description, remark, created_by, updated_by)
SELECT 100000000005001, 1, parent.id, 'P5_EXTERNAL_DMZ_IMPORTANT', 'P5 重要外联DMZ区', 4, 'ACTIVE',
       '图中编号 7：重要外联 DMZ 区', '2026-08-28 UAT 网络架构图初始化', 1, 1
FROM arch_network_zone parent
WHERE parent.tenant_id = 1 AND parent.code = 'P5'
  AND NOT EXISTS (SELECT 1 FROM arch_network_zone WHERE tenant_id = 1 AND code = 'P5_EXTERNAL_DMZ_IMPORTANT');

INSERT INTO arch_network_zone
    (id, tenant_id, parent_id, code, name, restriction_level, status, description, remark, created_by, updated_by)
SELECT 100000000005002, 1, parent.id, 'P5_EXTERNAL_DMZ_GENERAL', 'P5 一般外联DMZ区', 3, 'ACTIVE',
       '图中编号 7：一般外联 DMZ 区', '2026-08-28 UAT 网络架构图初始化', 1, 1
FROM arch_network_zone parent
WHERE parent.tenant_id = 1 AND parent.code = 'P5'
  AND NOT EXISTS (SELECT 1 FROM arch_network_zone WHERE tenant_id = 1 AND code = 'P5_EXTERNAL_DMZ_GENERAL');

INSERT INTO arch_network_zone
    (id, tenant_id, parent_id, code, name, restriction_level, status, description, remark, created_by, updated_by)
SELECT 100000000008000, 1, NULL, 'P8', 'P8 开放服务域', 0, 'ACTIVE',
       '网络架构图 P8 区域根节点', '2026-08-28 UAT 网络架构图初始化', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM arch_network_zone WHERE tenant_id = 1 AND code = 'P8');

INSERT INTO arch_network_zone
    (id, tenant_id, parent_id, code, name, restriction_level, status, description, remark, created_by, updated_by)
SELECT 100000000008001, 1, parent.id, 'P8_OPEN_SERVICE', 'P8 开放服务区', 2, 'ACTIVE',
       '图中编号 6：开放区 WEB/AP、容器集群、虚机部署、百川、MPP、Hadoop 等实例归属分区', '2026-08-28 UAT 网络架构图初始化', 1, 1
FROM arch_network_zone parent
WHERE parent.tenant_id = 1 AND parent.code = 'P8'
  AND NOT EXISTS (SELECT 1 FROM arch_network_zone WHERE tenant_id = 1 AND code = 'P8_OPEN_SERVICE');

INSERT INTO arch_network_zone
    (id, tenant_id, parent_id, code, name, restriction_level, status, description, remark, created_by, updated_by)
SELECT 100000000008002, 1, parent.id, 'P8_DATABASE', 'P8 数据库区', 3, 'ACTIVE',
       '图中数据库区域；从开放服务区中单独拆分，便于 DB 实例归属和网络策略审计', '2026-08-28 UAT 网络架构图初始化', 1, 1
FROM arch_network_zone parent
WHERE parent.tenant_id = 1 AND parent.code = 'P8'
  AND NOT EXISTS (SELECT 1 FROM arch_network_zone WHERE tenant_id = 1 AND code = 'P8_DATABASE');

INSERT INTO arch_network_zone_subnet
    (id, tenant_id, network_zone_id, cidr_block, gateway_ip, purpose, status, remark, created_by, updated_by)
SELECT 100000000101001, 1, zone.id, '10.18.32.0/21', NULL, 'P1 入向 DMZ', 'ACTIVE', '图中编号 1', 1, 1
FROM arch_network_zone zone WHERE zone.tenant_id = 1 AND zone.code = 'P1_INTERNET_DMZ_IN'
  AND NOT EXISTS (SELECT 1 FROM arch_network_zone_subnet WHERE tenant_id = 1 AND cidr_block = '10.18.32.0/21');

INSERT INTO arch_network_zone_subnet
    (id, tenant_id, network_zone_id, cidr_block, gateway_ip, purpose, status, remark, created_by, updated_by)
SELECT 100000000101002, 1, zone.id, '10.22.32.0/21', NULL, 'P1 入向 DMZ', 'ACTIVE', '图中编号 1', 1, 1
FROM arch_network_zone zone WHERE zone.tenant_id = 1 AND zone.code = 'P1_INTERNET_DMZ_IN'
  AND NOT EXISTS (SELECT 1 FROM arch_network_zone_subnet WHERE tenant_id = 1 AND cidr_block = '10.22.32.0/21');

INSERT INTO arch_network_zone_subnet
    (id, tenant_id, network_zone_id, cidr_block, gateway_ip, purpose, status, remark, created_by, updated_by)
SELECT 100000000102001, 1, zone.id, '10.18.40.0/21', NULL, 'P1 出向 DMZ', 'ACTIVE', '图中编号 2', 1, 1
FROM arch_network_zone zone WHERE zone.tenant_id = 1 AND zone.code = 'P1_INTERNET_DMZ_OUT'
  AND NOT EXISTS (SELECT 1 FROM arch_network_zone_subnet WHERE tenant_id = 1 AND cidr_block = '10.18.40.0/21');

INSERT INTO arch_network_zone_subnet
    (id, tenant_id, network_zone_id, cidr_block, gateway_ip, purpose, status, remark, created_by, updated_by)
SELECT 100000000102002, 1, zone.id, '10.22.40.0/21', NULL, 'P1 出向 DMZ', 'ACTIVE', '图中编号 2', 1, 1
FROM arch_network_zone zone WHERE zone.tenant_id = 1 AND zone.code = 'P1_INTERNET_DMZ_OUT'
  AND NOT EXISTS (SELECT 1 FROM arch_network_zone_subnet WHERE tenant_id = 1 AND cidr_block = '10.22.40.0/21');

INSERT INTO arch_network_zone_subnet
    (id, tenant_id, network_zone_id, cidr_block, gateway_ip, purpose, status, remark, created_by, updated_by)
SELECT 100000000103001, 1, zone.id, '10.18.0.0/20', NULL, 'P1 互联网隔离区', 'ACTIVE', '图中编号 3', 1, 1
FROM arch_network_zone zone WHERE zone.tenant_id = 1 AND zone.code = 'P1_INTERNET_ISOLATION'
  AND NOT EXISTS (SELECT 1 FROM arch_network_zone_subnet WHERE tenant_id = 1 AND cidr_block = '10.18.0.0/20');

INSERT INTO arch_network_zone_subnet
    (id, tenant_id, network_zone_id, cidr_block, gateway_ip, purpose, status, remark, created_by, updated_by)
SELECT 100000000103002, 1, zone.id, '10.22.0.0/20', NULL, 'P1 互联网隔离区', 'ACTIVE', '图中编号 3', 1, 1
FROM arch_network_zone zone WHERE zone.tenant_id = 1 AND zone.code = 'P1_INTERNET_ISOLATION'
  AND NOT EXISTS (SELECT 1 FROM arch_network_zone_subnet WHERE tenant_id = 1 AND cidr_block = '10.22.0.0/20');

INSERT INTO arch_network_zone_subnet
    (id, tenant_id, network_zone_id, cidr_block, gateway_ip, purpose, status, remark, created_by, updated_by)
SELECT 100000000202001, 1, zone.id, '10.19.0.0/19', NULL, 'P2 业务终端', 'ACTIVE', '图中编号 5；编号 4 办公电脑 CIDR 待补充', 1, 1
FROM arch_network_zone zone WHERE zone.tenant_id = 1 AND zone.code = 'P2_BUSINESS_TERMINAL'
  AND NOT EXISTS (SELECT 1 FROM arch_network_zone_subnet WHERE tenant_id = 1 AND cidr_block = '10.19.0.0/19');

INSERT INTO arch_network_zone_subnet
    (id, tenant_id, network_zone_id, cidr_block, gateway_ip, purpose, status, remark, created_by, updated_by)
SELECT 100000000202002, 1, zone.id, '10.23.0.0/19', NULL, 'P2 业务终端', 'ACTIVE', '图中编号 5；编号 4 办公电脑 CIDR 待补充', 1, 1
FROM arch_network_zone zone WHERE zone.tenant_id = 1 AND zone.code = 'P2_BUSINESS_TERMINAL'
  AND NOT EXISTS (SELECT 1 FROM arch_network_zone_subnet WHERE tenant_id = 1 AND cidr_block = '10.23.0.0/19');

INSERT INTO arch_network_zone_subnet
    (id, tenant_id, network_zone_id, cidr_block, gateway_ip, purpose, status, remark, created_by, updated_by)
SELECT 100000000501001, 1, zone.id, '10.18.96.0/20', NULL, 'P5 重要外联 DMZ', 'ACTIVE', '图中编号 7', 1, 1
FROM arch_network_zone zone WHERE zone.tenant_id = 1 AND zone.code = 'P5_EXTERNAL_DMZ_IMPORTANT'
  AND NOT EXISTS (SELECT 1 FROM arch_network_zone_subnet WHERE tenant_id = 1 AND cidr_block = '10.18.96.0/20');

INSERT INTO arch_network_zone_subnet
    (id, tenant_id, network_zone_id, cidr_block, gateway_ip, purpose, status, remark, created_by, updated_by)
SELECT 100000000501002, 1, zone.id, '10.22.96.0/20', NULL, 'P5 重要外联 DMZ', 'ACTIVE', '图中编号 7', 1, 1
FROM arch_network_zone zone WHERE zone.tenant_id = 1 AND zone.code = 'P5_EXTERNAL_DMZ_IMPORTANT'
  AND NOT EXISTS (SELECT 1 FROM arch_network_zone_subnet WHERE tenant_id = 1 AND cidr_block = '10.22.96.0/20');

INSERT INTO arch_network_zone_subnet
    (id, tenant_id, network_zone_id, cidr_block, gateway_ip, purpose, status, remark, created_by, updated_by)
SELECT 100000000502001, 1, zone.id, '10.18.128.0/24', NULL, 'P5 一般外联 DMZ', 'ACTIVE', '图中编号 7', 1, 1
FROM arch_network_zone zone WHERE zone.tenant_id = 1 AND zone.code = 'P5_EXTERNAL_DMZ_GENERAL'
  AND NOT EXISTS (SELECT 1 FROM arch_network_zone_subnet WHERE tenant_id = 1 AND cidr_block = '10.18.128.0/24');

INSERT INTO arch_network_zone_subnet
    (id, tenant_id, network_zone_id, cidr_block, gateway_ip, purpose, status, remark, created_by, updated_by)
SELECT 100000000502002, 1, zone.id, '10.22.128.0/20', NULL, 'P5 一般外联 DMZ', 'ACTIVE', '图中编号 7', 1, 1
FROM arch_network_zone zone WHERE zone.tenant_id = 1 AND zone.code = 'P5_EXTERNAL_DMZ_GENERAL'
  AND NOT EXISTS (SELECT 1 FROM arch_network_zone_subnet WHERE tenant_id = 1 AND cidr_block = '10.22.128.0/20');

INSERT INTO arch_network_zone_subnet
    (id, tenant_id, network_zone_id, cidr_block, gateway_ip, purpose, status, remark, created_by, updated_by)
SELECT 100000000801001, 1, zone.id, '10.16.0.0/20', NULL, 'P8 虚机部署单元', 'ACTIVE', '图中编号 6', 1, 1
FROM arch_network_zone zone WHERE zone.tenant_id = 1 AND zone.code = 'P8_OPEN_SERVICE'
  AND NOT EXISTS (SELECT 1 FROM arch_network_zone_subnet WHERE tenant_id = 1 AND cidr_block = '10.16.0.0/20');

INSERT INTO arch_network_zone_subnet
    (id, tenant_id, network_zone_id, cidr_block, gateway_ip, purpose, status, remark, created_by, updated_by)
SELECT 100000000801002, 1, zone.id, '10.20.0.0/20', NULL, 'P8 虚机部署单元', 'ACTIVE', '图中编号 6', 1, 1
FROM arch_network_zone zone WHERE zone.tenant_id = 1 AND zone.code = 'P8_OPEN_SERVICE'
  AND NOT EXISTS (SELECT 1 FROM arch_network_zone_subnet WHERE tenant_id = 1 AND cidr_block = '10.20.0.0/20');

INSERT INTO arch_network_zone_subnet
    (id, tenant_id, network_zone_id, cidr_block, gateway_ip, purpose, status, remark, created_by, updated_by)
SELECT 100000000801003, 1, zone.id, '10.16.16.0/20', NULL, 'P8 Hadoop', 'ACTIVE', '图中编号 6', 1, 1
FROM arch_network_zone zone WHERE zone.tenant_id = 1 AND zone.code = 'P8_OPEN_SERVICE'
  AND NOT EXISTS (SELECT 1 FROM arch_network_zone_subnet WHERE tenant_id = 1 AND cidr_block = '10.16.16.0/20');

INSERT INTO arch_network_zone_subnet
    (id, tenant_id, network_zone_id, cidr_block, gateway_ip, purpose, status, remark, created_by, updated_by)
SELECT 100000000801004, 1, zone.id, '10.20.16.0/20', NULL, 'P8 Hadoop', 'ACTIVE', '图中编号 6', 1, 1
FROM arch_network_zone zone WHERE zone.tenant_id = 1 AND zone.code = 'P8_OPEN_SERVICE'
  AND NOT EXISTS (SELECT 1 FROM arch_network_zone_subnet WHERE tenant_id = 1 AND cidr_block = '10.20.16.0/20');

INSERT INTO arch_network_zone_subnet
    (id, tenant_id, network_zone_id, cidr_block, gateway_ip, purpose, status, remark, created_by, updated_by)
SELECT cidr.id, 1, zone.id, cidr.cidr_block, NULL, 'P8 开放区容器集群', 'ACTIVE', '图中编号 6：6 个容器集群', 1, 1
FROM arch_network_zone zone
JOIN (
    SELECT 100000000801005 AS id, '10.16.32.0/20' AS cidr_block UNION ALL
    SELECT 100000000801006, '10.20.32.0/20' UNION ALL
    SELECT 100000000801007, '10.16.48.0/20' UNION ALL
    SELECT 100000000801008, '10.20.48.0/20' UNION ALL
    SELECT 100000000801009, '10.16.64.0/20' UNION ALL
    SELECT 100000000801010, '10.20.64.0/20' UNION ALL
    SELECT 100000000801011, '10.16.80.0/20' UNION ALL
    SELECT 100000000801012, '10.20.80.0/20' UNION ALL
    SELECT 100000000801013, '10.16.96.0/20' UNION ALL
    SELECT 100000000801014, '10.20.96.0/20' UNION ALL
    SELECT 100000000801015, '10.16.112.0/20' UNION ALL
    SELECT 100000000801016, '10.20.112.0/20'
) cidr
WHERE zone.tenant_id = 1 AND zone.code = 'P8_OPEN_SERVICE'
  AND NOT EXISTS (
      SELECT 1 FROM arch_network_zone_subnet subnet
      WHERE subnet.tenant_id = 1 AND subnet.cidr_block = cidr.cidr_block
  );

INSERT INTO arch_network_zone_subnet
    (id, tenant_id, network_zone_id, cidr_block, gateway_ip, purpose, status, remark, created_by, updated_by)
SELECT 100000000801017, 1, zone.id, '10.16.240.0/20', NULL, 'P8 百川集群', 'ACTIVE', '图中编号 6', 1, 1
FROM arch_network_zone zone WHERE zone.tenant_id = 1 AND zone.code = 'P8_OPEN_SERVICE'
  AND NOT EXISTS (SELECT 1 FROM arch_network_zone_subnet WHERE tenant_id = 1 AND cidr_block = '10.16.240.0/20');

INSERT INTO arch_network_zone_subnet
    (id, tenant_id, network_zone_id, cidr_block, gateway_ip, purpose, status, remark, created_by, updated_by)
SELECT 100000000801018, 1, zone.id, '10.20.240.0/20', NULL, 'P8 百川集群', 'ACTIVE', '图中编号 6', 1, 1
FROM arch_network_zone zone WHERE zone.tenant_id = 1 AND zone.code = 'P8_OPEN_SERVICE'
  AND NOT EXISTS (SELECT 1 FROM arch_network_zone_subnet WHERE tenant_id = 1 AND cidr_block = '10.20.240.0/20');

INSERT INTO arch_network_zone_subnet
    (id, tenant_id, network_zone_id, cidr_block, gateway_ip, purpose, status, remark, created_by, updated_by)
SELECT 100000000801019, 1, zone.id, '10.17.0.0/19', NULL, 'P8 MPP', 'ACTIVE', '图中编号 6', 1, 1
FROM arch_network_zone zone WHERE zone.tenant_id = 1 AND zone.code = 'P8_OPEN_SERVICE'
  AND NOT EXISTS (SELECT 1 FROM arch_network_zone_subnet WHERE tenant_id = 1 AND cidr_block = '10.17.0.0/19');

INSERT INTO arch_network_zone_subnet
    (id, tenant_id, network_zone_id, cidr_block, gateway_ip, purpose, status, remark, created_by, updated_by)
SELECT 100000000801020, 1, zone.id, '10.21.0.0/19', NULL, 'P8 MPP', 'ACTIVE', '图中编号 6', 1, 1
FROM arch_network_zone zone WHERE zone.tenant_id = 1 AND zone.code = 'P8_OPEN_SERVICE'
  AND NOT EXISTS (SELECT 1 FROM arch_network_zone_subnet WHERE tenant_id = 1 AND cidr_block = '10.21.0.0/19');

INSERT INTO arch_network_zone_subnet
    (id, tenant_id, network_zone_id, cidr_block, gateway_ip, purpose, status, remark, created_by, updated_by)
SELECT 100000000802001, 1, zone.id, '10.17.32.0/20', NULL, 'P8 数据库', 'ACTIVE', '图中数据库区域', 1, 1
FROM arch_network_zone zone WHERE zone.tenant_id = 1 AND zone.code = 'P8_DATABASE'
  AND NOT EXISTS (SELECT 1 FROM arch_network_zone_subnet WHERE tenant_id = 1 AND cidr_block = '10.17.32.0/20');

INSERT INTO arch_network_zone_subnet
    (id, tenant_id, network_zone_id, cidr_block, gateway_ip, purpose, status, remark, created_by, updated_by)
SELECT 100000000802002, 1, zone.id, '10.21.32.0/20', NULL, 'P8 数据库', 'ACTIVE', '图中数据库区域', 1, 1
FROM arch_network_zone zone WHERE zone.tenant_id = 1 AND zone.code = 'P8_DATABASE'
  AND NOT EXISTS (SELECT 1 FROM arch_network_zone_subnet WHERE tenant_id = 1 AND cidr_block = '10.21.32.0/20');
