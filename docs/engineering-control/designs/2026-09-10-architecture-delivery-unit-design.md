# 交付单元（架构管理）工程设计

## 文档状态
- 修订：1
- 状态：已确认
- 用户确认依据：2026-09-10 当前会话，用户先提出需求，随后确认“关联范围=同一物理子系统、关系无方向、两侧可查、逻辑子系统已删除”，并在复核设计文档时回复“可以”。
- 关联需求：`docs/requirements/REQ-20260910-073-architecture-delivery-unit/requirement.md`

## 目标与成功信号
在架构管理菜单下提供交付单元主数据与其部署单元关联的完整维护能力，使“交付单元由哪些部署单元承载”和“某部署单元被哪些交付单元使用”可被直接查询。

成功信号：
- 具备查看权限的用户可在架构管理菜单进入“交付单元”，完成增删改查并看到抽屉式信息展示。
- 交付单元详情抽屉内可以增删同一物理子系统的部署单元关联，保存后立即生效。
- 部署单元详情抽屉可以看到关联的交付单元。
- 无权限、跨项目、跨物理子系统、重名、并发冲突均被服务端拒绝并给出可读提示。

## 使用者与场景
- 技术架构师（`architecture:delivery-unit:manage`）：维护交付单元与关联。
- 架构相关角色（`architecture:view/apply/manage`、`architecture:deployment-unit:view`）：只读查看交付单元。
- 平台管理员（超级管理员）：菜单与权限种子已授予。

## 必须需求与验收条件

| ID | 必须行为 | 验收 | 反例 |
| --- | --- | --- | --- |
| R1 | 新增交付单元：名称 + 归属物理子系统，系统分配编号 | 创建成功返回 `DU<物理编号><三位序号>`；同项目同物理子系统内重名被拒 | 手工填写或修改编号成功；跨物理子系统重名被误拒 |
| R2 | 查询：分页列表（名称/归属物理子系统/状态筛选）与抽屉式详情 | 列表与详情字段完整、分页正确、筛选生效 | 列表返回其他项目数据 |
| R3 | 修改：名称/描述/备注可改，归属不可改，乐观锁保护 | 正常修改递增 row_version；旧 row_version 提交被拒 | 通过修改接口改变归属物理子系统 |
| R4 | 删除：软删除 + 二次确认，并清理其关联 | 删除后列表不含该记录；关联表不再存在该交付单元的行；部署单元主记录不受影响 | 删除被关联的交付单元后部署单元侧仍显示该关联 |
| R5 | 关联部署单元：仅同一物理子系统、无方向、交付单元详情抽屉内维护 | 跨物理子系统 ID 提交被拒；合法关联保存后交付单元详情可见 | 通过构造请求关联其他物理子系统的部署单元 |
| R6 | 反向可查：部署单元详情可查看关联交付单元 | 部署单元详情抽屉展示关联交付单元 | 只能从交付单元一侧查询 |
| R7 | 权限与菜单：架构管理下新增菜单，view/manage 两级权限，服务端强制校验并审计 | 无权限 403；写操作有审计记录 | 仅靠前端隐藏按钮即视为受控 |
| R8 | 迁移只追加、既有契约不变、桌面与移动视口全状态可用 | 治理与迁移检查通过；四视口验收覆盖加载/空/失败/无权限/提交中 | 修改历史迁移或删除既有字段 |

## 不变量与约束
- 交付单元归属物理子系统在创建后不可变更；关联双方必须属于同一物理子系统（数据库复合外键强制）。
- 交付单元编号在创建时分配、全局不可修改，序号永久占用不回收。
- 只读写本模块 `arch_` 表；平台数据通过 `com.ccb.system.capability` 公开契约访问。
- 租户与项目只能来自服务端认证与项目上下文，HTTP DTO 不得接收 `tenantId`。
- Flyway 只能追加新迁移；不修改已发布脚本。
- 关联表由交付单元一侧拥有；部署单元作废时把该关联纳入引用守卫并 fail-closed。

## 非目标
- 不改 `release` 模块 mock 交付单元；不做交付版本、制品、交付映射审批与 Excel 导入。
- 不恢复逻辑子系统模型；不调整物理子系统、部署单元既有契约与既有数据。
- 不在部署单元侧提供关联编辑入口。
- 不新增平台公共组件、公共包或公共 API。

## 方案比较与选择

**选定方案：架构模块内新增独立交付单元聚合 + 单张无方向关联表**
- 理由：交付单元是稳定逻辑身份，与部署单元生命周期独立；单张关系表同时满足双向查询；同物理子系统约束可用复合外键在数据库层强制，避免只靠服务层校验产生脏关联。
- 备选一「复用部署单元表增加类型列」：被拒。两种对象字段、生命周期与编号规则不同，混表会导致既有唯一键、校验和列表语义被污染。
- 备选二「仅服务层校验同一物理子系统，不加复合外键」：被拒。约束会随新增入口（导入、批量脚本）漂移，且本需求把该规则列为必须需求。
- 备选三「前端 mock / 不落库」：被拒。需求要求真实增删改查并作为交付单元主数据来源。

## 架构边界与组件职责
- `boundary.inside`：`business/architecture` 的交付单元模型、存储、服务、控制器与前端页面组件；本需求追加的 V202 迁移与菜单/权限种子。
- `boundary.outside`：`release` 等业务模块、平台 system/security 实现、物理子系统与部署单元既有契约。
- 组件：
  - `DeliveryUnitModels`（模型与命令/查询 DTO）：领域值与受控枚举。
  - `DeliveryUnitStore`（持久化）：主记录、编号分配（物理子系统级行锁）、关联读写、分页查询。
  - `DeliveryUnitService`（应用服务）：参数与权限校验、同物理子系统约束校验、事务编排、审计。
  - `DeliveryUnitController`（Web）：权限注解、项目上下文获取、`ApiResponse` 包装。
  - `DeliveryUnitPage.vue` + `DeliveryUnitDetailDrawer.vue`（前端）：列表、抽屉表单、详情与关联维护。
  - `DeploymentUnitService` / `DeploymentUnitDetailDrawer.vue`（既有组件的小幅扩展）：作废引用守卫 + 只读反查区块。
- 依赖方向：交付单元 → 物理子系统 / 部署单元（同模块内），平台能力只经公开契约。

## 接口、数据和状态流

### 数据模型（新增迁移 `V202__create_architecture_delivery_units.sql`）
`arch_delivery_unit`
- `id, tenant_id, project_id, code, physical_subsystem_id, name, status, description, remark, deleted, row_version, created_by, updated_by, created_at, updated_at`
- 唯一键：`(tenant_id, project_id, physical_subsystem_id, name)`、`(tenant_id, code)`、`(tenant_id, physical_subsystem_id, id)`（供关联表复合外键）
- 外键：`(tenant_id, physical_subsystem_id)` → `arch_physical_subsystem`
- 约束：`status IN ('ACTIVE','INACTIVE')`、`deleted IN (0,1)`、`row_version >= 0`

`arch_delivery_unit_number_seq`
- `tenant_id, project_id, physical_subsystem_id, next_ordinal`
- 编号分配在事务内对该行 `SELECT ... FOR UPDATE`，与部署单元编号策略一致；上限 999。

`arch_delivery_unit_deployment_unit`
- `tenant_id, project_id, physical_subsystem_id, delivery_unit_id, deployment_unit_id, created_by, created_at`
- 主键 `(tenant_id, delivery_unit_id, deployment_unit_id)`；索引 `(tenant_id, deployment_unit_id)`
- 复合外键 `(tenant_id, physical_subsystem_id, delivery_unit_id)` → `arch_delivery_unit(tenant_id, physical_subsystem_id, id)` 与 `(tenant_id, physical_subsystem_id, deployment_unit_id)` → `arch_deployment_unit(tenant_id, physical_subsystem_id, id)`
- 需要给 `arch_deployment_unit` 追加唯一键 `(tenant_id, physical_subsystem_id, id)`（追加迁移，存量数据天然唯一）

### API `/api/architecture/delivery-units`
| 方法 | 路径 | 权限 | 行为 |
| --- | --- | --- | --- |
| GET | `/` | view | 分页列表，筛选 `name/physicalSubsystemId/status`，必填 `projectRef` |
| GET | `/{id}` | view | 详情，含 `relatedDeploymentUnits` |
| POST | `/` | manage | 新增：`physicalSubsystemId,name,description,remark,relatedDeploymentUnitIds[]` |
| PUT | `/{id}` | manage | 修改：`name,description,remark,rowVersion`；请求体携带与当前值不同的 `physicalSubsystemId` 时返回 400 |
| PUT | `/{id}/deployment-units` | manage | 覆盖式更新关联集合（原子） |
| POST | `/{id}/deactivate`、`/{id}/reactivate` | manage | 状态流转 |
| DELETE | `/{id}` | manage | 软删除并清理关联 |
| GET | `/api/architecture/deployment-units/{id}/delivery-units` | 部署单元 view | 只读反查 |

- 错误语义：参数非法 400；无权限 403；重名/同项目冲突 409；跨物理子系统关联 409；并发旧版本 409；不存在 404。

### 状态与数据流
新增：校验项目与物理子系统（启用、未删除）→ 事务内分配编号 → 插入主记录 → 校验并写入关联 → 审计。
修改：读取主记录（项目内、未删除）→ 校验 `row_version` → 更新名称/描述/备注 → 审计。
关联更新：读取主记录 → 校验目标部署单元属于同一物理子系统且未删除、状态启用 → 差集增删 → 审计。
删除：读取主记录 → 删除关联 → 软删除主记录 → 审计。
反查：按 `deployment_unit_id` 关联交付单元主表，过滤未删除，返回只读列表。

## 错误、降级与恢复
- 编号容量用尽（>999）：返回冲突错误并提示联系管理员，不降级为复用序号。
- 审计写入失败：记录告警日志，不阻断业务结果（与现有架构写操作一致）。
- 物理子系统选项加载失败：列表主流程不受阻，抽屉内给出可重试提示。
- 关联目标在提交前被停用/删除：事务内校验失败并回滚，前端提示重新选择。

## 安全、性能、兼容性与运维
- 安全：服务端 `@PreAuthorize` 两级权限 + `projectAccessService.requireAccessible` 项目校验 + 实体归属校验；前端显隐仅改善体验。
- 性能：列表按 `(tenant_id, project_id, status, id)` 建索引；关联反查走 `(tenant_id, deployment_unit_id)` 索引；关联列表批量查询，避免 N+1。
- 兼容性：纯新增表、菜单与权限行；既有接口与数据结构不变；`DeploymentUnitView` 契约不变。
- 运维：迁移可重复部署（种子用 `NOT EXISTS`/`INSERT IGNORE` 与稳定 ID）；回退为回退代码 + 停用菜单权限。

## 验证策略
| 需求 | 信号 | 方式 |
| --- | --- | --- |
| R1 | 创建成功返回合规编号；重名 409 | 服务与控制器聚焦测试 |
| R2 | 列表分页与详情字段 | 存储集成测试 + 浏览器验收 |
| R3 | 归属不可改、乐观锁 409 | 服务测试 |
| R4 | 软删除后列表与会话查询不返回，关联清理 | 存储集成测试 |
| R5 | 跨物理子系统关联被拒；合法关联持久化 | 服务测试 + 数据库约束测试 |
| R6 | 部署单元反查返回交付单元 | 控制器测试 + 浏览器验收 |
| R7 | 无权限 403、写操作审计 | 权限测试 + 审计断言 |
| R8 | 迁移/治理检查、前端构建、四视口 | `check-flyway-migrations.mjs`、`check-all-governance.mjs`、`npm --prefix web run build`、浏览器验收 |

## 假设、未知项与决策记录
- 假设：`arch_deployment_unit` 的 `(tenant_id, physical_subsystem_id, id)` 在存量数据上唯一（由主键 `id` 蕴含）。推翻证据：存在同一 id 对应多个物理子系统的数据（不可能，id 为主键）。
- 假设：架构角色 111 与超级管理员 1 是交付单元维护的授权主体（与 V97 部署单元一致）。
- 未知项：无阻塞项。`release` 模块是否切换为读取本主数据由后续独立需求决定。
- 决策：
  - D1 关联仅限同一物理子系统，并由数据库复合外键强制（用户确认 B）。
  - D2 关联无方向，单表双查（用户补充需求）。
  - D3 只做增删改查，不引入版本快照与导入（YAGNI，用户确认）。
  - D4 删除为软删除 + 关联清理；部署单元作废纳入引用守卫。
  - D5 编号格式 `DU<物理编号><三位序号>`，创建时分配。

## 风险与回退原则
- 风险：复合外键需要给既有 `arch_deployment_unit` 追加唯一键。影响：迁移失败会阻塞发布。缓解：追加迁移、先在本地库验证；该键由主键蕴含，不改变既有语义。
- 风险：菜单/权限种子与存量角色不匹配导致入口不可见。缓解：按 V97 同法同时授予角色 1、111 与既有架构只读角色，并在验收中核对菜单可见性。
- 回退原则：代码回退即可恢复服务；数据侧只做加法，回退不需要改写存量数据；如需彻底下线，先停用菜单权限再删除新表（空表直接删，有数据先导出）。
