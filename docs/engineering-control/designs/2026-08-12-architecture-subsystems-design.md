# 物理子系统与逻辑子系统工程设计

## 文档状态

- 需求编号：REQ-20260812-021
- 主题：architecture-subsystems
- 设计修订：4
- 状态：已批准
- 批准依据：用户于 2026-08-14 批准修订 3、实施计划与最小 `SystemOperationAudit` 平台契约；在 T0 暴露 CLI 和迁移占位阻塞后，明确要求修复两个问题，批准修订 4 的版本重分配与开发入口兼容动作。

## 1. 目标与成功定义

在独立 `business/architecture` 模块内提供逻辑子系统和物理子系统的租户隔离、权限受控、关联完整且可审计的基础信息管理能力。

成功信号：

1. 两类资源具备真实数据库支持的分页、筛选、详情、新增、编辑和软删除闭环。
2. 物理记录始终关联一个当前租户有效逻辑记录；删除保护和并发父锁不产生悬挂活动引用。
3. 物理记录使用可空事业群文本和必选平台组织负责团队；组织失效后历史仍可读且下一次编辑要求重选。
4. 用户电话和参数选项通过最小平台公开契约读取，不复制平台数据、不扩大系统管理权限。
5. 创建、编辑、删除及已认证失败尝试可在现有操作日志按 trace ID 复核。
6. 页面使用固定强类型表单，桌面和移动端均可完整操作，且不依赖已下线的动态表单元数据。

## 2. 已确认决策

| 主题 | 决策 | 理由 |
| --- | --- | --- |
| 领域归属 | 独立 `business/architecture` | 子系统主数据不是系统管理平台配置 |
| V1 范围 | 两个独立 CRUD 页面 | 先交付最小主数据闭环 |
| 启停用 | 不实施 | 用户明确缩小范围 |
| 编号 | 手工录入，服务端大写，永久唯一 | 兼容旧编号并避免未定义生成规则 |
| 父子关系 | 逻辑一对多物理，物理必选逻辑 | 保持明确所有关系 |
| 逻辑事业群 | 平台组织 ID，必填 | 保留修订 2 已确认设计 |
| 物理事业群 | `businessGroupName` 可空文本 | 用户要求先简单化，不建立新主数据模块 |
| 物理负责团队 | 平台组织 ID 必填并保存名称快照 | 当前值受控，历史值可读 |
| 人员 | 逻辑联系人必填；物理负责人和联系人均可空 | 保持逻辑设计，按用户确认放宽物理字段 |
| 电话 | 从平台用户实时读取，不存业务表 | 避免号码漂移和重复存储 |
| 分类 | 六类现有参数设计不变 | 用户明确“不改动这块的设计” |
| 表单 | 固定强类型表单 | 用户选择方案 1，且最新 main 已下线元数据能力 |
| 权限 | 架构控制器直接使用 Spring Security | 现有认证已把权限注入 Authorities，无需平台守卫 |
| 审计 | 最小 `SystemOperationAudit` 写现有日志 | 用户批准方案 1，保持统一审计体系 |

## 3. 范围与不变量

### 3.1 范围内

- `ccb-architecture` Maven 模块和 `web/src/modules/architecture` 前端模块。
- 两类资源、两页面、八项权限、菜单、参数目录、Mock 和 Flyway。
- 用户/参数只读查询及操作审计的通用平台公开契约。
- 根/Boot POM、模块治理、路由和 CODEOWNERS 装配。
- 桌面、移动端、明暗主题和真实 API UAT。

### 3.2 范围外

- 系统启用、停用、状态变更和状态历史。
- 动态表单元数据、schema API、发布快照和扩展字段。
- 自动编号、智能补全、审批、导入导出和外部同步。
- 新事业群主数据模块、事业群/团队级行权限、跨租户共享和级联删除。
- 旧系统截图中缺乏可靠主数据来源的扩展关系。

### 3.3 不变量

- 运行期 `tenant_id` 只来自 `AuthUser`，请求和页面不可维护，新表无租户默认值。
- 编号保存前转大写；编号和名称唯一键不包含删除标记。
- 物理活动记录只关联同租户未删除逻辑记录。
- 被引用逻辑记录不可删除；父子写入使用一致锁顺序。
- 负责团队快照和用户电话均由服务端产生，客户端不可提交。
- 固定表单前端校验不替代服务端校验。
- 成功写入和成功审计同事务；失败审计独立尽力执行且不覆盖原错误。
- 不修改 V1—V34 或其他已发布 Flyway 脚本。

## 4. 数据设计

### 4.1 `arch_logical_subsystem`

| 列 | 类型 | 约束 |
| --- | --- | --- |
| `id` | BIGINT | 主键 |
| `tenant_id` | BIGINT | 非空、无默认值；所有访问条件的一部分 |
| `code` | VARCHAR(32) | 非空，大写；`tenant_id + code` 永久唯一 |
| `short_name` | VARCHAR(100) | 非空 |
| `name` | VARCHAR(200) | 非空；`tenant_id + name` 永久唯一 |
| `business_org_id` | BIGINT | 非空，当前租户有效组织 |
| `deployment_platform_code` | VARCHAR(64) | 可空，白名单参数代码 |
| `system_type_code` | VARCHAR(64) | 可空，白名单参数代码 |
| `system_ownership_code` | VARCHAR(64) | 可空，白名单参数代码 |
| `contact_user_id` | BIGINT | 非空，当前租户有效用户 |
| `description` | VARCHAR(2000) | 可空 |
| `remark` | VARCHAR(1000) | 可空 |
| `deleted` | TINYINT | 非空，默认 0 |
| `created_by` / `updated_by` | BIGINT | 认证用户 |
| `created_at` / `updated_at` | TIMESTAMP | 数据库时间 |

逻辑表建立 `UNIQUE (tenant_id,id)`，供物理表建立租户安全组合外键。

### 4.2 `arch_physical_subsystem`

共享逻辑表的标识、名称、描述、软删除和审计列，并增加：

| 列 | 类型 | 约束 |
| --- | --- | --- |
| `logical_subsystem_id` | BIGINT | 非空，与 `tenant_id` 组成租户安全父关联 |
| `business_group_name` | VARCHAR(100) | 可空；trim 后空串保存为 null |
| `responsible_team_org_id` | BIGINT | 非空；保存时必须为当前租户有效组织 |
| `responsible_team_name_snapshot` | VARCHAR(200) | 非空；服务端从组织当前名称生成 |
| `runtime_code` | VARCHAR(64) | 可空，白名单参数代码 |
| `system_level_code` | VARCHAR(64) | 可空，白名单参数代码 |
| `development_framework_code` | VARCHAR(64) | 可空，白名单参数代码 |
| `owner_user_id` | BIGINT | 可空；非空时必须为当前租户有效用户 |
| `contact_user_id` | BIGINT | 可空；非空时必须为当前租户有效用户 |

`FOREIGN KEY (tenant_id,logical_subsystem_id)` 引用逻辑表 `(tenant_id,id)`，采用 RESTRICT。组织、用户和参数不新增跨平台外键，由平台查询契约和服务端校验保证当前状态。

### 4.3 负责团队历史语义

- 创建或编辑时，服务端按认证租户读取有效组织；请求中的团队名称字段被忽略或因 DTO 不存在而不参与绑定。
- 保存同一事务内写入组织 ID 和当时名称快照。
- 查询时若组织仍有效，返回当前名称并令 `responsibleTeamValid=true`；组织已停用、删除或不存在时返回快照并令其为 false。
- 失效不阻止详情和列表读取，也不自动改写历史快照。
- 编辑时必须重新通过有效组织校验；原失效 ID 不能原样保存。删除不要求先恢复组织引用。

### 4.4 唯一性与软删除

两表均建立 `tenant_id + code`、`tenant_id + name` 唯一键，不包含 `deleted`。列表和详情默认读取 `deleted=0`；更新和删除使用 `tenant_id + id + deleted=0` 并检查影响行数。

## 5. 模块与平台边界

### 5.1 业务模块

新建 `server/src/modules/architecture`，artifact `ccb-architecture`，根包 `com.ccb.architecture`：

- `web`：显式控制器、DTO、Bean Validation 和模块局部 404 Advice。
- `service`：逻辑/物理用例、事务、引用和审计编排。
- `repository`：只访问两个 `arch_` 业务表。
- `options`：将平台组织、用户、参数投影为架构页面安全选项。
- `model`：强类型命令、记录、详情和选项 DTO。

前端 API、类型、页面、抽屉和样式全部位于 `web/src/modules/architecture/**`。

### 5.2 平台公开契约

在 `com.ccb.system.capability` 增加无架构语义的公开类型，实现位于非公开 `com.ccb.system.internal.capability`：

```text
SystemReferenceQuery
  PageResult<SystemUserReference> searchActiveUsers(AuthUser user, PageQuery page, String keyword)
  Optional<SystemUserReference> findUser(AuthUser user, long userId, boolean activeOnly)
  List<SystemParameterReference> activeParameters(AuthUser user, String categoryCode)

SystemOperationAudit
  void recordSuccess(SystemOperationAuditCommand command)
  void recordFailure(SystemOperationAuditCommand command)
```

`SystemUserReference` 固定包含 `id/displayName/username/phone` 和内部有效性判断所需的最小状态；HTTP 选项只映射前四项。`SystemParameterReference` 固定包含 `code/label`。审计命令包含租户、操作者、操作码、对象路径、结果、受限错误摘要和 trace ID，不含业务正文。

组织继续使用已经公开的 `com.ccb.system.org.OrganizationService.tree(AuthUser)`。架构模块将树压平并过滤 `status=1`，形成 `OrganizationOption{id,name,parentId,pathLabel}`；不把树中的用户或头像 URL 暴露到架构 HTTP。

不新增 `SystemPermissionGuard`：现有 JWT 过滤器已将权限码转换为 `GrantedAuthority`，控制器使用固定 `@PreAuthorize`。不新增 `PublishedFormSchemaQuery`，不访问 `biz_form_*`。

### 5.3 装配与治理

- 根 POM 管理并聚合 `ccb-architecture`。
- Boot POM 依赖 `ccb-architecture`。
- `governance/modules.yaml` 登记 `business/architecture`，并为 platform/system 公开 `com.ccb.system.capability`。
- Boot 和前端应用允许依赖新业务模块；platform/shared 不反向依赖业务模块。
- `MODULES.md` 和 CODEOWNERS 同步模块边界。

## 6. HTTP 契约

### 6.1 资源 API

```text
GET    /api/architecture/logical-subsystems
GET    /api/architecture/logical-subsystems/{id}
POST   /api/architecture/logical-subsystems
PUT    /api/architecture/logical-subsystems/{id}
DELETE /api/architecture/logical-subsystems/{id}

GET    /api/architecture/physical-subsystems
GET    /api/architecture/physical-subsystems/{id}
POST   /api/architecture/physical-subsystems
PUT    /api/architecture/physical-subsystems/{id}
DELETE /api/architecture/physical-subsystems/{id}
```

分页统一使用 `page`、`size` 和 `PageResult{records,total,page,size}`。逻辑筛选固定为 `code/shortName/name/businessOrgId`；物理筛选固定为 `code/shortName/name/businessGroupName/responsibleTeamOrgId/logicalSubsystemId`。不接受任意列名、排序表达式、tenant 或 status。

新增/编辑 DTO 只包含第 4 节用户可编辑字段。响应可增加显示标签、联系人实时电话、团队当前/快照展示名、`responsibleTeamValid` 和审计时间；不得返回 tenant、删除标志或内部平台行。

### 6.2 选项 API

```text
GET /api/architecture/options/{resource}/organizations
GET /api/architecture/options/{resource}/users
GET /api/architecture/options/{resource}/parameters/{categoryCode}
GET /api/architecture/options/physical-subsystem/logical-subsystems
```

`resource` 只接受 `logical-subsystem` 与 `physical-subsystem`，分别检查对应 list 权限，不接收客户端权限码，也不以两项权限 OR 放行。

| DTO | 固定 HTTP 字段 |
| --- | --- |
| `OrganizationOption` | `id:number,name:string,parentId:number|null,pathLabel:string` |
| `UserOption` | `id:number,displayName:string,username:string,phone:string|null` |
| `ParameterOption` | `code:string,label:string` |
| `LogicalSubsystemOption` | `id:number,code:string,name:string` |

组织和用户服务端分页；用户搜索防止管理字段泄漏。逻辑上下文参数只允许 `ARCH_DEPLOYMENT_PLATFORM`、`ARCH_SYSTEM_TYPE`、`ARCH_SYSTEM_OWNERSHIP`；物理上下文只允许 `ARCH_RUNTIME`、`ARCH_SYSTEM_LEVEL`、`ARCH_DEVELOPMENT_FRAMEWORK`。

### 6.3 错误语义

| 条件 | HTTP |
| --- | --- |
| 格式、引用、负责团队或参数无效 | 400 |
| 未认证 | 401 |
| 缺少动作权限 | 403 |
| 当前租户资源不存在/已删除、选项资源上下文未知 | 404 / code 40400 |
| 编号或名称永久唯一冲突 | 409 |
| 逻辑被物理引用 | 409 |
| 物理初检通过后父记录被并发删除 | 409 |

模块局部 `ArchitectureExceptionAdvice` 只映射 `ArchitectureNotFoundException`，不修改全局 400/401/403/409 行为。

## 7. 校验、并发与事务

### 7.1 通用写入顺序

1. Spring Security 校验固定动作权限。
2. 从 `AuthUser` 取得租户和操作者，拒绝无有效认证主体。
3. trim、空值归一化、编号转大写并执行长度/格式校验。
4. 通过平台契约校验组织、用户和参数；负责团队同时取得服务端名称快照。
5. 校验编号和名称永久唯一。
6. 事务内写业务记录并调用 `SystemOperationAudit.recordSuccess`。
7. 数据库唯一冲突映射为 409，响应返回服务端规范化结果。

失败时主事务先回滚，再调用 `recordFailure` 的独立事务。失败审计不保存提交内容；审计异常只记录应用错误，原业务响应不变。

### 7.2 父子并发

- 物理新增或更改父记录前，先按 `tenant_id + id + deleted=0` 非锁定初检；普通无效引用返回 400。
- 进入事务后，物理写入和逻辑删除都先锁定同一逻辑父记录，再处理物理行。
- 物理先获得锁并提交时，删除看到活动引用后返回 409。
- 删除先获得锁并软删除，且物理请求此前已通过活动初检时，物理获得锁后返回 409。
- 最终状态不得出现活动物理记录指向已删除逻辑记录。

## 8. 权限与审计

| 资源 | 查看 | 新增 | 编辑 | 删除 |
| --- | --- | --- | --- | --- |
| 逻辑 | `architecture:logical:list` | `architecture:logical:list:create` | `architecture:logical:list:update` | `architecture:logical:list:delete` |
| 物理 | `architecture:physical:list` | `architecture:physical:list:create` | `architecture:physical:list:update` | `architecture:physical:list:delete` |

V37 只向初始化超级管理员授权，不扩大普通角色权限。所有仓储 SQL 显式包含 tenant 和软删除条件。

V35 为 `sys_operation_log` 增加可空 `trace_id VARCHAR(64)` 和 `(tenant_id,trace_id)` 索引。架构操作码固定区分 logical/physical 与 create/update/delete；`request_path` 包含对象 ID，错误摘要限长，不记录请求体。

## 9. 前端设计

### 9.1 路由与页面

- `/architecture/logical-subsystems`
- `/architecture/physical-subsystems`

路由在 `AppLayout` children 中静态注册，服务端菜单控制可见性。页面、API、类型、组件和 CSS 归 `web/src/modules/architecture`。

### 9.2 复用与固定表单

复用交付示范中心的桌面表格/移动卡片、表单分区和抽屉模式，以及 `UiPageHeader`、`UiToolbar`、`UiDataTable`、`UiFormDrawer`、`UiEmptyState` 和语义主题变量。

固定表单由 TypeScript 类型、明确字段数组和 Element Plus `FormRules` 组成；没有运行时 schema 请求、元数据缓存、fallback 或任意字段渲染。逻辑表单分为“基础标识、归属与分类、说明”，物理表单分为“基础标识、关联与运行、说明”。

### 9.3 交互

- 逻辑页面按编号/简称/名称/事业群筛选；物理页面按编号/简称/名称/事业群文本/负责团队/所属逻辑筛选。
- 用户选择器 300ms 防抖、服务端分页，展示账号和电话；组织选择器显示层级路径；参数按类别加载。
- 负责团队失效时，详情和列表显示快照与失效标记；编辑表单显示字段错误并要求重选。
- 编号提交前前端 trim/uppercase，最终以后端响应为准。
- 提交期间禁用重复操作；关闭脏抽屉先确认；409 保留输入；保存后保留筛选与页码。
- 新增/编辑请求不含 tenant、status、phone 或 snapshot。

### 9.4 响应式与状态

- 桌面端使用服务端分页表格和右侧抽屉。
- 小于 760px 隐藏桌面表格，使用业务卡片；身份区展示编号和名称，事实区展示事业群/团队/逻辑关联，查看为主操作，编辑与删除进入更多菜单。
- 页面根 `min-width:0`，筛选和抽屉在 375px 下单列；页面本身无横向滚动。
- 覆盖 1280×800、375×812、390×844、430×932 及明暗主题。
- 覆盖初始加载、局部加载、初始空、筛选空、失败重试、无权限、只读、详情加载、提交中、删除中和冲突状态。

## 10. 初始化与迁移

### 10.1 版本分配

2026-08-14 再次刷新 `origin/main` 后最高迁移为 V34，所有远端分支均没有 V35—V40。迁移版本按实际准备合入的先后顺序分配，不让尚未实施的需求文档阻塞当前已批准实现：

- 本需求：V35、V36、V37。
- REQ-20260814-022：顺延为 V38、V39。
- REQ-20260814-021：顺延为 V40。

编码前和提交前都必须重扫目标主干；若实际文件或稳定 ID 已被占用，停止并重新分配，不覆盖已落地迁移。

### 10.2 本需求迁移

- `V35__extend_operation_log_trace.sql`：兼容扩展审计表。
- `V36__create_architecture_subsystems.sql`：两个业务表、唯一键、组合父外键和索引。
- `V37__seed_architecture_subsystem_catalog.sql`：菜单、权限、超级管理员授权和六类参数。

菜单 ID 保持 600、601、602；权限 ID 为 6011—6014、6021—6024。参数分类 ID 为 360001—360006，初始配置 ID 为 360101—360106。迁移使用显式租户 1 作为初始目录种子，不影响运行期 AuthUser 租户语义。

六类参数保持：

| 分类代码 | 初始标签 |
| --- | --- |
| `ARCH_DEPLOYMENT_PLATFORM` | 员工渠道平台（P2） |
| `ARCH_SYSTEM_TYPE` | 应用平台类 |
| `ARCH_SYSTEM_OWNERSHIP` | 渠道整合层 |
| `ARCH_RUNTIME` | 7*24 |
| `ARCH_SYSTEM_LEVEL` | A+ |
| `ARCH_DEVELOPMENT_FRAMEWORK` | 员工渠道平台（P2） |

不写入任何 `biz_form_*` 表。

### 10.3 Mock

`local` profile 且 `MOCK_DATA_ENABLED=true` 时，统一初始化器从 `mock/mock-data.json` 幂等同步虚构架构记录。每行显式正数 tenant，先校验同租户根组织，再校验组织、用户和逻辑关联；失败整体回滚。非 local 永不运行，数据不得包含真实人员或旧截图业务记录。

## 11. 验证策略

### 11.1 自动检查

- `mvn -pl :ccb-system -am test`：用户/参数查询、操作审计及现有 system 回归。
- `mvn -pl :ccb-architecture -am test`：CRUD、DTO、权限、租户、引用、团队快照/失效、电话、参数、并发和 HTTP。
- `mvn -pl :ccb-infrastructure -am test`：V35—V37、空库/增量和 Mock。
- `mvn test` 与 Boot package：全 reactor 和装配。
- `npm --prefix web run build`：Vue 类型和生产构建。
- scope、governance、module-boundaries、Flyway、`git diff --check`。

### 11.2 运行验收

- 使用真实 MySQL，分别从空库和已执行到 V34 的库迁移到 V37。
- 以管理员、只读、无权限和测试租户执行两资源 API 矩阵。
- 复核操作日志的 tenant/operator/action/path/result/trace，确认失败审计不覆盖业务错误。
- 在四个视口完成筛选、分页、详情、新增、编辑、删除、负责团队失效、脏表单、409、权限和主题 UAT。
- 检查 Network/DOM 无 tenant/status/schema 请求，Console 无新增错误，`scrollWidth <= innerWidth`。

## 12. 风险、门禁与回退

| 风险 | 控制 |
| --- | --- |
| 主干在实施期间新增 V35—V37 | 编码和提交前重扫；冲突时重新分配，不覆盖已发布迁移 |
| Codex CLI/桌面插件状态来源不同 | 入口脚本优先使用 CLI JSON，兼容验证桌面 config/cache 的 enabled+manifest，不以缓存目录单独放行 |
| 平台公开能力影响既有 system | 公开包最小化、Owner 审批、聚焦和全量回归 |
| 组织失效造成历史不可读 | 名称快照 + validity 投影；编辑强制重选 |
| 用户/参数选项泄露管理字段 | exact-key DTO 和 HTTP JSONPath 断言 |
| 并发父子写入 | MySQL 两事务受控时序测试 |
| 固定表单与移动端漂移 | TypeScript 构建、四视口浏览器 UAT |

编码前还必须满足：当前 scope `codex_allowed=true`、公共能力 Owner 审批、目标分支一致、开发入口检查通过、新计划已获用户批准、目标主干最高仍为 V34 且 V35—V37/稳定 ID 未被占用。

回退按前端 → 架构业务 → 平台契约 → Boot/根装配 → 治理登记逆序执行；V35—V37 和业务数据保留。关闭菜单和撤销权限使用后续补偿迁移，不在生产 DROP 或手工清理。

## 13. 方案比较

### 13.1 采用：独立业务模块 + 最小平台契约

领域逻辑聚合在 architecture；平台只提供用户/参数读取和统一审计。职责清晰，业务用户不需要系统管理权限。

### 13.2 未采用：全部放入 platform/system

会把架构主数据和规则混入平台系统管理，违背用户明确的 module 归属要求。

### 13.3 未采用：架构模块直接查询或写平台表

直接耦合私有表结构，绕过公开契约、权限语义和审计所有权。即使只读可以短期工作，也会形成不可治理的隐式依赖。

### 13.4 未采用：动态表单发布元数据

用户选择固定表单；最新主干又明确下线该能力。继续新增 schema 查询会同时违背产品决策和仓库准入规则。

## 14. 修订记录

- 修订 1：初始旧系统映射和 V1 范围。
- 修订 2：独立业务模块、tenant、平台 capability、schema 与完整并发/审计设计，用户于 2026-08-13 批准。
- 修订 3：根据用户新增字段决策和 2026-08-14 最新 main，物理事业群改为可空文本，新增必选负责团队与名称快照，物理人员改为可空；移除全部动态 schema 能力；平台 capability 缩减为用户/参数查询和操作审计；迁移顺延为 V38—V40并增加 V35—V37 前置门禁。用户于 2026-08-14 批准。
- 修订 4：用户要求修复迁移前置与开发入口阻塞；按主干实际落地顺序把本需求迁移改为 V35—V37，未实施需求顺延到 V38—V40，并将 CLI/桌面插件双来源验证纳入 T0。用户于 2026-08-14 批准执行。
