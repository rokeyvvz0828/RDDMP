# 共享账本与命令契约

在初始化账本、诊断阶段阻塞、恢复中断任务或解释 `control_loop.py` 行为前读取本文件。阶段工作方法仍以各阶段 Skill 为准。

## 账本中的四类真值

| 区域 | 作用 | 更新方式 |
| --- | --- | --- |
| `phase`、`phase_history` | 当前控制阶段及有证据的转移历史 | 仅由 `transition` 更新 |
| `stage_artifacts.<phase>` | 各阶段完整产物及历史修订 | 仅由 `record-artifact` 追加 |
| 顶层当前视图 | 当前目标、需求、模型摘要、任务、反馈和采样 | 由受控命令同步 |
| `predevelopment` | 导入的完整设计、计划和控制种子 | 仅由 `import-handoff` 建立 |

发生歧义时，需求基准以最新 `stage_artifacts.baseline` 为准，阶段以 `phase` 为准，任务运行状态以顶层 `tasks` 为准。`predevelopment` 是输入证据，不是已经验证的系统模型或控制计划。

## 关键顶层字段

```text
schema_version: 当前为 4
revision: 每次成功写入后递增
mode: light | standard | high-assurance
phase: baseline | modeling | planning | executing | observing | correcting | verifying | converged
phase_history: [{from, to, evidence, recorded_at}]
stage_artifacts: {阶段: [{revision, evidence, recorded_at, data}]}
objective, requirements, invariants, constraints, non_goals
predevelopment
control_model: {plant, sensors, actuators, disturbances, assumptions, measurements}
tasks, feedback, samples
```

不要手工修改这些字段。脚本会在写入前校验引用和状态，并通过同目录临时文件、刷盘和原子替换保存账本。

## 命令副作用

| 命令 | 主要写入 | 关键限制 |
| --- | --- | --- |
| `init` | 新账本和初始需求 | 已有文件需显式 `--force`；仍需记录 baseline 产物 |
| `import-handoff` | 新账本、开发前交接、baseline 修订 1 | 只接受完整批准的交接包，不生成模型和任务 |
| `record-artifact` | 当前阶段产物及同步视图 | `--phase` 必须等于当前阶段 |
| `update-model` | 模型摘要 | 兼容命令，不能代替 modeling 产物 |
| `add-task` | 简化任务 | 兼容命令，不能满足完整任务契约门禁 |
| `set-task` | 任务状态和证据 | `verified` 需要证据且不能有阻塞反馈 |
| `sample` | P0-P3 计数、加权分数和原始证据 | 分数只表示趋势，不替代原始严重度 |
| `add-feedback` | 原子反馈 | 已验证任务会退回 `observing` |
| `decide` | 反馈裁决 | `reject` 直接关闭；其他决策保留生命周期 |
| `resolve` | 关闭证据 | 只允许关闭已 `accept` 的反馈 |
| `transition` | 阶段和转移历史 | 同时检查静态阶段图和动态证据 |
| `status` | 无 | 始终用于人工诊断，不因阻塞返回失败 |
| `gate` | 无 | 存在阻塞项时退出码为 2 |

## 模式对最终门禁的影响

- `light`：不增加最终最少采样数和双传感器要求；使用脚本时仍受阶段产物和转移门禁约束。
- `standard`：至少两次采样，必须需求要有测量绑定。
- `high-assurance`：至少三次采样，并至少有两类独立传感器。

所有模式都要求任务被验证、反馈完成裁决、最终 P0/P1 为零，并存在 `gate_result=pass` 的收敛产物。

## 常见阻塞定位

- 无法进入 `modeling`：检查 baseline 产物、目标、必须需求和 `baseline_status`。
- 无法进入 `planning`：检查模型产物、边界、传感器、执行器及 must 需求测量映射。
- 无法进入 `executing`：检查计划产物、任务完整契约和 must 需求覆盖。
- 无法进入 `observing`：检查执行产物状态及是否存在可测量任务输出。
- 无法进入 `correcting`：检查观察产物和未关闭的可行动反馈。
- 无法进入 `verifying`：检查全部任务是否 verified、反馈是否裁决并关闭。
- 无法进入 `converged`：先运行 `gate`，逐项处理其阻塞输出，不要手改账本。

