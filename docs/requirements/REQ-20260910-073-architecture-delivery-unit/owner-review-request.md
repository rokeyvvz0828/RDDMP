# REQ-20260910-073 交付单元 公共能力变更复核申请

> 提交人：ivanh（需求/研发）　接收人：模块 Owner **rokeyvvz0828**　提出日期：2026-09-11
> 复核类型：**追加 Flyway 迁移 + 菜单/权限种子 + 字典种子**（`platform/infrastructure` 高风险面）

## 1. 复核对象

| 项 | 内容 |
| --- | --- |
| 需求 | `REQ-20260910-073` 架构管理 - 交付单元（`status: ready`） |
| 需求文档 | `docs/requirements/REQ-20260910-073-architecture-delivery-unit/requirement.md` |
| 设计文档 | `docs/engineering-control/designs/2026-09-10-architecture-delivery-unit-design.md`（修订 3） |
| 任务范围 | `docs/requirements/REQ-20260910-073-architecture-delivery-unit/codex-task-scope.yaml` |
| 承载模块 | `business/architecture` |
| 分支 | `dev-ivanh`（需求分支命名规约：`feat/REQ-20260910-073-architecture-delivery-unit`；经用户确认在 `dev-ivanh` 实施） |
| 提交范围 | `3f0098b..71a20ae`（20 个提交，首个提交 `0ee2764`） |
| 受控账本 | `.ai-control/requirements/req-20260910-073-architecture-delivery-unit/`（当前 phase：`verifying`） |

## 2. 为什么需要 Owner 复核

`node scripts/check-codex-scope.mjs` 对 `platform` / `shared` / `composition` 类型的模块变更强制要求 `public_capability_change` 具备 **issue 引用 + Owner 审批 + 兼容性说明 + 回归测试**。本需求新增的 2 个 Flyway 迁移位于 `platform/infrastructure`，因此被判定为公共能力变更。

当前门禁输出（唯一未通过项）：

```text
Codex scope check failed for REQ-20260910-073:
public capability change requires issue, owner approval, compatibility and regression tests
```

未审批状态下 `codex-task-scope.yaml` 已如实登记：`required=true`、`issue=null`、`owner_approved=false`、`old_behavior_preserved=true`、`regression_tests` 已列 3 项。

## 3. 变更面（精确清单）

### 3.1 `platform/**` 下只有 2 个文件，且均为新增（无既有文件修改）

```text
A  V202__create_architecture_delivery_units.sql   +207
A  V203__delivery_unit_artifact_type.sql          +85
```

`git diff --name-status 3f0098b..71a20ae -- server/src/platform/.../db/migration/` 仅输出上述两个 `A`（新增），**未修改任何已发布迁移**；`shared/**`、`platform/system`、`platform/security`、平台参数管理实现均未改动。

### 3.2 V202 语句清单

| 语句 | 对象 | 说明 |
| --- | --- | --- |
| `ALTER TABLE arch_deployment_unit` | 既有表 | **仅追加唯一键** `uk_arch_deployment_unit_tenant_physical_id (tenant_id, physical_subsystem_id, id)`，用于关联表复合外键；该组合由主键 `id` 蕴含，不改变既有语义、不改写存量行 |
| `CREATE TABLE` | `arch_delivery_unit` | 新表：交付单元主记录（含软删除标记与乐观锁） |
| `CREATE TABLE` | `arch_delivery_unit_number_seq` | 新表：编号序列（行锁分配，序号不回收） |
| `CREATE TABLE` | `arch_delivery_unit_deployment_unit` | 新表：交付单元↔部署单元无方向关联，靠复合外键在数据库层强制“同一物理子系统” |
| `INSERT INTO sys_menu` | 菜单 `816 交付单元`（父 `800`） | 路由 `/architecture/delivery-units` |
| `INSERT INTO sys_menu_permission` ×2 | `8161` view、`8162` manage | `architecture:delivery-unit:view` / `architecture:delivery-unit:manage` |
| `INSERT IGNORE` ×4 | `sys_role_menu` / `sys_role_permission` / `sys_user_role` | 授予角色 1（超级管理员）与 111（技术架构师）；持有既有 `architecture:view/apply/manage`(8031/8032/8033) 或 `architecture:deployment-unit:view`(8041) 的存量角色获得查看权限与菜单 |
| 临时表守卫 | `tmp_arch_v202_seed_guard` | 身份不符时**失败关闭**，避免静默复用其他菜单/权限 ID |

### 3.3 V203 语句清单

| 语句 | 对象 | 说明 |
| --- | --- | --- |
| `ALTER TABLE arch_delivery_unit` | 本需求新建表 | 追加可空列 `artifact_type_code VARCHAR(64)`，存量行 NULL，不回填 |
| `INSERT INTO sys_dict_type` | 字典类别 `360016 / ARCH_ARTIFACT_TYPE / 制品类型` | 复用平台既有字典表，未建新表 |
| `INSERT INTO sys_config` ×3 | `360107/360108/360109` | 字典项：`architecture.artifact-type.container=容器`、`archive=压缩包`、`script=脚本`（`config_key` 使用命名空间，满足 `sys_config` 租户级唯一键） |
| 临时表守卫 | `tmp_arch_v203_seed_guard` | 同上 fail-closed |

### 3.4 授权模型（需要 Owner 判断是否可接受）

- 关联（交付单元↔部署单元）的**写入统一要求** `architecture:delivery-unit:manage`，与从哪一侧发起无关（交付单元侧接口与部署单元侧接口同一规则）。
- 部署单元侧只读候选/反查沿用**既有部署单元查看权限**集合，未放宽任何既有接口的授权表达式（新增独立端点而非扩权）。
- 前端显隐仅改善体验，服务端 `@PreAuthorize` + 项目范围 + 实体归属校验为最终依据。

## 4. 请 Owner 确认的问题

- [ ] **Q1** 在 `arch_deployment_unit` 上追加唯一键 `(tenant_id, physical_subsystem_id, id)` 可以接受（该组合由主键蕴含，仅为本需求关联表复合外键提供父表索引）。
- [ ] **Q2** 新增菜单 `816` 与权限 `8161/8162` 的编号与语义可以接受（`800` 下 `801`—`815` 已被既有架构菜单占用）。
- [ ] **Q3** 授予角色 1、111，以及“持有 `architecture:view/apply/manage` 或 `architecture:deployment-unit:view` 的存量角色自动获得交付单元查看权限与菜单”这一存量角色处理方式可以接受（不放宽写权限）。
- [ ] **Q4** 关联写入统一收敛到 `architecture:delivery-unit:manage`（即仅持有 `architecture:deployment-unit:manage` 的角色看不到部署单元侧的关联编辑入口）可以接受。
- [ ] **Q5** V203 向平台字典写入 1 个类别 + 3 个字典项（`ARCH_ARTIFACT_TYPE` / 容器 / 压缩包 / 脚本）可以接受，后续取值由参数管理维护。
- [ ] **Q6** 回退方案可以接受：回退代码 + 停用菜单 `816` 与权限 `8161/8162`；三张新表为空时可直接 `DROP`，已产生数据先导出；V203 新增列与字典行可保留（不影响其他功能）。

## 5. 客观证据（可复现，均已实际执行）

| 证据 | 命令 | 结果 |
| --- | --- | --- |
| 迁移在真实 MySQL 8.4 上执行 | `mvn -pl :ccb-architecture -am test -Dtest=DeliveryUnitMySqlTest -Dsurefire.failIfNoSpecifiedTests=false` | `Tests run: 13, Failures: 0, Errors: 0`；Flyway 迁移到 v203 成功，字典种子与守卫通过 |
| 架构模块完整回归 | `mvn -pl :ccb-architecture -am test` | `Tests run: 463, Failures: 0, Errors: 0`，`BUILD SUCCESS` |
| 前端构建 | `npm --prefix web run build` | 通过（`vue-tsc --noEmit` + `vite build`） |
| 浏览器端到端 | Playwright + Chromium（桌面 1440×900 / 移动 390×844） | `accept 16/16`、`verify-artifact-type 10/10`、`verify-drawer-close-guard 15/15`、`verify-deployment-side-relation 7/7`、`verify-empty-slot 4/4`、`accept-mobile 7/7`，无 console 错误 |
| 关联安全修复回归 | `DeliveryUnitMySqlTest` 守卫断言 | 部署单元被交付单元关联时作废返回 409（fail-closed） |
| 范围审计 | `node scripts/check-codex-scope.mjs --scope docs/requirements/REQ-20260910-073-architecture-delivery-unit/codex-task-scope.yaml --base 3f0098b --head HEAD` | 唯一未通过项即本文第 2 节的公共能力审批；文件集合全部落在 `writable_paths` |
| 治理检查 | `node scripts/check-governance.mjs` / `check-repo-skill.mjs` | 通过 |
| 迁移脚本检查 | `node scripts/check-flyway-migrations.mjs` | 因**既有** `V84_1__seed_requirement_system_catalog.sql` 命名不符合 `V<number>__description.sql` 而失败；该文件本需求未改动（基线故障） |
| 模块边界检查 | `node scripts/check-module-boundaries.mjs` | 因**既有** `business/requirement` 与 `platform/boot` 5 处越界导入而失败；与本需求无关（基线故障） |
| 账本布局检查 | `node scripts/check-ai-control-layout.mjs` | 因**既有** `docs/requirements/REQ-20260904-061-.../codex-task-scope.yaml` 非 JSON 兼容 YAML 而失败（基线故障） |
| 收敛门禁 | `python3 .agents/skills/control-engineering/scripts/control_loop.py gate --state .ai-control/requirements/req-20260910-073-architecture-delivery-unit/state.json` | 唯一阻塞项为 `ConvergenceReport 的 gate_result 不是 pass`（等待本审批）；6 条反馈全部关闭，8 次采样 `P0=0 / P1=0` |

UAT：`docs/requirements/REQ-20260910-073-architecture-delivery-unit/uat-checklist.md`（19 项清单 + 记录栏），本地真实环境执行中。

## 6. Owner 结论（已填写）

```text
结论：通过
条件（如有）：无
issue 引用：全部豁免
审批人 GitHub 账号：rokeyvvz0828
审批时间：2026-09-11T10:18:00+08:00
审批原文摘要（可公开引用，1—3 句）：
结论：通过；条件（如有）：无；issue 引用：全部豁免；审批人 GitHub 账号：rokeyvvz0828；审批时间：2026-09-11T10:18:00+08:00。
Q1—Q6 逐项意见（不同意请写明）：全部同意
```

回传原文已登记到 `codex-task-scope.yaml` 的 `public_capability_change`（`issue="豁免：owner-review-request.md#6-Owner-结论"`、`owner_approved=true`、`owner_approved_by`、`owner_approved_at`、`owner_approval_evidence`），范围门禁由失败转为通过：

```text
Codex scope check passed for REQ-20260910-073; 63 changed file(s).
```

## 7. 批准后我执行的命令（供 Owner 核对将要发生什么）

```bash
cd /home/ivanh/source/RDDMP
REQ_DIR=docs/requirements/REQ-20260910-073-architecture-delivery-unit
PREFIX=req-20260910-073-architecture-delivery-unit
AI_DIR=.ai-control/requirements/$PREFIX
STATE=$AI_DIR/state.json

# (1) 在任务范围内登记 Owner 审批结论（issue 用真实编号或豁免引用）
node -e '
const fs=require("fs");
const p="docs/requirements/REQ-20260910-073-architecture-delivery-unit/codex-task-scope.yaml";
const s=JSON.parse(fs.readFileSync(p,"utf8"));
s.public_capability_change.issue="<填入 Issue 号或豁免引用>";
s.public_capability_change.owner_approved=true;
s.public_capability_change.owner_approved_by="rokeyvvz0828";
s.public_capability_change.owner_approved_at="<ISO-8601>";
s.public_capability_change.owner_approval_evidence="<审批原文摘要>";
delete s.public_capability_change.issue_note;
fs.writeFileSync(p,JSON.stringify(s,null,2)+"\n");
'

# (2) 范围门禁复跑：应输出 “Codex scope check passed”
node scripts/check-codex-scope.mjs --scope $REQ_DIR/codex-task-scope.yaml --base 3f0098b --head HEAD

# (3) 收敛报告改为 pass（唯一改动是 gate_result 与 route_reason，其余证据不变）
node -e '
const fs=require("fs");
const p=".ai-control/requirements/req-20260910-073-architecture-delivery-unit/convergence.json";
const r=JSON.parse(fs.readFileSync(p,"utf8"));
r.gate_result="pass";
r.route_reason="全部技术门禁通过；Owner 已按第 6 节结论完成 platform/infrastructure 迁移与权限种子复核";
r.residual_risks=r.residual_risks.filter(x=>!x.startsWith("公共能力授权未闭合"));
fs.writeFileSync(p,JSON.stringify(r,null,2)+"\n");
'
python3 .agents/skills/control-engineering/scripts/control_loop.py record-artifact \
  --state $STATE --phase verifying --input $AI_DIR/convergence.json \
  --evidence "Owner 复核结论：<issue 引用>；审批人 rokeyvvz0828"

# (4) 收敛门禁 + 阶段转移
python3 .agents/skills/control-engineering/scripts/control_loop.py gate --state $STATE
python3 .agents/skills/control-engineering/scripts/control_loop.py transition \
  --state $STATE --to converged --evidence "Owner 审批完成，收敛门禁通过"

# (5) 把结论写回需求文档并提交
git add $REQ_DIR .ai-control/requirements/$PREFIX
git commit -m "chore(architecture): REQ-20260910-073 Owner 复核通过并收敛"
```

以上命令均不修改既有迁移、不改写存量数据、不连接生产系统。
