# 投产管理 工程设计

## 文档状态

- 需求编号：`REQ-20260901-057`
- 修订：1
- 状态：已批准
- 用户确认依据：用户确认投产演练、时序、回退时序、问题跟踪和投产组织按当前项目独立维护，不绑定投产窗口或版本申请。

## 1. 目标和边界

在既有 `ccb-release` 模块中增加投产管理业务入口。顶部项目切换栏的 `project-context` 是唯一当前项目来源；前端请求带 `projectId`，服务端从租户和项目关系校验数据范围。投产管理与现有配置管理并列，不改变既有投产流程。

页面入口：

- `/release-operations/drill-plans`
- `/release-operations/timelines`
- `/release-operations/rollback-timelines`
- `/release-operations/issues`
- `/release-operations/organization`

## 2. 信息架构和交互

统一使用现有 release 页面导航、`UiToolbar`/Element Plus 表格、抽屉或对话框、主题变量和错误处理模式。桌面端使用内容区双栏或表格布局；手机端表格转为纵向卡片，时序图在自身容器内横向滚动，不引入页面级横向滚动。危险删除必须二次确认，保存期间禁用提交，项目切换时关闭编辑弹层并清空旧项目状态。

投产演练计划页面：顶部为方案和环境搭建说明表单，下方为轮次表格；轮次字段为轮次名称、计划时间、状态、结果说明。项目只有一个演练计划聚合根，轮次为多条子记录。

时序页面：上方为标题和说明，中央按 `seq_no` 展示节点、计划开始/结束、责任人和状态的横向节点图，下方维护明细表。普通时序和回退时序共用组件与接口，通过 `timelineType=NORMAL|ROLLBACK` 隔离数据。

问题页面：筛选栏支持关键词、优先级和状态；表格展示编号、标题、优先级、状态、责任人和更新时间；编辑表单包含发现时间、问题描述、原因分析、处理措施、跟踪记录和关闭时间。

组织页面：左侧无层级投产组列表，右侧显示所选组成员；新增/编辑组只维护名称和说明，成员对话框只展示当前项目有效成员，支持添加和移除。

全页面状态：项目未初始化显示空状态；接口加载显示 loading；接口失败显示可重试错误；无权限由路由和接口共同阻止；新增/编辑显示保存中；重复点击被阻止；无数据显示空提示；跨项目切换时旧数据和未保存编辑不保留。

## 3. 数据模型

追加迁移 `V123__release_operations_management.sql`：

| 表 | 责任 |
|---|---|
| `rel_release_drill_plan` | 每个项目一套方案和环境搭建说明、版本号 |
| `rel_release_drill_round` | 演练轮次、计划时间、状态和结果 |
| `rel_release_timeline` | 项目下 NORMAL/ROLLBACK 时序标题和说明 |
| `rel_release_timeline_item` | 时序节点、序号、时间、责任人、状态和描述 |
| `rel_release_issue` | 投产问题及分析跟踪字段、状态和版本号 |
| `rel_release_group` | 无层级投产组 |
| `rel_release_group_member` | 投产组与当前项目成员的关联 |

所有表包含 `tenant_id`、项目标识、创建/更新审计字段和软删除字段；写操作携带 `rowVersion`。项目引用使用 `BIGINT project_id`，与项目服务的实体主键一致。组成员同时保存 `member_id` 和用户快照，服务端校验 member 属于当前项目且有效，不接受任意用户 ID。

## 4. API 契约

统一前缀 `/api/release/operations`，响应使用 `ApiResponse`，写操作受权限保护：

- `GET|PUT /drill-plan`；`POST|PUT|DELETE /drill-plan/rounds/{id}`。
- `GET|PUT /timelines/{timelineType}`；`POST|PUT|DELETE /timelines/{timelineType}/items/{id}`。
- `GET|POST|PUT|DELETE /issues`，列表支持 `projectId`、`keyword`、`priority`、`status`、分页。
- `GET|POST|PUT|DELETE /groups`；`GET /groups/{id}/members`；`POST|DELETE /groups/{id}/members/{memberId}`。
- `GET /members` 返回当前项目有效项目成员选项，使用现有项目成员公开接口/安全引用，不读取 `sys_*` 私有表。

新增权限：`release-operations:access`，以及各页面 `view/manage` 权限。Controller 检查 RBAC，Service 检查租户、项目成员范围、实体状态和版本号，写操作记录现有业务审计能力可接受的操作事件。

## 5. 依赖和风险

- 复用 `useProjectContextStore`、现有 release API 文件、页面导航样式、Element Plus 组件和 `apiErrorMessage`。
- 投产组织人员选项通过 `platform/system` 的只读项目成员引用契约取得；后端不直接访问其内部表，也不降级为自由输入。
- 新增 `ProjectMemberReferenceQuery` 公共能力，只返回指定租户/项目下有效成员的最小引用，不改变既有项目成员 HTTP 管理接口。
- V123 只追加，回退保留数据并关闭菜单入口。

## 6. 验收传感器

后端服务测试覆盖项目隔离、组成员校验、普通/回退时序隔离、删除和 rowVersion 冲突；前端构建覆盖 DTO 和路由；Flyway/治理/差异检查覆盖仓库规则；浏览器使用已登录测试账号验证五个路由、项目切换、增改删、空/失败状态和 `390x844`、`1280x800` 视口。
