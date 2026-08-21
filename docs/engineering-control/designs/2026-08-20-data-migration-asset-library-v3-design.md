# 数据迁移资产库 V3 工程设计

## 状态

- 设计修订：1
- 状态：已批准
- 来源：`数据迁移资产库功能设计说明书_v3.docx`
- 批准证据：用户确认放弃此前资产设计，以最新 `main` 的全新隔离分支按本设计建立 `REQ-20260820-031` 并开始实现。

### 菜单层级修订

用户于 2026-08-21 确认：新增“数据迁移”一级目录；将“数迁资产看板”“数迁资产内容”“基础资料管理”调整为二级目录，其原有子菜单、权限码和路由保持不变。通过追加 V36 幂等迁移完成，不修改已发布 V35。

## 边界与架构

新增 `business/data-migration` 业务模块和 Maven artifact `ccb-data-migration`。模块拥有项目、组件、资产、结构化数据、专题类型、回收站、看板快照和业务审计；只消费 `shared/common`、`platform/infrastructure` 的 JDBC/MinIO 能力和 `platform/security` 的认证主体。平台模块不反向依赖该业务模块，`boot` 仅负责装配。

前端新增 `web/src/modules/data-migration`，所有请求集中在 `web/src/api/data-migration.ts`。动态菜单指向统一业务路由 `/data-migration/:group/:section`，路由参数只选择已注册页面定义，不能决定服务端权限或 SQL 字段。

## 数据设计

- `dm_project`：项目编号、名称、简介、租户和审计字段。
- `dm_component`：项目、事业群、组件编号/名称、总分核对标记和简介；项目内编号唯一。
- `dm_asset`：11 类内容资产的公共归属、类型、颗粒度、名称、限定搜索字段、类型码、组件关系、业务元数据 JSON、当前文件信息、上传人、逻辑删除和乐观版本。
- `dm_check_rule`、`dm_parameter`、`dm_issue`、`dm_table_field`：高密度结构化数据采用独立表，保留项目、组件、上传人和删除状态。
- `dm_topic_type`：管理员维护的项目级/组件级专题类型。
- `dm_dashboard_snapshot`：按统计日期、项目、组件和指标保存每日快照。
- `dm_operation_log`：模块写操作审计，不直接写平台私有审计表。

所有表包含 `tenant_id`；业务关联同时校验租户。对象键仅保存在服务端，格式为 `data-migration/<tenant>/<project>/<asset-uuid>/<random-name>`，不返回前端。

## 权限与状态

- 查询和下载：`data-migration:access`、`data-migration:write`、`data-migration:manage` 或 `system:admin`。
- 新增和上传：`data-migration:write`、`data-migration:manage` 或 `system:admin`。
- 编辑和逻辑删除：管理员可操作全部；普通人员还需 `created_by == currentUser.id`。
- 基础项目、组件和专题类型维护：仅 `data-migration:manage` 或 `system:admin`。
- 活跃记录 `deleted=0`；逻辑删除后进入回收站；恢复改回活跃；彻底清理删除业务行和当前文件对象，审计保留。
- 重新上传先写新对象，数据库成功切换当前对象后删除旧对象；失败时保留旧记录并清理新对象。

## API 与错误

- `/api/data-migration/options`：项目、组件和码值选项。
- `/api/data-migration/projects`、`/components`：基础资料分页、维护、导入导出。
- `/api/data-migration/assets/{type}`：文件型资产分页、单条/批量上传、更新、下载、删除。
- `/api/data-migration/check-rules`、`/parameters`、`/issues`、`/table-fields/{target|intermediate}`：结构化分页、维护、XLSX 导入导出。
- `/api/data-migration/recycle-bin`：管理员查询、恢复和彻底清理。
- `/api/data-migration/dashboards/overall|components`：快照查询和钻取条件。

校验失败返回 `400`，无认证 `401`，无权限 `403`，实体不存在或不可见 `404`，乐观锁冲突 `409`，文件过大 `413`，Excel 行错误通过成功响应中的逐行结果表达。批量导入限制 5,000 行和 50MB。

## 前端设计

整体看板复用交付示范中心指标网格和工具栏；组件看板、资产列表、基础资料和问题清单复用桌面表格、移动卡片、抽屉表单、批量操作栏和分页模式。页面不使用嵌套卡片，不用装饰渐变或超大标题。

桌面端列表使用稳定列宽和表格内部滚动；移动端隐藏桌面表格并显示身份区、事实区和操作区卡片。新增/编辑抽屉在手机端近全宽且正文局部滚动。危险删除进入“更多”菜单并说明逻辑删除或彻底清理后果。加载、空、失败、无权限、提交中和部分成功均有持续可见状态。

## 性能与运行

- 列表只允许 20/50/100 条，默认 20；索引覆盖租户、删除状态、项目、组件、资产类型、上传人和限定名称字段。
- 每日 02:00 生成快照；开发和测试可调用管理员刷新接口验证，生产入口不显示手工刷新。
- 文件上传和下载不把完整内容载入业务实体；Excel 解析限制大小和行数。

## 非目标和回退

不实现旧设计能力，不启用 Mock 数据，不修改平台文件预览、工作流和系统权限实现。回退先停菜单和调度，再回退应用；新增表和对象保留，禁止生产直接删除。
