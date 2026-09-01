---
name: verify-control-convergence
description: 在闭环工程控制的 verifying 阶段独立检查需求覆盖、任务验证、反馈闭合、误差趋势、回归、不变量、运行证据和残余风险，判断系统是否稳定收敛并允许交付。适用于项目最终验收、重要集成点或纠偏后的完成判定；不以迭代次数、Agent 共识、代码整洁或单次测试通过替代收敛证据。
---

# 验证控制收敛

完成是一个门禁结论，不是执行者的状态声明。验收者应尽可能独立于主要实现者。

## 进入条件

- 当前阶段为 `verifying`。
- 所有计划任务已执行并经过观察；
- 所有新反馈已裁决；
- 已接受反馈已复验关闭，或存在明确阻塞等待门禁报告；
- 最终工程状态、账本和验证环境可读取。

## 输入契约

- 最新需求基准及修订历史；
- 系统模型和测量映射；
- 任务覆盖、执行报告和实际 diff；
- 全部样本、反馈裁决与关闭证据；
- 自动化测试、运行、视觉、数据、性能或安全证据；
- 延期项、未知项、扰动和残余风险。

## 执行步骤

### 1. 审计设定值稳定性

确认最终验收使用用户批准的最新基准。检查实现过程中是否未经授权删除、弱化或重新解释必须需求，以及是否引入非目标范围。

### 2. 审计追踪完整性

逐个 `must` 需求验证链路：

```text
需求 -> 任务 -> 实际修改 -> 传感器 -> 验收证据
```

任一链接缺失即为阻塞项。代码存在不等于行为已验证。

### 3. 运行最终采样

检查真实最终 diff 和受影响调用方，运行最小但完整的回归集合。根据需求类型补充运行、截图、交互、数据、迁移、性能或安全证据。High-assurance 模式关键需求至少需要两类异质传感器。

将最终误差计数和原始证据写入账本 `sample`。

### 4. 判断动态稳定性

检查连续样本，而不只看最后一点：

- `P0/P1` 是否归零；
- 总体严重误差是否下降；
- 是否存在相反状态来回切换的振荡；
- 纠正是否造成未测回归或影响面扩大的超调；
- 外部扰动下关键行为是否保持，或是否有明确受控降级；
- 传感器和模型盲区是否被披露。

### 5. 执行账本门禁

先将完整 `ConvergenceReport` 保存为 JSON，并执行 `record-artifact --phase verifying --input <json>`。然后运行：

```text
python <skill-dir>/scripts/control_loop.py gate --state .ai-control/state.json
```

非零退出表示闭环未收敛。不得手工改 JSON、忽略阻塞项或仅重跑到偶然通过。

### 6. 给出完成判定

结论只能是：

- `pass`：所有阻塞门禁通过，可转移到 `converged`；
- `return-to-observing`：证据不足或最终采样不完整；
- `return-to-correcting`：存在已证实偏差；
- `return-to-modeling`：传感器、因果模型或扰动假设失效；
- `blocked`：缺少用户决策、权限或外部条件。

## 输出契约

执行最终验收和生成结构化 JSON 前，完整读取 [references/convergence-report-contract.md](references/convergence-report-contract.md)；下列字段列表只是概要。

向主 Agent 返回 `ConvergenceReport`：

```text
baseline_revision: 标识
requirement_results: [{id, result, task_ids[], evidence[], limitations[]}]
task_results: [{id, status, evidence[]}]
feedback_summary: {total, accepted_open, escalated, deferred_by_severity}
sample_trend: [{sample_id, P0, P1, P2, P3, score}]
regression_checks: []
invariant_results: []
scope_audit: {within_scope, unexpected_changes[]}
disturbance_resilience: []
residual_risks: []
gate_result: pass | return-to-observing | return-to-correcting | return-to-modeling | blocked
route_reason: 证据
```

## 收敛门禁

只有以下条件同时成立才能 `pass`：

- 每个 `must` 需求被已验证任务覆盖并有有效证据；
- 每个必须需求绑定有效传感器；
- 所有任务状态为 `verified`；
- 所有反馈已裁决，已接受反馈均已复验关闭；
- 不存在延期或升级中的 `P0/P1`；
- Standard 至少两次、High-assurance 至少三次采样；
- 最终样本 `P0=0` 且 `P1=0`；
- 自动化和必要的运行证据通过；
- 最终修改位于批准范围内并保持不变量和共享契约；
- 延期 `P2/P3`、模型不确定性和残余风险已披露。

门禁通过后，主 Agent 执行 `transition --to converged`。若该命令拒绝转移，结论必须改为未收敛。

## 禁止的完成依据

- 已达到预定迭代次数；
- Token 或时间接近上限；
- 多个 Agent 都认为可以；
- diff 很小、代码整洁或编译成功；
- 单个正常路径测试通过；
- 尚未裁决的反馈被暂时忽略。
