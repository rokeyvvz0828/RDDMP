---
id: REQ-20260826-054
status: "ready"
owner: rokeyvvz0828
module: business/architecture
source_issue: 09-network-zones-and-access-relationship
---

# 网络分区与网络访问关系

## 业务目标

在架构模块内建立网络分区、外部网络地址、网络访问申请与网络访问关系能力，并把网络分区作为部署单元、资源申请和资源下发的结构化字段贯通起来。

本需求把 Wayfinder 09 票据转换为仓库正式实施批次。外部 `temp/spec.md`、`temp/CONTEXT.md`、`temp/implementation/issues/09-network-zones-and-access-relationship.md` 与 `docs/original/tech/网络权限管理.md` 仅作为需求来源和术语证据；用户于 2026-08-26 直接下达“实现09 网络权限管理，并且实现部署单元、资源申请、资源下发中的网络分区功能”作为本批次开工授权。附件中的执行门禁、生成顺序、提醒回收等说明不自动成为本批次实现命令。

## 范围

### 本次实施

- 网络分区：支持维护树形网络分区，分区具有编码、名称、父分区、限制级别、状态和说明；环境部署实例必须归属到一个启用叶子分区；子分区限制级别不能低于父分区。
- 网络分区网段：支持为启用叶子网络分区维护一个或多个 CIDR 网段、网关、用途和状态；新增环境部署实例的 IP 地址必须落在所属网络分区的启用网段内；自动部署 Mock 预览/下发生成的 IP 必须从所属分区启用网段中取值。
- 外部网络地址：支持维护不属于环境部署实例的 IP、CIDR 或域名地址，用于网络访问申请的来源或目标。
- 部署单元网络分区：部署单元可维护默认网络分区，并在版本快照中保留该默认分区。
- 资源申请网络分区：资源登记明细继续保留兼容文本字段，同时新增结构化网络分区引用；非 DB 类型资源明细应选择启用叶子分区。
- 资源下发网络分区：自动部署预览和手动下发实例记录结构化网络分区；新建在用环境部署实例必须写入网络分区引用，并保留显示名称快照；当分区配置启用网段后，下发 IP 必须匹配该分区子网。
- 网络访问申请：申请人选择来源和目标端点；端点可以是“物理子系统、具体环境、部署单元”级联下的一个或多个环境部署实例，也可以是外部网络地址；默认使用当前级联结果内全部在用实例，跨部署单元需要拆分申请。
- 网络访问关系：网络访问申请办理成功后，按申请当时的来源实例集合、目标实例集合、协议、端口、用途和有效期形成访问关系快照；后续新增实例不自动加入既有关系。
- 权限、菜单和审计：新增网络分区与网络访问关系菜单、权限和角色授权；写操作保留操作审计。

### 本次不实施

- 真实网络设备、防火墙、云平台或 CLB/DNS/证书系统的外部开通动作。
- 定时提醒、自动回收、外部运维通知、附件上传和报表导出。
- 将原始文档中的四类网络工单动态表单整体重做；既有 CLB/DNS/证书专项工单继续保持兼容。
- 自动把部署单元未来新增实例加入历史访问关系；关系变更需另起申请。

## 业务规则

1. 网络分区为树形结构；启用子分区的限制级别必须大于或等于启用父分区；有启用子分区的父分区不能被用作新实例的归属分区。
2. 部署单元默认网络分区只作为申请和下发的默认值，不替代资源申请或实际下发时的服务端校验。
3. 资源申请和资源下发保留旧 `networkZone` 文本字段用于兼容历史数据；新流程优先使用 `networkZoneId`，展示名称以网络分区主数据为准。
4. 网络分区网段只允许维护在启用叶子分区上；有启用子分区或启用网段的父分区不能继续作为实例归属分区。
5. 环境部署实例的机器名和 IP 仍遵守 04 资源下发批次的唯一性与下线规则；新增在用实例必须选择启用叶子网络分区，且 IP 必须落入该分区至少一个启用 CIDR 网段。
6. 网络访问申请的一侧若选择托管实例，必须限定在一个部署单元内；来源和目标实例集合在提交时形成快照。
7. 批准网络访问申请只在 RDDMP 内形成网络访问关系记录，不代表真实网络设备已完成开通。
8. 关闭网络访问关系只关闭 RDDMP 内的关系状态，不直接回收外部网络策略。

## API 与数据

- API：
  - `GET/POST /api/architecture/network-zones`
  - `PUT /api/architecture/network-zones/{id}`
  - `POST /api/architecture/network-zones/{id}/deactivate|reactivate`
  - `GET/POST /api/architecture/network-zones/{zoneId}/subnets`
  - `PUT /api/architecture/network-zones/{zoneId}/subnets/{subnetId}`
  - `POST /api/architecture/network-zones/{zoneId}/subnets/{subnetId}/deactivate|reactivate`
  - `GET/POST /api/architecture/external-network-addresses`
  - `PUT /api/architecture/external-network-addresses/{id}`
  - `POST /api/architecture/external-network-addresses/{id}/deactivate|reactivate`
  - `GET/POST /api/architecture/network-access-applications`
  - `POST /api/architecture/network-access-applications/{id}/submit|approve|reject|cancel`
  - `GET /api/architecture/network-access-relations`
  - `POST /api/architecture/network-access-relations/{id}/close`
- 数据 Owner：`business/architecture` 拥有网络分区、外部网络地址、网络访问申请和关系；部署单元、资源申请、环境部署实例仍由架构模块原 Owner 维护。
- 数据库迁移：追加 `V111__create_architecture_network_zones_access.sql`、`V112__seed_architecture_network_zones_access.sql`、`V113__create_architecture_network_zone_subnets.sql` 与 `V114__seed_architecture_network_zones_from_reference_diagram.sql`，不修改已发布迁移。
- UAT 分区数据：`V114` 按用户确认的网络架构图初始化 P1/P2/P5/P8 分区和 CIDR 网段；P2 办公电脑区因原图仅标注“20 开头”暂不初始化网段。
- 权限：
  - `architecture:network-zone:view/manage`
  - `architecture:network-access:view/apply/manage`

## 验收标准

1. 管理人员可维护网络分区树；子分区限制级别低于父分区、父级非叶子分区被用于新实例时被拒绝。
2. 部署单元可选择默认网络分区；部署单元版本快照保留默认分区。
3. 资源申请明细和资源下发实例可选择结构化网络分区；环境部署实例列表展示网络分区名称。
4. 管理人员可为叶子网络分区维护 CIDR 网段；非法 CIDR、网关不在 CIDR 内、非叶子分区新增网段、停用分区新增网段均被拒绝。
5. 自动部署预览/下发生成的实例 IP 位于所属网络分区的启用网段；手动下发填写的 IP 不在所属分区启用网段内时被服务端拒绝。
6. 网络访问申请可选择托管来源和目标实例集合，或选择外部网络地址；跨部署单元实例被服务端拒绝。
7. 网络访问申请批准后生成网络访问关系，关系展示申请时的实例快照、协议、端口、用途和有效期；新增实例不改变历史关系。
8. 网络分区、外部地址、网络访问申请和关系写操作有权限控制与操作审计。
9. 架构模块聚焦测试、Flyway 迁移检查、前端构建和 diff 检查按实际执行结果记录。

## 测试与发布

- 必须执行：
  - `mvn -pl :ccb-architecture -am "-Dtest=NetworkAccessServiceTest,EnvironmentResourceServiceTest,DeploymentUnitServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
  - `npm --prefix web run build`
  - `node scripts/check-flyway-migrations.mjs`
  - `node scripts/check-codex-scope.mjs --scope docs/requirements/REQ-20260826-054-network-zones-access-relationship/codex-task-scope.yaml --base HEAD --head HEAD --working-tree`
  - `git diff --check`
- 上线验证：空库和既有库迁移到 V114；以网络管理角色维护分区、网段和外部地址；以申请角色提交网络访问申请；以管理角色批准并生成关系；资源申请、资源下发和实例列表验证网络分区贯通；桌面和移动视口检查页面无横向溢出。
- 回退或补偿：应用代码按前端、服务、迁移登记逆序回退；已执行迁移保留数据，通过后续补偿迁移隐藏菜单、撤销授权、停用网段或恢复兼容结构，不在生产手工删除表或数据。
