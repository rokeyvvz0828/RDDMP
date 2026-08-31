# CorrectionDecision 产物契约

在裁决反馈和生成 correcting 阶段 JSON 前读取本文件。每份产物对应一个反馈；多项反馈分别形成产物并逐项写入账本。

## 完整示例

```json
{
  "feedback_id": "F-0001",
  "decision": "accept",
  "decision_reason": "独立集成检查稳定复现 R1 偏差",
  "dynamic_pattern": "local",
  "causal_assessment": {
    "hypothesis": "响应转换遗漏目标字段",
    "supporting_evidence": ["失败断言定位到转换输出"],
    "falsifier": "直接调用转换逻辑时目标字段存在"
  },
  "control_action": {
    "gain": "medium",
    "target": "目标响应转换",
    "write_scope": ["src/target-service.py", "tests/test_target_service.py"],
    "invariants": ["保持公开入口兼容"],
    "impact_limit": "目标模块",
    "rollback": "恢复纠正任务开始前快照",
    "correction_check": ["原失败检查通过", "相关兼容性测试通过"],
    "route": "executing"
  },
  "resolution": "open",
  "residual_risk": ["外部数据库故障恢复仍未采样"]
}
```

## 决策生命周期

| `decision` | 账本动作 | 关闭条件 |
| --- | --- | --- |
| `accept` | `decide --decision accept` | 纠正后由独立观察证据执行 `resolve` |
| `reject` | `decide --decision reject` | 命令会以裁决理由直接关闭 |
| `defer` | `decide --decision defer` | 仅限 P2/P3，并在交付时披露 |
| `escalate` | `decide --decision escalate` | 获得用户、权限或外部决策后重新裁决 |

## 动态模式与动作强度

- `local`：对已定位因果来源实施小范围动作。
- `systemic`：返回 planning 或 modeling 修正上游结构。
- `oscillation`、`overshoot`：降低增益、冻结冲突表面并增加采样。
- `stagnation`：检查模型、任务边界和传感器是否持续无效。
- `delay`：等待传播条件后再采样，避免过早反向修正。
- `sensor-fault`：先修正测量系统，不改产品来迎合错误断言。

`control_action` 必须绑定已接受反馈，限制写入面和影响半径，并定义原失败检查、回归检查和回滚。代码已修改不等于反馈已关闭。

