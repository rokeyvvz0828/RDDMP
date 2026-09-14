# 数据迁移“投产及演练”优化工程设计

## 文档状态

- 修订：1
- 状态：已确认
- 用户确认依据：用户确认轮次纯手工输入、组件级单选物理子系统、源文件单文件，并确认 `granularity` 指向参数管理、列表展示涉及物理子系统与所属轮次。
- 需求：`REQ-20260820-031`
- 控制前缀：`req-20260820-031-data-migration-asset-library-v3`

## 目标与成功信号

将“数据迁移 / 数迁资产内容 / 投产及演练”从通用文件资产薄页升级为专属域功能，统一管理投产方案、应急方案、投产总结等八类资料，支持颗粒度/资料类型/关键字筛选、分页、单文件上传、查看、编辑、下载、逻辑删除和统一回收站。

成功信号：

1. 列表可按颗粒度、资料类型、资料名称关键字组合筛选并服务端分页，展示资料编号、名称、类型、颗粒度、所属轮次和涉及物理子系统。
2. 颗粒度和资料类型全部来自“系统管理/参数管理”，服务端和前端不硬编码选项。
3. 组件级资料必选一个当前项目启用组件，项目级资料不选系统；资料名称只绑定一个源文件，删除、恢复、彻底清理保持附件/审计一致。
4. 写操作执行认证、RBAC、项目隔离、实体授权、附件校验和操作审计。
5. 桌面端和手机视口无页面级横向滚动，并覆盖加载/空/失败/无权限/提交中状态。

## 使用者与场景

- 数据迁移管理员：维护投产及演练资料、参与统一回收站管理。
- 普通数据迁移人员：查看、下载、上传并维护本人资料。
- 入口：现有“数迁资产内容 › 投产及演练”菜单和全局项目切换器；页面不提供项目选择，项目恒取当前项目上下文。

## 必须需求与验收条件

### R1 元数据与必填规则

`dm_release_drill` 新增 `granularity`、`material_type_code`、`drill_round` 字段；资料名称、颗粒度、资料类型必填，组件级必填一个物理子系统，新增必填一个源文件，所属轮次为可选手工输入文本。

验收：缺少任一必填项返回 400；项目级提交系统返回 400；组件级缺系统返回 400；轮次允许为空。

### R2 参数管理码值

- 颗粒度：`DM_RELEASE_DRILL_GRANULARITY`（项目级 `PROJECT`、组件级 `COMPONENT`）。
- 资料类型：`DM_RELEASE_DRILL_TYPE`（八类初始项）。

参数通过追加迁移 `V184` 幂等初始化；服务端新增/编辑/筛选校验启用项，未知或停用编码拒绝；前端通过 `GET /api/data-migration/options/{category}` 读取，不硬编码。

验收：`V184` 重复执行不覆盖管理员改动；停用编码不能写入；颗粒度/类型切换按参数联动。

### R3 物理子系统与所属轮次展示

列表展示“涉及物理子系统”列（组件级显示系统编号-名称，项目级为空）和“所属轮次”列。

验收：列表接口返回 `system_code/system_name/drill_round`；组件级名称经 `dm_component` + `arch_physical_subsystem` 投影。

### R4 单文件附件与编辑替换

一个资料记录只绑定一个主文件（`dm_content_attachment.business_type='RELEASE_DRILL'`，`sort_order=0`）；新增必传一个文件；编辑可保留原文件或重新上传新文件替换。

验收：新增无文件返回 400；编辑不传新文件时原附件保留；编辑传新文件时旧主文件解绑；文件单个不超过 50MB。

### R5 列表、筛选与前端状态

列表支持资料名称关键字、颗粒度、资料类型组合筛选和 20/50/100 分页；新增/编辑抽屉支持字段联动和上传态；项目切换后重置筛选并重新查询。

验收：筛选回第一页；加载、空、失败、无权限、提交中、重复提交和未保存保护可观察且可恢复。

### R6 权限、项目隔离与审计

服务端强制认证、RBAC、`DataMigrationPermissionService` 项目可达性、实体归属、系统编号和附件状态校验；写操作写入 `dm_operation_log`，操作码为 `RELEASE_DRILL_CREATE/UPDATE/DELETE/RESTORE/PURGE`，`entity_type='RELEASE_DRILL'`。

验收：省略 `projectId` 拒绝；跨项目 ID 拒绝；普通用户越权 403；审计行可追踪。

### R7 统一回收站与链路收敛

`RELEASE_DRILL` 从通用 `ContentFileAssetService.MANAGED_TYPES`、`ContentAssetController` 资源映射和 `ContentAssetRecycleBinSource` 摘除，由新增 `ReleaseDrillRecycleBinSource` 唯一认领。

验收：统一回收站列表/详情/恢复/彻底删除均走专用服务；无重复认领；看板等历史类型计数不受影响。

### R8 范围控制

只修改数据迁移模块、`V184` 追加迁移、数据迁移前端 API 和页面，以及同一前缀下已授权的文档；不读取或改写投产模块表，不修改平台公共构件、全局项目切换器和公共附件/审计表结构。

验收：范围检查无越界文件；`npm --prefix web run build` 与 `mvn -pl :ccb-data-migration -am test` 通过。

## 不变量与约束

- 单租户；每次查询和写入绑定 `tenant_id` 和当前项目。
- Flyway 只追加，不修改已发布脚本；本功能使用 `V184`。
- 不与 `biz_form_*` 或输入项配置交互。
- 业务关联统一使用 `(project_id, system_code)`，不保存 `arch_physical_subsystem.id`。
- 逻辑删除保留删除人/时间；`doc_code` 由服务端生成且不可修改。
- 页面遵循 `design-h5.md`、`UiToolbar`、`UiDataTable`、`UiFormDrawer` 和统一状态组件。

## 非目标

- 不从投产模块读取轮次或用参数管理维护轮次。
- 不做多源文件、批量上传或多系统关联。
- 不改投产模块、不改系统管理参数管理实现、不改全局项目切换器。
- 不新增独立 `dm_release_drill_type` 业务字典表。

## 方案比较与选择

| 方案 | 结论 | 原因 |
|---|---|---|
| 专属 `ReleaseDrillService/Controller/RecycleBinSource` + `V184` + 参数管理 | 选择 | 与迁移方案/专题材料一致，字段、校验、附件和回收站边界清晰 |
| 继续扩展通用 `ContentFileAssetService/AssetListView` | 不选 | 无法表达颗粒度/类型/轮次字段联动，弱化服务端校验和审计 |
| 新建独立 `dm_release_drill_type` 表 | 不选 | 与用户确认的“系统管理/参数管理”归属冲突 |
| 轮次从投产模块跨模块读库/跨模块接口 | 不选 | 用户改为纯手工输入，避免跨业务模块依赖 |

## 架构边界与组件职责

- 边界内：`dm_release_drill`、`dm_content_attachment`、`dm_operation_log`、参数管理码值、专用服务/控制器/回收站来源、数据迁移前端页面和 API。
- 边界外：投产模块、`platform/system` 参数实现、全局项目切换、公共附件表结构、其他数据迁移内容类型。
- `ReleaseDrillService`：CRUD、组合筛选、校验、附件绑定、审计和回收站 SPI。
- `ReleaseDrillController`：`/api/data-migration/release-drills` 路由与权限。
- `ReleaseDrillRecycleBinSource`：统一回收站认领。
- 前端 `ReleaseDrillsPage.vue`：列表、筛选、抽屉表单、上传、下载、删除。

## 接口、数据和状态流

调用已有接口：
- `GET /api/data-migration/options/{category}`：颗粒度、资料类型选项。
- `GET /api/data-migration/recycle-bin`：统一回收站。
- 平台附件上传接口：源文件临时上传。

新建接口：
- `GET /api/data-migration/release-drills`：列表筛选分页。
- `GET /api/data-migration/release-drills/options/systems`：当前项目启用组件选项。
- `GET /api/data-migration/release-drills/{id}`：详情。
- `POST /api/data-migration/release-drills`：新增。
- `PUT /api/data-migration/release-drills/{id}`：编辑（可替换源文件）。
- `DELETE /api/data-migration/release-drills`：批量逻辑删除。
- `GET /api/data-migration/release-drills/{id}/download`：主文件下载路径。

数据流：

1. 前端按当前项目拉取颗粒度/类型/系统选项，并本地随输随筛系统选项。
2. 新增：上传临时附件 → `ReleaseDrillService.create` 生成 `DRILL-*` 编号、写入元数据、绑定主文件、原子写审计。
3. 编辑：读取库中记录归属，更新元数据，可选替换主文件，不改变项目与 `doc_code`。
4. 删除：`deleted=1` + 删除人/时间 + 审计；回收站恢复/彻底清理保持附件与审计一致。

## 错误、降级与恢复

- 必填缺失、类型/颗粒度无效、系统不在项目内、附件无效/超限 → 400，返回可读中文错误。
- 越权或跨项目 → 403/项目守卫拒绝。
- `doc_code` 冲突或回收站恢复冲突 → 409，提示刷新。
- 参数停用后存量记录仍可只读查看，但新增/编辑不能使用该编码。
- 应用回退不执行反向迁移；数据恢复依靠逻辑删除与附件绑定，不物理删除。

## 安全、性能、兼容性与运维

- 安全：服务端 RBAC + 项目守卫 + 实体授权 + 附件归属校验 + 审计。
- 性能：系统选项按项目一次返回（约 500 内），前端本地筛选；列表服务端分页并绑定项目索引。
- 兼容：`ContentAssetTables` 保留 `RELEASE_DRILL` 到表的映射供看板/历史信封识别；通用资产链路和回收站不回退认领。
- 运维：`V184` 追加式且幂等；`INSERT IGNORE` 不覆盖管理员参数。

## 验证策略

- `R1/R4/R6`：`ReleaseDrillServiceTest` 校验必填、组件级、单附件、权限、项目隔离和审计。
- `R2`：码值服务测试 + `V184` MySQL 迁移测试。
- `R3/R5`：前端构建 + 页面契约测试 + 浏览器验收（桌面和移动视口）。
- `R7`：`ReleaseDrillRecycleBinSourceTest` + 注册测试确认无重复认领。
- `R8`：`mvn -pl :ccb-data-migration -am test`、`node scripts/check-all-governance.mjs`、任务范围检查和 `npm --prefix web run build`。

## 假设、未知项与决策记录

假设：
- `dm_release_drill` 当前无业务存量行；`V184` 仍按默认值回填以兼容低基线。
- “组件级”等价于项目内一个 `dm_component` 启用记录，沿用 `system_code`。
- 单文件编辑时若不传新文件则保留原文件。

决策：
- D1：所属轮次纯手工输入，不跨模块读投产轮次。
- D2：组件级单选物理子系统，项目级不选。
- D3：源文件单文件。
- D4：颗粒度和资料类型统一由“系统管理/参数管理”维护。

## 风险与回退原则

- 风险：摘除通用 `RELEASE_DRILL` 链路导致看板/注册测试回归；回收站出现无来源或双来源。
- 缓解：同批新增 `ReleaseDrillRecycleBinSource` 和移除通用来源；运行注册与回收站回归测试。
- 回退：不执行 `V184` 且回退应用代码；不删除已发布历史迁移；模块未投产，无生产数据转换。
