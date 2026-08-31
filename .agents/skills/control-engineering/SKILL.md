---
name: control-engineering
description: 编排 AI 软件项目生成的工程控制闭环。由主 Agent 选择控制深度，按需求定标、系统建模、任务规划、受控执行、独立观测、偏差纠正和收敛验收七个阶段调用专用 Skill，并用共享状态账本限制阶段跳转。适用于复杂或含糊需求、多文件功能、跨模块重构、长期自主开发、多 Agent 协作、反复需求漂移，以及用户提到主 Agent、负反馈、闭环推理、验证 Agent 或迭代纠偏的场景。除非用户明确要求，不用于简单问答或单个显然修改。
---

# 闭环工程控制编排器

只负责闭环的控制深度、阶段路由、共享状态和停止判断。不要在本 Skill 中替代阶段 Skill 推导详细产物。

## 开发前入口与接管边界

当用户从想法开始创建项目或提出仍有实质歧义的重要功能，且没有已批准的设计与实施计划时，先调用 `$brainstorming`，再由其路由到 `$writing-plans`。这两个 Skill 只生成需求设计、计划和 `PredevelopmentHandoff`，不得实施。

收到批准的交接包后，先运行 `import-handoff`，再由本 Skill 接管。导入得到的基准可以通过复核后进入 `modeling`；开发前计划和控制模型种子只是候选输入，不能跳过系统建模或直接进入 `executing`。

已有明确、可验收规格且用户只要求实施时，可以直接建立 `RequirementBaseline`。进入闭环后，只有实质基准变化才回到 `$brainstorming` 进行设计修订；工程事实变化留在 `modeling/planning` 内处理。

## 保持控制器唯一

- 主 Agent 是唯一控制器，持有需求设定值、阶段状态、共享契约、反馈裁决和完成结论。
- 任务 Agent 是有界执行器，只能在任务包授权范围内行动。
- 观察 Agent 是传感器，只报告可复现偏差、证据和测量限制，不直接改变设定值或实现。
- 用户批准的需求基准是设定值。新事实可以更新系统模型，只有用户授权或已证明不可实现时才能重定标。
- 负反馈必须是“预期 - 观察”的有证据偏差；意见数量、Agent 共识和泛泛批评不是控制信号。

## 选择控制深度

| 模式 | 使用条件 | 最低控制要求 |
| --- | --- | --- |
| `light` | 局部、可逆、低风险修改 | 可在内存维护状态；一次实现和一次独立验证 |
| `standard` | 多文件、共享契约、需求有歧义或容易漂移 | 持久账本；至少两次采样；全部七阶段 |
| `high-assurance` | 安全、迁移、公共 API、跨系统或高回滚成本 | 持久账本；至少三次采样；异质传感器；回归和残余风险审计 |

风险、误差增长或回滚成本上升时升级模式。模式只能提高，除非用户明确批准降低。

## 驱动阶段状态机

```text
baseline -> modeling -> planning -> executing -> observing <-> correcting -> verifying -> converged
```

以下表格是阶段路由的唯一权威；未列出的转移一律禁止：

| 当前阶段 | 允许目标 |
| --- | --- |
| `baseline` | `modeling` |
| `modeling` | `baseline`、`planning` |
| `planning` | `modeling`、`executing` |
| `executing` | `modeling`、`planning`、`observing` |
| `observing` | `modeling`、`executing`、`correcting`、`verifying` |
| `correcting` | `modeling`、`planning`、`executing`、`observing`、`verifying` |
| `verifying` | `modeling`、`observing`、`correcting`、`converged` |
| `converged` | 无 |

使用 `scripts/control_loop.py transition` 执行阶段转移。`converged` 转移会先运行收敛门禁，门禁不通过时必须保留在 `verifying`。

## 按阶段调用 Skill

每次只把当前阶段需要的上下文加载给对应 Skill；阶段通过退出门禁后再调用下一阶段。

| 阶段 | 必须调用 | 主 Agent 接收的结果 |
| --- | --- | --- |
| `baseline` | `$establish-requirement-baseline` | 可追踪需求基准与测量意图 |
| `modeling` | `$model-engineering-system` | 最小系统模型、可观测性和可控性结论 |
| `planning` | `$plan-controlled-tasks` | 有界任务包、依赖顺序和采样计划 |
| `executing` | `$execute-controlled-task` | 实际修改、局部证据、扰动和未决项 |
| `observing` | `$observe-engineering-output` | 独立测量和原子反馈项 |
| `correcting` | `$correct-engineering-deviation` | 反馈裁决、限幅纠正和复验结果 |
| `verifying` | `$verify-control-convergence` | 门禁结果、残余风险和完成判定 |

同一任务可循环 `executing -> observing -> correcting -> observing`。不得因迭代次数、Token 压力或主观满意提前退出。

## 维护共享账本

初始化、恢复中断任务或诊断门禁前，完整读取 [references/ledger-and-command-contract.md](references/ledger-and-command-contract.md)，按其中的真值层级、命令副作用和退出码解释账本。

Standard 和 High-assurance 模式默认使用目标项目内的 `.ai-control/state.json`。所有阶段共享同一文件，不得各建私有事实源。若从开发前流程进入，完整交接包保存在账本 `predevelopment` 区域，需求基准仍以 `stage_artifacts.baseline` 为权威。

每个阶段先按对应 Skill 生成其输出契约 JSON，再调用 `record-artifact` 将完整结构存入账本的 `stage_artifacts`。JSON 文件只是命令输入，可以随后删除；共享账本是唯一权威状态。缺少当前阶段产物时，`transition` 必须拒绝进入下一阶段。

没有交接包时，初始化后进入 `baseline`：

```text
python <skill-dir>/scripts/control_loop.py init --state .ai-control/state.json --mode standard --objective 目标 --requirement R1=可观察的必须行为
```

已有批准的开发前交接包时导入：

```text
python <skill-dir>/scripts/control_loop.py import-handoff --state .ai-control/state.json --input .ai-control/<topic>-handoff.json --mode standard
```

导入成功必须显示 `账本阶段=baseline；下一阶段=modeling`。不得把开发前任务直接同步为受控任务。

阶段交接：

```text
python <skill-dir>/scripts/control_loop.py record-artifact --state .ai-control/state.json --phase baseline --input .ai-control/baseline.json --evidence 需求基准已审查
python <skill-dir>/scripts/control_loop.py transition --state .ai-control/state.json --to modeling --evidence 需求基准门禁通过
python <skill-dir>/scripts/control_loop.py status --state .ai-control/state.json
```

各阶段必须使用 `record-artifact` 保存完整输出契约，并按其 Skill 使用 `set-task`、`sample`、`add-feedback`、`decide`、`resolve` 和 `gate` 更新操作状态。兼容命令 `update-model` 和 `add-task` 不能替代完整阶段产物。命令失败表示当前控制条件不成立，不得手工修改 JSON 绕过门禁。

## 处理异常

- 需求冲突：停止实现，回到 `baseline` 或升级给用户。
- 观测不足：回到 `modeling` 增加传感器，不以推理填补证据。
- 动作无效：回到 `planning` 重划执行器和任务边界。
- 反馈振荡：冻结冲突表面，交给 `$correct-engineering-deviation` 诊断延迟、噪声和隐藏耦合。
- 外部扰动：记录扰动，重验受影响模型和需求；不要偷偷移动设定值。
- 用户并行修改：保护其修改，只重新估计当前被控对象。

## 完成条件

只有 `$verify-control-convergence` 返回通过，且账本成功转移到 `converged`，主 Agent 才能宣布完成。最终报告必须说明已满足需求、关键证据、延期的非关键偏差、模型不确定性和残余风险。
