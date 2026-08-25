---
name: plan-controlled-tasks
description: 根据已批准需求基准和工程系统模型，将目标分解为可独立验证、写入边界清晰、控制增益受限的任务包，并定义依赖顺序、共享契约所有权、采样点、回滚和升级条件。用于闭环工程控制的 planning 阶段，以及任务边界失效或纠偏需要重新规划时；不执行代码修改，也不裁决反馈。
---

# 规划受控任务

任务包是主 Agent 发出的控制动作契约。按可验证结果拆分，而不是按文件数量或 Agent 数量拆分。

## 进入条件

- 需求基准已通过门禁且修订号明确。
- 系统模型为 `ready`，必须需求可观测且在授权范围内可控。
- 当前阶段为 `planning`，或因任务失效从执行/纠偏阶段回退。

## 输入契约

- `RequirementBaseline`；
- `EngineeringSystemModel`；
- 账本 `predevelopment.handoff.implementation_plan` 中的文件地图、任务候选、依赖和验证命令（如有）；
- 已有修改和必须保护的用户工作；
- 可用 Agent、工具、权限、时间和验证成本；
- 未完成任务、已接受反馈和最新扰动。

## 执行步骤

### 1. 对账开发前计划

若存在开发前计划，先将其每个任务与已验证的 `EngineeringSystemModel` 对账：确认文件存在性、接口消费者、依赖方向、传感器判别力、写入权限和回滚可行性。可复用目标与验证意图，但必须修正被模型推翻的路径和顺序。开发前任务不是 `ControlledTask`，不能原样执行。

### 2. 按闭环结果切分

每个任务必须能在一个局部闭环内完成：读取输入事实、施加有限动作、产生可观察结果、通过指定检查。若一个任务需要改变多个独立共享契约，将其拆分或交给主 Agent 统一持有。

### 3. 建立任务依赖图

标注前置任务、共享接口、数据迁移顺序和集成点。只有写入面、契约和运行资源互不冲突的任务才可并行。存在以下任一情况时串行：

- 同时修改同一文件或生成物；
- 一个任务消费另一个任务正在改变的接口；
- 共享数据库、端口、测试环境或迁移顺序会互相污染；
- 验收证据依赖尚未稳定的上游输出。

### 4. 定义任务包

每个 `ControlledTask` 必须包含：

- 稳定任务 ID 和单一结果目标；
- 覆盖的需求 ID；
- 已证实输入事实及来源；
- 允许写入的所有权边界；
- 必须保持的不变量和共享契约；
- 明确非目标和禁止动作；
- 有顺序的动作范围，不写未经验证的微观实现；
- 至少一个正常、边界或失败路径验收检查；
- 需要返回的 diff、命令结果、运行证据和扰动；
- 最大纠正范围、回滚点、停止和升级条件。

### 5. 设计采样计划

按风险和系统变化速度安排采样：基线、任务完成、共享契约集成、纠偏后和最终验收。定义每个采样点由谁观察、使用什么传感器、判别哪些需求。避免每改一行完整评审，也避免所有任务结束后才首次测量。

### 6. 检查覆盖与可执行性

建立 `需求 -> 任务 -> 传感器` 三向追踪。任务范围必须足以影响因果来源，但不能大到无法定位偏差。估计影响半径和回滚成本；高风险动作拆成更小步并提高采样频率。

## 输出契约

生成结构化 JSON 前，完整读取 [references/control-plan-contract.md](references/control-plan-contract.md)；下列字段列表只是概要。

向主 Agent 返回 `ControlPlan`：

```text
baseline_revision: 标识
model_revision: 标识
tasks: [{
  id, goal, requirement_ids[], prerequisites[], input_facts[],
  write_scope[], invariants[], interface_contracts[], non_goals[],
  action_bounds[], acceptance_checks[], evidence_required[],
  rollback, stop_conditions[], escalation_conditions[]
}]
dependency_edges: [{from, to, reason}]
parallel_groups: [[task_id]]
integration_points: [{after_tasks[], checks[]}]
sampling_plan: [{label, trigger, observer, sensors[], requirement_ids[]}]
coverage: [{requirement_id, task_ids[], sensor_ids[]}]
plan_status: ready | blocked
```

## 退出门禁

进入 `executing` 前必须满足：

- 所有 `must` 需求至少由一个任务覆盖；
- 每个任务都有写入边界、验收检查、证据要求和升级条件；
- 依赖图无无法解释的环；
- 并行任务没有写入、契约或运行资源冲突；
- 共享契约存在唯一所有者和明确集成点；
- 采样频率与风险相称，且最终门禁所需证据可产生；
- 任务动作位于用户授权与系统可控范围内。

门禁通过后，将完整 `ControlPlan` 保存为 JSON，并执行 `record-artifact --phase planning --input <json>`。该命令从计划同步带写入范围、不变量、接口契约、动作限幅、证据、回滚、停止和升级条件的任务包；不要用精简 `add-task` 替代。记录成功后才能请求主 Agent 转移到 `executing`。

## 重新规划条件

- 执行发现任务输入事实错误；
- 写入面或共享消费者超出所有权边界；
- 验收无法区分正确与错误；
- 连续纠偏没有降低误差；
- 外部扰动改变依赖关系；
- 新的必须工作无法归入现有任务包。
