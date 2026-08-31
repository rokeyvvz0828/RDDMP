---
id: REQ-20260823-049
status: ready
owner: rokeyvvz0828
module: business/architecture
source_issue: 02-deployment-unit-version-and-import
---

# 部署单元版本与初始化导入

## 业务目标

技术架构师在已发布的物理子系统下维护部署单元（可独立部署、升级、启停和运行的
环境无关架构定义，数据库、MQ 等满足独立生命周期边界的服务同样以部署单元表达），
获得稳定身份、永久唯一编号、可追溯的发布版本，以及受控的初始化导入能力。部署单元
成为可跨环境复用且历史可追溯的架构定义，且只影响后续计划和设计视图，绝不自动改写
既有计划快照或环境部署实例。

本需求以仓库治理和本文件为编码准入基准。外部 Wayfinder 票据（
`temp/implementation/issues/02-deployment-unit-version-and-import.md`）与
`temp/spec.md`、`temp/CONTEXT.md` 作为需求来源；用户于 2026-08-23 直接下达“实现部署
单元模块”任务，作为本批次开工授权。

## 范围

### 本次实施

- 在 `business/architecture` 模块内新增部署单元主记录：稳定身份、永久唯一编号、
  显示名称、部署单元类型、所属物理子系统、发布状态、当前版本号、描述与备注。
- 版本机制：首次创建即发布版本 1 并分配编号；已发布部署单元的显示内容变更自动
  形成新版本（版本行不可原地改写），完整保留版本快照与操作审计。
- 生命周期：停用（阻止新的引用但保留历史和实例）、重新启用、作废（仅从未被引用的
  错误部署单元，编号仍不可复用）。
- 变更规则：已发布部署单元不允许改挂物理子系统；停用/作废保留历史；编号一经发布
  永久占用、不可修改、不可复用。
- 初始化导入：Excel 模板上传 → 解析与关系校验 → 预览 → 确认写入 → 批次记录、
  成功/失败明细、错误报告导出；失败行不阻断成功行，修正后支持重新导入。
- 权限：只有技术架构师角色可执行维护写操作和导入；查询、导出遵循服务端 RBAC、
  租户隔离和审计要求。
- 前端：部署单元列表/创建/详情（含版本历史与生命周期操作）和初始化导入
  （上传、预览、确认、批次台账、错误报告下载），桌面与移动视口全状态。

### 本次不实施

- 资源申请、搭建计划/任务、环境部署实例对部署单元的引用（后续批次），仅提供
  引用检查 SPI 供未来模块接入。
- 部署平台、服务器类型等实例级属性（按规格属于环境部署实例，不在部署单元维护）。
- 部署单元公告、交付映射、部署记录、灾备关系。
- 租户概念扩展、动态表单元数据、AI 建议。
- 环境类型/具体环境/网络等本批次之外模块的任何改动。

## 现状与规则

- 当前入口：`business/architecture` 已实现逻辑/物理子系统主数据、变更工单、
  确定性编号（`A0001`/`W0001A`）与工作流审批；部署单元为其直接下级，由技术架构师
  直接维护，不经过变更工单审批。
- 业务规则：
  - 部署单元编号采用确定性规则：`D` + 物理子系统编号 + 该物理下三位递增序号
    （如物理 `W0001A` 的第二个部署单元为 `DW0001A002`）；每物理子系统最多 999 个
    永久占用序号，发布后编号不可修改、不可复用。
  - 首次创建即发布版本 1（分配编号、状态 ACTIVE）；后续显示内容（简称/名称/类型/
    描述/备注）变更即发布新版本，版本行不可原地改写。
  - 已发布部署单元不得改挂物理子系统（更新接口不提供归属字段）。
  - 停用不校验引用，只阻止新引用并保留历史；重新启用恢复可引用；作废仅允许
    从未被引用（引用检查 SPI + 模块内依赖检查均通过）且失败关闭（检查异常视为
    存在引用）。
  - 同一物理子系统下名称（tenant + physical + name）唯一，停用/作废记录同样占用
    名称，防止重新导入产生二义身份。
  - 导入行按物理子系统编号做关系校验；校验通过行确认写入时逐行事务，行级失败
    记录明细继续处理其余行；批次整体异常时整批回滚。
  - 重新导入同一文件时，已存在的 ACTIVE 同名同物理行视为“已存在（跳过）”，
    不重复创建；与停用/作废记录同名则报错。
- 角色、权限、数据范围和审计：
  - 新增 `architecture:deployment-unit:view`（查看）与
    `architecture:deployment-unit:manage`（维护写操作与导入）；新角色
    `ARCHITECTURE_TECHNICAL_MANAGER`（技术架构师）拥有两者；超级管理员同样拥有；
    已有 `architecture:view` 角色的存量角色仅获得查看权限。
  - 查询、导出、导入批次查看要求具备查看权限；创建/更新/停用/启用/作废/导入
    上传与确认要求具备维护权限。
  - 服务端校验认证、RBAC、当前租户、实体存在性、状态机与引用约束；租户标识只从
    认证上下文注入，HTTP DTO 不接受/返回 tenantId。
  - 所有写操作记录平台操作审计（操作人、动作、对象、结果、trace ID），版本发布
    同时落入版本行快照。
- 外部系统、附件或敏感信息：无外部系统；导入文件仅含虚构测试数据，不含生产数据。

## 接口与数据

- API 契约（全部 `{ code, data, message, traceId }`，`/api` 前缀）：
  - `GET /api/architecture/deployment-units`（分页筛选：编号/简称/名称/物理子系统/
    类型/状态）、`GET /api/architecture/deployment-units/{id}`、
    `GET /api/architecture/deployment-units/{id}/versions`。
  - `POST /api/architecture/deployment-units`（创建并发布版本 1）、
    `PUT /api/architecture/deployment-units/{id}`（修改并发布新版本）、
    `POST /api/architecture/deployment-units/{id}/deactivate`、
    `POST /api/architecture/deployment-units/{id}/reactivate`、
    `POST /api/architecture/deployment-units/{id}/void`。
  - `POST /api/architecture/deployment-unit-imports`（multipart Excel 上传，
    返回预览批次）、`GET /api/architecture/deployment-unit-imports`（批次分页）、
    `GET /api/architecture/deployment-unit-imports/{id}`（批次与明细）、
    `POST /api/architecture/deployment-unit-imports/{id}/confirm`（确认写入）、
    `GET /api/architecture/deployment-unit-imports/{id}/error-report`（失败明细 CSV
    导出）、`GET /api/architecture/deployment-unit-imports/template`（模板下载）。
  - 选项接口复用 `GET /api/architecture/options/{resource}/...` 模式补充
    `deployment-unit` 资源与物理子系统级联选项。
- 数据 Owner：`business/architecture`（`arch_` 前缀表）。
- 数据库迁移与存量兼容：仅追加 `V96__create_architecture_deployment_units.sql` 与
  `V97__seed_architecture_deployment_units.sql`；不修改 V1-V95；存量数据无需改写，
  空迁移对新库和既有库均幂等可重复执行。
- 脱敏输入输出示例：导入模板列
  `物理子系统编号,部署单元简称,部署单元名称,部署单元类型,描述,备注`；
  类型取值 `应用|数据库|消息队列`；虚构样例
  `W0001A,ECIP-AP,电子渠道接入应用,应用,渠道接入,演示数据`。

## 验收标准

1. 技术架构师可在已发布物理子系统下创建部署单元（应用/数据库/MQ 类型），首次创建
   即分配编号并形成版本 1；编号格式符合 `D<物理编号><三位序号>` 且全局唯一。
2. 编号一经发布不可修改、不可复用；显示名称/类型/描述变更形成新版本且版本行不可
   原地改写；修改历史可追溯到操作人、时间与快照。
3. 已发布部署单元不能改挂物理子系统；停用后不能用于新引用且历史保留；从未被引用
   的错误部署单元可作废，作废后编号仍不可复用；有引用或检查异常时作废失败关闭。
4. 只有 `architecture:deployment-unit:manage` 可执行创建、更新、停用、启用、作废与
   导入；只有查看权限返回 403；查询与导出遵守租户隔离，审计记录完整。
5. 导入流程具备预览、关系校验、确认写入、批次台账、成功/失败明细与错误报告导出；
   失败行不阻断成功行；修正后重新导入可幂等完成；批次级异常整批回滚。
6. 公开 API、真实 MySQL/Flyway、并发编号分配不重号、失败回滚以及桌面与移动端
   主要旅程（列表、创建、版本历史、生命周期操作、导入预览/确认/批次）均通过验收。

## 测试与发布

- 必须执行的测试：
  - 编号与生命周期领域测试：编号格式/容量/并发、创建即版本 1、更新生成新版本、
    停用/启用/作废状态机、引用守卫失败关闭。
  - MySQL 集成测试（Testcontainers + 真实迁移）：唯一约束、并发编号不重号、
    导入预览/确认/幂等重导/回滚、版本不可改写、权限与租户隔离。
  - `mvn -pl :ccb-architecture -am test`、`mvn test`、`npm --prefix web run build`。
  - `node scripts/check-all-governance.mjs`、`check-module-boundaries.mjs`、
    `check-flyway-migrations.mjs`、`check-codex-scope.mjs`、`git diff --check`。
- 上线验证：空库与既有库迁移至 V86；以技术架构师角色完成创建、改版本、停用、
  作废、导入全旅程；普通查看角色验证 403；真实浏览器桌面/移动视口 UAT。
- 回退或补偿：按前端、服务、迁移登记逆序回退应用代码；保留 V96-V97 与数据；
  通过后续补偿迁移隐藏入口，不执行生产逆向删除。
- 风险与人工复核人：数据库迁移与权限变更需模块 Owner 复核；导入采用 Excel
  （Apache POI，与 requirement/test-management 模块一致的既有依赖），模板格式
  变更需同步契约文档。
