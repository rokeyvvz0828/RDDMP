# 部署单元版本与初始化导入 — 设计（REQ-20260823-049）

日期：2026-08-23 · 模块：business/architecture · 状态：approved-by-task

## 1. 目标与范围

在 `business/architecture` 内实现部署单元（US15-23）：稳定身份、永久唯一编号、
发布版本、停用/作废生命周期和受控 Excel 初始化导入。不引入新 Maven 模块；
不实现引用方模块（资源申请/计划/实例），只预留引用检查 SPI。

## 2. 领域模型

### 2.1 部署单元主记录 `arch_deployment_unit`

| 字段 | 说明 |
| --- | --- |
| id / tenant_id | 主键与租户（服务端注入） |
| code | 永久唯一编号，首次发布时分配，之后不可修改 |
| physical_subsystem_id | 归属物理子系统；发布后不可变更（更新接口不提供） |
| short_name / name | 显示名称；随版本变化，不改变稳定身份 |
| kind | `APPLICATION` / `DATABASE` / `MQ`（受控类型，可随版本变化） |
| status | `ACTIVE` / `INACTIVE` / `VOIDED` |
| current_version | 当前版本号（首次发布为 1） |
| description / remark | 展示内容，随版本快照 |
| row_version / created_by / updated_by / created_at / updated_at | 审计与乐观锁 |

名称唯一约束：`(tenant_id, physical_subsystem_id, name)`，含停用/作废记录，
防止重新导入产生二义身份。

### 2.2 版本表 `arch_deployment_unit_version`

- `(tenant_id, unit_id, version_no)` 唯一；version_no 从 1 递增。
- 快照字段：short_name / name / kind / description / remark / published_by /
  published_at；无更新/删除 API，版本行不可原地改写。
- 创建部署单元 = 首次发布（分配编号、写版本 1、状态 ACTIVE）。
- 更新 ACTIVE 部署单元的展示内容 = 事务内更新主记录并追加版本 N+1，操作审计
  （`ARCHITECTURE_DEPLOYMENT_UNIT_UPDATE`）。

### 2.3 编号规则

`D` + 物理子系统编号 + 三位序号，如物理 `W0001A` 的第二个部署单元为
`DW0001A002`；每物理子系统最多 999 个永久占用序号。

- 分配表 `arch_deployment_unit_number_seq(tenant_id, physical_subsystem_id,
  next_ordinal)`，发布事务内 `SELECT ... FOR UPDATE` 行锁后递增；
  主记录 `code` 加唯一索引兜底。
- 发布后编号不可修改、不可复用；作废/停用不归还序号。

### 2.4 生命周期状态机

```
ACTIVE --deactivate--> INACTIVE --reactivate--> ACTIVE
ACTIVE/INACTIVE --void--> VOIDED（终态）
```

- 停用：不校验引用，仅阻止新引用；历史保留。
- 作废：仅当引用检查 SPI 全部返回无引用且模块内无依赖时允许；检查异常或
  不可判定按有引用处理（fail-closed）；作废后编号仍不可复用。
- 更新仅允许 ACTIVE；INACTIVE 需先重新启用。

### 2.5 引用检查 SPI

`com.ccb.architecture.integration.DeploymentUnitReferenceChecker`（公开包），
沿用 `SubsystemReferenceChecker` 的中性契约风格：`checkerKey()` 与
`check(DeploymentUnitReferenceCheckRequest)` 返回
`ReferenceCheckResult`（复用既有记录，含 `INDETERMINATE` 语义）。
首期无实现方 → 默认无引用；异常按 `INDETERMINATE` 失败关闭。

## 3. 初始化导入

- 格式：Excel `.xlsx`（Apache POI 5.5.1，与 requirement/test-management 一致）。
- 模板列：`物理子系统编号, 部署单元简称, 部署单元名称, 部署单元类型, 描述, 备注`；
  类型取 `应用|数据库|消息队列`。
- 流程：
  1. `POST /imports` 上传 → 解析 + 校验 → 建批次（status=PREVIEW）与行明细
     （VALID/INVALID + 错误信息）→ 返回预览。
  2. `POST /imports/{id}/confirm`：对 VALID 行逐行事务“创建并发布版本 1”；
     行级失败记录 FAILED 明细并继续；批次整体异常整批回滚（status=FAILED）；
     全部成功 SUCCESS，部分失败 PARTIAL。
  3. 批次台账：来源文件名、行数、预览/成功/失败数、状态、创建人/时间；
     失败明细可导出 CSV 错误报告。
- 关系校验：物理子系统编号必须存在、ACTIVE 且属于当前租户；简称/名称长度与必填；
  类型受控；文件内与库内 `(physical, name)` 冲突检测。
- 幂等重导：库内已存在 ACTIVE 同名同物理行 → 视为“已存在（跳过）”计入成功；
  与 INACTIVE/VOIDED 同名 → 报错（名称不可复用）。

## 4. 权限与审计

- 新权限：`architecture:deployment-unit:view`（8041）、
  `architecture:deployment-unit:manage`（8042）；菜单 804（部署单元）、
  805（部署单元初始化导入，permission_code=manage）。
- 新角色 111 `ARCHITECTURE_TECHNICAL_MANAGER`（技术架构师）持有 8041+8042；
  超级管理员（角色 1）同样持有；存量拥有 `architecture:view` 的角色仅追加 8041。
- Controller `@PreAuthorize`：查询 `hasAnyAuthority('architecture:deployment-unit:view',
  'architecture:view','architecture:apply','architecture:manage')`；
  写操作仅 `'architecture:deployment-unit:manage'`。
- 全部写操作走 `SystemOperationAudit`；版本发布同时落版本快照。

## 5. API 一览

- `GET/POST /api/architecture/deployment-units`；`GET/PUT /{id}`；
  `GET /{id}/versions`；`POST /{id}/deactivate|reactivate|void`。
- `POST /api/architecture/deployment-unit-imports`（multipart）；
  `GET /api/architecture/deployment-unit-imports`；`GET /{id}`；
  `POST /{id}/confirm`；`GET /{id}/error-report`（CSV）；
  `GET /template`（模板下载）。
- 选项：`GET /api/architecture/options/deployment-unit/physical-subsystems`。

## 6. 测试策略

- 领域测试：编号格式/容量/并发、创建即版本 1、更新生成新版本且旧版本不可改写、
  状态机、引用守卫 fail-closed、导入解析/预览/确认/幂等。
- MySQL 集成测试（Testcontainers + 真实 Flyway 至 V86）：唯一约束、编号并发、
  版本不可改写、导入整批回滚与行级明细、权限种子存在性。
- 前端：列表/创建/详情版本/生命周期/导入上传预览确认/批次台账/错误报告下载，
  桌面与移动视口。

## 7. 风险与回退

- 编号并发：行锁 + 唯一索引兜底；测试覆盖并发。
- 版本不可改写：无更新接口 + 快照行 + 审计。
- 作废 fail-closed：SPI 异常按存在引用处理。
- 回退：保留迁移与数据，后续补偿迁移关闭入口；不逆向删除。
