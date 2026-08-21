# 版本申请文件介质实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

## 状态与来源

- 计划修订：1
- 设计修订：1
- 设计文档：`docs/engineering-control/designs/2026-08-19-release-file-media-design.md`
- 需求文档：`docs/requirements/REQ-20260819-039-release-file-media/requirement.md`
- 任务范围：`docs/requirements/REQ-20260819-039-release-file-media/codex-task-scope.yaml`
- 状态：可移交
- 分支：按用户明确要求使用现有本地 `rokey` 分支；该分支不符合仓库标准功能分支命名，范围检查会记录此例外，不切换或清理当前工作区。

**目标：** 让一张版本申请同时支持普通交付单元和多条无版本号文件路径，并把每条路径独立接入冲突确认、审批摘要、投产基线、生产版本和统计分析。

**架构：** 扩展发布模块现有统一交付明细模型，使用 `DeliveryItemType`、`file_path` 和固定长度 `item_key` 区分普通交付单元与文件介质。普通请求字段 `deliveries` 保持兼容，新增路径专用 `fileMedia`；生产链路使用同一项键创建候选、查询历史和回算当前生产记录，不建立第二套状态机。

**技术栈：** Java 17、Spring Boot 3.4.4、JdbcTemplate、Flyway、JUnit 5、Mockito、Vue 3、TypeScript、Element Plus、Vite、MySQL 8.4。

## 全局约束

- 仅修改 `REQ-20260819-039` 任务范围列出的文件，不覆盖或回退当前 `rokey` 分支上的既有修改。
- Flyway 只新增 `V46__release_file_media.sql`，不修改 V38、V39 或已执行迁移。
- 一张申请仍只属于一个项目和一个物理子系统，审批继续整单处理。
- 文件介质固定为交付单元 `FILE`、名称“文件介质”、制品类型 `FILE`，没有版本号；固定字段由后端生成。
- 路径规范化只去除首尾空格，保留正文、大小写和斜杠；最长 1024 字符，禁止控制字符。
- 文件路径只作为转义后的业务文本保存和显示，后端不得解析、读取或访问实际文件系统。
- 项目、物理子系统和普通交付单元选择源继续使用前端 Mock，不新增主数据服务。
- 沿用现有 RBAC、实体授权、乐观锁、修订历史、状态事件、附件、审批和投产审计。
- 新增前端优先复用现有发布申请抽屉、状态组件和交付示范中心表单模式；遵守 `design-h5.md`。
- 运行验证继续设置 `MOCK_DATA_ENABLED=false`，禁止覆盖人工测试数据。
- 不自动执行 Git 提交、推送、重置或清理；每个任务只记录限定路径 diff 和验证证据。

---

## 文件职责地图

| 路径 | 状态 | 单一职责 | 事实依据 |
| --- | --- | --- | --- |
| `server/src/platform/infrastructure/src/main/resources/db/migration/V46__release_file_media.sql` | candidate-new | 追加文件介质字段、存量回填、关系键及索引迁移 | V38/V39 当前拥有申请交付、关系和投产表 |
| `server/src/modules/release/src/main/java/com/ccb/release/application/model/ReleaseApplicationModels.java` | existing | 申请输入、内部交付项、响应、冲突变化类型 | 当前所有申请 DTO 集中于此 |
| `server/src/modules/release/src/main/java/com/ccb/release/application/persistence/ReleaseApplicationStore.java` | existing | 申请交付修订持久化、冲突检索、追加关系持久化 | 当前 SQL 以 `delivery_unit_code` 为身份 |
| `server/src/modules/release/src/main/java/com/ccb/release/application/service/ReleaseApplicationService.java` | existing | 路径校验、稳定项键、冲突和追加业务规则 | 当前创建、更新和冲突事实由此编排 |
| `server/src/modules/release/src/main/java/com/ccb/release/application/service/ReleaseSubmissionService.java` | existing | 提交字段复验、场景推导和审批摘要 | 当前仍硬性要求至少一个交付单元并摘要 deliveries |
| `server/src/modules/release/src/main/java/com/ccb/release/production/model/ProductionModels.java` | existing | 投产基线和生产版本条目契约 | 当前条目版本号非空且无项类型 |
| `server/src/modules/release/src/main/java/com/ccb/release/production/persistence/ReleaseProductionStore.java` | existing | 候选去重、结果维护、当前生产和历史查询 | 当前使用物理子系统和交付单元编码 |
| `server/src/modules/release/src/main/java/com/ccb/release/production/service/ReleaseProductionService.java` | existing | 准出申请转候选、投产结果和历史授权编排 | 当前按申请 deliveries 遍历 |
| `server/src/modules/release/src/main/java/com/ccb/release/production/web/ReleaseProductionController.java` | existing | 生产版本历史 HTTP 入口 | 当前历史路由无法区分多个 `FILE` 路径 |
| `server/src/modules/release/src/main/java/com/ccb/release/reporting/model/ReleaseAnalyticsModels.java` | existing | 统计摘要契约 | 当前只返回交付单元总数 |
| `server/src/modules/release/src/main/java/com/ccb/release/reporting/persistence/ReleaseAnalyticsStore.java` | existing | 申请、交付内容和投产结果数据库聚合 | 当前按 `delivery_unit_code` 去重 |
| `server/src/modules/release/src/main/java/com/ccb/release/reporting/service/ReleaseAnalyticsService.java` | existing | 统计查询校验和服务边界 | 保持接口编排位置不变 |
| `web/src/api/release.ts` | existing | 发布模块前端 DTO 和 HTTP 调用 | 当前无文件介质、项类型或按条目历史接口 |
| `web/src/modules/release/components/ReleaseApplicationDrawer.vue` | existing | 混合申请编辑、动态路径和即时校验 | 当前只支持普通交付单元 |
| `web/src/modules/release/ReleaseManagementPrototype.vue` | existing | 申请保存、冲突确认和页面数据编排 | 当前透传 `ReleaseApplicationWrite` 并维持冲突令牌 |
| `web/src/modules/release/ReleaseApplicationDetailPage.vue` | existing | 完整业务详情和审批判断依据 | 当前制品登记只展示 deliveries |
| `web/src/modules/release/components/ReleaseApplicationView.vue` | existing | 申请列表摘要 | 当前数量只统计普通交付单元 |
| `web/src/modules/release/components/ReleaseConflictDialog.vue` | existing | 冲突历史事实和用户动作 | 当前只展示版本变化 |
| `web/src/modules/release/components/ReleaseBaselineView.vue` | existing | 投产候选搜索、展示和结果维护 | 当前所有行都要求版本号 |
| `web/src/modules/release/components/ReleaseCurrentProductionView.vue` | existing | 当前生产记录和历史对话框 | 当前历史按交付单元编码查询 |
| `web/src/modules/release/components/ReleaseAnalyticsView.vue` | existing | 统计摘要、图表和下钻 | 当前文案和数量只表达交付单元 |
| `web/src/modules/release/release-prototype.css` | existing | 发布模块响应式表单和交付项样式 | 新增动态路径行需复用语义变量 |

## 任务依赖图与并行策略

```text
T1 数据模型与迁移
  -> T2 申请、冲突与审批摘要
      -> T3 投产基线与生产历史
          -> T4 申请端前端流程
              -> T5 下游展示与统计
                  -> T6 真实迁移、接口和浏览器验收
```

计划按串行执行。虽然部分前后端文件理论上可并行，但当前工作区已有大量未提交改动，且 T2-T5 共享 DTO、项键和显示契约；串行采样可减少接口版本错配和误覆盖风险。

## 需求覆盖表

| 需求 | 任务 |
| --- | --- |
| R1 交付内容类型 | T1, T2, T4 |
| R2 多文件路径 | T1, T2, T4 |
| R3 固定 FILE 映射 | T1, T2 |
| R4 路径业务身份 | T1, T2, T3 |
| R5 冲突与追加 | T1, T2, T4 |
| R6 独立投产基线 | T1, T3, T5 |
| R7 生产版本 | T1, T3, T5 |
| R8 兼容、权限和审计 | T1, T2, T3, T6 |
| R9 详情与统计 | T4, T5 |
| R10 响应式与完整流程 | T4, T5, T6 |

### T1：建立统一交付项数据契约与兼容迁移

**需求映射：** R1, R2, R3, R4, R6, R7, R8

**前置任务：** 无

**已证实输入事实：**

- V39 的活动申请明细唯一键为 `(tenant_id, application_id, delivery_unit_code, application_revision)`。
- V38 的投产来源唯一键和候选/当前索引只包含 `delivery_unit_code`。
- `rel_application_relation` 也以 `delivery_unit_code` 区分追加关系，多条固定 `FILE` 会发生冲突。
- `ReleaseApplicationModels.ArtifactType` 当前只有 `IMAGE`、`BINARY`，`DeliverySnapshot` 无项类型和路径。

**文件：**

- 新建：`server/src/platform/infrastructure/src/main/resources/db/migration/V46__release_file_media.sql`
- 修改：`server/src/modules/release/src/main/java/com/ccb/release/application/model/ReleaseApplicationModels.java`
- 修改：`server/src/modules/release/src/main/java/com/ccb/release/application/persistence/ReleaseApplicationStore.java`
- 测试：`server/src/modules/release/src/test/java/com/ccb/release/ReleaseSchemaContractTest.java`
- 测试：`server/src/modules/release/src/test/java/com/ccb/release/application/service/ReleaseApplicationPersistenceContractTest.java`

**接口：**

- 消费：V38/V39 的 `rel_application_delivery`、`rel_application_relation`、`rel_production_entry` 结构。
- 产出：
  - `enum DeliveryItemType { DELIVERY_UNIT, FILE_MEDIA }`
  - `enum ArtifactType { IMAGE, BINARY, FILE }`
  - `record FileMediaInput(String filePath)`
  - `record FileMediaSnapshot(long id, String filePath)`
  - 内部 `DeliverySnapshot` 增加 `itemType`、`filePath`、`itemKey`，并保留旧参数委托构造器用于未改动测试和调用方兼容。
  - `CreateRequest`、`UpdateRequest` 增加 `List<FileMediaInput> fileMedia`；`Response` 增加 `List<FileMediaSnapshot> fileMedia`。
  - 存量普通项键 `UNIT:<delivery_unit_code>`；文件项键 `FILE:<lowercase SHA-256 hex>`。

- [ ] **步骤 1：先建立 V46 失败契约**

  在 `ReleaseSchemaContractTest` 新增测试，读取 `db/migration/V46__release_file_media.sql`，断言迁移同时处理三张表、包含 `item_type`、`file_path`、`item_key`、`artifact_version` 可空修改、存量 `UNIT:` 回填、新唯一键和新索引；断言不包含 `DELETE FROM rel_`。

  在 `ReleaseApplicationPersistenceContractTest` 增加 SQL 契约断言：活动子项写入和读取包含 `item_type, file_path, item_key`，冲突查询使用 `d.item_key IN`，追加关系使用 `item_key`。

- [ ] **步骤 2：运行基准检查并确认当前失败**

  运行：

  ```bash
  env JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -pl :ccb-release -am \
    -Dtest=ReleaseSchemaContractTest,ReleaseApplicationPersistenceContractTest \
    -Dsurefire.failIfNoSpecifiedTests=false test
  ```

  预期：失败，原因是 V46 不存在及模型/SQL 尚无文件介质字段；不得出现无关模块编译失败。

  证据：退出码、失败测试名和首个契约断言。

- [ ] **步骤 3：实现追加迁移**

  V46 对 `rel_application_delivery`：

  - 添加 `item_type VARCHAR(24)`、`file_path VARCHAR(1024)`、`item_key VARCHAR(128)`；
  - 存量回填 `DELIVERY_UNIT` 和 `CONCAT('UNIT:', delivery_unit_code)` 后改为非空；
  - `artifact_version` 改为可空；
  - 唯一键改为 `(tenant_id, application_id, item_key, application_revision)`；
  - 冲突索引改为 `(tenant_id, item_key, active, application_id)`。

  V46 对 `rel_application_relation` 添加 `item_key`、`file_path`，回填普通项键，唯一键改为 `(tenant_id, application_id, related_application_id, item_key, relation_type)`。

  V46 对 `rel_production_entry` 添加并回填相同三字段，允许文件项版本为空，来源唯一键改用 `item_key`，候选和当前索引改用 `(tenant_id, window_id/subsystem_code, item_key, ...)`。

  对三张表添加可由 MySQL 8.4 执行的检查约束：普通项版本非空且文件路径为空；文件项固定 `FILE`、版本为空且路径非空。

- [ ] **步骤 4：扩展模型和申请持久化**

  在模型中增加上述类型。Store 的明细读写使用统一内部 `DeliverySnapshot`，普通和文件项都保存真实 `item_key`；读取存量和新记录时按 `item_type` 映射。冲突检索参数从 `deliveryCodes` 改为 `itemKeys`。追加关系写入同时保存 `delivery_unit_code`、`item_key`、`file_path`。

- [ ] **步骤 5：运行局部与模型编译回归**

  运行 T1 聚焦测试命令。

  预期：两类测试通过，0 个失败；V46 契约包含回填和新键，现有普通明细 SQL 契约仍通过。

  追加运行：

  ```bash
  env JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -pl :ccb-release -am -DskipTests compile
  ```

  预期：所有依赖 `DeliverySnapshot` 的生产代码和测试源码可编译；旧构造器兼容未改动调用点。

- [ ] **步骤 6：建立限定路径检查点**

  运行：

  ```bash
  git diff --check -- \
    server/src/platform/infrastructure/src/main/resources/db/migration/V46__release_file_media.sql \
    server/src/modules/release/src/main/java/com/ccb/release/application/model/ReleaseApplicationModels.java \
    server/src/modules/release/src/main/java/com/ccb/release/application/persistence/ReleaseApplicationStore.java \
    server/src/modules/release/src/test/java/com/ccb/release/ReleaseSchemaContractTest.java \
    server/src/modules/release/src/test/java/com/ccb/release/application/service/ReleaseApplicationPersistenceContractTest.java
  ```

  预期：无空白错误；不执行 Git 提交。

**验收检查：** 存量普通项可回填；同申请同修订允许多个不同文件项键；关系和投产表不再以 `FILE` 编码错误去重；文件版本可空仅由数据库约束允许于文件项。

**回滚：** 代码文件可按限定 diff 回退；V46 未执行前可删除候选迁移。V46 执行后禁止编辑或降版，只能在确认无文件介质数据时通过后续补偿迁移恢复旧键。

**停止条件：** 存量数据存在无法生成唯一普通项键的重复行；MySQL 8.4 拒绝检查约束；V46 与 origin/main 新增迁移版本冲突；必须修改 V38/V39 才能继续。

**升级条件：** 需要改变固定 `FILE` 映射、路径规范化或历史数据含义；需要删除存量记录；迁移预计锁表时间不可接受。

### T2：实现路径校验、冲突、追加与审批摘要

**需求映射：** R1, R2, R3, R4, R5, R8

**前置任务：** T1

**已证实输入事实：**

- 创建和更新当前强制 `deliveries` 非空，并按交付单元编码去重。
- 冲突令牌当前包含交付单元编码和版本；追加判定仅检查历史准出版本变化。
- `ReleaseSubmissionService` 再次强制交付单元非空，并将申请交付明细纳入审批摘要。

**文件：**

- 修改：`server/src/modules/release/src/main/java/com/ccb/release/application/service/ReleaseApplicationService.java`
- 修改：`server/src/modules/release/src/main/java/com/ccb/release/application/service/ReleaseSubmissionService.java`
- 测试：`server/src/modules/release/src/test/java/com/ccb/release/application/service/ReleaseApplicationServiceTest.java`
- 测试：`server/src/modules/release/src/test/java/com/ccb/release/application/service/ReleaseSubmissionServiceTest.java`

**接口：**

- 消费：T1 的 `FileMediaInput`、统一 `DeliverySnapshot`、`DeliveryItemType` 和 `itemKey` Store 查询。
- 产出：
  - `normalizeFilePath(String)`：trim、非空、最大 1024、拒绝 U+0000-U+001F 与 U+007F。
  - `fileItemKey(String)`：UTF-8 SHA-256 小写十六进制，前缀 `FILE:`。
  - 普通项键 `UNIT:<code>`。
  - 冲突令牌按 `itemKey` 排序，普通项附版本，文件项附规范化路径。
  - 文件路径历史准出即构成追加事实；普通项继续要求同编码版本变化。
  - 审批摘要和 workflow variables 包含稳定排序后的文件路径。

- [ ] **步骤 1：建立应用服务失败测试**

  新增测试覆盖：仅文件申请成功、混合申请成功、两类均空失败、路径 trim、重复路径失败、1025 字符失败、控制字符失败、服务端生成固定 `FILE` 字段、同窗口同路径冲突、不同路径不冲突、历史准出同路径标记追加、冲突令牌在事实变化后变化。

  Submission 测试覆盖：仅文件申请可提交；摘要对路径稳定排序；文件项没有版本号；既有普通交付摘要结果保持兼容。

- [ ] **步骤 2：运行测试并确认当前失败**

  ```bash
  env JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -pl :ccb-release -am \
    -Dtest=ReleaseApplicationServiceTest,ReleaseSubmissionServiceTest \
    -Dsurefire.failIfNoSpecifiedTests=false test
  ```

  预期：新增文件介质测试失败，现有普通、应急、冲突确认和审批测试继续编译。

- [ ] **步骤 3：实现统一输入校验与响应拆分**

  `ReleaseApplicationService` 将普通 `deliveries` 和 `fileMedia` 转为统一内部项；至少一项非空。普通项继续校验唯一编码、`IMAGE/BINARY` 和非空无空格版本；文件项仅消费路径，生成固定字段和项键。`response()` 将内部项拆分回普通 `deliveries` 和路径 `fileMedia`。

- [ ] **步骤 4：改造冲突、追加关系和令牌**

  冲突检索统一传 `itemKey`。`versionChanges` 保持普通版本变化语义；文件项的追加关系保存 `item_key`、`file_path` 和“文件介质再次申请”原因，不伪造 previous/current version。冲突响应通过历史申请的 `fileMedia` 展示完整路径。令牌包含所有历史活动项键和对应版本/路径，保持排序稳定。

- [ ] **步骤 5：改造提交复验和审批摘要**

  `validateSubmissionFields` 改为至少一个统一交付项。`deriveScenario` 将已准出同路径视为追加。摘要和变量显式包含 `deliveryItems`，每项包含 `itemType`、普通编码/版本或文件路径；同一内容不因输入顺序变化导致摘要变化。

- [ ] **步骤 6：运行局部与发布模块回归**

  先运行 T2 聚焦测试，再运行：

  ```bash
  env JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -pl :ccb-release -am test
  ```

  预期：发布模块全部测试通过；普通、应急、附件、工作流绑定和冲突继续创建测试无回归。

**验收检查：** 三种申请组合均可持久化；非法路径零写入；同窗口同路径冲突；明确继续创建后令牌可通过；历史事实变化会拒绝旧令牌；审批摘要含路径且稳定。

**回滚：** 回退 T2 服务和测试文件，保留 T1 数据字段但不创建文件业务数据。

**停止条件：** 现有工作流摘要契约禁止增加交付项；追加流程只能依赖版本变化且不能表达文件重复；冲突历史必须改变外部动作协议。

**升级条件：** 用户需要路径大小写或斜杠归一化；文件重复不应形成追加；审批页面要求额外文件属性。

### T3：实现文件路径独立投产基线与生产历史

**需求映射：** R4, R6, R7, R8

**前置任务：** T2

**已证实输入事实：**

- 准出处理当前遍历 `Application.deliveries()`，按窗口、申请和 `delivery_unit_code` 查找来源记录。
- 候选替换和当前生产查询当前按物理子系统与交付单元编码判断身份。
- 生产版本历史 HTTP 路由以 `{subsystemCode}/{deliveryUnitCode}` 查询，多条 `FILE` 无法区分。

**文件：**

- 修改：`server/src/modules/release/src/main/java/com/ccb/release/production/model/ProductionModels.java`
- 修改：`server/src/modules/release/src/main/java/com/ccb/release/production/persistence/ReleaseProductionStore.java`
- 修改：`server/src/modules/release/src/main/java/com/ccb/release/production/service/ReleaseProductionService.java`
- 修改：`server/src/modules/release/src/main/java/com/ccb/release/production/web/ReleaseProductionController.java`
- 测试：`server/src/modules/release/src/test/java/com/ccb/release/production/service/ReleaseProductionServiceTest.java`
- 测试：`server/src/modules/release/src/test/java/com/ccb/release/production/web/ReleaseProductionControllerSecurityTest.java`

**接口：**

- 消费：T2 输出的统一 `DeliverySnapshot.itemType/filePath/itemKey`。
- 产出：
  - `ProductionModels.Entry` 增加 `itemType`、`filePath`、`itemKey`，`artifactVersion` 可空；提供旧参数委托构造器。
  - `ReleaseProductionStore.findBySource(..., String itemKey)`。
  - `ReleaseProductionStore.findLatestCandidate(..., String itemKey)`。
  - `ReleaseProductionStore.findHistory(long tenantId, String subsystemCode, String itemKey)`。
  - 新接口 `GET /api/release/production-versions/entries/{entryId}/history`；保留原普通交付单元历史接口兼容旧调用。

- [ ] **步骤 1：建立投产失败测试**

  新增测试：同一申请两条文件路径生成两条候选；同路径新准出替换旧活动候选；不同路径互不替换；批量结果可同时处理文件和普通项；成功文件项进入 current versions；结果回改后上一成功记录重新成为当前；按 entryId 历史只返回同一物理子系统和项键；跨租户 entryId 返回无权/不存在。

  Controller 安全测试断言新历史接口要求 `release:production-version:view` 或管理员权限。

- [ ] **步骤 2：运行测试并确认当前失败**

  ```bash
  env JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -pl :ccb-release -am \
    -Dtest=ReleaseProductionServiceTest,ReleaseProductionControllerSecurityTest \
    -Dsurefire.failIfNoSpecifiedTests=false test
  ```

  预期：新增项键和按条目历史测试失败，现有窗口结束门禁和批量维护测试仍编译。

- [ ] **步骤 3：扩展条目映射和候选同步**

  Store 的列、RowMapper、insert、来源查询、候选失活和 current/history SQL 统一使用 `item_key`。Service 准出同步对每个统一交付项创建独立 Entry，文件版本保持 `null`，路径原文透传。

- [ ] **步骤 4：增加按 entryId 历史查询**

  Service 先按 `entryId + tenantId` 查询锚点，再用锚点的 `subsystemCode + itemKey` 查询历史。Controller 暴露新接口。原 `{subsystemCode}/{deliveryUnitCode}` 仅保留普通项兼容，不用于文件介质。

- [ ] **步骤 5：运行投产与模块回归**

  运行 T3 聚焦测试及 `mvn -pl :ccb-release -am test`。

  预期：文件项独立候选、维护、current 和 history 全部通过；现有基线时间门禁、乐观锁、结果日志和权限测试通过。

**验收检查：** 多路径绝不共享活动候选或结果；文件项版本始终为空；当前生产按物理子系统和路径回算；历史查询不暴露跨租户条目。

**回滚：** 回退 T3 Java 代码；V46 字段保留。存在文件投产条目后禁止回退到按交付单元编码查询的旧服务。

**停止条件：** current production SQL 无法在不改变既有普通项排序语义时按项键回算；新旧历史路由发生 Spring 映射冲突；结果日志需要新增文件专属表。

**升级条件：** 需要按项目而非物理子系统隔离路径生产身份；同一路径在不同物理子系统应被视为同一生产对象；需要路径级部分审批。

### T4：交付混合申请编辑、详情和冲突交互

**需求映射：** R1, R2, R5, R9, R10

**前置任务：** T3

**已证实输入事实：**

- 申请抽屉当前只维护 `selectedUnitCodes` 和 `deliveries`，并强制至少一个交付单元。
- 页面编排已正确保存冲突令牌并在“仍创建新申请”后直接提交，新增路径不得破坏该流程。
- 详情、列表和冲突弹窗只读取 `deliveries`。

**文件：**

- 修改：`web/src/api/release.ts`
- 修改：`web/src/modules/release/components/ReleaseApplicationDrawer.vue`
- 修改：`web/src/modules/release/ReleaseManagementPrototype.vue`
- 修改：`web/src/modules/release/ReleaseApplicationDetailPage.vue`
- 修改：`web/src/modules/release/components/ReleaseApplicationView.vue`
- 修改：`web/src/modules/release/components/ReleaseConflictDialog.vue`
- 修改：`web/src/modules/release/release-prototype.css`

**接口：**

- 消费：T2/T3 的 `fileMedia`、`itemType`、文件路径和 existing conflict token 协议。
- 产出：
  - `type ReleaseItemTypeCode = 'DELIVERY_UNIT' | 'FILE_MEDIA'`
  - `interface ReleaseFileMediaDto { id: number; filePath: string }`
  - `interface ReleaseFileMediaInput { filePath: string }`
  - `ReleaseApplicationDto.fileMedia` 和 `ReleaseApplicationWrite.fileMedia`
  - `ArtifactTypeCode` 扩展 `FILE` 供投产 DTO 使用；普通 `ReleaseDeliveryDto.artifactType` 使用单独 `DeliveryArtifactTypeCode = 'IMAGE' | 'BINARY'`。

- [ ] **步骤 1：扩展 TypeScript 契约并建立构建失败信号**

  先修改类型定义和组件模板引用 `fileMedia`、`itemType`，运行：

  ```bash
  npm --prefix web run build
  ```

  预期：在抽屉状态、初始化、保存和关联页面尚未完成时出现具体 TypeScript 属性或可空版本错误，不允许出现依赖安装错误。

- [ ] **步骤 2：实现交付内容多选和动态路径行**

  使用 `el-checkbox-group` 提供“交付单元”“文件介质”，默认 `DELIVERY_UNIT`。保留物理子系统必填。文件区使用稳定本地 key 的动态行和图标删除按钮，提供“添加文件路径”命令；每条输入最大 1024。

  取消已填写类型时使用 `ElMessageBox.confirm` 明确会清空的数据；取消确认则恢复选中状态。至少一种类型且对应至少一项，路径 trim 后去重并拒绝控制字符。保存 payload 分别提交 `deliveries` 和 `fileMedia`。

- [ ] **步骤 3：恢复编辑和保护冲突提交状态**

  `initialize()` 根据响应两组数组恢复选项和动态行。物理子系统切换清空普通交付单元，但文件路径继续归属新系统前必须二次确认；确认后保留路径并改变系统归属，取消则恢复原系统。`ReleaseManagementPrototype` 的 pending submission 和 conflict token 继续整体保存新 payload，不重复弹冲突。

- [ ] **步骤 4：改造详情、列表和冲突展示**

  详情“制品登记”显示总交付内容数，普通项显示版本，文件项显示完整路径和“无版本号”。列表摘要显示“X 个交付单元 · Y 条文件介质”。冲突弹窗增加文件介质路径区，版本变化只显示普通项；文件重复使用“文件路径再次申请”说明。

- [ ] **步骤 5：实现桌面和移动样式**

  在 `release-prototype.css` 增加交付内容选择、文件路径动态行和长路径换行样式。`760px` 以下单列排列，输入框 `min-width: 0`，删除按钮固定尺寸，抽屉操作区保持可达；不新增页面级横向滚动。

- [ ] **步骤 6：运行前端构建和静态检查**

  ```bash
  npm --prefix web run build
  git diff --check -- web/src/api/release.ts web/src/modules/release
  ```

  预期：TypeScript 和 Vite 构建成功；无空白错误；普通申请保存与冲突处理代码仍存在且无重复提交分支。

**验收检查：** 三种内容组合可编辑；取消类型不会静默丢数据；同申请重复路径在提交前可见；长路径不遮挡删除和底部操作；冲突继续创建只确认一次。

**回滚：** 回退 T4 前端文件即可恢复旧 UI；后端兼容缺少 `fileMedia` 的旧请求。

**停止条件：** Element Plus 多选无法可靠撤销清空确认；现有抽屉销毁行为导致编辑恢复丢失；必须修改公共 UI 组件才能适配。

**升级条件：** 用户要求拖拽排序、批量粘贴路径、目录选择器或实际文件上传；移动端需要独立页面替代抽屉。

### T5：改造基线、生产版本和统计展示

**需求映射：** R6, R7, R9, R10

**前置任务：** T4

**已证实输入事实：**

- 基线和生产版本搜索、列、对话框当前直接显示 `artifactVersion`。
- 生产历史前端调用旧的 subsystem/deliveryUnit 路由。
- 统计摘要按 `delivery_unit_code` 计数，UI 文案只表达“交付单元”。

**文件：**

- 修改：`server/src/modules/release/src/main/java/com/ccb/release/reporting/model/ReleaseAnalyticsModels.java`
- 修改：`server/src/modules/release/src/main/java/com/ccb/release/reporting/persistence/ReleaseAnalyticsStore.java`
- 修改：`server/src/modules/release/src/main/java/com/ccb/release/reporting/service/ReleaseAnalyticsService.java`
- 测试：`server/src/modules/release/src/test/java/com/ccb/release/reporting/persistence/ReleaseAnalyticsStoreTest.java`
- 测试：`server/src/modules/release/src/test/java/com/ccb/release/reporting/service/ReleaseAnalyticsServiceTest.java`
- 修改：`web/src/api/release.ts`
- 修改：`web/src/modules/release/components/ReleaseBaselineView.vue`
- 修改：`web/src/modules/release/components/ReleaseCurrentProductionView.vue`
- 修改：`web/src/modules/release/components/ReleaseAnalyticsView.vue`
- 修改：`web/src/modules/release/release-prototype.css`

**接口：**

- 消费：T3 `ProductionEntryDto.itemType/filePath/itemKey` 和按 entryId 历史 API。
- 产出：
  - `ProductionEntryDto.artifactVersion?: string`。
  - `getProductionVersionHistory(entryId: number)` 调用 `/release/production-versions/entries/{entryId}/history`。
  - `ReleaseAnalyticsModels.Summary` 增加 `fileMediaCount`，`deliveryUnitCount` 只统计 `DELIVERY_UNIT`。

- [ ] **步骤 1：建立统计失败测试**

  Store 测试断言交付单元数量使用 `item_type='DELIVERY_UNIT'` 和 `item_key`，文件数量使用 `item_type='FILE_MEDIA'`，生产结果仍按每个投产项计数。Service 测试断言新增字段透传且现有维度校验不变。

- [ ] **步骤 2：运行统计测试并确认失败**

  ```bash
  env JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -pl :ccb-release -am \
    -Dtest=ReleaseAnalyticsStoreTest,ReleaseAnalyticsServiceTest \
    -Dsurefire.failIfNoSpecifiedTests=false test
  ```

  预期：新增 `fileMediaCount` 和 item_type SQL 断言失败。

- [ ] **步骤 3：实现后端统计区分**

  Summary 分别返回普通交付单元数和文件介质数。Store 使用活动申请明细的 `item_type` 与 `item_key` 计数，不使用固定 `FILE` 编码去重。下钻仍以申请为行，避免一张混合申请被重复分页；必要时增加普通/文件数量相关子查询字段供列表展示。

- [ ] **步骤 4：改造基线和生产版本显示**

  基线文件项的主字段显示路径，版本列显示“无版本号”，搜索关键字包含路径，结果维护标题使用路径。普通项显示保持不变。

  生产版本文件项显示路径、`FILE` 标签和“无版本号”；历史对话框按 entryId 加载，并在每条文件历史中显示路径和来源申请。搜索和筛选支持 `FILE`。

- [ ] **步骤 5：改造统计文案和摘要**

  统计卡片将“交付单元”信息改为“交付内容”，同时显示 `deliveryUnitCount` 个交付单元与 `fileMediaCount` 条文件介质；投产结果文案改为“交付内容结果”。保持图表尺寸和下钻分页稳定。

- [ ] **步骤 6：运行后端、前端和发布模块回归**

  ```bash
  env JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -pl :ccb-release -am test
  npm --prefix web run build
  ```

  预期：发布模块测试和前端构建均通过；无空版本渲染、TypeScript 可空错误或历史接口 404。

**验收检查：** 文件路径在基线和生产版本可检索；版本位置明确显示“无版本号”；历史按项键隔离；统计普通/文件数量准确且申请下钻不重复分页。

**回滚：** 回退 T5 报表和前端展示文件；存在文件投产数据时不能回退生产版本前端到旧 history 路由。

**停止条件：** 统计分页无法在不改变现有申请级语义下区分文件数量；历史 API 返回同路径跨子系统记录；文件项导致批量维护选择键重复。

**升级条件：** 用户要求按路径、制品类型或文件介质单独图表下钻；需要导出能力或历史路径改名追踪。

### T6：执行迁移、真实业务流与响应式收敛验收

**需求映射：** R8, R9, R10，并集成验收 R1-R7

**前置任务：** T5

**已证实输入事实：**

- 本地基础容器 `rddmp-local-mysql`、MinIO 和 kkFileView 已运行。
- 当前后端使用 local profile 和隔离数据库，前端使用 `127.0.0.1:5173`。
- 当前人工业务数据要求 `MOCK_DATA_ENABLED=false`。

**文件：**

- 测试与证据：`.ai-control/requirements/req-20260819-039-release-file-media/execution-T6.json`
- 观测：`.ai-control/requirements/req-20260819-039-release-file-media/observation-T6.json`
- 收敛：`.ai-control/requirements/req-20260819-039-release-file-media/convergence.json`

**接口：**

- 消费：T1-T5 的 V46、后端 API 和前端页面。
- 产出：Flyway V46 已执行、真实混合申请完整业务证据、桌面/手机浏览器证据和最终残余风险。

- [ ] **步骤 1：运行静态、治理和全量构建检查**

  ```bash
  node scripts/check-development-entry.mjs --require-plugin
  node scripts/check-all-governance.mjs
  git diff --check
  env JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn test
  npm --prefix web run build
  ```

  预期：除已记录的 `rokey` 分支命名例外外，治理、全量后端测试、前端构建和 diff 检查通过，0 个测试失败。

- [ ] **步骤 2：在迁移前创建本地数据库备份点**

  停止当前后端，加载 `.env` 但不输出配置值，使用当前隔离数据库用户创建仅限本地的压缩前 SQL 备份：

  ```bash
  set -a; source ./.env; set +a
  DB_NAME=${DB_URL%%\?*}; DB_NAME=${DB_NAME##*/}
  docker exec -e MYSQL_PWD="$DB_PASSWORD" rddmp-local-mysql \
    mysqldump -u"$DB_USERNAME" --single-transaction --no-tablespaces --skip-triggers "$DB_NAME" \
    > /private/tmp/rddmp-req039-before-v46.sql
  test -s /private/tmp/rddmp-req039-before-v46.sql
  ```

  预期：备份文件非空，命令输出不包含密码或业务数据正文。若当前用户没有 dump 权限，停止迁移并升级，不以无备份方式继续。

- [ ] **步骤 3：构建并启动新后端执行 V46**

  ```bash
  export JAVA_HOME=$(/usr/libexec/java_home -v 17)
  mvn -pl :ccb-boot -am package -DskipTests
  set -a; source ./.env; set +a
  export MOCK_DATA_ENABLED=false
  exec "$JAVA_HOME/bin/java" -jar server/src/platform/boot/target/ccb-boot-0.1.0-SNAPSHOT.jar \
    --spring.profiles.active=local
  ```

  预期：Flyway 验证并迁移到 V46；后端在 8080 启动；日志无 checksum、唯一键、回填或检查约束错误。

  健康检查：

  ```bash
  curl -fsS http://127.0.0.1:8080/actuator/health
  ```

  预期：`{"status":"UP"}`。

- [ ] **步骤 4：启动前端并验证资源可达**

  ```bash
  npm --prefix web run dev -- --host 127.0.0.1
  curl -fsSI http://127.0.0.1:5173/
  ```

  预期：Vite 监听 `127.0.0.1:5173`，SPA 返回 200。

- [ ] **步骤 5：执行真实 API 业务流**

  使用测试账号和真实后端接口：

  1. 创建包含一个普通交付单元和两条文件路径的草稿；
  2. 查询详情确认固定 `FILE`、空版本和路径原文；
  3. 同窗口创建相同路径，确认冲突事实和三种动作；
  4. 选择继续创建并提交，确认不重复弹窗；
  5. 由有权限审批人从申请详情审批至“制品准出”；
  6. 等窗口投产结束条件满足后，在基线中分别维护两条路径；
  7. 将一条设为成功，查询生产版本和历史；再回改并验证历史成功回算；
  8. 查询统计确认普通交付单元和文件介质数量。

  预期：所有状态由后端真实数据驱动，申请、审批、基线、生产版本和统计事实一致，无 Mock 业务行写入。

- [ ] **步骤 6：执行浏览器桌面与移动验收**

  路由：`/release/applications`、目标申请详情、`/release/production-baseline`、`/release/production-versions`、`/release/analytics`。

  视口：桌面 `1440x900`、`1280x800`，手机 `375x812`、`390x844`、`430x932`。

  检查：交付内容多选、动态路径增删、清空确认、长路径换行、抽屉底部按钮、详情审批依据、冲突继续创建、基线维护、生产历史、统计文案、浅色/深色主题、控制台错误、失败/加载/空状态。

  像素与溢出信号：`document.documentElement.scrollWidth <= window.innerWidth`；按钮文字不截断；文件路径不覆盖相邻操作。

- [ ] **步骤 7：范围与收敛检查**

  ```bash
  node scripts/check-codex-scope.mjs \
    --scope docs/requirements/REQ-20260819-039-release-file-media/codex-task-scope.yaml \
    --working-tree
  git status --short
  ```

  预期：范围检查只报告已批准的 `rokey` 分支命名例外，不出现本任务新增越界文件；所有已存在的其他用户改动保持原样。

**验收检查：** R1-R10 均有测试或真实运行证据；Flyway 到 V46；健康端点和 SPA 200；真实混合申请可完成审批到投产；规定视口无溢出；无未关闭高优先级反馈。

**回滚：** 前后端停止新进程并恢复上一构建。若 V46 后尚无文件介质数据，可使用备份恢复隔离数据库；若已有文件介质数据，保留新结构并通过后续补偿迁移修复，不启动旧应用读取新数据。

**停止条件：** 无可用迁移前备份；V46 启动失败；真实 API 状态不一致；跨租户数据可见；文件路径触发服务器文件访问；任一手机视口核心操作不可达；全量测试失败。

**升级条件：** 需要删除或修改人工测试数据；需要新权限或工作流定义；需要改变已确认路径身份；无法在当前 `rokey` 工作区区分本任务与其他人的同文件改动。

## 集成检查

| 完成任务 | 命令/传感器 | 预期 |
| --- | --- | --- |
| T1 | Schema + persistence contract tests | V46、回填、项键和 SQL 契约通过 |
| T2 | Application + submission tests | 三种申请、校验、冲突、追加和摘要通过 |
| T3 | Production + controller security tests | 独立候选、结果、current、history 和权限通过 |
| T4 | `npm --prefix web run build` | 申请编辑、详情、冲突 TypeScript 契约通过 |
| T5 | release module tests + frontend build | 下游显示、统计和历史 API 无回归 |
| T6 | `mvn test`、浏览器、健康检查、治理 | 真实链路与规定视口收敛 |

## 控制模型种子

以下仅为 `hypotheses-only` 候选，必须由 `$model-engineering-system` 验证：

- 被控边界候选：申请交付项输入与修订、冲突身份、审批摘要、投产候选、结果维护、生产历史、统计和关联前端视图。
- 状态变量候选：`item_type`、`file_path`、`item_key`、`artifact_version`、申请状态、冲突令牌、候选活动状态、投产结果、当前成功记录。
- 接口候选：申请 create/update/detail/conflicts/submit、生产 baseline/update/current/history、统计 summary/drilldown、Vue API DTO。
- 传感器候选：Schema 契约测试、Service 单测、Controller 权限测试、SQL 断言、全量 Maven、Vite build、Flyway 日志、health、真实 API、浏览器尺寸和控制台。
- 执行器候选：V46 DDL、模型/Store/Service 修改、API DTO、申请抽屉和下游视图修改、服务重启。
- 扰动候选：脏工作区同文件变化、存量异常数据、origin/main 新迁移、MySQL 锁和检查约束、前端长路径、浏览器缓存、当前测试账号权限。
- 时延候选：全量 Maven、Flyway DDL、服务冷启动、审批流推进、通知刷新、浏览器动画。
- 假设候选：路径只作文本身份；trim 是唯一规范化；固定 `FILE` 不依赖主数据；当前隔离 DB 用户具备迁移和备份权限。

## 风险与用户批准

高风险动作：

1. V46 会修改含存量业务数据的三张表的列、唯一键和索引，执行前必须建立可验证备份点。
2. 当前 `rokey` 分支不符合仓库分支规范且有大量未提交改动；不得重置、切换或自动提交。
3. 文件介质数据一旦写入，旧应用无法正确区分多条固定 `FILE`，不能直接回退旧后端。
4. 新增按 entryId 历史接口必须保持租户和查看权限校验，旧普通历史接口保留兼容。

用户已明确批准本计划，允许追加 V46、在建立备份后迁移现有隔离数据库，并继续使用当前 `rokey` 分支。交接包据此标记为 `approved` 并导入 control-engineering。
