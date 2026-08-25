---
name: finishing-a-development-branch
description: 在实现已经完成、验证通过且需要决定如何集成 Git 工作时使用；先复验测试和工作区状态，再让用户在本地合并、推送并创建 PR、保留或丢弃之间明确选择，并按工作树来源安全清理。
---

# 完成开发分支

## 概述

用结构化选项完成开发工作。

核心顺序：验证测试 -> 检测环境 -> 确定基准分支 -> 展示选项 -> 执行选择 -> 按来源清理。

开始时说明：“正在使用 finishing-a-development-branch Skill 完成本次工作。”

## 步骤 1：验证测试

在提供任何集成选项前，运行项目完整测试套件和 `$verify-control-convergence` 要求的最终检查。

若测试失败：

```text
测试失败（N 个失败），当前不能合并或创建 PR：

<关键失败>

必须先修复或由用户明确接受残余风险。
```

停止，不进入环境检测和集成动作。只有闭环账本已经 `converged` 且最终测试证据仍有效，才能继续。

## 步骤 2：检测环境

PowerShell：

```powershell
$gitDir = git rev-parse --path-format=absolute --git-dir 2>$null
$gitCommon = git rev-parse --path-format=absolute --git-common-dir 2>$null
$branch = git branch --show-current
$worktreePath = git rev-parse --show-toplevel
```

Bash：

```bash
GIT_DIR=$(cd "$(git rev-parse --git-dir)" 2>/dev/null && pwd -P)
GIT_COMMON=$(cd "$(git rev-parse --git-common-dir)" 2>/dev/null && pwd -P)
BRANCH=$(git branch --show-current)
WORKTREE_PATH=$(git rev-parse --show-toplevel)
```

| 状态 | 菜单 | 清理方式 |
|---|---|---|
| 普通仓库 | 标准 4 项 | 无工作树可清理 |
| 链接工作树，有命名分支 | 标准 4 项 | 按来源判断 |
| 链接工作树，detached HEAD | 精简 3 项，不提供本地合并 | 宿主管理，不清理 |

当前目录不是 Git 仓库时，只报告完成证据和文件变化，不提供分支操作菜单。

## 步骤 3：确定基准分支

优先读取项目指令或计划中记录的基准分支。没有记录时检查：

```powershell
git merge-base HEAD main 2>$null
if ($LASTEXITCODE -ne 0) { git merge-base HEAD master 2>$null }
```

```bash
git merge-base HEAD main 2>/dev/null || git merge-base HEAD master 2>/dev/null
```

仍无法确定时，只问一个问题确认基准分支，不自行猜测。

## 步骤 4：展示选项

普通仓库或有命名分支的工作树只展示：

```text
实现和验证已经完成。请选择后续处理：

1. 在本地合并回 <base-branch>
2. 推送并创建 Pull Request
3. 保持当前分支不变，稍后处理
4. 丢弃本次工作

请选择编号。
```

detached HEAD 只展示：

```text
实现和验证已经完成。当前是宿主管理的 detached HEAD。

1. 创建新分支后推送并创建 Pull Request
2. 保持当前工作区不变，稍后处理
3. 丢弃本次工作

请选择编号。
```

保持菜单简洁，不把某一项当作默认授权。

## 步骤 5：执行选择

### 选项 1：本地合并

先定位主仓库根目录，再离开待删除工作树：

```powershell
$commonDir = git rev-parse --path-format=absolute --git-common-dir
$mainRoot = git -C (Join-Path $commonDir '..') rev-parse --show-toplevel
Set-Location $mainRoot
git checkout <base-branch>
git pull
git merge <feature-branch>
```

```bash
MAIN_ROOT=$(git -C "$(git rev-parse --git-common-dir)/.." rev-parse --show-toplevel)
cd "$MAIN_ROOT"
git checkout <base-branch>
git pull
git merge <feature-branch>
```

合并成功后重新运行最终测试。只有测试通过才进入清理，再用非强制方式删除功能分支：

```text
git branch -d <feature-branch>
```

`git pull` 会改变外部仓库状态并访问网络；执行前确认用户已选择本选项，遇到权限或冲突立即停止。

### 选项 2：推送并创建 PR

```text
git push -u origin <feature-branch>
```

使用项目已有 GitHub/GitLab 工具创建 PR。推送和创建 PR 都是外部操作，必须以用户选择为授权，并遵循项目 `AGENTS.md`、PR 模板和目标分支要求。保留工作树，便于处理评审反馈。

detached HEAD 时先通过宿主“创建分支”能力或用户批准的 Git 命令创建命名分支；无法创建时给出建议分支名、提交信息和 PR 文本，交由用户在 Codex App 或本地完成。

### 选项 3：保持不变

报告分支名和工作区完整路径，不清理工作树。

### 选项 4：丢弃

这是破坏性动作，必须先列出影响并要求精确确认：

```text
这会永久删除：
- 分支 <name>
- 提交 <commit-list>
- 工作树 <path>

请输入“丢弃”确认。
```

只有收到完全一致的确认后才能清理工作树，并用 `git branch -D <feature-branch>` 删除分支。detached HEAD 菜单中的“丢弃”为第 3 项，同样执行此确认门禁。

## 步骤 6：清理工作区

只为“本地合并”和“丢弃”清理；PR 和保留选项不得清理。

- 普通仓库：没有工作树可清理。
- 路径位于项目 `.worktrees/` 或 `worktrees/` 下：可视为本流程创建，离开该目录后执行 `git worktree remove <path>` 和 `git worktree prune`。
- 其他路径：视为 Codex/IDE 宿主管理，不得手工删除；使用宿主退出能力或保持不变。

移除工作树前必须先确认合并成功或收到精确丢弃确认，且当前工作目录已经切换到主仓库根目录。

## 快速参考

| 选项 | 合并 | 推送 | 保留工作树 | 删除分支 |
|---|---:|---:|---:|---:|
| 本地合并 | 是 | 否 | 否 | 是，非强制 |
| 创建 PR | 否 | 是 | 是 | 否 |
| 保持不变 | 否 | 否 | 是 | 否 |
| 丢弃 | 否 | 否 | 否 | 是，强制且需确认 |

## 红线

禁止：

- 测试失败或闭环未收敛时提供合并/PR；
- 合并后不复验；
- 未经选择就推送、创建 PR 或合并；
- 未经精确确认删除工作；
- 强制推送，除非用户另行明确要求；
- 合并成功前删除工作树；
- 在待删除工作树内部执行移除；
- 清理宿主管理或来源不明的工作树；
- 创建 PR 后立即删除工作树。
