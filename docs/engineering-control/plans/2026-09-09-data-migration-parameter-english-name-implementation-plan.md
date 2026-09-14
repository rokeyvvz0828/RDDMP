# 迁移参数英文名实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

## 状态与来源

- 计划修订：1
- 设计修订：1
- 设计文档：`docs/engineering-control/designs/2026-09-09-data-migration-parameter-english-name-design.md`
- 状态：待用户复核

**目标：** 为迁移参数增加必填、仅允许字母数字下划线、在租户和项目及系统编号范围内不区分大小写唯一的参数英文名，并贯通维护、搜索、Excel 和回收站。

**架构：** 通过追加 `V198` 扩展 `dm_parameter`，由大小写不敏感列和唯一索引提供最终并发保护；`ParameterService` 继续作为全部业务入口的单一规则实现，前端只扩展现有 API 类型和 `ParametersPage.vue`。数据库、后端、前端按契约顺序串行实施，避免字段和 Excel 列位移不一致。

**技术栈：** Java 17、Spring Boot 3.4.4、JdbcTemplate、Apache POI、MySQL 8.4/Flyway、Vue 3、TypeScript、Element Plus、Vite。

## 全局约束

- 只修改 `codex-task-scope.yaml` 中 `writable_paths` 覆盖的文件。
- 不修改历史 `V197__data_migration_parameter_domain.sql`，只追加 `V198__data_migration_parameter_english_name.sql`。
- 不处理或回填已有 `dm_parameter` 数据；存在记录时 V198 必须失败关闭。
- `parameter_name` 既有必填和唯一规则、菜单 752、路由、权限码、项目范围、Owner、审计和逻辑删除语义保持不变。
- 参数英文名允许 `[A-Za-z0-9_]+`，最大 255 字符，允许数字或下划线开头；唯一比较不区分大小写。
- 复用现有 `UiDataTable`、`UiFormDrawer`、导入对话框、项目上下文和语义主题，不修改公共组件。
- 不访问生产系统，不使用真实数据，不自行合并或发布。

---

## 文件职责地图

| 路径 | 状态 | 单一职责 | 事实依据 |
| --- | --- | --- | --- |
| `server/src/platform/infrastructure/src/main/resources/db/migration/V198__data_migration_parameter_english_name.sql` | candidate-new | 空表断言、英文名列和不区分大小写唯一索引 | 当前最高迁移为 V197，Flyway 只追加 |
| `server/src/modules/data-migration/src/main/java/com/ccb/datamigration/service/ParameterService.java` | existing | 参数规则、查询、Excel、回收站和审计编排 | 当前全部参数入口已集中在该服务 |
| `server/src/modules/data-migration/src/main/java/com/ccb/datamigration/web/ParameterController.java` | existing | 现有 REST 路由与 RBAC HTTP 适配 | 请求体当前以 Map 传递，无需新 DTO |
| `server/src/modules/data-migration/src/test/java/com/ccb/datamigration/service/ParameterServiceTest.java` | existing | 参数服务行为和 Excel 回归传感器 | 当前已有创建、筛选、导入和权限测试 |
| `server/src/modules/data-migration/src/test/java/com/ccb/datamigration/DataMigrationModuleRegistrationTest.java` | existing | 迁移文件和模块契约静态传感器 | 当前登记 V194/V197 等域迁移 |
| `web/src/api/data-migration.ts` | existing | 参数 REST TypeScript 契约 | 已定义 `ParameterRecord`/`ParameterFormData` |
| `web/src/modules/data-migration/views/content/ParametersPage.vue` | existing | 参数列表、表单、详情、导入和响应式交互 | 当前专属页面已复用统一 UI 能力 |
| `.ai-control/requirements/req-20260906-067-dm-param/*.json` | existing | 当前需求执行、观测和收敛证据 | 任务范围指定控制前缀 |

## 任务依赖图与并行策略

`T1 数据库与后端契约 -> T2 前端契约与交互 -> T3 集成观测与收敛`

全部串行。T2 依赖 T1 固定字段名、错误语义和 Excel 列顺序；T3 必须测量组合结果。当前任务不使用子 Agent 或并行写入。

## 需求覆盖表

| 需求 | 任务 |
| --- | --- |
| R1 必填与字符规则 | T1, T2 |
| R2 不区分大小写唯一 | T1 |
| R3 REST/页面/回收站贯通 | T1, T2 |
| R4 Excel 模板、导入与导出 | T1, T2 |
| R5 关键字搜索 | T1, T2 |
| R6 V198 追加且不处理已有数据 | T1, T3 |
| R7 权限、审计与响应式不变量 | T1, T2, T3 |

### T1：数据库与后端形成可测试的参数英文名契约

**需求映射：** R1, R2, R3, R4, R5, R6, R7

**前置任务：** 无

**需求映射与前置事实：** V197 已创建 `dm_parameter` 和中文参数名唯一键；`ParameterService` 统一承接 CRUD、Excel、回收站和关键字 SQL；Controller 使用 Map 请求体且现有 RBAC 无需改变。

**文件：**

- 新建：`server/src/platform/infrastructure/src/main/resources/db/migration/V198__data_migration_parameter_english_name.sql`
- 修改：`server/src/modules/data-migration/src/main/java/com/ccb/datamigration/service/ParameterService.java`
- 修改：`server/src/modules/data-migration/src/main/java/com/ccb/datamigration/web/ParameterController.java`（仅在契约注释或显式适配需要时）
- 测试：`server/src/modules/data-migration/src/test/java/com/ccb/datamigration/service/ParameterServiceTest.java`
- 测试：`server/src/modules/data-migration/src/test/java/com/ccb/datamigration/DataMigrationModuleRegistrationTest.java`

**接口：**

- 消费：创建/更新请求 Map 的 `parameterNameEn`；Excel 六列“参数类型、参数范围分类、系统编号、参数名称、参数英文名、参数说明”。
- 产出：所有参数查询 Map 的 `parameter_name_en`；400 格式错误；409 同范围大小写不敏感冲突；关键字匹配三字段。

- [ ] **步骤 1：建立后端失败检查**

在 `ParameterServiceTest` 增加以下可判别场景：合法 `ABC_01` 创建后 SQL 参数含英文名；空值和 `ABC-01`/中文/空格返回 BAD_REQUEST；同范围已有 `ABC_01` 时 `abc_01` 返回 CONFLICT；跨项目或系统可复用；Excel 模板含第 5 列“参数英文名”，导入按六列解析并对文件内英文名使用 `Locale.ROOT` 小写键去重；关键字 SQL 包含 `a.parameter_name_en LIKE ?`。

- [ ] **步骤 2：运行失败信号**

运行：`mvn -pl :ccb-data-migration -am -Dtest=ParameterServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

预期：新增断言失败，原因指向当前服务没有 `parameter_name_en`、没有 `parameterNameEn` 校验或仍使用五列 Excel。

证据：命令退出码、失败测试名和关键断言。

- [ ] **步骤 3：追加 V198**

迁移先以存储过程断言 `SELECT COUNT(*) FROM dm_parameter` 为 0，再添加：

```sql
ALTER TABLE dm_parameter
    ADD COLUMN parameter_name_en VARCHAR(255) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL
        COMMENT '参数英文名（仅字母、数字和下划线；同项目同系统不区分大小写唯一）'
        AFTER parameter_name,
    ADD UNIQUE KEY uk_dm_parameter_name_en
        (tenant_id, project_id, system_code, parameter_name_en);
```

断言失败必须不执行 ALTER；不得修改或复制 V197。

- [ ] **步骤 4：扩展 ParameterService 最小契约**

在 SELECT、RECYCLE、findRaw 和 findRawIncludingDeleted 中加入 `parameter_name_en`；创建和更新通过 `requireEnglishName(Object)` 规范化：trim、非空、code point 数不超过 255、`matches("[A-Za-z0-9_]+")`。增加 `englishNameExists(...)`/`activeEnglishNameExists(...)`，同范围查询以大小写不敏感列比较，捕获数据库唯一异常时返回英文名冲突文案。

将模板与导出列改为六列和八列；导入在参数名称后读取英文名，文件内键使用 `scope + systemCode + parameterNameEn.toLowerCase(Locale.ROOT)`；INSERT/UPDATE 写入英文名。列表及回收站关键字 SQL 增加英文名 OR 分支和对应参数。权限、项目范围、Owner、审计调用保持原样。

- [ ] **步骤 5：运行局部与相关回归**

运行：`mvn -pl :ccb-data-migration -am -Dtest=ParameterServiceTest,DataMigrationModuleRegistrationTest -Dsurefire.failIfNoSpecifiedTests=false test`

预期：相关测试 0 失败，新增字符、唯一、Excel、搜索和迁移静态断言全部通过。

运行：`node scripts/check-flyway-migrations.mjs`

预期：V198 命名和追加顺序检查通过，V197 校验和未修改证据成立。

证据：Surefire 报告、命令退出码、`git diff --exit-code -- .../V197__data_migration_parameter_domain.sql`。

**验收：** 合法/非法/重复/跨范围场景可判别；六列导入与八列导出固定；活动和回收站搜索包含英文名；权限审计逻辑未改变。

**回滚：** 在未发布时撤销 T1 源码和新 V198；V198 一旦在共享环境执行则保留列和索引，通过关闭写入口回退应用，不执行生产 DROP。

**停止条件：** V198 编号已被其他迁移占用；目标迁移测试发现 `dm_parameter` 有记录；实现要求修改 V197、公共组件、权限码或任务范围外文件。

**升级条件：** MySQL 环境不支持选定排序规则；业务要求保留已有参数；现有中文唯一约束与英文名独立唯一发生需求冲突。

### T2：前端完整呈现和提交参数英文名

**需求映射：** R1, R3, R4, R5, R7

**前置任务：** T1

**需求映射与前置事实：** T1 固定请求 `parameterNameEn`、响应 `parameter_name_en`、六列模板和服务端错误；现有页面已有加载、失败、无权限、提交中、导入结果与 UiDataTable 局部滚动。

**文件：**

- 修改：`web/src/api/data-migration.ts`
- 修改：`web/src/modules/data-migration/views/content/ParametersPage.vue`

**接口：**

- 消费：T1 的 `parameterNameEn` 请求和 `parameter_name_en` 响应。
- 产出：类型安全的参数表单与记录；列表、详情、抽屉、模板说明和搜索占位中的参数英文名交互。

- [ ] **步骤 1：建立前端契约失败检查**

运行只读 Node 探针，断言 API 类型和页面源码同时包含 `parameterNameEn`、`parameter_name_en`、标签“参数英文名”、正则 `/^[A-Za-z0-9_]+$/`、搜索占位“参数名称 / 参数英文名 / 参数说明”以及六列模板说明。

运行：`node --input-type=module -e "import fs from 'node:fs'; const a=fs.readFileSync('web/src/api/data-migration.ts','utf8'); const p=fs.readFileSync('web/src/modules/data-migration/views/content/ParametersPage.vue','utf8'); if(![a.includes('parameter_name_en'),a.includes('parameterNameEn'),p.includes('参数英文名'),p.includes('parameterNameEn'),p.includes('参数名称 / 参数英文名 / 参数说明')].every(Boolean)) process.exit(1)"`

预期：实施前退出码非 0。

- [ ] **步骤 2：扩展 API 类型**

`ParameterRecord` 新增 `parameter_name_en: string`，`ParameterFormData` 新增 `parameterNameEn: string`；保持现有请求函数签名和路由不变。

- [ ] **步骤 3：扩展页面状态与表单**

reset/create/edit/submit 全链路维护 `parameterNameEn`。保存前先检查必填，再以 `/^[A-Za-z0-9_]+$/` 给出“参数英文名只允许字母、数字和下划线”；maxlength 为 255，服务端错误继续在抽屉错误区持久显示并防重复提交。

- [ ] **步骤 4：扩展展示、搜索和 Excel 说明**

桌面表格在参数名称后增加参数英文名列；详情增加英文名；关键词占位扩展；模板说明包含英文名。移动端继续由 `UiDataTable` 容器局部滚动，页面根不增加固定最小宽度；弹层保持视口宽度和正文局部滚动。

- [ ] **步骤 5：运行前端局部契约和构建**

运行步骤 1 的 Node 探针，预期退出码 0。

运行：`npm --prefix web run build`

预期：`vue-tsc` 和 Vite 构建通过，0 个 TypeScript 错误。

证据：探针输出、构建退出码与产物摘要。

**验收：** 新增/编辑必填和格式提示就近可见；返回字段反显；列表详情可扫描；搜索和模板文案一致；所有既有异步状态保留。

**回滚：** 同步撤销 API 类型与页面字段；不得仅回滚前端而保留强制必填后端作为可发布状态。

**停止条件：** 需要修改 `components/ui` 或全局样式才能呈现；T1 契约字段或错误语义变化；现有页面存在用户并行修改冲突。

**升级条件：** 375px 页面级溢出无法在当前页面局部修复；产品要求新增独立英文名筛选器或更改列顺序。

### T3：组合验证、独立观测和收敛

**需求映射：** R2, R3, R4, R5, R6, R7

**前置任务：** T1, T2

**需求映射与前置事实：** T1/T2 已提供局部测试结果；当前任务是数据库迁移和前后端必填契约升级，必须验证组合结果和范围一致性。

**文件：**

- 修改：`.ai-control/requirements/req-20260906-067-dm-param/*.json`
- 测试：`server/src/modules/data-migration/src/test/java/com/ccb/datamigration/**`

**接口：**

- 消费：T1/T2 实际 diff、测试和构建输出。
- 产出：执行记录、独立观察、反馈闭合和收敛判定。

- [ ] **步骤 1：运行后端与治理集成检查**

运行：`mvn -pl :ccb-data-migration -am test`

运行：`node scripts/check-flyway-migrations.mjs`

运行：`node scripts/check-all-governance.mjs`

运行：`git diff --check`

预期：目标模块测试、Flyway 和 diff 检查 0 失败；治理若仍被无关 `REQ-20260904-061` 非 JSON 文件阻断，必须记录为既有外部扰动，不得改动该需求文件。

- [ ] **步骤 2：验证迁移边界**

在可用 MySQL 8.4 测试容器中分别验证空 `dm_parameter` 成功应用 V198、插入 `ABC_01` 后 `abc_01` 触发唯一冲突；另以 V197 后含一行参数的临时数据库验证 V198 在 ALTER 前失败且不新增列。不得连接生产数据库。

预期：三条数据库信号分别为成功、重复冲突、前置断言失败且结构不变。

- [ ] **步骤 3：真实浏览器验收**

启动本地后端和前端，在 `1280x800`、`375x812`、`390x844`、`430x932` 使用具备迁移参数权限的测试角色验证新增、编辑、英文名搜索、六列导入部分失败、详情、删除和回收站；使用无权限角色验证拒绝。检查接口状态、控制台错误、明暗主题和 `document.documentElement.scrollWidth <= window.innerWidth`。

预期：合法路径完成；非法字符和大小写重复有明确错误；无权限请求被服务端拒绝；无白屏、遮挡或页面级横向溢出。

- [ ] **步骤 4：范围审计和账本记录**

运行任务范围检查，确认所有产品 diff 位于授权路径，V197 无变化，未改公共组件、权限码或其他业务模块。按 control-engineering 阶段产物记录 execution、observation、feedback 和 convergence，不覆盖旧账本历史。

**验收：** 全部 must 需求有自动化或运行证据；P0/P1 反馈为 0；无法执行的浏览器或 MySQL 验证明确保留为未收敛风险，不虚报通过。

**回滚：** 未发布时整体撤销本需求增量；已执行 V198 时保留追加结构、关闭写入口并回退前后端同一版本。

**停止条件：** 发现跨租户或越权访问、V198 改写已有数据、V197 被改变、数据库唯一性不满足，或真实浏览器核心路径不可用。

**升级条件：** 需要生产数据决策、平台 Owner 批准范围外迁移变更、公共 UI 修复或新权限模型。

## 集成检查

- T1 后：`mvn -pl :ccb-data-migration -am -Dtest=ParameterServiceTest,DataMigrationModuleRegistrationTest -Dsurefire.failIfNoSpecifiedTests=false test`。
- T2 后：参数前端源码契约探针和 `npm --prefix web run build`。
- T1+T2 后：`mvn -pl :ccb-data-migration -am test`、Flyway、治理、范围与 diff 检查。
- 最终：MySQL 8.4 空表/非空表/大小写重复三组信号，以及四个视口真实浏览器路径。

## 控制模型种子

以下均为待 `model-engineering-system` 验证的假设：

- 被控边界候选：V198、ParameterService/Controller、前端 API/页面、参数测试和当前前缀账本。
- 状态变量候选：英文名格式有效性、范围内规范值唯一性、Excel 列顺序、搜索字段集合、权限/审计不变量、响应式溢出状态。
- 接口候选：`parameterNameEn -> parameter_name_en`、六列导入、八列导出、单 keyword 三字段匹配。
- 传感器候选：JUnit、迁移静态检查、MySQL 8.4 行为测试、TypeScript 构建、源码契约探针、真实浏览器 DOM/API/console。
- 执行器候选：V198 DDL、ParameterService SQL/校验、API 类型、ParametersPage 表单与列。
- 扰动候选：V198 编号竞争、目标库存在数据、数据库排序规则差异、旧 Excel 模板、无关治理文件失败、用户并行修改。
- 时延候选：Maven 全量测试、前端构建、MySQL 容器启动和浏览器联调。
- 核心假设：目标迁移环境没有需要保留的参数记录；若 `COUNT(*) > 0`，该假设失效并停止执行。

## 风险与用户批准

高风险动作是追加 V198 并将写契约升级为必填。计划通过空表断言防止静默破坏数据，通过后端和数据库双层校验确保并发唯一，通过前后端同批验证避免旧客户端不兼容。用户批准本计划后才将交接包设为 approved 并导入工程控制闭环。
