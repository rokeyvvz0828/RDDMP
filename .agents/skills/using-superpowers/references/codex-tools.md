# Codex 工具与环境适配

## 多 Agent 支持

需要分派有界任务或独立观察时，Codex 配置应启用多 Agent：

```toml
[features]
multi_agent = true
```

使用当前运行环境实际提供的创建、通信、等待和关闭 Agent 工具。主 Agent 始终持有需求基准、共享账本、任务边界和最终裁决；子 Agent 不得自行扩大范围。

## Git 环境检测

创建工作树或收尾分支前，先用只读命令识别环境。PowerShell：

```powershell
$gitDir = (git rev-parse --path-format=absolute --git-dir 2>$null)
$gitCommon = (git rev-parse --path-format=absolute --git-common-dir 2>$null)
$branch = git branch --show-current
$superproject = git rev-parse --show-superproject-working-tree 2>$null
```

Bash：

```bash
GIT_DIR=$(cd "$(git rev-parse --git-dir)" 2>/dev/null && pwd -P)
GIT_COMMON=$(cd "$(git rev-parse --git-common-dir)" 2>/dev/null && pwd -P)
BRANCH=$(git branch --show-current)
SUPERPROJECT=$(git rev-parse --show-superproject-working-tree 2>/dev/null)
```

- `GIT_DIR != GIT_COMMON` 且不在子模块中：已经位于链接工作树，不再嵌套创建。
- `BRANCH` 为空：处于 detached HEAD，通常由宿主环境管理；收尾时不能直接按普通本地分支合并。
- 命令失败：当前目录不是 Git 仓库。记录事实并跳过 Git 专属步骤，不把它当作开发阻塞。

## Codex 工作区收尾

如果 Codex App 创建了隔离工作区或沙箱禁止创建分支、推送或 PR：

- 完成所有授权范围内的实现、测试和提交准备；
- 给出建议分支名、提交信息和 PR 摘要；
- 告知用户使用 App 的“创建分支”或“移交到本地”能力继续；
- 不删除或清理不由本插件创建的工作树。

任何推送、创建 PR、合并、删除分支或丢弃工作的动作，都必须先获得用户对相应选项的明确选择。
