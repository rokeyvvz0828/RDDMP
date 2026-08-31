---
name: model-engineering-system
description: 为 AI 软件项目建立能支持控制决策的最小系统模型，识别被控对象边界、关键状态、输入输出、接口、数据流、传感器、执行器、扰动、时延、假设及可观测性和可控性。用于闭环工程控制的 modeling 阶段，或执行停滞、反馈振荡、隐藏耦合和环境变化后重新建模；不负责移动需求基准或直接实施修复。
---

# 建模工程系统

模型必须足以解释“偏差怎样产生、怎样被看见、什么动作能改变它”，不追求描述整个仓库。

## 进入条件

- 已有通过门禁的 `RequirementBaseline`。
- 当前阶段为 `modeling`，或后续阶段因不可观测、不可控、扰动或模型失效回退。
- 可以只读检查仓库、运行环境、依赖、测试和已有改动。

## 输入契约

- 需求基准及其修订号；
- 账本 `predevelopment.handoff.control_seed` 中明确标记的候选边界、传感器、执行器、扰动、时延和假设（如有）；
- 仓库指令和所有权边界；
- 当前代码、配置、数据结构、依赖和运行入口；
- 现有测试、日志、监控、截图或其他传感器；
- 权限、可修改范围、用户并行改动和外部系统限制。

## 执行步骤

### 1. 确定被控对象边界

列出系统内对象和环境对象。边界至少覆盖受影响的入口、状态持有者、共享契约、关键消费者和外部依赖。文件清单只能作为证据，不能代替边界模型。

若存在开发前 `control_seed`，逐项用代码、运行环境或接口事实验证。保留被证实项，修正被推翻项，并记录未能验证的盲区；不得因为候选项写在已批准计划中就把它升级为模型事实。

### 2. 建立最小因果链

对每个需求追踪：

```text
输入 -> 状态转换 -> 接口/数据流 -> 可观察输出
```

记录关键状态、前置条件、失败路径、异步或缓存时延。只保留可能产生需求误差的节点和耦合。

### 3. 识别传感器

逐个验证候选传感器：它测量什么、何时采样、能否区分正确与错误、可能漏掉什么、是否与其他传感器共享同一错误假设。优先使用原始、可重复、接近运行行为的证据。

测量映射必须覆盖每个 `must` 需求。若没有判别力，先设计新的断言、日志、探针、截图或数据检查，不进入任务规划。

### 4. 识别执行器

执行器不是“能编辑文件”，而是能改变偏差因果来源的动作，例如代码、配置、迁移、测试设施、接口适配或任务边界调整。记录每个执行器的影响面、权限、回滚方式和最大允许动作。

### 5. 识别扰动和时延

记录依赖升级、外部服务、环境差异、并行修改、偶发测试、缓存、队列和反馈延迟。将偶发性和主观意见视为测量噪声，不能直接驱动高增益修改。

### 6. 判断可观测性与可控性

对每个 `must` 需求分别给出：

- `observable=yes|partial|no`，以及传感器证据和盲区；
- `controllable=yes|partial|no`，以及能影响因果来源的执行器；
- 当前模型假设和能推翻它的证据；
- 若为 `partial/no`，应增加传感器、扩大授权、隔离扰动或升级用户的动作。

不得用“应该可以”替代判定依据。

## 输出契约

生成结构化 JSON 前，完整读取 [references/engineering-system-model-contract.md](references/engineering-system-model-contract.md)；下列字段列表只是概要。

向主 Agent 返回 `EngineeringSystemModel`：

```text
baseline_revision: 标识
plant_boundary: {inside: [], environment: []}
state_variables: []
causal_paths: [{requirement_id, input, transitions[], outputs[]}]
interfaces: [{name, producers[], consumers[], invariant}]
sensors: [{id, target, method, blind_spots[], independence}]
actuators: [{id, target, action, authority, impact_radius, rollback}]
measurements: [{requirement_id, sensor_ids[], observable}]
disturbances: [{source, effect, detectable, controllable}]
delays: [{source, consequence, sampling_rule}]
assumptions: [{text, evidence, falsifier}]
control_assessment: [{requirement_id, observable, controllable, action}]
model_status: ready | blocked
```

## 退出门禁

进入 `planning` 前必须满足：

- 每个 `must` 需求存在从输入到输出的最小因果链；
- 每个 `must` 需求至少有一个有判别力的传感器；
- 每个已知偏差至少有一个能触及因果来源的执行器；
- 共享接口、数据所有者和关键消费者没有遗漏；
- 扰动、时延、假设和传感器盲区已显式记录；
- 所有模型结论都可追溯到工程事实，而不是仅由推理生成。

不可观测时先增加传感器；不可控时先调整授权、边界、适配或降级策略。必须需求既不可控又无法隔离时，返回 `blocked` 并升级用户。

门禁通过后，将完整 `EngineeringSystemModel` 保存为 JSON，并执行 `record-artifact --phase modeling --input <json>`。该命令同时同步门禁使用的模型摘要；不要用 `update-model` 的字符串摘要替代完整产物。记录成功后才能请求主 Agent 转移到 `planning`。

## 重新建模条件

- 观察结果推翻因果假设；
- 同类纠正连续失败；
- 两个修复相互抵消或振荡；
- 用户或其他进程改变代码和环境；
- 共享契约出现未建模消费者；
- 测试通过但真实行为失败，说明传感器模型有误。
