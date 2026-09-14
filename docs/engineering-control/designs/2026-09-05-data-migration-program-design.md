# 数据迁移“迁移程序”优化工程设计

## 文档状态

- 修订：1
- 状态：已确认
- 用户确认依据：用户确认一个程序包可绑定多个 ≤50MB 源文件（方案 B）；确认当前数据库无历史数据；确认本设计无歧义后可落盘。
- 需求：`REQ-20260820-031` 数迁资产内容增量
- 控制前缀：`req-20260820-031-data-migration-asset-library-v3`

## 目标与成功信号

将“数据迁移 / 数迁资产内容 / 迁移程序”从通用文件资产列表升级为独立菜单能力，统一管理迁出/迁入程序包，支持程序类型、系统编号、程序包名称、程序包说明、多文件上传、下载、查看、编辑、删除和统一回收站。

成功信号：

1. 一个迁移程序记录可绑定多个源文件，每个文件不超过 50MB；新增至少绑定一个源文件。
2. 程序类型来自“系统管理/参数管理”，前端和服务端不硬编码；迁出程序、迁入程序两项预置可维护。
3. 列表支持程序类型、系统编号、程序包名称模糊搜索组合筛选，服务端分页并展示程序类型、系统编号、程序包名称、说明及操作。
4. 下载支持单个文件下载与全部文件打包下载。
5. 删除、恢复、彻底清理走统一回收站，附件与审计保持一致。
6. 写操作执行认证、RBAC、项目隔离、实体授权、附件校验和审计。
7. 桌面端和手机视口无页面级横向滚动，覆盖加载/空/失败/无权限/提交中状态。

## 使用者与场景

- 数据迁移管理员：维护迁出、迁入程序包，管理回收站。
- 普通数据迁移人员：查看、下载、上传并维护本人程序包。
- 入口：现有“数迁资产内容 › 迁移程序”菜单与全局项目切换器；页面不提供项目选择，项目恒取当前项目上下文。

## 必须需求与验收条件

### R1 元数据与必填规则

程序包记录包含：`program_type`（程序类型）、`system_code`（系统编号）、`doc_name`（程序包名称）、`program_description`（程序包说明，选填）。程序类型、系统编号、程序包名称必填；新增必填至少一个源文件。

验收：缺少任一必填项返回 400；文件超过 50MB 返回 400；新增无文件返回 400；说明允许为空。

### R2 参数管理码值

程序类型使用新增码值类别 `DM_PROGRAM_TYPE`，预置 `MIGRATE_OUT`=迁出程序、`MIGRATE_IN`=迁入程序。参数通过追加迁移幂等（INSERT IGNORE）初始化，不覆盖管理员后续修改。

验收：`GET /api/data-migration/options/DM_PROGRAM_TYPE` 返回两项；停用或未知编码不能写入；前端不在代码中硬编码类型列表。

### R3 系统编号来源与必填

系统编号单选，候选数据源为本项目 `dm_component` 中 `enabled=1` 的组件；一次返回全量，前端浏览器内按编号/名称不区分大小写随输随筛。

验收：未选系统返回 400；提交系统不在当前项目启用组件中返回 400；历史只读记录仍可展示名称。

### R4 列表、筛选与分页

列表展示程序包编号、程序类型、系统编号、程序包名称、程序包说明、操作（下载、编辑、删除）。筛选支持程序类型、系统编号、程序包名称关键字，组合生效并按 20/50/100 分页。

验收：筛选变化回第一页；加载、空、失败、无权限、提交中、重复提交状态可观察且可恢复。

### R5 多文件附件

一个程序包记录可绑定多个源文件（`dm_content_attachment.business_type='SCRIPT'`，`sort_order` 递增）。新增至少一个；编辑可保留、增删、替换文件；每个文件大小不超过 50MB。

验收：新增可绑定多个文件；编辑不改变文件时保留；编辑删除单文件后附件列表同步；下载单文件返回指定附件；下载全部返回 ZIP。

### R6 统一回收站链路

`SCRIPT` 从通用 `ContentFileAssetService.MANAGED_TYPES`、`ContentAssetController` 资源映射和 `ContentAssetRecycleBinSource` 摘除，由新增 `ProgramService`、`ProgramRecycleBinSource` 唯一认领。

验收：统一回收站列表/详情/恢复/彻底删除均走专用服务；删除/恢复保持多附件与审计；彻底删除清理全部附件；看板历史计数不回归。

### R7 权限、项目隔离与审计

服务端强制认证、RBAC、`DataMigrationPermissionService` 项目可达性、实体归属、系统编号和附件状态校验；写操作写入 `dm_operation_log`，操作码为 `PROGRAM_CREATE/UPDATE/DELETE/RESTORE/PURGE`，`entity_type='PROGRAM'`。

验收：省略 `projectId` 拒绝；跨项目 ID 拒绝；普通用户越权 403；审计行可追踪。

### R8 前端交互与移动端

新增/编辑统一使用抽屉表单；列表表格与移动端重组遵循 `design-h5.md`；上传区支持多文件、删除待上传项、展示已绑定文件与 50MB 大小限制。

验收：375/390/430/桌面视口无页面级横向滚动；上传中/上传失败/重复提交有反馈；长文本不撑破容器。

### R9 数据库与模块边界

只在 `dm_script` 追加字段、新增追加式 Flyway 迁移；不修改已发布迁移；不改参数管理、公共附件表结构、全局项目切换器、投产等其他模块。

验收：`dm_script` 增加 `program_type`、`program_description`；范围检查无越界文件；Maven 与前端构建通过。

### R10 非历史兼容

当前数据库无历史数据，新字段落库后直接按新必填规则运行，不需迁移旧记录。

验收：迁移从空表创建后可直接创建符合新字段的记录。

## 不变量与约束

- 单租户；每次查询和写入绑定 `tenant_id` 和当前项目。
- Flyway 只追加，不修改已发布脚本。
- 业务关联统一使用 `(project_id, system_code)`，系统编号须为当前项目启用组件。
- 逻辑删除保留删除人/时间；`doc_code` 由服务端生成且不可修改。
- 页面遵循 `design-h5.md`、`UiToolbar`、`UiDataTable`、`UiFormDrawer` 和统一状态组件。
- 不与 `biz_form_*` 或输入项配置交互。

## 非目标

- 不做程序包内容的版本历史或增量比对。
- 不改参数管理实现、公共附件存储或全局项目切换器。
- 不新增独立 `dm_program_type` 业务字典表。
- 不迁移旧业务数据（当前无历史数据）。
- 不将系统编号改为多选。

## 方案比较与选择

| 方案 | 结论 | 原因 |
|---|---|---|
| 新增专用 `ProgramService/ProgramController/ProgramRecycleBinSource` + `dm_script` 加列 + 参数管理 | 选择 | 字段、多附件、校验、回收站边界清晰，与专题材料/投产演练一致 |
| 继续扩展通用 `ContentFileAssetService/AssetListView` | 不选 | 无法表达程序类型、说明、多文件编辑和审计，通用组件会过度膨胀 |
| 新建独立 `dm_program` 表 | 不选 | `dm_script` 已是程序内容表并接入现有附件/回收站，应保留映射与历史类型语义 |
| 程序类型维护在业务表 | 不选 | 与“系统管理/参数管理”维护码值的项目规则冲突 |

## 架构边界与组件职责

- 边界内：`dm_script`、`dm_content_attachment`、`dm_operation_log`、参数管理码值 `DM_PROGRAM_TYPE`、专用服务/控制器/回收站来源、数据迁移前端页面和 API。
- 边界外：投产模块、系统管理参数实现、全局项目切换、公共附件表结构、其他数据迁移内容类型。
- `ProgramService`：CRUD、组合筛选、码值与系统校验、多附件绑定、下载打包、审计和回收站 SPI。
- `ProgramController`：`/api/data-migration/programs` 路由与权限。
- `ProgramRecycleBinSource`：统一回收站认领 `SCRIPT`。
- 前端 `ProgramsPage.vue`：列表、筛选、抽屉表单、多文件上传、下载、编辑、删除。

## 接口、数据和状态流

调用已有接口：

- `GET /api/data-migration/options/{category}`：程序类型选项（`DM_PROGRAM_TYPE`）。
- `GET /api/data-migration/components/options/systems`：当前项目启用组件（如需保持页面风格也可由程序控制器委派）。
- 平台附件上传接口：源文件临时上传。
- `GET /api/data-migration/recycle-bin`：统一回收站。

新建接口：

- `GET /api/data-migration/programs`：列表筛选分页。
- `GET /api/data-migration/programs/options/types`：程序类型启用选项。
- `GET /api/data-migration/programs/options/systems`：当前项目启用组件选项。
- `GET /api/data-migration/programs/{id}`：详情。
- `POST /api/data-migration/programs`：新增（元数据 + 多个源文件）。
- `PUT /api/data-migration/programs/{id}`：编辑（元数据 + 附件增删替换）。
- `DELETE /api/data-migration/programs`：批量逻辑删除。
- `GET /api/data-migration/programs/{id}/attachments`：附件列表。
- `GET /api/data-migration/programs/{id}/download`：指定附件下载。
- `GET /api/data-migration/programs/{id}/download-all`：全部附件打包下载。

数据流：

1. 前端按当前项目拉取程序类型与启用组件选项，系统选项本地随输随筛。
2. 新增：上传临时附件 → `ProgramService.create` 生成 `SCR-*` 编号、写入元数据、绑定多个附件、原子写审计。
3. 编辑：校验记录归属，更新元数据，按提交的附件更新列表，不改变项目与 `doc_code`。
4. 删除：`deleted=1` + 删除人/时间 + 审计；回收站恢复/彻底清理保持附件与审计一致。
5. 下载全部：服务端按当前记录附件生成 ZIP 流，前端保存文件。

## 错误、降级与恢复

- 必填缺失、程序类型/系统编号无效、系统不在项目内、附件无效/超限 → 400，返回可读中文错误。
- 越权或跨项目 → 403/项目守卫拒绝。
- `doc_code` 冲突或回收站恢复冲突 → 409，提示刷新。
- 打包下载含不可读附件时返回明确错误，不产生空 ZIP 或部分文件误导。
- 参数停用后存量记录仍可只读查看，但新增/编辑不能使用该编码。
- 应用回退不执行反向迁移；数据恢复依靠逻辑删除与附件绑定。

## 安全、性能、兼容性与运维

- 安全：服务端 RBAC + 项目守卫 + 实体授权 + 附件归属校验 + 审计。
- 性能：系统选项按项目一次返回，前端本地筛选；列表服务端分页并复用项目索引。
- 兼容：`ContentAssetTables` 保留 `SCRIPT` 到 `dm_script` 的映射供看板/历史信封识别；旧通用资产链路不再认领程序。
- 运维：新 Flyway 迁移追加式且幂等；`INSERT IGNORE` 不覆盖管理员参数。

## 验证策略

- `R1/R3/R6/R7`：`ProgramServiceTest`、`RecycleBinSourceTest` 和权限测试。
- `R2`：码值服务测试 + 新增 Flyway MySQL 测试。
- `R4/R8`：前端构建 + 页面契约测试 + 浏览器验收（桌面和移动视口）。
- `R9`：`mvn -pl :ccb-data-migration -am test`、范围检查和 `npm --prefix web run build`。

## 假设、未知项与决策记录

- 假设：当前开发/测试库无历史 `dm_script` 数据；若有环境存在旧数据，后续按存量只读/补录口径处理。
- 决策：一个程序包绑定多个文件；程序类型、系统编号、程序包名称必填；程序包说明选填；多文件下载提供单文件与打包两种方式。

## 风险与回退原则

- 风险：多附件与统一回收站耦合改动较多，可能影响其他 `SCRIPT` 场景。缓解：先摘除通用认领再做专用实现，并跑回收站/看板回归。
- 回退：应用回退保留表和附件；新列与参数可保留；若需彻底回退前先备份（当前开发库无生产数据）。
