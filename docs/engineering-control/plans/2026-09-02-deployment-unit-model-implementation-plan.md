# 部署单元名称、类型与关联模型实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

## 状态与来源

- 计划修订：1
- 设计修订：1
- 设计文档：`docs/engineering-control/designs/2026-09-02-deployment-unit-model-design.md`
- 需求文档：`docs/requirements/REQ-20260901-057-deployment-unit-model/requirement.md`
- 任务范围：`docs/requirements/REQ-20260901-057-deployment-unit-model/codex-task-scope.yaml`
- 状态：可移交（用户于 2026-09-02 批准修订 1）

**目标：** 将部署单元原子切换为完整名称、应用/数据库/Web 类型和可搜索多选的双向结构化关联，并同步导入、资源申请、前端和模块契约。

**架构：** 使用追加 Flyway 迁移完成旧字段删除、早期开发数据规范化和双向关系/关系历史建表；后端以 `name + kind + relatedDeploymentUnitIds` 为唯一写契约，关系按规范化端点事务替换。前端使用单名称输入框和类型/标准后缀联动，模块内下游只依赖完整名称、`kind` 和 deploymentUnitId。

**技术栈：** Java 17、Spring Boot 3.4.4、JdbcTemplate、MySQL 8.4、Flyway、JUnit/AssertJ/Testcontainers、Vue 3、TypeScript、Element Plus、Vite。

## 全局约束

- 只修改 `codex-task-scope.yaml` 的 `writable_paths`；保护现有未跟踪的 T11 账本文件。
- 开发前重新执行 `node scripts/check-development-entry.mjs --require-plugin`，并扫描 V148 是否仍为空闲；冲突时停止并修订 scope。
- 只追加 `V148__refine_deployment_unit_model.sql`，不得修改 V1—V147。
- 不保留 MQ、PL、`shortName`、`relatedDeploymentUnitName`、`deploymentUnitType` 的新契约兼容入口。
- 名称服务端统一大写并满足 `^[A-Z0-9]+_[A-Z0-9]{1,8}$`；标准后缀 AP/DB/WB 必须与 APPLICATION/DATABASE/WEB 一致。
- 双向关系按 `(tenant_id, min(unitA,unitB), max(unitA,unitB))` 单行保存；拒绝自身、跨租户、非 ACTIVE 和不存在目标。
- 保持部署单元 code、物理归属、生命周期、网络分区、描述、备注、版本、权限、租户、乐观锁和审计职责。
- 早期开发数据允许规范化或重建，但不得把重建生产数据库作为回退方式。
- 前端复用共享 UI、Element Plus、语义主题和交付示范中心模式；不修改公共 UI。
- 每个任务完成局部验证和一个小提交检查点，不混入无关格式化或历史账本修复。

---

## 文件职责地图

| 路径 | 状态 | 职责与证据 |
| --- | --- | --- |
| `server/src/platform/infrastructure/src/main/resources/db/migration/V148__refine_deployment_unit_model.sql` | candidate-new | 追加模型切换、开发数据规范化、约束、关系和关系历史结构；当前最高迁移为 V147。 |
| `server/src/modules/architecture/src/main/java/com/ccb/architecture/model/DeploymentUnitModels.java` | existing | 部署单元枚举、主记录、版本、命令和查询类型。 |
| `server/src/modules/architecture/src/main/java/com/ccb/architecture/persistence/DeploymentUnitStore.java` | existing | 主记录、版本和新关系/历史的 JdbcTemplate 读写。 |
| `server/src/modules/architecture/src/main/java/com/ccb/architecture/service/DeploymentUnitService.java` | existing | 名称规则、类型映射、关系事务、版本、生命周期和视图投影。 |
| `server/src/modules/architecture/src/main/java/com/ccb/architecture/web/DeploymentUnitController.java` | existing | CRUD、版本和新增关联候选分页搜索 HTTP 契约。 |
| `server/src/modules/architecture/src/main/java/com/ccb/architecture/service/DeploymentUnitImportService.java` | existing | xlsx 模板、预览、确认与错误报告；移除简称列并采用新名称/类型。 |
| `server/src/modules/architecture/src/main/java/com/ccb/architecture/environment/model/EnvironmentResourceModels.java` | existing | 资源申请部署单元投影和明细响应，删除旧关联名称/登记类型字段。 |
| `server/src/modules/architecture/src/main/java/com/ccb/architecture/environment/persistence/EnvironmentResourceStore.java` | existing | 资源申请部署单元选项、明细快照和查询 SQL，改为完整名称与 kind。 |
| `server/src/modules/architecture/src/main/java/com/ccb/architecture/environment/service/EnvironmentResourceService.java` | existing | APPLICATION/DATABASE/WEB 到 AP/DB/WB 的资源字段分流。 |
| `server/src/modules/architecture/src/main/java/com/ccb/architecture/environment/web/EnvironmentResourceController.java` | existing | 资源申请选项和详情 DTO 适配。 |
| `server/src/modules/architecture/src/test/java/com/ccb/architecture/web/DeploymentUnitControllerTest.java` | candidate-new | view/manage 权限、请求响应字段和选项分页契约。 |
| `server/src/modules/architecture/src/test/java/com/ccb/architecture/service/DeploymentUnitServiceTest.java` | existing | 名称、标准/自定义后缀、关系目标校验、版本和生命周期单元测试。 |
| `server/src/modules/architecture/src/test/java/com/ccb/architecture/service/DeploymentUnitLifecycleMySqlTest.java` | existing | 真实 MySQL 唯一性、双向关系、重复、并发、停用和作废限制。 |
| `server/src/modules/architecture/src/test/java/com/ccb/architecture/service/DeploymentUnitImportMySqlTest.java` | existing | 新导入模板、预览、确认和错误报告。 |
| `server/src/modules/architecture/src/test/java/com/ccb/architecture/environment/service/EnvironmentResourceServiceTest.java` | existing | AP/DB/WB 三类资源分流和旧字段负向回归。 |
| `server/src/modules/architecture/src/test/java/com/ccb/architecture/repository/ArchitectureMigrationMySqlTest.java` | existing | 空库/增量 V124、列/约束/关系表和数据规范化验证。 |
| `web/src/modules/architecture/types.ts` | existing | 部署单元、关联选项、导入和资源申请 TypeScript 契约。 |
| `web/src/modules/architecture/api.ts` | existing | 部署单元 CRUD、版本、关联候选远程分页搜索和资源选项请求。 |
| `web/src/modules/architecture/DeploymentUnitPage.vue` | existing | 列表筛选、单名称输入、类型联动、远程多选和生命周期操作。 |
| `web/src/modules/architecture/components/DeploymentUnitDetailDrawer.vue` | existing | 完整名称、类型、双向关联和版本历史展示。 |
| `web/src/modules/architecture/DeploymentUnitImportPage.vue` | existing | 新模板说明、预览列和错误行展示。 |
| `web/src/modules/architecture/ResourceRequestPage.vue` | existing | 按 kind 展示/提交 DB 与 AP/WB 字段，不再显示旧字段。 |
| `web/src/modules/architecture/utils.ts` | existing | `WEB` 中文标签和共享部署单元类型显示。 |
| `web/src/modules/architecture/architecture.css` | existing | 名称输入醒目层级、远程多选标签和移动端弹层局部样式。 |
| `mock/mock-data.json` | existing | 虚构完整名称、三种类型和可复现双向关联演示数据。 |
| `docs/integration/architecture-module-contract.md` | existing | 新部署单元、搜索选项、导入、资源申请和破坏性字段删除契约。 |

## 任务依赖图与并行策略

```text
T1 后端/迁移原子切换
  -> T2 前端用户旅程切换
    -> T3 契约、Mock、全栈验收与交付证据
```

本计划不安排并行实施。旧字段是 Java 记录构造参数、数据库列、资源申请投影和 TypeScript 类型的共享契约，拆开并行会造成中间不可编译或运行时查询缺列。每个任务内部可并行执行互不写文件的只读扫描或验证命令，但不得并行修改共享契约。

## 需求覆盖表

| 需求 | 任务 |
| --- | --- |
| R1 删除简称和重复事实源 | T1、T2、T3 |
| R2 单名称输入与格式校验 | T1、T2 |
| R3 三种类型与标准/自定义后缀 | T1、T2 |
| R4 租户内 ACTIVE 搜索多选 | T1、T2 |
| R5 双向关系事务一致 | T1、T3 |
| R6 版本、历史、审计和下游同步 | T1、T2、T3 |
| R7 权限、租户和模块边界 | T1、T3 |
| R8 全状态与响应式体验 | T2、T3 |

### T1：后端、数据库与下游服务原子切换

**需求映射：** R1、R2、R3、R4、R5、R6、R7

**前置任务：** 无

**文件：**

- 新建：`server/src/platform/infrastructure/src/main/resources/db/migration/V148__refine_deployment_unit_model.sql`
- 新建：`server/src/modules/architecture/src/test/java/com/ccb/architecture/web/DeploymentUnitControllerTest.java`
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/model/DeploymentUnitModels.java`
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/persistence/DeploymentUnitStore.java`
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/service/DeploymentUnitService.java`
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/web/DeploymentUnitController.java`
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/service/DeploymentUnitImportService.java`
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/environment/model/EnvironmentResourceModels.java`
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/environment/persistence/EnvironmentResourceStore.java`
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/environment/service/EnvironmentResourceService.java`
- 修改：`server/src/modules/architecture/src/main/java/com/ccb/architecture/environment/web/EnvironmentResourceController.java`
- 测试：`server/src/modules/architecture/src/test/java/com/ccb/architecture/service/DeploymentUnitServiceTest.java`
- 测试：`server/src/modules/architecture/src/test/java/com/ccb/architecture/service/DeploymentUnitLifecycleMySqlTest.java`
- 测试：`server/src/modules/architecture/src/test/java/com/ccb/architecture/service/DeploymentUnitImportMySqlTest.java`
- 测试：`server/src/modules/architecture/src/test/java/com/ccb/architecture/environment/service/EnvironmentResourceServiceTest.java`
- 测试：`server/src/modules/architecture/src/test/java/com/ccb/architecture/repository/ArchitectureMigrationMySqlTest.java`

**接口：**

- 消费：现有认证用户、`architecture:deployment-unit:view/manage`、物理子系统和网络分区公开查询、`SystemOperationAudit`、`DeploymentUnitReferenceChecker`。
- 产出：
  - `DeploymentUnitKind = APPLICATION | DATABASE | WEB`。
  - `DeploymentUnitCommand(Long physicalSubsystemId, String name, String kind, List<Long> relatedDeploymentUnitIds, Long defaultNetworkZoneId, String description, String remark, Long rowVersion)`。
  - `RelatedDeploymentUnitView(long id, String code, String name, String kind, long physicalSubsystemId, String physicalSubsystemName, String status)`。
  - `DeploymentUnitView` 和 `DeploymentUnitVersionView` 删除旧三个字段；当前详情包含 `relatedDeploymentUnits`，版本只保留当时名称、类型、网络分区、描述、备注和发布信息。
  - `GET /api/architecture/deployment-units/options?keyword=&page=1&size=20&excludeId=` 返回 `PageResult<RelatedDeploymentUnitView>`，只含当前租户 ACTIVE 记录。
  - 资源申请内部类型映射：`APPLICATION -> AP`、`DATABASE -> DB`、`WEB -> WB`。

- [ ] **步骤 1：重新验证研发入口、scope 和迁移版本**

运行：

```powershell
node scripts/check-development-entry.mjs --require-plugin
node scripts/check-codex-scope.mjs --base HEAD --head HEAD --scope docs/requirements/REQ-20260901-057-deployment-unit-model/codex-task-scope.yaml
node scripts/check-flyway-migrations.mjs
Get-ChildItem server/src/platform/infrastructure/src/main/resources/db/migration -Filter 'V124__*.sql'
```

预期：前三项退出 0；V124 无现有文件。若 V124 已占用，停止并修订需求、scope、设计和计划中的迁移号。

证据：记录命令、退出码、当前最高迁移和工作区状态；不得把既有 T11 文件算入本需求。

- [ ] **步骤 2：先建立失败测试和旧字段基线**

在上述测试中加入可判别断言：

```java
assertThat(DeploymentUnitKind.values())
        .extracting(Enum::name)
        .containsExactly("APPLICATION", "DATABASE", "WEB");

assertThat(view.name()).isEqualTo("SMSLJ_AP");
assertThat(view.relatedDeploymentUnits())
        .extracting(RelatedDeploymentUnitView::id)
        .containsExactly(relatedId);
```

补充非法 `smslj`、`SMSLJ`、`SMSLJ_`、`SMSLJ_TOO_LONG9`、`SMSLJ_AP + DATABASE`、自关联、跨租户、INACTIVE、重复、并发解除/新增、有关联时作废测试；导入用“物理子系统编号、部署单元名称、部署单元类型、描述、备注”五列。

运行：

```powershell
mvn -pl :ccb-architecture -am -Dtest=DeploymentUnitServiceTest,DeploymentUnitLifecycleMySqlTest,DeploymentUnitImportMySqlTest,EnvironmentResourceServiceTest,ArchitectureMigrationMySqlTest,DeploymentUnitControllerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

预期：当前代码编译失败或测试失败，失败明确指向 `WEB`、新命令字段、关系视图、选项接口或 V124 尚不存在，而不是环境或无关测试错误。

证据：保存退出码和首个相关失败断言。

- [ ] **步骤 3：追加 V124 并实现持久化契约**

迁移按以下顺序执行：

1. 将既有 `MQ` 规范化为 `WEB`；对不符合新格式或重复的早期部署单元，用永久唯一 `code` 加 `_AP/_DB/_WB` 形成可执行迁移的完整名称，并同步版本行。
2. 删除主表和版本表的 `short_name`、`related_deployment_unit_name`、`deployment_unit_type`；删除资源申请明细中只复制这些旧事实的列。
3. 将主表名称唯一键改为 `(tenant_id,name)`，为主表和版本表增加 APPLICATION/DATABASE/WEB 与名称格式检查。
4. 创建 `arch_deployment_unit_relation`，包含 tenant、low/high unit ID、created_by/created_at，主键或唯一键覆盖 `(tenant_id,unit_low_id,unit_high_id)`，并为双方查询建立索引和租户外键。
5. 创建 `arch_deployment_unit_relation_history`，记录 `LINK/UNLINK`、source_unit_id、两个端点、changed_by、changed_at 和可选 source_version_no；历史只追加。

在 `DeploymentUnitStore` 中实现准确方法：

```java
List<RelatedDeploymentUnitRow> findRelatedUnits(long tenantId, long unitId);
List<DeploymentUnit> lockActiveUnits(long tenantId, List<Long> ids);
void replaceRelations(long tenantId, long sourceUnitId, Set<Long> targetIds,
                      long actorId, int sourceVersionNo);
PageResult<DeploymentUnit> searchActiveOptions(long tenantId, String keyword,
                                               Long excludeId, int page, int size);
boolean hasRelations(long tenantId, long unitId);
```

`replaceRelations` 必须在调用方事务内比较集合，端点取 min/max，新增当前关系并写 LINK 历史，删除当前关系并写 UNLINK 历史。

- [ ] **步骤 4：实现领域服务、HTTP、导入与资源申请切换**

`DeploymentUnitService.prepare` 使用 `Locale.ROOT` 大写和正则解析；标准后缀映射为不可变 Map，自定义后缀只校验格式。创建分配 ID 后校验关联目标并写版本 1；更新锁定主记录、校验行版本、发布新版本并在同一事务替换关系。停用保留关系，作废先检查关系再调用既有引用检查器。

Controller 列表删除 `shortName` 查询参数；新增 `/options` 分页接口并复用查看权限。Controller 测试断言无权限 403、view 可搜索、manage 可写、请求附带 `tenantId` 不影响服务端租户。

导入模板删除简称列，类型只接受“应用/数据库/Web”或枚举值；不在导入中维护关联 ID。资源申请删除旧字段，依据 `kind` 决定 DB 或 AP/WB 字段，`WEB` 必须走非 DB 分支并显示 Web。

- [ ] **步骤 5：运行后端局部与模块回归**

运行：

```powershell
mvn -pl :ccb-architecture -am -Dtest=DeploymentUnitServiceTest,DeploymentUnitLifecycleMySqlTest,DeploymentUnitImportMySqlTest,EnvironmentResourceServiceTest,ArchitectureMigrationMySqlTest,DeploymentUnitControllerTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl :ccb-architecture -am test
node scripts/check-flyway-migrations.mjs
```

预期：聚焦测试和 `ccb-architecture` 依赖链全部通过；迁移无重复版本；真实 MySQL 测试证明 V124 表、列、约束和关系一致性。

证据：测试数、退出码、迁移版本、关系并发最终行数和资源申请三类断言。

- [ ] **步骤 6：建立提交检查点**

```powershell
git add server/src/modules/architecture server/src/platform/infrastructure/src/main/resources/db/migration/V148__refine_deployment_unit_model.sql
git commit -m "feat(architecture): refine deployment unit model"
```

预期：提交只包含 T1 文件；提交前 `git diff --cached --check` 退出 0。

**验收检查：** 名称格式和唯一性、三种类型、标准/自定义后缀、搜索分页、自关联/跨租户/失效目标、双向增删、重复/并发、版本、审计、停用/作废、导入、资源申请三类分流、V124 空库/增量。

**回滚：** 实施未提交时仅回退 T1 授权文件；提交后 revert T1 提交。已执行 V124 的数据库使用后续补偿迁移或重建早期开发库，不修改 V124 内容。

**停止条件：** V124 被占用；发现 scope 外下游强依赖；需要修改 platform/system、shared 或公共 UI；真实 MySQL 显示未建模外键；无法在单事务内保证关系一致。

**升级条件：** 需要保留旧客户端兼容；用户要求主名称允许下划线；双方版本必须同步增长；生产环境存在不可删除/不可规范化数据。

### T2：前端单名称输入、类型联动与远程多选

**需求映射：** R1、R2、R3、R4、R6、R8

**前置任务：** T1

**文件：**

- 修改：`web/src/modules/architecture/types.ts`
- 修改：`web/src/modules/architecture/api.ts`
- 修改：`web/src/modules/architecture/DeploymentUnitPage.vue`
- 修改：`web/src/modules/architecture/components/DeploymentUnitDetailDrawer.vue`
- 修改：`web/src/modules/architecture/DeploymentUnitImportPage.vue`
- 修改：`web/src/modules/architecture/ResourceRequestPage.vue`
- 修改：`web/src/modules/architecture/utils.ts`
- 修改：`web/src/modules/architecture/architecture.css`

**接口：**

- 消费：T1 产出的 `DeploymentUnitView`、`RelatedDeploymentUnitView`、`DeploymentUnitPayload` 和 `/deployment-units/options`。
- 产出：一个完整名称输入框、`APPLICATION|DATABASE|WEB` 类型控件、远程分页多选关联、完整名称列表/详情/导入/资源申请体验。

- [ ] **步骤 1：建立 TypeScript 编译失败基线**

先把 `DeploymentUnitKind` 改为：

```ts
export type DeploymentUnitKind = 'APPLICATION' | 'DATABASE' | 'WEB'
```

并把 payload 目标契约写为：

```ts
export interface DeploymentUnitPayload {
  physicalSubsystemId: number | null
  name: string
  kind: DeploymentUnitKind | ''
  relatedDeploymentUnitIds: number[]
  defaultNetworkZoneId: number | null
  description: string | null
  remark: string | null
  rowVersion: number | null
}
```

运行：`npm --prefix web run build`

预期：当前页面因旧字段和 MQ 标签引用产生明确 TypeScript 错误。

证据：退出码及首批旧字段错误位置。

- [ ] **步骤 2：实现名称与类型联动**

在 `DeploymentUnitPage.vue` 内建立单一规则函数，避免 watcher 相互递归：

```ts
const standardSuffixByKind = {
  APPLICATION: 'AP',
  DATABASE: 'DB',
  WEB: 'WB'
} as const

function normalizeDeploymentUnitName(value: string) {
  return value.trim().toUpperCase()
}
```

名称输入事件只大写并在后缀为 AP/DB/WB 时回填类型；类型事件仅在“无后缀”或“当前为标准后缀”时追加/替换后缀，自定义后缀保持。提交前校验 `^[A-Z0-9]+_[A-Z0-9]{1,8}$` 并将错误显示在名称字段附近。

删除简称和登记类型筛选/列/卡片/表单；列表身份区域展示完整名称及 code。名称输入区域使用语义变量形成明显信息层级，不硬编码颜色。

- [ ] **步骤 3：实现远程搜索多选和详情**

`api.ts` 增加：

```ts
searchDeploymentUnitOptions(query: {
  keyword?: string
  page?: number
  size?: number
  excludeId?: number
}): Promise<PageResult<RelatedDeploymentUnit>>
```

使用 `el-select multiple filterable remote collapse-tags`，输入防抖，首次打开和搜索显示 loading；创建时无 `excludeId`，编辑时排除当前 ID。搜索失败保留已选 options 和 ID，显示局部错误；编辑回显先使用详情中的关联对象，不依赖再次搜索成功。

详情抽屉按“名称、类型、所属物理子系统、关联部署单元、网络分区、版本、描述、备注”展示；关联项显示名称、code、物理子系统和状态。版本历史删除旧字段。

- [ ] **步骤 4：同步导入和资源申请页面**

导入页预览删除简称列，展示完整名称和应用/数据库/Web。资源申请移除 `relatedDeploymentUnitName` 与 `deploymentUnitType` 表单/详情字段，使用 `deploymentUnitKind` 决定 DB 与 AP/WB 布局；WEB 标签为“Web”。

- [ ] **步骤 5：运行前端构建和源码负向检查**

运行：

```powershell
npm --prefix web run build
rg -n "deploymentUnitType|relatedDeploymentUnitName|DeploymentUnitKind.*MQ|部署单元简称|登记表部署单元类型" web/src/modules/architecture
```

预期：构建退出 0；负向扫描无部署单元旧字段业务引用。物理子系统自身的 `shortName` 不属于本需求，不得误删。

证据：vue-tsc、Vite 构建结果和负向扫描结果。

- [ ] **步骤 6：建立提交检查点**

```powershell
git add web/src/modules/architecture
git commit -m "feat(architecture): update deployment unit form"
```

预期：提交仅含 T2 文件；`git diff --cached --check` 退出 0。

**验收检查：** 单输入框、大写、标准后缀双向联动、自定义后缀不被覆盖、远程搜索多选、编辑回显、搜索失败保留、详情双向关系、导入和资源申请新契约、移动端标签不溢出。

**回滚：** revert T2 提交可恢复前端，但只能与 T1 后端回滚配套，不允许旧前端连接新后端。

**停止条件：** 后端 T1 契约未稳定；需要修改共享 UI 才能实现；Element Plus 多选在 375px 无法通过局部样式避免页面级溢出。

**升级条件：** 用户要求新的公共选择器组件；自定义后缀需要单独输入框；移动端需要改变现有弹窗为新抽屉交互。

### T3：契约、Mock、全栈验收与交付证据

**需求映射：** R1、R5、R6、R7、R8

**前置任务：** T1、T2

**文件：**

- 修改：`mock/mock-data.json`
- 修改：`docs/integration/architecture-module-contract.md`
- 修改：`docs/requirements/REQ-20260901-057-deployment-unit-model/requirement.md`（仅在实际接口或迁移号与已批准设计一致但表述需同步时）
- 修改：`docs/requirements/REQ-20260901-057-deployment-unit-model/codex-task-scope.yaml`（仅在迁移号冲突或经用户批准扩大路径时）
- 证据：`.ai-control/requirements/req-20260901-057-deployment-unit-model/execution-T*.json`
- 证据：`.ai-control/requirements/req-20260901-057-deployment-unit-model/observation-T*.json`
- 证据：`.ai-control/requirements/req-20260901-057-deployment-unit-model/convergence.json`

**接口：**

- 消费：T1/T2 已通过的后端与前端实现。
- 产出：可复现的虚构数据、冻结的新公开架构契约、自动化/运行/浏览器证据和回退说明。

- [ ] **步骤 1：同步 Mock 与公开契约**

Mock 部署单元使用 `PORTAL_AP`、`PORTAL_DB`、`PORTAL_WB` 等合法完整名称和三种类型，删除旧字段；至少建立一组跨物理子系统双向关系，确保重复同步幂等。若 Mock 初始化器需要新增关系白名单或同步顺序，修改应位于 T1 后端提交而非在 T3 临时扩权。

契约明确：

- CRUD/版本字段删除清单和新命令/响应。
- `/deployment-units/options` 搜索参数、权限、分页和 ACTIVE 限制。
- 关系双向语义、停用保留、作废 409、关系历史与审计。
- 导入五列模板和类型值。
- 资源申请只按 kind 分流，不返回旧字段。

- [ ] **步骤 2：执行静态、聚焦、全量和迁移验证**

运行：

```powershell
git diff --check
node scripts/check-flyway-migrations.mjs
node scripts/check-all-governance.mjs
node scripts/check-codex-scope.mjs --base HEAD~3 --head HEAD --scope docs/requirements/REQ-20260901-057-deployment-unit-model/codex-task-scope.yaml
mvn -pl :ccb-architecture -am test
mvn test
npm --prefix web run build
```

预期：本需求引入的测试、构建、迁移、模块边界和 scope 均通过。若 `check-all-governance` 仍只报告已记录的历史旧账本错误，必须单独列出且不得描述为本需求通过；若出现本前缀错误则修复后重跑。

证据：每条命令的退出码、测试数量、构建摘要、迁移版本和实际变更文件清单。

- [ ] **步骤 3：启动本地栈并验证真实 API**

按 README 启动 MySQL、后端和前端，使用虚构管理员/只读/无权限角色验证：

1. 创建 `SMSLJ_AP`、`YGQL1_DB`、`PORTAL_WB` 和自定义 `BATCH_JOB1`。
2. 验证非法名称、标准后缀错配和重复名称的 400/409。
3. 搜索跨物理子系统目标，多选后保存；从双方详情验证双向显示。
4. 重复保存、并发修改和解除关系；验证只有一条当前关系且双方同步消失。
5. 停用保留关系；有关联时作废 409，解除后作废成功。
6. 创建 DB、应用和 Web 资源申请明细，验证字段分流。
7. 查看操作日志和关系历史，不记录表单正文或敏感值。

预期：HTTP、数据库最终状态、权限和审计均符合 R1-R7，服务端无 500。

证据：脱敏请求/响应摘要、状态码、关系表/历史表行数、审计 operation_code 和控制台日志摘要。

- [ ] **步骤 4：真实浏览器桌面与移动验收**

路径：`/architecture/deployment-units`，视口：`1280x800`、`375x812`、`390x844`、`430x932`，浅色和深色主题。

逐项操作：新建、输入联动、自定义后缀、搜索/清空/重试、多选标签、编辑回显、409 保留输入、详情双方关联、停用/作废确认、关闭脏表单、刷新返回列表。检查 `document.documentElement.scrollWidth <= window.innerWidth`、底部按钮可达、弹窗正文独立滚动、Network 无意外重复请求、Console 无新增错误。

预期：R8 全部通过；失败状态有可恢复操作，不以 toast 作为唯一关键信息。

证据：角色、路由、视口、主题、操作结果、Network/Console 和必要截图记录到 observation。

- [ ] **步骤 5：建立最终提交检查点并准备交付**

```powershell
git add mock/mock-data.json docs/integration/architecture-module-contract.md docs/requirements/REQ-20260901-057-deployment-unit-model .ai-control/requirements/req-20260901-057-deployment-unit-model
git commit -m "docs(architecture): close deployment unit model change"
```

预期：提交前 scope 与 `git diff --cached --check` 通过；最终 handoff 如实列出未执行或失败的验证，不自行合并、推送或发布。

**验收检查：** 契约/Mock 与实现一致；全部 must 需求有执行和观察证据；范围无越界；真实 API 和四视口验收；无未关闭 P0/P1。

**回滚：** revert T3 文档/Mock 提交；产品回滚按 T2、T1 逆序，数据库采用补偿迁移或早期开发库重建。

**停止条件：** 真实 API 发现租户/权限越界；关系表与双方视图不一致；资源申请类型分流错误；迁移破坏非开发数据；浏览器出现白屏、页面级横向滚动或不可达提交操作。

**升级条件：** 全仓治理的历史账本错误被 Required Check 强制阻断本需求；需要生产数据清理；需要修改 scope 外文件；回退必须删除不可恢复数据。

## 集成检查

| 完成任务 | 命令/传感器 | 通过信号 |
| --- | --- | --- |
| T1 | 聚焦 Maven + `ccb-architecture` 全测 + Flyway 检查 | 后端编译、测试、V124 和双向关系通过 |
| T1、T2 | `npm --prefix web run build` + 旧字段负向扫描 | Vue 类型/构建通过且部署单元旧字段无残留 |
| T1—T3 | Maven 全测、前端构建、governance、scope、真实 API、四视口浏览器 | R1-R8 证据齐全，无本需求 P0/P1 |

## 控制模型种子

以下仅为 `hypotheses-only` 候选，必须由 `$model-engineering-system` 验证：

- 被控边界候选：部署单元主记录/版本/关系/关系历史、导入、资源申请投影、部署单元 Vue 页面和公开架构契约。
- 状态变量候选：完整名称、kind、status、rowVersion、当前关系集合、当前版本号、关系历史事件、搜索选项加载状态、表单脏状态。
- 接口候选：DeploymentUnitCommand/View、`/deployment-units/options`、资源申请 DeploymentUnitRef、Flyway V124。
- 传感器候选：Java 单元/Controller/Testcontainers、Flyway 检查、Vue build、源码负向扫描、真实 HTTP/SQL/审计、浏览器 Network/Console/viewport。
- 执行器候选：V124 迁移、DeploymentUnitService 事务、Store 关系替换、Vue 名称联动和远程多选。
- 扰动候选：并行分支占用 V124、既有开发库非法名称/外键、搜索请求乱序、用户并行修改、历史账本治理噪声。
- 时延候选：远程搜索防抖与网络响应、关系并发事务锁、Maven/Testcontainers、浏览器全视口验收。
- 假设：主名称不含下划线；关系历史足以追踪被关联端变化；停用保留关系且作废先解除；均以已批准设计中的证伪条件为准。

## 风险与用户批准

- 高风险动作：V124 删除数据库列并改变唯一键/检查约束；旧 API/DTO 字段被删除；资源申请字段分流切换；双向关系新增并发写路径。
- 回退限制：执行 V124 后不能靠简单代码回退恢复旧结构，必须使用补偿迁移或重建早期开发环境。
- 用户已于 2026-09-02 明确批准计划修订 1 及上述高风险动作；允许创建隔离工作树、导入 control-engineering 账本并按 T1 → T2 → T3 串行实施。
