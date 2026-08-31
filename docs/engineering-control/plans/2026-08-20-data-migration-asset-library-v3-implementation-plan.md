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
