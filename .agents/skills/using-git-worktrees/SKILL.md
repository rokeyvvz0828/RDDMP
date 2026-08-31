---
name: using-git-worktrees
description: 在开始需要隔离的功能开发、执行已批准实施计划或用户提到 worktree、隔离分支和并行开发时使用；先识别现有 Codex 工作区，再优先使用宿主原生能力，最后才安全创建 Git worktree，并验证依赖和测试基线。
---

# 使用 Git 工作树隔离开发

## 概述

确保功能开发发生在隔离工作区。先检测当前环境，再使用宿主原生工作区工具；没有原生工具时才回退到 `git worktree`。

核心原则：先识别现有隔离，再使用原生能力，最后回退到 Git；不要与宿主环境争夺工作区所有权。

开始时说明：“正在使用 using-git-worktrees Skill 准备隔离工作区。”

## 步骤 0：检测现有隔离

在创建任何内容前执行只读检查。

PowerShell：

```powershell
$gitDir = git rev-parse --path-format=absolute --git-dir 2>$null
$gitCommon = git rev-parse --path-format=absolute --git-common-dir 2>$null
$branch = git branch --show-current
$superproject = git rev-parse --show-superproject-working-tree 2>$null
$root = git rev-parse --show-toplevel 2>$null
```

Bash：

```bash
GIT_DIR=$(cd "$(git rev-parse --git-dir)" 2>/dev/null && pwd -P)
GIT_COMMON=$(cd "$(git rev-parse --git-common-dir)" 2>/dev/null && pwd -P)
BRANCH=$(git branch --show-current)
SUPERPROJECT=$(git rev-parse --show-superproject-working-tree 2>/dev/null)
ROOT=$(git rev-parse --show-toplevel 2>/dev/null)
```

如果命令失败，当前目录不是 Git 仓库。报告事实并在当前目录继续，不创建工作树。

`GIT_DIR != GIT_COMMON` 在子模块中也可能成立。`SUPERPROJECT` 非空时按普通仓库处理，不把子模块误判为链接工作树。

若 `GIT_DIR != GIT_COMMON` 且不是子模块：

- 已经处于链接工作树，直接进入“项目初始化”，不得嵌套创建。
- 有分支时报告完整路径和分支名。
- `branch` 为空时报告 detached HEAD，并记录“收尾阶段需要由宿主创建分支或移交到本地”。

若处于普通检出：检查用户或 `AGENTS.md` 是否已经声明工作树偏好。没有声明时先询问：

> 是否要建立隔离工作树？它可以保护当前分支不受本次开发修改影响。

用户拒绝时在当前目录继续，跳到项目初始化。

## 步骤 1：创建隔离工作区

### 1A. 宿主原生工作区工具

用户同意隔离后，先检查当前 Codex/IDE 是否提供工作区、worktree 或“创建分支”能力。存在时使用原生能力，并直接进入项目初始化。

原生工具负责目录、分支和清理。此时手工执行 `git worktree add` 会产生宿主无法管理的状态。

### 1B. Git worktree 回退

只有没有原生工具时才手工创建。

#### 目录选择

优先级：

1. 用户或项目指令指定的工作树目录；
2. 项目根目录已有 `.worktrees/`；
3. 项目根目录已有 `worktrees/`；
4. 默认使用项目根目录的 `.worktrees/`。

两者都存在时选 `.worktrees/`。

#### 验证忽略规则

项目内目录必须先证明被 Git 忽略：

```powershell
git check-ignore -q .worktrees
if ($LASTEXITCODE -ne 0) { git check-ignore -q worktrees }
```

```bash
git check-ignore -q .worktrees 2>/dev/null || git check-ignore -q worktrees 2>/dev/null
```

若未忽略，先把所选目录写入 `.gitignore`，展示变更并按项目提交规范建立提交，然后再创建工作树。不要让工作树内容进入版本控制。

#### 创建

分支名必须描述任务且不与已有分支冲突。PowerShell：

```powershell
$path = Join-Path $location $branchName
git worktree add $path -b $branchName
Set-Location $path
```

Bash：

```bash
path="$LOCATION/$BRANCH_NAME"
git worktree add "$path" -b "$BRANCH_NAME"
cd "$path"
```

若沙箱拒绝创建，报告权限限制并在当前目录继续。不要通过写入仓库外未知路径绕过沙箱。

## 步骤 2：项目初始化

先读取项目文档和锁文件，使用项目已声明的包管理器和命令。常见探测：

```text
package.json + 锁文件 -> 对应的 npm/pnpm/yarn/bun 安装命令
Cargo.toml -> cargo build
requirements.txt -> 项目指定的 Python 环境后再安装
pyproject.toml -> 项目声明的 uv/poetry/pip 工具
go.mod -> go mod download
```

不得仅因检测到文件就擅自在全局环境安装依赖。依赖下载需要网络或会产生大范围写入时，遵循宿主授权流程。

## 步骤 3：验证干净基线

运行项目对应测试，证明工作区从可识别状态开始：

```text
npm test | cargo test | pytest | go test ./... | 项目文档中的等价命令
```

测试失败时，报告失败数量、关键错误和命令，询问是先调查还是在已知基线下继续。未经用户选择不得把基线失败归因于本次工作，也不得继续后宣称全部通过。

测试通过时报告：

```text
工作区：<完整路径>
分支：<名称或 detached HEAD>
基线：<命令，N 个测试，0 个失败>
状态：可以开始 <功能名称>
```

随后把路径、分支和基线证据交给 `$control-engineering`，由闭环主 Agent 接管实施。

## 快速判定

| 情况 | 动作 |
|---|---|
| 已在链接工作树 | 不再创建，进入初始化 |
| 位于子模块 | 按普通仓库处理 |
| 有宿主原生工具 | 使用原生工具 |
| 无原生工具 | 回退到 `git worktree` |
| `.worktrees/` 已存在 | 使用它并验证忽略 |
| 两个目录都存在 | 使用 `.worktrees/` |
| 都不存在 | 按指令或默认 `.worktrees/` |
| 目录未忽略 | 更新 `.gitignore` 后再创建 |
| 创建被沙箱拒绝 | 报告并在当前目录继续 |
| 基线测试失败 | 报告并等待用户决定 |

## 红线

禁止：

- 已经隔离时创建嵌套工作树；
- 有宿主原生能力时直接运行 `git worktree add`；
- 未验证忽略规则就创建项目内工作树；
- 跳过项目初始化和基线测试；
- 未经允许在失败基线上继续；
- 清理不是本 Skill 创建或宿主管理的工作树。

