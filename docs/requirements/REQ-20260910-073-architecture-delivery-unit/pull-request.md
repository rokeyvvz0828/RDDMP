# PR：REQ-20260910-073 架构管理 - 交付单元

> **提交前必读（base 分支）**
> 本需求在 `dev-ivanh` 上实施。`origin/dev-ivanh` 与 `origin/main` 已大幅分叉（838 文件 / 61 提交 vs 20 提交），直接开 `dev-ivanh → main` 的 PR 会**把他人 38 个未集成提交一起拖入评审，并在 diff 中回退 main 上 20 个已合并提交**（含 PR #15 与整条 data-migration 线）。
> 正确顺序：**先把 `origin/main` 合入 `dev-ivanh` 并解决 4 个冲突，再以本分支为目标向 `main` 提 PR**。冲突清单（`git merge-tree origin/dev-ivanh origin/main`）：
> `server/src/modules/data-migration/.../DashboardService.java`、`IssueService.java`、`ProjectComponentService.java`、`TargetTableService.java` —— 全部属于 `business/data-migration`，**与本次改动无交集**。
> 集成后执行：`git push origin feat/REQ-20260910-073-architecture-delivery-unit && gh pr create --base main --head feat/REQ-20260910-073-architecture-delivery-unit --title "feat(architecture): 交付单元（REQ-20260910-073）" --body-file docs/requirements/REQ-20260910-073-architecture-delivery-unit/pull-request.md`

## 需求与范围

- 需求编号：`REQ-20260910-073`
- 需求文档：`docs/requirements/REQ-20260910-073-architecture-delivery-unit/requirement.md`
- Codex 任务范围：`docs/requirements/REQ-20260910-073-architecture-delivery-unit/codex-task-scope.yaml`
- `.ai-control` 前缀与当前 phase：`req-20260910-073-architecture-delivery-unit`，phase = **`converged`**（收敛门禁 PASS）
- 目标模块与 Owner：`business/architecture`，Owner `rokeyvvz0828`
- Codex 参与范围：需求/设计/计划落盘、T1—T10 受控实施、测试与浏览器验收、账本与纠偏记录。**未参与**批准、合并、发布；Owner 复核由 `rokeyvvz0828` 于 2026-09-11T10:18:00+08:00 完成

## 变更

- 用户目标与可观察结果：架构管理菜单下提供**交付单元**（名称 + 归属物理子系统）的增删改查；交付单元与**同物理子系统**的部署单元建立**无方向关联**，两侧详情抽屉都可编辑且双向即时可见；交付单元具备由**统一字典**驱动的**制品类型**按钮式单选。
- 实际修改内容：
  1. 数据层：新增 `arch_delivery_unit`、`arch_delivery_unit_number_seq`、`arch_delivery_unit_deployment_unit`；`arch_delivery_unit.artifact_type_code`；关联表复合外键强制“同一物理子系统”。
  2. 服务与接口：交付单元列表/详情/新增/修改/软删除/停用启用、关联覆盖式保存（两侧各一入口，写同一张关系表）、部署单元反查、同物理子系统候选、制品类型字典选项；关联写入统一要求 `architecture:delivery-unit:manage`；写操作全部审计。
  3. 部署单元作废守卫纳入交付单元关联（fail-closed）。
  4. 前端：交付单元页面（`UiFormDrawer` 抽屉表单 + `UiDataTable` + 移动卡片）、详情抽屉关联维护、部署单元详情抽屉关联维护、制品类型按钮式单选、列表列与筛选。
  5. 种子：菜单 `816`、权限 `8161/8162`、字典类别 `ARCH_ARTIFACT_TYPE`（容器/压缩包/脚本）。
- 明确不实施内容：不改 `release` 模块 mock；不做交付版本/制品/Excel 导入；不恢复逻辑子系统；不在部署单元**表单**中增加交付单元选择（避免只改关联也发布新版本）；不新增平台公共组件或独立字典表。
- 公共能力/跨模块契约影响：**有**。新增 2 个 Flyway 迁移于 `platform/infrastructure`（`V202`、`V203`），其余无跨模块契约变更；不存在既有文件修改（`git diff --name-status` 仅两个 `A`）。Owner 复核结论：**通过，issue 全部豁免，Q1—Q6 全部同意**（`docs/requirements/REQ-20260910-073-architecture-delivery-unit/owner-review-request.md`）。

## 风险

- 权限、数据范围与审计：查询沿用交付单元查看权限（兼容既有 `architecture:view/apply/manage`、`architecture:deployment-unit:view`）；写操作仅 `architecture:delivery-unit:manage`；所有请求经项目访问校验；写操作写 `SystemOperationAudit`，审计失败不阻断业务结果。
- 数据库迁移与存量兼容：只追加——3 张新表、1 处由主键蕴含的唯一键 `(tenant_id, physical_subsystem_id, id)`、1 个可空列、1 个字典类别与 3 个字典项、菜单与权限种子；**未修改任何已发布迁移、未改写存量行**；存量交付单元为空、存量部署单元该字段为 NULL。种子含 `fail-closed` 身份守卫。
- 外部访问、附件与敏感数据：无外部系统访问、无附件、无敏感数据。
- 兼容性和已知风险：见下方“未关闭反馈与剩余风险”。

## 验证

- [ ] `node scripts/check-all-governance.mjs` —— **未通过**，三项均为**本任务之前既有的基线故障**：① `V84_1__seed_requirement_system_catalog.sql` 命名不符合 `V<number>__description.sql`；② `business/requirement` 与 `platform/boot` 5 处越界导入；③ `docs/requirements/REQ-20260904-061-.../codex-task-scope.yaml` 非 JSON 兼容 YAML。本需求未触及上述文件。
- [x] 当前任务 `check-codex-scope.mjs`
- [x] 目标模块测试
- [ ] `mvn test`（全仓）—— **未执行**；实际执行的是 `mvn -pl :ccb-architecture -am test`
- [x] `npm --prefix web run build`
- [x] API 与权限验证
- [x] 浏览器验收（涉及用户流程）

实际命令、结果及 `.ai-control` 证据：

| 命令 | 结果 |
| --- | --- |
| `node scripts/check-codex-scope.mjs --scope docs/requirements/REQ-20260910-073-architecture-delivery-unit/codex-task-scope.yaml --base 3f0098b --head HEAD` | `Codex scope check passed for REQ-20260910-073; 63 changed file(s).` |
| `mvn -pl :ccb-architecture -am test` | `Tests run: 463, Failures: 0, Errors: 0`，`BUILD SUCCESS`（含真实 MySQL 8.4 + 完整 Flyway 的 13 项交付单元集成测试） |
| `npm --prefix web run build` | 通过（`vue-tsc --noEmit` + `vite build`） |
| Playwright + Chromium 桌面/移动端 | `accept 16/16`、`verify-artifact-type 10/10`、`verify-drawer-close-guard 15/15`、`verify-deployment-side-relation 7/7`、`verify-empty-slot 4/4`、`accept-mobile 7/7`，共 59 项全 PASS，无 console 错误 |
| `python3 .../control_loop.py gate` | `收敛门禁：PASS（通过）`；9 次采样 P0=0/P1=0；11 条不变量全部 pass |

账本证据：`.ai-control/requirements/req-20260910-073-architecture-delivery-unit/`（`state.json` phase=`converged`、`convergence.json`、`execution` 等价物 `iteration-I1..I21.json`、`control-definition{,-2,-3}.json`、`control-plan{,-2,-3}.json`）。

未关闭反馈与剩余风险：**未关闭反馈 0 条**（F-1—F-6 全部裁决并复验关闭）。剩余风险 8 条，完整列表见 `convergence.json` 的 `residual_risks`，摘要：

1. 未用无权限账号做端到端 403 验证（仅控制器注解开层与代码路径）
2. 部署单元作废守卫未对本地演示数据真实执行，以真实数据库集成断言为准
3. 全部观测由同一 Agent 执行，属非独立验证
4. 三项治理基线故障（见上）为本任务之前既有，未修复
5. 移动端仅 Chromium 视口模拟（390×844），未真机验证
6. 部署单元侧关联编辑复用交付单元维护权限，只持 `architecture:deployment-unit:manage` 的角色看不到入口
7. 制品类型字典项被停用/删除后，历史数据 label 会回落显示原始 code
8. `UiFormDrawer` 单向绑定语义未改（平台公共组件不在本需求范围），其他页面若做未保存守卫需按同一模式处理

## 发布与回退

- 上线后验证：确认菜单 `816` 与权限 `8161/8162` 生效、管理员与架构角色可访问、其他角色无越权入口；抽查一条交付单元的创建、关联保存与部署单元反查；核对审计记录。
- 回退/补偿步骤：回退本需求提交；停用菜单 `816` 与权限 `8161/8162` 后重新发布；三张新表为空时可直接 `DROP`，已产生数据先导出；`V203` 新增列与字典行可保留（不影响其他功能）。
- 人工专项复核人（高风险变更）：`rokeyvvz0828` —— **已完成**（2026-09-11T10:18:00+08:00，结论：通过，issue 全部豁免）
