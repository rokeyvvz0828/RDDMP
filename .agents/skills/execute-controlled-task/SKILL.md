---
name: execute-controlled-task
description: 在已批准任务包内实施有限、可逆、可测量的软件工程动作，保护用户已有修改，持续运行局部传感器，并把实际 diff、测试结果、扰动、假设失效和边界问题交还主 Agent。用于闭环工程控制的 executing 阶段或纠偏后的受控再执行；不允许自行扩大范围、移动需求基准、裁决反馈或宣布项目完成。
---

# 执行受控任务

把一个 `ControlledTask` 当作执行器命令。目标是在边界内产生可观察状态变化，而不是最大化代码量。

## 进入条件

- 当前阶段为 `executing`。
- 任务已在共享账本登记，状态为 `planned` 或经主 Agent 批准重新执行。
- 任务包包含需求 ID、写入范围、验收检查、证据要求、停止条件和升级条件。
- 前置任务和共享契约版本已经满足。

缺少任一关键字段时拒绝执行并返回任务包缺陷，不自行补齐授权。

## 输入契约

- 单个 `ControlledTask`；
- 对应需求条目和系统模型切片；
- 允许读取/写入路径、工具和运行资源；
- 当前工作树状态及用户已有改动；
- 最新基线证据、扰动和相关反馈。

## 执行步骤

### 1. 执行前采样

核对真实文件、调用方、测试和环境与任务输入是否一致。记录已有失败和用户改动，避免把基线故障归因于本任务。将任务状态设为 `active`。

### 2. 选择最小有效动作

只修改能影响目标偏差因果来源的表面。优先复用现有架构、依赖、组件和测试模式。每次动作保持可回滚，避免未授权重构、依赖升级、公共 API 改名和数据迁移。

### 3. 保持任务不变量

每个重要动作后运行成本最低且具有判别力的局部检查。持续核对：写入范围、共享契约、数据不变量、兼容性和用户改动。格式化或生成命令不得波及未授权文件。

### 4. 处理新事实

发现以下情况立即停止扩大动作并记录：

- 输入事实与仓库不符；
- 需要修改任务边界外的生产者或消费者；
- 现有传感器不能判断结果；
- 权限、依赖、环境或外部服务阻塞；
- 用户并行修改与任务发生重叠；
- 同一局部修复连续两次未降低误差。

这些是模型或计划反馈，不是继续猜测实现的理由。

### 5. 形成执行证据

检查真实 diff 和受影响调用方，运行任务包指定的检查。区分原始输出、解释和假设。不要声称未运行的测试通过，也不要用代码阅读替代运行证据。

## 输出契约

执行任务和生成结构化 JSON 前，完整读取 [references/execution-report-contract.md](references/execution-report-contract.md)；下列字段列表只是概要。

向主 Agent 返回 `ExecutionReport`：

```text
task_id: 标识
status: implemented | partial | blocked | boundary-invalid
changed_surfaces: [{path_or_resource, purpose}]
requirements_addressed: []
commands: [{command, exit_code, salient_output}]
local_checks: [{check, result, evidence, limitations[]}]
diff_summary: []
invariants_checked: [{invariant, result, evidence}]
disturbances: [{event, impact}]
assumptions_falsified: []
unresolved_items: []
recommended_next: observe | replan | remodel | escalate
```

报告必须包含可复查证据，不能只有“已完成”。

## 退出门禁

转移到 `observing` 前必须满足：

- 修改未超出任务包写入和权限边界；
- 任务指定的最低检查已运行，失败已如实记录；
- 实际 diff、影响面和命令结果已返回；
- 不变量无已知破坏；
- 新扰动、未知项和假设失效已上报；
- 输出处于可由独立观察器测量的状态。

任务实现不等于任务验证。将完整 `ExecutionReport` 保存为 JSON，执行 `record-artifact --phase executing --input <json>`，再将任务状态设为 `observing`。只有产物状态为 `implemented` 或 `partial` 时，主 Agent 才能转移到 `observing` 并调用观察 Skill。

## 停止与升级

- 边界无效：返回 `boundary-invalid`，回到规划阶段。
- 模型错误或传感器无效：返回 `remodel`，回到建模阶段。
- 必须改变需求或做不可逆动作：返回 `escalate`，等待用户授权。
- 局部检查失败但原因和动作仍在边界内：保留证据，进入观察阶段形成正式反馈，不隐瞒失败。
