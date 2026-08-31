# 网络访问判定与关系生命周期实施计划

> 执行要求：用户已批准开始执行；实施仍必须逐任务保持在 scope 写入范围内。

## 状态与来源

- 需求编号：REQ-20260828-055
- 需求文档：`docs/requirements/REQ-20260828-055-network-access-decision-lifecycle/requirement.md`
- scope 文档：`docs/requirements/REQ-20260828-055-network-access-decision-lifecycle/codex-task-scope.yaml`
- 设计文档：`docs/engineering-control/designs/2026-08-28-network-access-decision-lifecycle-design.md`
- 计划修订：1
- 状态：已批准执行

## 硬门禁

进入实施前必须同时满足：

1. 用户明确批准需求、设计、计划和 scope。
2. `requirement.status=ready`。
3. `codex-task-scope.yaml` 中 `requirement.codex_allowed=true`。
4. `public_capability_change.owner_approved=true`。
5. 开发入口检查通过：`node scripts/check-development-entry.mjs --require-plugin`。
6. Git 工作树检查完成，保护既有无关未跟踪内容。
7. V104、V105 迁移号在当前目标分支未被占用。
8. 明确流程定义草稿、发布和部署的验收方式。

任一门禁不满足，停止实施。

## 全局约束

- 只修改批准后的 scope `writable_paths`。
- 不修改 V100-V103 历史迁移。
- 不修改 workflow/system/shared 内部实现。
- 不连接生产或真实外部网络平台。
- 不恢复动态表单元数据。
- 不把健康检查或前端构建描述成浏览器 UAT 通过。
- 小步提交；每个任务提交前运行对应传感器。

## 任务依赖图

```text
T0 -> T1 -> T2 -> T3 -> T4 -> T5 -> T6 -> T7 -> T8 -> T9
```

本计划建议串行执行。迁移、模型、服务、前端页面和测试存在共享边界，不建议并行改同一模块。

## T0：审计后准入复核

**目标：** 确认用户已批准并且仓库允许编码。

**文件：** 无产品代码修改。

**步骤：**

1. 读取批准后的 requirement、scope、设计和计划。
2. 运行 `git status --short --branch`，记录无关未跟踪内容并保护。
3. 运行 `node scripts/check-development-entry.mjs --require-plugin`。
4. 检查迁移目录中 V104、V105 是否未占用。
5. 运行 scope 基线检查，确认只读和禁止路径可执行。

**验收：** 所有门禁有命令输出或文件证据。

**停止条件：** scope 未解锁、迁移号冲突、插件检查失败、工作树存在覆盖风险。

## T1：数据迁移与领域模型

**目标：** 建立判定、规则、生命周期和工作流持久化基础。

**候选文件：**

- `server/src/modules/architecture/src/main/java/com/ccb/architecture/network/model/NetworkAccessModels.java`
- `server/src/modules/architecture/src/main/java/com/ccb/architecture/network/persistence/NetworkAccessStore.java`
- `server/src/platform/infrastructure/src/main/resources/db/migration/V115__extend_architecture_network_access_lifecycle.sql`
- `server/src/platform/infrastructure/src/main/resources/db/migration/V116__seed_architecture_network_access_lifecycle_workflow.sql`
- `server/src/modules/architecture/src/test/java/com/ccb/architecture/network/persistence/NetworkAccessStoreMySqlTest.java`

**步骤：**

1. 先写迁移和模型测试，覆盖 09 存量数据兼容。
2. 追加 V104，扩展申请、关系、历史、轮次、回执和免申请规则表。
3. 追加 V105，补齐权限/菜单/Mock/流程草稿种子。
4. 扩展 Java 枚举和 record，保持 09 DTO 兼容读取。
5. 增加 store 查询和写入方法，不直接写其他模块表。

**验证：**

```text
mvn -pl :ccb-architecture -am "-Dtest=NetworkAccessStoreMySqlTest" test
git diff --check
```

**停止条件：** 迁移无法兼容 09 数据、需要改历史脚本、需要 workflow 内部表结构假设未经确认。

## T2：端口、地址和时间覆盖基础库

**目标：** 提供判定引擎可复用的纯计算能力。

**候选文件：**

- `server/src/modules/architecture/src/main/java/com/ccb/architecture/network/service/NetworkPortRanges.java`
- `server/src/modules/architecture/src/main/java/com/ccb/architecture/network/service/NetworkAccessCoverage.java`
- `server/src/modules/architecture/src/test/java/com/ccb/architecture/network/service/NetworkPortRangesTest.java`
- `server/src/modules/architecture/src/test/java/com/ccb/architecture/network/service/NetworkAccessCoverageTest.java`

**步骤：**

1. 实现端口解析、规范化、合并和子集判断。
2. 复用 `NetworkCidr` 完成 IPv4/CIDR 包含判断。
3. 实现域名精确匹配和未知地址失败保守。
4. 实现 `LIMITED` 与 `LONG_TERM` 时间覆盖判断。
5. 覆盖边界测试：端口 1/65535、范围反转、空输入、长期请求、有限期覆盖。

**验证：**

```text
mvn -pl :ccb-architecture -am "-Dtest=NetworkPortRangesTest,NetworkAccessCoverageTest" test
git diff --check
```

**停止条件：** 现有协议枚举无法表达全协议覆盖，或外部地址类型需要新增规则但未获审计确认。

## T3：判定服务和 API

**目标：** 实现二值判定和严格失败保守策略。

**候选文件：**

- `server/src/modules/architecture/src/main/java/com/ccb/architecture/network/service/NetworkAccessDecisionService.java`
- `server/src/modules/architecture/src/main/java/com/ccb/architecture/network/service/NetworkAccessService.java`
- `server/src/modules/architecture/src/main/java/com/ccb/architecture/network/web/NetworkAccessController.java`
- `server/src/modules/architecture/src/test/java/com/ccb/architecture/network/service/NetworkAccessDecisionServiceTest.java`
- `server/src/modules/architecture/src/test/java/com/ccb/architecture/network/web/NetworkAccessControllerTest.java`

**步骤：**

1. 新增 decision command/result 模型。
2. 解析和校验输入；任一失败返回 `NEEDS_APPLICATION`。
3. 查询并计算 active relation 完整覆盖。
4. 查询并计算 active exemption rule 完整覆盖。
5. 新增 `POST /api/architecture/network-access/decision`。
6. 增加权限和审计。

**验证：**

```text
mvn -pl :ccb-architecture -am "-Dtest=NetworkAccessDecisionServiceTest,NetworkAccessControllerTest" test
git diff --check
```

**停止条件：** 判定需要读取其他模块私有表，或无法在快照中证明关系覆盖。

## T4：申请动作和关系生命周期服务

**目标：** 让开通、修改、续期、关闭都通过申请办理。

**候选文件：**

- `server/src/modules/architecture/src/main/java/com/ccb/architecture/network/service/NetworkAccessService.java`
- `server/src/modules/architecture/src/main/java/com/ccb/architecture/network/service/NetworkAccessLifecycleService.java`
- `server/src/modules/architecture/src/main/java/com/ccb/architecture/network/persistence/NetworkAccessStore.java`
- `server/src/modules/architecture/src/test/java/com/ccb/architecture/network/service/NetworkAccessLifecycleServiceTest.java`

**步骤：**

1. 扩展创建申请命令，支持 `OPEN/MODIFY/RENEW/CLOSE`。
2. 校验目标关系、动作类型、端点、有效期和并发版本。
3. 将原直接关闭关系入口改为禁止直接关闭或引导关闭申请。
4. 实现成功投影：
   - open 创建新关系。
   - modify 关闭旧关系并创建替代关系。
   - renew 关闭旧关系并创建续期关系。
   - close 关闭目标关系。
5. 写入申请历史和关系替代链。

**验证：**

```text
mvn -pl :ccb-architecture -am "-Dtest=NetworkAccessLifecycleServiceTest,NetworkAccessServiceTest" test
git diff --check
```

**停止条件：** 用户未确认替代关系模型，或直接关闭兼容策略未确认。

## T5：平台工作流接入

**目标：** 访问申请提交和完成由平台工作流驱动。

**候选文件：**

- `server/src/modules/architecture/src/main/java/com/ccb/architecture/network/service/NetworkAccessApplicationSubmissionService.java`
- `server/src/modules/architecture/src/main/java/com/ccb/architecture/network/service/NetworkAccessWorkflowPayloadFactory.java`
- `server/src/modules/architecture/src/main/java/com/ccb/architecture/network/workflow/NetworkAccessWorkflowLifecycleConsumer.java`
- `server/src/modules/architecture/src/test/java/com/ccb/architecture/network/workflow/NetworkAccessWorkflowLifecycleConsumerTest.java`

**步骤：**

1. 参考网络工单工作流接入模式，但使用独立 business type 和 definition code。
2. 提交申请时启动工作流并记录实例、版本、轮次和 digest。
3. 处理 completed/returned/rejected/terminated/cancelled 事件。
4. completed 事件调用 T4 生命周期投影。
5. 重复事件、乱序事件和非当前轮次事件必须幂等。

**验证：**

```text
mvn -pl :ccb-architecture -am "-Dtest=NetworkAccessWorkflowLifecycleConsumerTest" test
git diff --check
```

**停止条件：** 目标环境无已发布部署流程定义，或需要改 workflow 内部代码。

## T6：下线实例风险投影

**目标：** 历史关系保留，但风险可见。

**候选文件：**

- `server/src/modules/architecture/src/main/java/com/ccb/architecture/network/service/NetworkAccessRelationRiskProjector.java`
- `server/src/modules/architecture/src/main/java/com/ccb/architecture/network/persistence/NetworkAccessStore.java`
- `server/src/modules/architecture/src/test/java/com/ccb/architecture/network/service/NetworkAccessRelationRiskProjectorTest.java`

**步骤：**

1. 从关系快照解析部署实例 ID。
2. 查询当前实例状态。
3. 关系响应增加风险字段。
4. 新申请服务端拒绝已下线实例。
5. 覆盖实例缺失、OFFLINE、混合端点和外部地址无实例场景。

**验证：**

```text
mvn -pl :ccb-architecture -am "-Dtest=NetworkAccessRelationRiskProjectorTest" test
git diff --check
```

**停止条件：** 快照结构不足以识别实例，需要先做存量数据补偿设计。

## T7：前端判定、申请和风险交互

**目标：** 在现有页面内交付可用入口，不做营销式重排。

**候选文件：**

- `web/src/modules/architecture/NetworkAccessPage.vue`
- `web/src/modules/architecture/api.ts`
- `web/src/modules/architecture/types.ts`
- `web/src/modules/architecture/architecture.css`

**步骤：**

1. 增加判定表单，复用申请端点选择。
2. 显示二值结论和原因摘要。
3. “需要申请”可带入创建开通申请。
4. 关系列表操作改为发起修改、续期、关闭申请。
5. 增加关系风险标识和详情。
6. 增加免申请规则维护入口，仅管理权限可用。
7. 验证桌面和手机视口滚动、按钮、抽屉高度和无权限状态。

**验证：**

```text
npm --prefix web run build
git diff --check
```

**停止条件：** 交互模式需要用户在抽屉和独立页面之间选择，或规则维护入口位置未获确认。

## T8：权限、审计、Mock 和端到端整理

**目标：** 补齐权限种子、审计覆盖和可验收演示数据。

**候选文件：**

- `server/src/modules/architecture/src/main/java/com/ccb/architecture/network/web/NetworkAccessController.java`
- `server/src/modules/architecture/src/main/java/com/ccb/architecture/network/service/NetworkAccessService.java`
- `server/src/platform/infrastructure/src/main/resources/db/migration/V116__seed_architecture_network_access_lifecycle_workflow.sql`

**步骤：**

1. 核对 view/apply/manage 权限覆盖。
2. 写成功和失败审计。
3. 通过 V105 补齐可验收的判定、规则、关系生命周期和风险种子数据。
4. 覆盖越权、跨租户、无权限、并发版本冲突。
5. 确保前端无权限态不暴露管理动作。

**验证：**

```text
mvn -pl :ccb-architecture -am "-Dtest=NetworkAccessControllerTest,NetworkAccessServiceTest" test
npm --prefix web run build
git diff --check
```

**停止条件：** 需要新增独立权限码或菜单结构超出 09 权限边界。

## T9：集成验证与收敛验收

**目标：** 证明实现满足需求并暴露残余风险。

**步骤：**

1. 运行后端聚焦测试。
2. 运行真实 MySQL Flyway 迁移测试。
3. 运行 Boot 回归。
4. 运行前端构建。
5. 启动本地后端和前端。
6. 使用已认证用户完成浏览器 UAT：
   - 判定不覆盖时需要申请。
   - 关系覆盖时不需要申请。
   - 规则覆盖时不需要申请。
   - 开通申请工作流成功后生成关系。
   - 修改或续期成功后替代关系。
   - 关闭申请成功后关闭关系。
   - 下线实例风险可见。
7. 运行 scope 检查和 diff check。
8. 写入 `.ai-control/requirements/req-20260828-055-network-access-decision-lifecycle/convergence.json`。

**验证命令候选：**

```text
mvn -pl :ccb-architecture -am "-Dtest=NetworkAccessServiceTest,NetworkAccessDecisionServiceTest,NetworkAccessLifecycleServiceTest,NetworkAccessWorkflowLifecycleConsumerTest,NetworkAccessStoreMySqlTest" test
mvn -pl :ccb-boot -am test
npm --prefix web run build
node scripts/check-codex-scope.mjs --scope docs/requirements/REQ-20260828-055-network-access-decision-lifecycle/codex-task-scope.yaml --base origin/dev-ivanh --head HEAD --working-tree
git diff --check
```

**停止条件：** Testcontainers/Docker 不可用、流程定义未发布部署、浏览器无法认证、scope 因无关文件失败。

## 提交边界

建议小步提交：

1. `feat(network-access): add lifecycle migrations and models`
2. `feat(network-access): add strict decision engine`
3. `feat(network-access): enforce application lifecycle changes`
4. `feat(network-access): connect access applications to workflow`
5. `feat(network-access): surface relation risk and frontend flows`
6. `test(network-access): add integration and uat evidence`

用户未批准前不创建上述提交。

## 审计关注点

1. 显式免申请规则台账是否符合业务预期。
2. 修改/续期替代关系模型是否符合审计口径。
3. 直接关闭接口的兼容处理是否接受。
4. 外部地址精确匹配是否足够。
5. 工作流定义发布和部署是否纳入本需求验收。
