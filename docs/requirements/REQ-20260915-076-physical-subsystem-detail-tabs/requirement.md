---
id: REQ-20260915-076
status: ready
owner: rokeyvvz0828
module: business/architecture
---

# 物理子系统详情抽屉四页签改造

## 业务目标

让架构人员在一个详情抽屉内读完物理子系统的全部事实：基础信息、参与人员、该子系统全部部署单元及其环境部署实例、该子系统全部交付单元，避免在列表、参与人员弹层、部署单元页、交付单元页之间反复跳转和重复筛选。

## 范围

### 本次实施

- 物理子系统详情抽屉（`SubsystemDetailDrawer.vue`）顶部新增四个页签，顺序固定为「信息 / 参与人员 / 部署单元 / 交付单元」。
- 「信息」页签完整保留现有内容：基本信息、归属信息、技术信息、描述四栏字段与发布状态标签，字段、分组、空值占位与移动端单列行为不变。
- 「参与人员」页签：
  - 把现有参与人员弹层（`SubsystemParticipants.vue`）的加载、负责人默认参与、候选人搜索、失效成员提示、只读/可编辑判断、保存、版本冲突与未完成责任错误提示逻辑，原样内嵌为页签内容；接口与权限代码不变。
  - 移除物理子系统列表桌面表格与移动卡片中的「参与人员」按钮入口，以及页面上独立的参与人员抽屉实例。
  - 抽屉关闭（遮罩、右上角、Esc）时，若参与人员存在未保存修改，先确认放弃；保存进行中不允许关闭。
- 「部署单元」页签：
  - 展示该物理子系统全部部署单元（编号、名称、类型、发布状态、默认网络分区、关联交付单元数量、更新时间）。
  - 顶部提供 radio group 环境切换：一个「全部」选项加上该项目的全部启用环境；默认「全部」。
  - 在每个部署单元下展示所选环境的「环境部署实例」（机器名、IP、实例编号、采用版本、实例状态、部署平台/网络分区），同时包含在用与已下线实例并带状态标签；该环境下无实例时显示空态。
  - 部署单元可打开既有部署单元详情抽屉（只读，不显示停用/作废/修改等维护动作）。
- 「交付单元」页签：
  - 展示该物理子系统全部交付单元（编号、名称、制品类型、状态、关联部署单元数量、更新时间），只读。
  - 交付单元可打开既有交付单元详情抽屉（只读，不显示维护动作）。
- 覆盖加载、空、失败、无权限状态；桌面与手机视口无页面级横向溢出；明暗主题可读。

### 本次不实施

- 不新增或修改任何后端接口、DTO、权限代码、数据表或 Flyway 迁移；环境、部署单元、交付单元、实例、参与人员所需接口均已存在。
- 不改变物理子系统发布状态机、审批流程、编号规则和项目隔离规则。
- 不在详情抽屉内新增部署单元、交付单元或参与人员的直写入口（参与人员保存沿用既有接口）。
- 不重命名既有路由、组件路径或菜单；不把详情改为独立路由页（保留抽屉内分页签，用户已确认方案 B）。
- 不改写已发布 Flyway 迁移。

## 现状与规则

- 物理子系统详情当前是列表页上的抽屉 `SubsystemDetailDrawer.vue`，只接收 `sections` 做四栏展示，不加载数据。
- 参与人员当前是独立的 `SubsystemParticipants.vue` 抽屉，由列表「参与人员」按钮打开；它负责脏检查、关闭确认与保存，是本次要内嵌并保留语义的来源实现。
- 环境部署实例接口 `GET /api/architecture/instances` 支持 `physicalSubsystemId`、`environmentId`、`deploymentUnitId`、`status` 过滤，`limit` 上限 200。
- 部署单元接口 `GET /api/architecture/deployment-units` 支持 `physicalSubsystemId` 过滤；交付单元接口 `GET /api/architecture/delivery-units` 同样支持。
- 物理子系统列表可查看权限为 `architecture:physical:list | architecture:view | architecture:apply | architecture:manage`；部署单元、交付单元、实例、参与人员的读权限与之一致，但仅有 `architecture:physical:list` 的用户可能对部分子资源返回 403，页签必须给出无权限提示而不是空白。
- 数据范围为“当前项目”，并受参与者数据范围约束（非管理者且非参与成员时实例接口返回空列表）。
- 明暗主题与移动端遵循根目录 `design-h5.md`，复用 `web/src/components/ui` 与 `architecture.css` 既有语义变量和列表/卡片模式。

## 接口与数据

- 本次不新增接口。前端消费：
  - `GET /api/architecture/physical-subsystems/{id}`（信息）
  - `GET/PUT /api/architecture/physical-subsystems/{id}/participants`（参与人员）
  - `GET /api/architecture/deployment-units?physicalSubsystemId=`（部署单元）
  - `GET /api/architecture/delivery-units?physicalSubsystemId=`（交付单元）
  - `GET /api/architecture/environments?status=ACTIVE`（环境切换）
  - `GET /api/architecture/instances?physicalSubsystemId=&environmentId=`（环境部署实例）
- 数据 Owner：`business/architecture`。无数据库变更、无迁移、无脱敏样例变更。
- 前端新增仅限展示组件；不改 `api.ts` 契约与 `types.ts` 既有类型语义（如需新增字段类型只允许追加）。

## 验收标准

1. 打开任一物理子系统详情抽屉，可见「信息 / 参与人员 / 部署单元 / 交付单元」四个页签；「信息」页签字段分组、字段值、状态标签与改造前逐项一致。
2. 物理子系统列表桌面表格与移动卡片中不再出现「参与人员」按钮；页面上不再存在独立参与人员抽屉；参与人员改在详情抽屉「参与人员」页签内查看与保存。
3. 在「参与人员」页签修改名单后：可保存并看到成功反馈；未保存时关闭抽屉会先弹出放弃确认；保存中不能关闭；无编辑权限时为只读展示。
4. 「部署单元」页签列出该物理子系统全部部署单元；radio group 至少包含「全部」和全部启用环境；切换环境只改变各部署单元下展示的实例集合，部署单元列表不重载丢失；实例同时展示在用/已下线状态；某环境无实例时显示空态而不是空白。
5. 「交付单元」页签列出该物理子系统全部交付单元，点击可打开只读交付单元详情；部署单元同样可打开只读详情且不出现维护动作。
6. 四个页签均覆盖加载、空、失败与无权限状态；`npm --prefix web run build` 通过；`git diff --check` 无空白错误；375x812、390x844、430x932 与桌面视口下详情抽屉无页面级横向溢出、无遮挡、明暗主题可读。

## 测试与发布

- 必须执行的测试：`npm --prefix web run build`、`node scripts/check-codex-scope.mjs --scope docs/requirements/REQ-20260915-076-physical-subsystem-detail-tabs/codex-task-scope.yaml --working-tree`、`git diff --check`。
- 浏览器验收：以具备架构查看权限的账号，桌面 1440 与手机 375/390/430 视口，逐页签检查加载/空/失败/无权限、参与人员保存与关闭确认、环境切换与实例分组、只读详情打开。
- 上线验证：登录后进入架构管理 → 物理子系统 → 任一条详情，核对四页签。
- 回退或补偿：回退本需求前端提交即可，无数据库变更、无数据补偿。
- 风险与人工复核人：抽屉内嵌入两张来自既有页面的数据视图，需架构模块 Owner 复核信息层级与权限提示是否符合预期。

## 交付与审批

- 实施分支：`dev-ivanh`（当前工作分支）。
- 用户决策：2026-09-15 明确选择方案 B（保留抽屉并在抽屉内分页签），并对环境切换默认「全部」、实例含在用/已下线、交付单元只读、参与人员逻辑不变等默认无异议。

## 已知问题

- `node scripts/check-development-entry.mjs --require-plugin` 在非 Codex 宿主（本会话）下无法读取 Codex 插件列表而报错；`control-engineering` Skill 已存在于仓库 `.agents/skills/`，按已知环境例外记录。
- 环境部署实例接口单次 `limit` 上限 200，页签通过分页循环拉取；极端超过 2000 条时只展示前 2000 条并提示，属已知展示上限。
