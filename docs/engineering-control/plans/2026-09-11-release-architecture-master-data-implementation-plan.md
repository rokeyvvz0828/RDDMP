# 配置管理接入架构主数据实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

**目标：** 用架构管理真实物理子系统和交付单元替换配置管理版本申请的静态 Mock，并由服务端保证项目隔离、父子关系、启用状态和可信快照。

**架构：** 配置管理定义消费端口，架构管理公开最小只读查询契约，Boot 适配器组装两者。架构交付单元制品类型通过追加迁移统一为 `IMAGE/BINARY`，配置管理创建和更新在写入前重新查询并规范化快照。

**技术栈：** Java 17、Spring Boot、Spring Security、JdbcTemplate、MySQL 8/Flyway、JUnit 5、Vue 3、TypeScript、Element Plus、Vite。

## 全局约束

- 不修改历史迁移；数据库只追加 `V213`。
- 不允许配置管理读取架构私有仓储、服务或表。
- 不改变审批、窗口、文件介质、投产基线和生产版本业务规则。
- 查询和保存都必须按租户与平台项目 ID 隔离。
- 新写入只接受启用物理子系统及其启用、类型为 `IMAGE/BINARY` 的交付单元。
- 已提交申请继续展示原快照；旧 Mock 或停用草稿必须重新选择后才能保存。
- 前端覆盖加载、空、失败、无权限、过期和请求乱序状态，并满足 `design-h5.md`。

---

## 状态与来源

- 计划修订：1
- 设计修订：2
- 设计文档：`docs/engineering-control/designs/2026-09-11-release-architecture-master-data-design.md`
- 状态：待确认

## 文件职责地图

| 路径 | 状态 | 职责 |
| --- | --- | --- |
| `V213__normalize_delivery_unit_artifact_types.sql` | candidate-new | 字典和存量制品类型规范化 |
| `architecture/integration/ReleaseMasterDataQuery.java` | candidate-new | 架构公开只读投影契约 |
| `architecture/service/JdbcReleaseMasterDataQuery.java` | candidate-new | 项目隔离、ACTIVE 过滤和批量读取 |
| `release/integration/ReleaseArchitectureDirectory.java` | candidate-new | 配置管理拥有的主数据消费端口 |
| `boot/release/ReleaseArchitectureDirectoryAdapter.java` | candidate-new | 两个业务模块公共契约适配 |
| `release/application/service/ReleaseMasterDataService.java` | candidate-new | 项目权限、分页选择和有效引用解析 |
| `release/application/web/ReleaseMasterDataController.java` | candidate-new | 配置管理选择 API 与 RBAC |
| `ReleaseApplicationService.java` | existing | 保存前规范化架构快照 |
| `web/src/api/release.ts` | existing | 主数据选择 API 类型与请求 |
| `ReleaseApplicationDrawer.vue` | existing | 异步联动选择、状态与旧快照反显 |
| `release-master-data.mock.ts` | existing-delete | 删除静态主数据源 |

## 任务依赖与并行策略

```text
T1 制品类型规范化 ─┐
                    ├─> T3 配置管理服务端接入 ─> T4 前端联动与组合验收
T2 架构查询与适配 ─┘
```

T1 与 T2 写入面互不重叠，可并行；T3 依赖二者的稳定契约；T4 依赖配置管理 HTTP 契约，串行集成。

## 需求覆盖

| 需求 | 任务 |
| --- | --- |
| R1 制品类型统一 | T1 |
| R2 架构主数据公开只读查询 | T2 |
| R3 配置权限与项目隔离选择 API | T3 |
| R4 保存前可信快照与失败关闭 | T3 |
| R5 前端替换 Mock 与联动状态 | T4 |
| R6 历史快照和旧草稿兼容 | T3, T4 |
| R7 回归、移动端与治理验收 | T1, T2, T3, T4 |

### T1：统一架构交付单元制品类型

**需求映射：** R1, R7

**前置任务：** 无

**文件：**
- 新建：`server/src/platform/infrastructure/src/main/resources/db/migration/V213__normalize_delivery_unit_artifact_types.sql`
- 修改：`server/src/test/java/com/ccb/ArchitectureTest.java`（仅当最新迁移断言需要更新）
- 修改：`server/src/platform/infrastructure/src/test/java/com/ccb/infrastructure/FlywayMigrationTest.java`（仅当迁移清单断言需要更新）
- 测试：迁移检查脚本和架构制品类型服务测试

**接口：**
- 消费：`ARCH_ARTIFACT_TYPE` 字典、`arch_delivery_unit.artifact_type_code`
- 产出：启用字典 code `IMAGE/BINARY`，存量字段只包含空值、`IMAGE` 或 `BINARY`

- [ ] **步骤 1：建立迁移基线检查**

  运行：`node scripts/check-flyway-migrations.mjs`

  预期：当前迁移到 V212 且检查通过；记录 V213 不存在。

- [ ] **步骤 2：追加 V213**

  新增镜像/二进制字典项，将容器映射为 `IMAGE`、压缩包和脚本映射为 `BINARY`，停用旧项，并添加稳定身份失败关闭检查。

- [ ] **步骤 3：验证迁移与架构回归**

  运行：`node scripts/check-flyway-migrations.mjs`

  运行：`mvn -pl :ccb-architecture -am test -Dtest=DeliveryUnitServiceTest,DeliveryUnitMySqlTest -Dsurefire.failIfNoSpecifiedTests=false`

  预期：迁移检查通过，制品类型新增、更新和存量映射断言通过。

- [ ] **步骤 4：记录证据与检查点**

  保存命令退出码、迁移断言和实际 diff；提交范围仅含 T1 文件。

**回滚：** 回退 T1 代码；已执行迁移的数据使用发布前备份补偿恢复，不修改 V213。

**停止条件：** `IMAGE/BINARY` 稳定 ID 与现有字典项冲突，或旧 code 被其他已证实业务契约直接依赖。

**升级条件：** 发现除架构交付单元之外的表持久化旧制品类型 code，需要扩大迁移范围。

### T2：建立架构只读查询契约与 Boot 适配器

**需求映射：** R2, R7

**前置任务：** 无

**文件：**
- 新建：`server/src/modules/architecture/src/main/java/com/ccb/architecture/integration/ReleaseMasterDataQuery.java`
- 新建：`server/src/modules/architecture/src/main/java/com/ccb/architecture/service/JdbcReleaseMasterDataQuery.java`
- 新建：`server/src/modules/architecture/src/test/java/com/ccb/architecture/service/JdbcReleaseMasterDataQueryTest.java`
- 新建：`server/src/modules/release/src/main/java/com/ccb/release/integration/ReleaseArchitectureDirectory.java`
- 新建：`server/src/platform/boot/src/main/java/com/ccb/boot/release/ReleaseArchitectureDirectoryAdapter.java`
- 新建：`server/src/platform/boot/src/test/java/com/ccb/boot/release/ReleaseArchitectureDirectoryAdapterTest.java`

**接口：**
- 消费：`arch_physical_subsystem`、`arch_delivery_unit`，认证用户 tenantId 和已解析 platform projectId
- 产出：分页 ACTIVE 物理子系统、分页 ACTIVE 交付单元、精确物理引用和批量交付引用

- [ ] **步骤 1：为查询边界建立失败测试**

  覆盖租户/项目隔离、删除和状态过滤、交付单元父子范围、关键字分页、批量查询缺项以及适配字段转换。

- [ ] **步骤 2：运行聚焦测试确认红灯**

  运行：`mvn -pl :ccb-architecture,:ccb-boot -am test -Dtest=JdbcReleaseMasterDataQueryTest,ReleaseArchitectureDirectoryAdapterTest -Dsurefire.failIfNoSpecifiedTests=false`

  预期：因新契约和实现不存在而编译失败。

- [ ] **步骤 3：实现最小公共契约和适配**

  架构查询仅返回稳定 ID、编码、名称、状态、父 ID 和制品类型；Boot 只做 DTO 转换，不增加业务条件。

- [ ] **步骤 4：运行聚焦及模块回归**

  运行：`mvn -pl :ccb-architecture,:ccb-boot -am test -Dtest=JdbcReleaseMasterDataQueryTest,ReleaseArchitectureDirectoryAdapterTest -Dsurefire.failIfNoSpecifiedTests=false`

  预期：全部通过，跨项目与错父子查询为空且无数据泄漏。

- [ ] **步骤 5：记录证据与检查点**

  保存接口签名、SQL 参数顺序、测试结果和实际 diff；提交范围仅含 T2 文件。

**回滚：** 回退 T2 新文件；无数据库副作用。

**停止条件：** 必须引用架构私有 DTO/Service 才能完成，或 Boot 无法在不形成循环依赖的情况下装配。

**升级条件：** 模块治理要求新增依赖声明或公开契约 Owner 复核未通过。

### T3：配置管理选择 API 与写入防伪校验

**需求映射：** R3, R4, R6, R7

**前置任务：** T1, T2

**文件：**
- 新建：`server/src/modules/release/src/main/java/com/ccb/release/application/service/ReleaseMasterDataService.java`
- 新建：`server/src/modules/release/src/main/java/com/ccb/release/application/web/ReleaseMasterDataController.java`
- 新建：`server/src/modules/release/src/test/java/com/ccb/release/application/service/ReleaseMasterDataServiceTest.java`
- 新建：`server/src/modules/release/src/test/java/com/ccb/release/application/web/ReleaseMasterDataControllerSecurityTest.java`
- 修改：`server/src/modules/release/src/main/java/com/ccb/release/application/service/ReleaseApplicationService.java`
- 修改：`server/src/modules/release/src/test/java/com/ccb/release/application/service/ReleaseApplicationServiceTest.java`

**接口：**
- 消费：T2 `ReleaseArchitectureDirectory`，`ProjectAccessService`，既有 CreateRequest/UpdateRequest
- 产出：两个 `/api/release/master-data` 分页 GET 接口，以及规范化后的 `DeliverySnapshot`

- [ ] **步骤 1：建立权限、项目和写入校验失败测试**

  覆盖配置权限、无项目权限、跨租户、停用和缺失数据、错父子关系、非数字 ID、重复 ID、伪造快照覆盖及文件介质不变。

- [ ] **步骤 2：运行聚焦测试确认红灯**

  运行：`mvn -pl :ccb-release -am test -Dtest=ReleaseMasterDataServiceTest,ReleaseMasterDataControllerSecurityTest,ReleaseApplicationServiceTest -Dsurefire.failIfNoSpecifiedTests=false`

  预期：因新服务/控制器不存在及旧服务信任客户端快照而失败。

- [ ] **步骤 3：实现选择服务和 Controller**

  Controller 使用配置管理申请权限；Service 先解析 `ProjectAccess`，再调用端口，统一返回分页 DTO 和可读错误。

- [ ] **步骤 4：在申请写入前规范化快照**

  保留请求结构兼容性，但忽略客户端编码、名称和类型；全部引用验证成功后生成申请领域快照并进入既有事务。

- [ ] **步骤 5：运行聚焦与配置模块回归**

  运行：`mvn -pl :ccb-release -am test -Dtest=ReleaseMasterDataServiceTest,ReleaseMasterDataControllerSecurityTest,ReleaseApplicationServiceTest -Dsurefire.failIfNoSpecifiedTests=false`

  预期：正常、越权、伪造、停用、跨项目和错父子路径均满足断言。

- [ ] **步骤 6：记录证据与检查点**

  保存请求/响应样例、错误码、事务无副作用断言、测试结果和实际 diff。

**回滚：** 回退 T3；前端尚未切换时旧 Mock 流程恢复，数据库无结构变化。

**停止条件：** 写校验需要改变已提交申请读取语义，或配置用户权限无法与项目访问权限同时满足。

**升级条件：** 发现现有外部客户端依赖伪造快照被原样保存的行为。

### T4：前端主数据联动与组合验收

**需求映射：** R5, R6, R7

**前置任务：** T3

**文件：**
- 修改：`web/src/api/release.ts`
- 修改：`web/src/modules/release/components/ReleaseApplicationDrawer.vue`
- 修改：`web/src/modules/release/release-prototype.css`
- 删除：`web/src/modules/release/release-master-data.mock.ts`

**接口：**
- 消费：T3 两个分页 GET 接口、既有申请 DTO 和当前项目上下文
- 产出：项目化物理子系统远程选择、交付单元联动、旧快照禁用反显和字段级状态

- [ ] **步骤 1：增加 API 类型与请求函数**

  定义最小选择 DTO 和分页请求，保持既有 release API 导出不变。

- [ ] **步骤 2：替换抽屉静态 Mock**

  增加物理子系统和交付单元 loading/error/options/requestRevision 状态；抽屉打开、项目变化和物理选择触发受控加载，切换时清空过期值。

- [ ] **步骤 3：实现旧草稿和全状态交互**

  旧快照无法在真实选项中匹配时作为禁用项反显并持续提示；加载失败支持重试，空结果说明维护入口，保存前阻止失效选择。

- [ ] **步骤 4：运行静态和构建检查**

  运行：`rg -n "release-master-data.mock|releaseSubsystemOptions" web/src`

  预期：无引用。

  运行：`npm --prefix web run build`

  预期：TypeScript 与 Vite 构建通过。

- [ ] **步骤 5：运行组合回归和治理检查**

  运行：`mvn -pl :ccb-release,:ccb-architecture,:ccb-boot -am test`

  运行：`node scripts/check-flyway-migrations.mjs && node scripts/check-all-governance.mjs && node scripts/check-codex-scope.mjs`

  预期：目标模块和治理门禁通过；已知 Mockito 沙箱限制必须在允许附加的环境复验。

- [ ] **步骤 6：真实浏览器验收**

  在桌面、375x812、390x844、430x932 验证新建、编辑旧草稿、切换项目/子系统、空数据、接口失败、无权限、保存与提交；检查控制台、网络错误和页面级横向溢出。

- [ ] **步骤 7：记录最终证据**

  保存构建、API、浏览器截图/断言、实际 diff 和残余风险，交给独立观测阶段。

**回滚：** 回退 T4 后恢复静态 Mock 文件与原抽屉引用；不回退 T1 数据迁移。

**停止条件：** 前端必须扩大到非版本申请页面才能完成，或真实 API 契约与 T3 不一致。

**升级条件：** H5 无法在既有抽屉结构中提供可达操作，需修改公共抽屉组件。

## 集成检查

```bash
git diff --check
node scripts/check-flyway-migrations.mjs
node scripts/check-all-governance.mjs
node scripts/check-codex-scope.mjs
mvn -pl :ccb-release,:ccb-architecture,:ccb-boot -am test
npm --prefix web run build
```

预期所有命令退出码为 0。Maven 若因受限执行环境禁止 Byte Buddy 附加而失败，必须在沙箱外或 CI 复验，不能把环境失败记为功能通过。

## 控制模型种子

以下内容仅为 `hypotheses-only`，由 control-engineering 建模阶段验证：

- 被控边界候选：架构主数据读取、Boot DTO 适配、配置管理选择与申请写入、版本申请抽屉。
- 状态变量候选：当前 tenant/project、选中 subsystem、候选 units、请求 revision、草稿有效性、迁移后的 artifact type。
- 传感器候选：JUnit、迁移检查、治理检查、TypeScript 构建、HTTP 状态、数据库无副作用断言、浏览器控制台和视口溢出。
- 执行器候选：V213、JDBC 查询、适配器、配置 Service/Controller、申请规范化、Vue 异步状态。
- 扰动候选：项目切换、慢请求乱序、主数据并发停用、旧 Mock 草稿、字典自定义、受限 JVM 附加环境。
- 时延候选：下拉远程查询、项目切换刷新、迁移执行、浏览器状态更新。

## 风险与用户批准

- 高风险动作：追加迁移修改共享字典和存量字段；新增跨模块公共查询契约；配置管理写入从信任快照改为服务端规范化。
- 设计已由用户确认；本计划在用户复核后才能导入 high-assurance 控制账本并开始产品代码实施。
