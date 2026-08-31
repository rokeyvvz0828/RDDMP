---
name: using-superpowers
description: 在每个新任务开始时使用，先发现并调用适用 Skill，再进行任何回复、澄清、勘察或修改；负责把软件构建请求依次路由到 brainstorming、writing-plans、using-git-worktrees、control-engineering 和 finishing-a-development-branch。
---

<SUBAGENT-STOP>
如果你是被主 Agent 分派来执行一个具体有界任务的子 Agent，忽略本 Skill，严格执行任务包中指定的 Skill 和边界。
</SUBAGENT-STOP>

<EXTREMELY-IMPORTANT>
只要认为某个 Skill 有 1% 的可能适用于当前任务，就必须先调用它。

一旦 Skill 适用，就没有跳过它的自由。不得用“任务很简单”“先看看文件”等理由绕过。
</EXTREMELY-IMPORTANT>

# 使用工程化开发能力

## 核心规则

在任何回复或动作之前调用用户点名或可能适用的 Skill，包括提出澄清问题、勘察代码库、检查 Git 状态和进入计划模式。若读取后确认不适用，可以停止使用。

调用时先向用户说明：“正在使用 `[Skill 名称]` 完成 `[目的]`。”Skill 含检查清单时，为每一项建立可跟踪任务并按顺序完成。

## Skill 优先级

流程 Skill 先于领域或实现 Skill，因为前者决定怎样工作，后者决定具体实现。

```text
软件想法、功能或行为变更
  -> $brainstorming
  -> 用户确认设计文档
  -> $writing-plans
  -> 用户确认实施计划与交接包
  -> $using-git-worktrees（需要或用户同意隔离时）
  -> $control-engineering
  -> $finishing-a-development-branch（存在 Git 分支且用户需要集成时）
```

- 新建项目、增加功能、改变行为：先调用 `$brainstorming`。
- 已有经用户确认且仍有效的设计：调用 `$writing-plans`。
- 已有批准的 `PredevelopmentHandoff`：导入后调用 `$control-engineering`。
- 明确缺陷且用户授权修复：由 `$control-engineering` 建立基准、采集负反馈并纠偏；不要虚构本插件未包含的上游调试 Skill。
- 开始实施计划前：检查 `$using-git-worktrees` 是否适用。
- 实现收敛、测试通过并需要合并、PR 或保留分支：调用 `$finishing-a-development-branch`。

## 常见自我辩解

| 想法 | 事实 |
|---|---|
| “这只是一个简单问题” | 问题也是任务，先检查 Skill。 |
| “我需要先补充上下文” | Skill 检查发生在澄清问题之前。 |
| “我先快速看一下代码” | Skill 会规定如何勘察，先调用。 |
| “我只检查一下 Git” | Git 状态缺少用户意图，先检查 Skill。 |
| “这不需要正式流程” | 已存在适用 Skill 时必须使用。 |
| “我记得 Skill 内容” | Skill 会更新，必须读取当前版本。 |
| “流程有点重” | 设计篇幅可缩短，门禁不能省略。 |
| “先做一步再说” | 第一步仍然必须是 Skill 检查。 |
| “知道这个概念就够了” | 理解概念不等于执行 Skill。 |

## Codex 适配

在 Codex 中完整读取 [references/codex-tools.md](references/codex-tools.md)，再执行多 Agent、工作树或分支收尾操作。

## 用户指令优先级

用户直接要求以及项目中的 `AGENTS.md` 等指令高于 Skill；Skill 高于默认工作习惯。只有用户明确要求跳过某个流程或当前指令与 Skill 冲突时才能偏离，并要说明影响。

