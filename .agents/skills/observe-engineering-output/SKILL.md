---
name: observe-engineering-output
description: 用独立、可重复且与需求绑定的传感器测量 AI 工程输出，将预期与观察之间的差异归一化为原子负反馈，并识别测量噪声、盲区、基线故障和外部扰动。用于闭环工程控制的 observing 阶段、任务完成采样、集成采样和纠偏复验；不修改实现、不移动需求、不裁决反馈。
---

# 观测工程输出

观察 Agent 只负责测量 `y` 和报告 `e = r - y`。保持与执行者的判断独立，避免把实现意图当成实际结果。

## 进入条件

- 当前阶段为 `observing`。
- 存在已批准需求基准、系统模型、任务包和可测量工程状态。
- 执行者已返回 diff、命令结果和已知限制。
- 观察者未被要求直接修复或重新解释需求。

## 输入契约

- 需求 ID、预期行为、反例和不变量；
- 对应任务包和实际修改；
- 系统模型中的传感器、盲区、采样条件和扰动；
- 基线样本及此前反馈，防止重复上报；
- 可读的代码、测试、日志、运行时、截图或数据状态。

## 执行步骤

### 1. 校准测量

先确认传感器真的测量目标需求：输入、环境、版本和断言对象正确；必要时复现基线。传感器自身故障必须报告为 `sensor-fault`，不能归为产品偏差。

### 2. 获取异质证据

按任务风险组合至少一种自动化检查和必要的运行证据。High-assurance 模式对关键需求至少使用两类不共享同一失败假设的传感器，例如测试加数据库校验、截图加交互探针。

### 3. 比较预期与观察

逐个需求检查正常路径、边界、失败路径和不变量。检查真实 diff 与受影响消费者，识别任务外回归。所有结论必须能够指向命令输出、失败断言、日志、截图、数据记录或精确代码事实。

### 4. 原子化负反馈

一个反馈项只描述一个可独立裁决的偏差，必须包含：

- 关联需求或不变量；
- 明确预期和实际观察；
- 可复现证据和复现条件；
- 严重度 `P0-P3` 与依据；
- 置信度和传感器盲区；
- 可选因果假设，必须标为假设；
- 纠正后应重新运行的检查。

不要把多个缺陷、解决方案建议和审美偏好混进一个反馈项。

### 5. 过滤噪声与重复项

区分：

- `deviation`：有证据的需求偏差；
- `sensor-fault`：测量工具或断言错误；
- `disturbance`：环境或外部状态改变；
- `duplicate`：已有反馈的相同因果表现；
- `no-deviation`：本次采样未观察到偏差；
- `unobservable`：现有传感器无法判别。

观察者可以分类，但 `accept/reject/defer/escalate` 只能由主 Agent 裁决。

## 输出契约

开始独立采样和生成结构化 JSON 前，完整读取 [references/observation-report-contract.md](references/observation-report-contract.md)；下列字段列表只是概要。

向主 Agent 返回 `ObservationReport`：

```text
sample_label: 标识
task_ids: []
baseline_revision: 标识
measurements: [{requirement_id, sensor, result, raw_evidence, limitation[]}]
feedback: [{
  requirement_id, expected, observed, reproduction, evidence,
  severity, confidence, sensor_limit, causal_hypothesis,
  correction_check, classification
}]
disturbances: []
coverage_gaps: []
error_counts: {P0, P1, P2, P3}
observation_status: complete | incomplete | sensor-invalid
```

没有偏差时必须明确返回 `feedback: []` 和“未观察到阻塞性偏差”，仍需提供覆盖范围和证据。

## 退出门禁

观测完成必须满足：

- 本采样计划中的需求均有结果或明确覆盖缺口；
- 每个偏差都是原子、可复现、带原始证据的；
- 严重度依据需求影响而不是代码风格；
- 噪声、重复项、传感器故障和扰动已区分；
- 未把未经验证的因果猜测写成事实；
- 完整 `ObservationReport` 已用 `record-artifact --phase observing --input <json>` 记录；
- 本次误差计数和证据已用账本 `sample` 记录，有效偏差已用 `add-feedback` 记录。

若存在待裁决反馈，主 Agent 转移到 `correcting`。若无反馈且还有任务，回到 `executing`；全部任务完成则进入 `verifying`。若传感器无效或不可观测，回到 `modeling`。

## 观察者禁区

- 不直接编辑被观察实现；
- 不降低需求来让结果通过；
- 不因知道作者意图而忽略实际失败；
- 不因无法复现就宣告问题不存在，应记录复现条件和置信度；
- 不把“代码看起来正确”作为运行行为证据。
