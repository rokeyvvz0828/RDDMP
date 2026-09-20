---
id: REQ-20260917-082
status: ready
owner: rokeyvvz0828
module: governance
---

# licon 分支例外治理

## 业务目标

保留需求分支命名规范的默认约束，同时允许既有协作分支 `licon` 被明确填写在任务范围中并通过范围校验，避免为该分支上的受控任务重复创建无意义的临时分支。

## 范围

### 本次实施

- 在正式 GitHub 协作规约中增加 `licon` 的唯一分支命名例外。
- 同步仓库摘要与开发指南中的分支说明。
- 修改 `check-codex-scope.mjs`，使其接受规范需求分支或精确值 `licon`。
- 增加脚本回归测试，验证 `licon` 被接受，`main` 和其他任意分支仍被拒绝。

### 本次不实施

- 不允许任意自定义分支，不允许 `main` 作为任务分支。
- 不修改 PR、CODEOWNERS、Required Checks、推送、强推或审批规则。
- 不修改任何业务模块、数据库、前端或项目日历实现。

## 现状与规则

- `docs/governance/GITHUB-RULES.md` 是分支规约唯一事实源。
- `scripts/check-codex-scope.mjs` 当前只接受 `feat|fix|hotfix|docs|chore/REQ-YYYYMMDD-NNN-short-name`。
- 任务仍必须使用 `ready` 需求、完整范围、模块 Owner、最小可写路径和其余全部治理门禁。

## 接口与数据

- `codex-task-scope.yaml` 的 `assignment.branch` 校验规则扩展为“规范需求分支或 `licon`”。
- 不新增接口、数据库、迁移、外部访问或敏感数据。

## 验收标准

1. 正式规约明确 `licon` 是唯一既有协作分支例外，默认需求分支规则不变。
2. 使用 `assignment.branch: licon` 的有效范围文件通过校验。
3. `main`、空值和任意非规范自定义分支仍被拒绝。
4. 分支例外不改变 `ready`、Owner、范围、禁止路径、PR 与 Required Checks 校验。
5. 分支校验脚本测试、治理检查与差异空白检查通过。

## 测试与发布

- 必须执行：分支范围校验脚本测试、`node scripts/check-all-governance.mjs`、范围检查与 `git diff --check`。
- 上线验证：在 `licon` 上以本任务范围运行校验成功，并以 `main` 或无效分支样例确认失败。
- 回退：回退治理文档和脚本变更，恢复仅允许规范需求分支的规则。
- 风险与人工复核人：治理 Owner 复核例外边界和 CI 行为。
