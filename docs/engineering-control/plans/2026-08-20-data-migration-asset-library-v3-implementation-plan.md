# 数据迁移资产库 V3 实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

## 状态与来源

- 计划修订：1
- 设计修订：1
- 设计文档：`docs/engineering-control/designs/2026-08-20-data-migration-asset-library-v3-design.md`
- 状态：可移交
- 批准证据：用户确认按上述设计建立正式需求、任务范围和控制账本并开始实现。

## 目标与全局约束

**目标：** 从最新 `main` 全新实现可运行、可授权、可审计并适配桌面和手机的数据迁移资产库 V3。

**架构：** 新增 `ccb-data-migration` 业务模块拥有领域规则和数据，通过现有 JDBC、MinIO、认证主体和统一响应能力工作。追加 Flyway 迁移登记表、菜单和权限，前端使用独立模块和 API 封装，不修改旧资产设计或 Mock 数据。

**技术栈：** Java 17、Spring Boot 3.4.4、JdbcTemplate、MySQL 8.4、MinIO、Apache POI、Vue 3、TypeScript、Element Plus。

全局约束：只修改任务范围授权路径；平台实现只读；迁移只追加；服务端执行 RBAC、租户和实体授权；写操作审计；不返回对象键；不使用 `biz_form_*`；不使用 Mock 数据作为验收数据；前端覆盖全状态和四个验收视口。

## 文件职责地图

- `governance/modules.yaml`、`docs/architecture/MODULES.md`、`.github/CODEOWNERS`：登记新模块和 Owner。
- `pom.xml`、`server/src/platform/boot/pom.xml`：Maven reactor 与组合根接入。
- `server/src/platform/infrastructure/src/main/resources/db/migration/V35__data_migration_asset_library_v3.sql`：模块表、索引、正式菜单和权限种子。
- `server/src/modules/data-migration/pom.xml`：模块依赖和 POI。
- `server/src/modules/data-migration/src/main/java/com/ccb/datamigration/model/**`：请求、响应、枚举和分页 DTO。
- `server/src/modules/data-migration/src/main/java/com/ccb/datamigration/service/**`：权限、项目组件、资产、结构化数据、Excel、文件和看板业务。
- `server/src/modules/data-migration/src/main/java/com/ccb/datamigration/web/**`：HTTP 适配和文件响应。
- `server/src/modules/data-migration/src/test/**`：权限、校验、查询、上传替换、回收站和导入测试。
- `web/src/api/data-migration.ts`：唯一前端请求入口。
- `web/src/modules/data-migration/**`：页面定义、看板、列表、移动卡片、抽屉和样式。
- `web/src/router/index.ts`：注册受控数据迁移路由。

### 菜单层级修订任务

**需求映射：** R1、R8

**前置任务：** T5

**文件：**
- 新建：`server/src/platform/infrastructure/src/main/resources/db/migration/V36__data_migration_menu_hierarchy.sql`
- 修改：`docs/requirements/REQ-20260820-031-data-migration-asset-library-v3/requirement.md`
- 修改：`docs/requirements/REQ-20260820-031-data-migration-asset-library-v3/codex-task-scope.yaml`

**接口：**
- 产出：动态菜单树根节点“数据迁移”，子目录 ID 600/620/640，保留原子菜单路由和权限。

- [ ] 追加幂等 V36，插入 ID 590 并更新三个目录的 `parent_id`，将 ID 620 改名为“数迁资产内容”。
- [ ] 运行 Flyway 命名/追加检查与 SQL 静态断言。
- [ ] 在本地数据库执行等价 SQL，登录后断言菜单树父子关系、子菜单数量、路由和权限码不变。
- [ ] 运行 Maven、治理和范围检查。

**回滚：** 执行反向 `UPDATE` 将 600/620/640 的 `parent_id` 恢复为 0，将 620 菜单名恢复为“数迁资产内容管理”；不删除菜单、权限或业务表。

**停止条件：** 菜单 ID、路由、权限码发生非预期变化，或迁移不是追加/幂等。

**升级条件：** 本地数据库版本高于工作树迁移版本且无法安全执行等价 SQL 验证。

## 任务依赖

`T1 -> T2 -> T3 -> T4 -> T5 -> T6`。数据库、接口和前端契约共享字段，全部串行，避免同一迁移、路由和 DTO 的并发漂移。

### T1：模块、数据库和正式菜单基础

**需求映射：** R1、R2、R7、R8

**前置任务：** 无

**文件：** 修改模块清单、架构文档、CODEOWNERS、根/boot POM；新建模块 POM、V35 迁移和迁移结构测试。

**接口：** 产出 `ccb-data-migration` artifact、`dm_*` 表、`data-migration:*` 权限和 20 个菜单节点。

- [ ] 建立模块依赖和迁移静态测试，确认当前 artifact/表不存在。
- [ ] 登记模块、组合根和 Owner，新增只追加 V35 迁移。
- [ ] 运行 `mvn -pl :ccb-data-migration -am test`，预期 reactor 成功且迁移 SQL 静态断言通过。
- [ ] 运行治理检查，保存模块边界、Flyway 和菜单种子证据。

**回滚：** 回退 T1 源码提交；已应用数据库保留表并隐藏菜单。

**停止条件：** artifact 循环依赖、V35 版本冲突或迁移包含破坏性语句。

**升级条件：** 现有公开存储/认证包无法满足业务模块依赖。

### T2：项目、组件、选项和服务端权限

**需求映射：** R2、R3、R7

**前置任务：** T1

**文件：** 新建 DTO、`DataMigrationPermissionService`、`ProjectComponentService`、`ProjectComponentController` 和聚焦测试。

**接口：** 产出 `/options`、`/projects`、`/components` 分页与维护 API；消费认证 `AuthUser`。

- [ ] 用 JDBC mock/集成测试建立项目唯一、组件项目内唯一、管理员维护、普通用户拒绝和租户过滤失败基线。
- [ ] 实现白名单 DTO、服务端授权、事务写入和 `dm_operation_log`。
- [ ] 实现组件删除关联校验和 409 错误。
- [ ] 运行聚焦测试，预期正常、400、403、404、409 和租户边界全部通过。

**回滚：** 回退 T2 Java 文件，保留空表。

**停止条件：** 必须直接写 `sys_*` 私有表或信任请求中的操作人。

**升级条件：** V3 要求新增项目成员数据范围。

### T3：文件型资产、重新上传和回收站

**需求映射：** R3、R4、R7、R8

**前置任务：** T2

**文件：** 新建资产 DTO、`AssetService`、`AssetController`、文件对象协调器和测试。

**接口：** 产出 `/assets/{type}`、`/recycle-bin`、受认证下载；消费 `MinioStorageService`。

- [ ] 建立 11 类白名单、限定查询、上传人实体授权、批量上传部分失败和对象补偿测试。
- [ ] 实现 50MB 限制、规范化文件名、随机对象键和当前文件替换事务。
- [ ] 实现逻辑删除、管理员恢复/彻底清理以及审计。
- [ ] 运行聚焦测试，预期对象键不出现在 JSON，普通人员不能更新他人资料。

**回滚：** 隐藏资产入口并回退 Java；保留数据库记录和对象以便补偿。

**停止条件：** 对象更新失败会静默覆盖有效记录或删除错误租户对象。

**升级条件：** 文件限制超过现有 multipart/MinIO 能力。

### T4：结构化资产、Excel 和看板

**需求映射：** R1、R5、R6、R7

**前置任务：** T3

**文件：** 新建规则、参数、问题、表结构 DTO/service/controller、`ExcelService`、`DashboardService`、定时任务和测试。

**接口：** 产出结构化 CRUD、XLSX 导入导出、整体/组件快照查询和管理员刷新接口。

- [ ] 建立码值、项目组件关联、5,000 行限制、逐行失败、关联删除和快照统计测试。
- [ ] 实现 Apache POI 流程、列白名单、资源关闭和公式不执行策略。
- [ ] 实现每日 02:00 快照和实时钻取条件映射。
- [ ] 运行聚焦测试并打开生成 XLSX 验证列头和行数。

**回滚：** 停止调度并回退结构化服务；保留已导入数据。

**停止条件：** Excel 解析无大小/行数限制或看板跨租户聚合。

**升级条件：** 需要异步超大批次或分布式任务协调。

### T5：统一前端模块和动态路由

**需求映射：** R1、R2、R3、R4、R5、R6

**前置任务：** T4

**文件：** 新建 API、类型、模块页面和 CSS；修改路由；前端构建作为测试。

**接口：** 消费 T2-T4 REST 契约，产出 `/data-migration/:group/:section` 用户界面。

- [ ] 建立 API 类型和页面注册表，未知 section 显示明确错误而不发请求。
- [ ] 实现整体/组件看板、通用资产列表、结构化列表、基础资料和回收站视图。
- [ ] 实现抽屉表单、上传/导入、下载、删除、恢复、钻取和权限显隐；后端仍是授权事实源。
- [ ] 实现桌面表格和手机卡片，覆盖加载、空、失败、无权限、提交中和部分成功。
- [ ] 运行 `npm --prefix web run build`，预期 TypeScript 和 Vite 构建成功。

**回滚：** 回退前端模块和路由，后端数据不变。

**停止条件：** 页面依赖 Mock、硬编码租户/上传人或发生页面级横向溢出。

**升级条件：** 现有公共组件无法满足且需要修改 `web/src/components/ui`。

### T6：全量检查和真实运行验收

**需求映射：** R1-R8

**前置任务：** T5

**文件：** 只更新当前前缀 execution、observation、convergence 和 handoff 证据。

**接口：** 消费全部实现，产出可复现测试与浏览器证据。

- [ ] 运行聚焦后端、全量 Maven、前端构建、治理、范围和 `git diff --check`。
- [ ] 使用 `MOCK_DATA_ENABLED=false` 启动本地 MySQL、MinIO、后端和前端，验证健康和菜单 API。
- [ ] 管理员验证项目、组件、文件上传替换、下载、删除恢复、Excel、看板钻取；普通人员验证查看/下载和他人资料拒绝。
- [ ] 在 1280x800、375x812、390x844、430x932 和明暗主题检查控制台、网络、遮挡和溢出。
- [ ] 记录独立观测、纠正偏差并执行收敛门禁。

**回滚：** 停止开发进程，隐藏菜单和调度，按 T1-T5 逆序回退应用提交。

**停止条件：** P0/P1、迁移失败、越权、对象丢失、白屏或核心流程不可达。

**升级条件：** 本地依赖不可用或需要生产验证权限。

## 需求覆盖

- R1 菜单与看板：T1、T4、T5、T6
- R2 基础资料：T1、T2、T5、T6
- R3 权限与审计：T2、T3、T4、T5、T6
- R4 文件型资产：T3、T5、T6
- R5 结构化资产：T4、T5、T6
- R6 Excel 与钻取：T4、T5、T6
- R7 数据隔离与校验：T1-T6
- R8 运行、响应式与回退：T1、T3、T5、T6

## 控制模型种子

候选被控边界为新业务模块、V35、组合根和前端模块；候选状态包括迁移、权限、资产生命周期、对象一致性、快照日期、构建和浏览器状态；候选传感器包括模块测试、SQL 断言、HTTP 权限流、MinIO 对象探针、XLSX 解析、菜单 API、构建和浏览器像素/溢出检查。这些在建模前均为 `hypotheses-only`。

## 高风险动作批准

用户已批准新增模块、追加数据库迁移、正式菜单权限入库、MinIO 文件写入和从最新 `main` 的隔离实现。生产访问、生产迁移、合并、推送和发布不在授权范围。

---

# 问题清单独立存储增量实施计划

## 状态与来源

- 计划修订：1（问题清单独立存储）
- 设计修订：1（见同名增量设计）
- 设计文档：`docs/engineering-control/designs/2026-08-20-data-migration-asset-library-v3-design.md`
- 状态：已解除范围阻断，进入受控产品实现
- 用户授权：用户确认问题清单独立为 `dm_issue`、不保留历史数据，并授权生成实施计划。

## 目标与全局约束

目标：将问题清单从 `dm_asset` 完整改造为 `dm_issue` 独立模型，清理旧 ISSUE 数据和问题关系，保留 `dm_asset` 仍被 REPORT、RULE、PARAMETER 和文件资产使用的通用字段。

全局约束：

- 只修改当前 `codex-task-scope.yaml` 的 `writable_paths`；平台模块和公共能力只读。
- Flyway 只追加，不修改 V84–V92；生产数据库不手工改表。
- 新增迁移候选文件为 `server/src/platform/infrastructure/src/main/resources/db/migration/V93__data_migration_issue_independent_storage.sql`。
- 当前范围文件已授权 V93；先完成 T-I1 基线复核，再按任务依赖进入产品实现。
- 不迁移、不归档、不保留 `dm_asset(asset_type='ISSUE')` 历史数据；清理前按环境执行计数和备份策略检查。
- 不删除 `dm_asset.structured_data`、REPORT 字段、附件字段、组件关联和通用审计字段。
- 保持 `/api/data-migration/issues` 路由、统一 `ApiResponse`、认证、RBAC、租户隔离、实体授权和 `dm_operation_log` 审计。

## 文件职责地图

- 新建（候选，当前未授权）：`server/src/platform/infrastructure/src/main/resources/db/migration/V93__data_migration_issue_independent_storage.sql`：创建 `dm_issue`、索引并清理旧 ISSUE 行/关系。
- 修改：`server/src/modules/data-migration/src/main/java/com/ccb/datamigration/service/IssueService.java`：从 `dm_issue` 读写结构化列和关系。
- 修改：`server/src/modules/data-migration/src/main/java/com/ccb/datamigration/service/IssueController.java`：保持路由，调整响应字段投影。
- 修改：`server/src/modules/data-migration/src/main/java/com/ccb/datamigration/service/ExcelService.java` 及相关测试：问题 XLSX 改为 `dm_issue` 列。
- 修改：`server/src/modules/data-migration/src/main/java/com/ccb/datamigration/service/AssetService.java`、`StructuredAssetService.java`：移除 ISSUE 类型白名单和旧 ISSUE SQL。
- 修改/新增测试：`server/src/modules/data-migration/src/test/**`：覆盖 CRUD、筛选、导入导出、关系、权限、审计和非 ISSUE 回归。
- 修改：`docs/requirements/REQ-20260820-031-data-migration-asset-library-v3/database-schema-and-relations.md`：反映独立 `dm_issue` 结构和关系。

## 任务依赖图与并行策略

串行依赖：`T-I1 -> T-I2 -> T-I3 -> T-I4 -> T-I5`。

数据库迁移、服务 SQL 和关系契约共享 `dm_issue` 字段，不能并行修改。T-I1 未解除范围阻断前，不启动 T-I2–T-I5。

## 范围授权阻断点

当前 `docs/requirements/REQ-20260820-031-data-migration-asset-library-v3/codex-task-scope.yaml` 的 `database.migration_files` 与 `scope.writable_paths` 只列到 V92，没有授权新建 V93。根据项目规约：

1. 不能修改已发布 V92 或其他历史迁移脚本来绕过授权。
2. 不能在未授权路径创建 V93。
3. 没有 V93，无法把 `dm_issue` 建表和旧 ISSUE 清理作为可重复部署的正式迁移交付。
4. 因此当前只能生成计划和做只读基线，不能执行产品代码、数据库或删除操作。

解除条件：由需求 Owner/授权维护者更新当前 `codex-task-scope.yaml`，至少增加：

```text
server/src/platform/infrastructure/src/main/resources/db/migration/V93__data_migration_issue_independent_storage.sql
```

并同步更新 `database.migration_files`、`release_verification` 或等价验收范围。范围更新后重新运行：

```bash
node scripts/check-codex-scope.mjs --scope docs/requirements/REQ-20260820-031-data-migration-asset-library-v3/codex-task-scope.yaml --base origin/main --head HEAD --working-tree
```

### T-I1：范围和数据库基线（已解除）

**需求映射：** R-I1, R-I2, R-I4

**前置任务：** 无

**文件：** 只读 `codex-task-scope.yaml`、Flyway 目录、IssueService、数据库；解除范围后才允许创建 V93。

**步骤：**

- [ ] 记录 `origin/main` 与当前 HEAD 基线，确认 `origin/main` 无新增提交。
- [ ] 记录本地和目标环境 `dm_asset` ISSUE 行数、`dm_asset_relation` ISSUE 源关系数、`pm_project` 可用性。
- [x] 检查 V93 已进入 writable_paths；范围阻断已解除。

**验收：** 基线 SQL、scope 检查和当前分支状态均有原始输出；未授权时产品 diff 为 0。

**回滚：** 无产品修改，不需要回滚。

**停止条件：** V93 未授权、目标环境不是明确的本地/批准测试库、发现 ISSUE 数据量非零但没有清理审批。

**升级条件：** Owner 不同意新增 V93，或要求保留历史数据/改用其他迁移策略。

### T-I2：独立表和清理迁移

**需求映射：** R-I1, R-I2, R-I4

**前置任务：** T-I1 通过范围门禁

**文件：** 新建 V93 候选迁移；修改数据库结构文档。

**接口：** 产出 `dm_issue` 表、唯一键、筛选索引、逻辑删除字段；清理旧 ISSUE 行和 `source_asset_type='ISSUE'` 关系。

**步骤：**

- [ ] 创建 `dm_issue`，字段与设计文档一致，唯一键覆盖租户/项目/问题编号/删除状态。
- [ ] 在明确的清理顺序下删除旧 ISSUE 关系和 `dm_asset` ISSUE 行；不复制数据。
- [ ] 运行 Flyway 静态检查和本地迁移，验证 `SHOW CREATE TABLE`、旧数据计数和关系计数。

**验收：** `dm_issue` 存在；旧 ISSUE 数据与关系为 0；REPORT/RULE/PARAMETER/附件数据不受影响。

**回滚：** 应用回滚不重新启用旧 ISSUE 路径；数据恢复只能使用迁移前数据库备份，经审批执行。

**停止条件：** 迁移执行环境无法确认、清理前计数未记录、删除影响非 ISSUE 数据或存在跨租户关系。

**升级条件：** 需要生产执行、需要保留历史数据、或发现 V93 与现有 Flyway 版本冲突。

### T-I3：后端问题服务和关系契约

**需求映射：** R-I1, R-I3, R-I4, R-I6

**前置任务：** T-I2

**文件：** 修改 IssueService、IssueController、ExcelService；修改/新增 `server/src/modules/data-migration/src/test/**`。

**接口：** 保持 `/api/data-migration/issues` 路由；请求字段映射到 `dm_issue` 列；关系源 ID 使用 `dm_issue.id`。

**步骤：**

- [ ] 将列表、详情、创建、更新、删除、恢复、清理和选项 SQL 改为 `dm_issue`。
- [ ] 将问题字段 JSON 读取改为列投影，保留码值、分页、关键字、租户和实体授权校验。
- [ ] 更新会议纪要、目标表、字段关系的增删查，校验目标类型和租户。
- [ ] 将问题 Excel 导入/导出改为结构化列，逐行错误契约保持不变。
- [ ] 从 AssetService/StructuredAssetService/ExcelService 类型集合移除 ISSUE。

**验收：** 静态搜索无 `dm_asset` ISSUE CRUD；问题模块测试覆盖 200/400/403/404/409 和租户边界；非 ISSUE 测试通过。

**回滚：** 在未完成前端切换前不发布；应用回退只能停用问题菜单和接口，不恢复旧 ISSUE 路径。

**停止条件：** 发现仍需从 dm_asset 读取问题字段，或关系目标校验无法证明租户安全。

**升级条件：** API 兼容需要新增版本化路由，或前端依赖旧 `structured_data` 响应。

### T-I4：前端和接口回归

**需求映射：** R-I3, R-I6

**前置任务：** T-I3

**文件：** 修改 `web/src/api/data-migration.ts`、问题清单页面和相关类型/测试（仅当前 writable_paths）。

**步骤：**

- [ ] 按新的结构化响应调整问题列表、详情、编辑抽屉、导入导出和回收站。
- [ ] 验证加载、空、失败、无权限、提交中、重复提交和关系目标不存在状态。
- [ ] 运行 `npm --prefix web run build`，并按需求视口验证问题清单页面。

**验收：** 桌面和 375x812、390x844、430x932 页面无白屏/横向溢出，接口不再发送 `structured_data` 问题 JSON。

**回滚：** 回退前端变更，后端数据模型不回退到 dm_asset ISSUE。

**停止条件：** 前端仍依赖旧 JSON 字段或出现权限/关系信息泄露。

**升级条件：** 需要修改公共 UI 组件或跨模块契约。

### T-I5：集成验证与收敛

**需求映射：** R-I1–R-I6

**前置任务：** T-I4

**文件：** 只更新当前任务前缀的 execution/observation/convergence 证据。

**步骤：**

- [ ] 运行 `mvn -pl :ccb-data-migration -am test`、`mvn test`、`npm --prefix web run build`。
- [ ] 运行治理检查、scope 检查和 `git diff --check`。
- [ ] 本地 `MOCK_DATA_ENABLED=false` 启动后端和前端，验证问题清单 CRUD、关系、权限和回收站。
- [ ] 进行至少三次异质采样：数据库结构/计数、静态 SQL 契约、HTTP/浏览器行为。
- [ ] 只有所有 must 需求通过且迁移、权限、回归和残余风险收敛后，才进入 `converged`。

**验收：** 六项增量需求均有独立证据；不存在旧 ISSUE 数据源；通用资产回归通过。

**回滚：** 按应用提交逆序回退；数据库清理不通过应用回滚恢复，使用批准的备份恢复方案。

**停止条件：** 任何 P0/P1、迁移失败、越权、旧数据源残留、通用资产回归失败或浏览器白屏。

**升级条件：** 需要生产访问、合并、推送或修改任务范围之外的文件。

## 增量需求覆盖

- R-I1：T-I2、T-I3、T-I5
- R-I2：T-I1、T-I2、T-I5
- R-I3：T-I3、T-I4、T-I5
- R-I4：T-I2、T-I3、T-I5
- R-I5：T-I3、T-I5
- R-I6：T-I3、T-I4、T-I5

## 历史范围阻断（已解除）

此前 V93 未列入 `codex-task-scope.yaml`，因此 T-I2 至 T-I5 曾被阻断。用户已授权执行，授权维护者已将 V93 加入 `scope.writable_paths`、`database.migration_files` 和回滚说明；重新检查时仅报告工作区既有的 `application-local.yml` 越界，该文件不属于本次 data-migration 改动并按用户要求忽略。
