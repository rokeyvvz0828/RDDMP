# licon 分支例外实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

## 状态与来源

- 计划修订：1
- 设计修订：1
- 设计文档：`docs/engineering-control/designs/2026-09-17-licon-branch-exception-design.md`
- 状态：已确认
- 用户确认依据：用户在本对话中确认实施本计划。

**目标：** 为精确分支名 `licon` 增加受控范围校验例外，同时保留默认需求分支规则及所有其他任务门禁。

**架构：** `GITHUB-RULES.md` 保持唯一事实源，`AGENTS.md` 和开发指南只同步摘要；范围校验脚本抽取可测试的分支判断函数，接受规范需求分支或精确 `licon`，其余验证流程不变。

**技术栈：** Markdown、Node.js ESM、Node 内置测试、Git 只读状态检查。

## 全局约束

- 只修改 `REQ-20260917-082` 授权路径；不改业务模块、数据库、PR、审批、Required Checks 或 `main` 保护。
- `licon` 是唯一例外，匹配必须大小写敏感且精确；不得使用通配、前缀或“非 main”逻辑。
- 正式规约、摘要和脚本行为同步；`main`、空值和其他非规范值仍失败。
- 既有 `ready`、模块 Owner、路径范围和禁止路径校验必须按原顺序继续执行。

---

## 文件职责地图

| 路径 | 状态 | 职责 |
| --- | --- | --- |
| `docs/governance/GITHUB-RULES.md` | existing | 分支规约唯一事实源。 |
| `AGENTS.md` | existing | 仓库规则摘要。 |
| `CODEX-DEVELOPMENT-GUIDE.md` | existing | 开发入口和分支流程说明。 |
| `scripts/check-codex-scope.mjs` | existing | 当前在第 52-54 行验证 `assignment.branch`。 |
| `scripts/check-codex-scope.test.mjs` | candidate-new | 分支值正负例回归测试。 |

## 任务依赖图与并行策略

`T1 -> T2 -> T3`。T1 先确定正式规则，T2 让脚本严格匹配该规则，T3 运行完整治理验证；无安全并行组。

## 需求覆盖表

| 需求 | 任务 |
| --- | --- |
| R1 正式例外 | T1, T3 |
| R2 接受 licon | T2, T3 |
| R3 拒绝无效分支 | T2, T3 |
| R4 保留其他门禁 | T2, T3 |

### T1：同步正式规约和摘要

**需求映射：** R1

**前置任务：** 无

**文件：**

- 修改：`docs/governance/GITHUB-RULES.md`
- 修改：`AGENTS.md`
- 修改：`CODEX-DEVELOPMENT-GUIDE.md`

**接口：** 消费现有默认分支格式；产出“规范需求分支或精确 `licon`”的正式文字规则。

- [ ] **步骤 1：记录当前规则基线。** 运行：`node scripts/check-codex-scope.mjs --scope docs/requirements/REQ-20260917-082-licon-branch-exception/codex-task-scope.yaml --working-tree`。预期：当前在 `licon` 上因分支格式失败；证据保存错误文本。
- [ ] **步骤 2：修改唯一事实源。** 在 `GITHUB-RULES.md` 保留默认格式，并增加 `licon` 的精确、唯一例外及“不改变 main/PR/审批/Checks”的限制。
- [ ] **步骤 3：同步摘要。** 在 `AGENTS.md` 和开发指南指向正式规则，说明例外仅在任务范围明确赋值为 `licon` 时适用，不可泛化。
- [ ] **步骤 4：检查文字一致性。** 运行：`rg -n "licon|REQ-YYYYMMDD" docs/governance/GITHUB-RULES.md AGENTS.md CODEX-DEVELOPMENT-GUIDE.md`。预期：三处描述无冲突；证据保存匹配行。

**验收：** 正式规则与摘要都同时表达默认格式、`licon` 精确例外和非范围行为不变。

**回滚：** 回退三个治理文档的本任务差异。

**停止条件：** 文字规则要求为任意其他分支或 `main` 放宽。

**升级条件：** 治理 Owner 要求变更 PR、分支保护或 CI Required Checks。

### T2：实现精确校验与回归测试

**需求映射：** R2, R3, R4

**前置任务：** T1

**文件：**

- 修改：`scripts/check-codex-scope.mjs`
- 新建：`scripts/check-codex-scope.test.mjs`

**接口：** 消费 `scope.assignment.branch` 字符串；产出可测试的 `isAllowedAssignmentBranch(branch)`，其结果仅为“默认正则匹配或 `branch === 'licon'`”。

- [ ] **步骤 1：建立失败测试。** 新增 Node 测试断言 `licon` 为真，`feat/REQ-20260917-082-licon-branch-exception` 为真，`main`、空字符串、`Licon` 和 `custom-branch` 为假。运行：`node --test scripts/check-codex-scope.test.mjs`。预期：添加 `licon` 断言前失败；证据记录失败输出。
- [ ] **步骤 2：抽取纯分支判断。** 在 `check-codex-scope.mjs` 以 ESM 导出无副作用的判断函数，并让现有 CLI 分支校验调用同一函数；保留原有错误和后续 Owner/需求/路径校验。
- [ ] **步骤 3：运行回归测试。** 运行：`node --test scripts/check-codex-scope.test.mjs`。预期：全部分支正负例通过；证据保存测试数和退出码。
- [ ] **步骤 4：验证实际范围。** 运行：`node scripts/check-codex-scope.mjs --scope docs/requirements/REQ-20260917-082-licon-branch-exception/codex-task-scope.yaml --working-tree`。预期：分支阶段通过，并继续执行其余已有门禁；证据保存最终结果。

**验收：** 仅 `licon` 作为例外通过，所有负例失败，现有规范需求分支仍通过。

**回滚：** 回退脚本和测试文件，恢复严格默认正则。

**停止条件：** 为实现测试而改变脚本的其它准入语义，或异常错误被吞掉。

**升级条件：** 现有脚本架构无法安全导入函数而不影响 CLI，需引入额外共享模块。

### T3：治理与范围收敛验证

**需求映射：** R1, R2, R3, R4

**前置任务：** T1, T2

**文件：**

- 测试：`scripts/check-codex-scope.test.mjs`
- 修改（仅修复已观测偏差）：T1/T2 列出的文件

**接口：** 消费正式规则、脚本函数和 `REQ-082` 范围；产出可重复的治理和范围证据。

- [ ] **步骤 1：运行脚本和治理检查。** 运行：`node --test scripts/check-codex-scope.test.mjs`、`node scripts/check-all-governance.mjs`、`git diff --check`。预期：全部退出码 0。
- [ ] **步骤 2：运行有效范围校验。** 运行：`node scripts/check-codex-scope.mjs --scope docs/requirements/REQ-20260917-082-licon-branch-exception/codex-task-scope.yaml --working-tree`。预期：`licon` 通过且路径范围/Owner/需求门禁仍生效。
- [ ] **步骤 3：验证负例。** 使用测试中受控的临时范围复制件分别设置 `main` 和 `custom-branch`，运行相同 CLI。预期：均以分支错误退出且不绕过后续治理；证据保存错误关键字。

**验收：** R1-R4 传感器全部通过，且没有新增其他分支例外。

**回滚：** 同时回退 T1 和 T2，恢复文档与脚本一致的默认规则。

**停止条件：** `main` 或任意自定义分支被接受，或治理检查发现规约与脚本不一致。

**升级条件：** CI 和本地 Node 版本产生不一致行为，或 Owner 要求将例外改为新的通用分支政策。

## 集成检查

执行 `node --test scripts/check-codex-scope.test.mjs`、`node scripts/check-all-governance.mjs`、`node scripts/check-codex-scope.mjs --scope docs/requirements/REQ-20260917-082-licon-branch-exception/codex-task-scope.yaml --working-tree` 和 `git diff --check`。不得以文档检查代替分支正负例。

## 控制模型种子

以下均为 `hypotheses-only`：被控边界是规约文字和范围校验；状态变量是输入分支与各门禁结论；传感器是 Node 测试、治理/范围检查和差异检查；执行器是三份文档、脚本和测试；扰动是大小写、空值、`main`、任意自定义分支和脚本导入副作用；时延是 Node CLI 和 Git 范围查询。

## 风险与用户批准

高风险是例外扩张或规约/脚本漂移。当前计划只接受精确值 `licon`，并以正负例锁定边界。用户已确认本计划，实施仍须遵循工程控制闭环。
