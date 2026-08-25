# ConvergenceReport 产物契约

在最终验收和生成 verifying 阶段 JSON 前读取本文件。报告必须基于最终工程状态和完整账本重新审计，不能复述执行者结论。

## 完整示例

```json
{
  "baseline_revision": "baseline-1",
  "requirement_results": [
    {
      "id": "R1",
      "result": "pass",
      "task_ids": ["T1"],
      "evidence": ["目标集成检查退出码 0"],
      "limitations": []
    }
  ],
  "task_results": [
    {"id": "T1", "status": "verified", "evidence": ["局部和集成检查通过"]}
  ],
  "feedback_summary": {
    "total": 1,
    "accepted_open": 0,
    "escalated": 0,
    "deferred_by_severity": {"P2": 0, "P3": 0}
  },
  "sample_trend": [
    {"sample_id": "S-0001", "P0": 0, "P1": 1, "P2": 0, "P3": 0, "score": 20},
    {"sample_id": "S-0002", "P0": 0, "P1": 0, "P2": 0, "P3": 0, "score": 0}
  ],
  "regression_checks": [
    {"check": "相关回归集合", "result": "pass", "evidence": "命令退出码 0"}
  ],
  "invariant_results": [
    {"invariant": "公开入口保持兼容", "result": "pass", "evidence": "兼容性测试通过"}
  ],
  "scope_audit": {"within_scope": true, "unexpected_changes": []},
  "disturbance_resilience": [
    {"disturbance": "测试环境重启", "result": "pass", "evidence": "重启后检查通过"}
  ],
  "residual_risks": [],
  "gate_result": "pass",
  "route_reason": "must 需求、任务、反馈、采样和回归门禁均通过"
}
```

## 需求证据链

每条 must 需求必须能追踪：

```text
需求 -> 受控任务 -> 实际修改 -> 传感器 -> 最终证据
```

任一链接缺失时不得使用 `pass`。证据必须来自最终状态，不能只引用修改前或纠偏前的成功记录。

## 模式门禁

| 模式 | 最少采样 | 传感器要求 |
| --- | --- | --- |
| `light` | 脚本不增加最低次数 | 仍需有可判别验收证据 |
| `standard` | 2 | must 需求绑定有效传感器 |
| `high-assurance` | 3 | 至少两类独立传感器 |

所有模式都要求最终 P0/P1 为零、任务全部 verified、反馈完成裁决、已接受反馈复验关闭、范围和不变量通过审计。

## 路由判定

- 证据缺失或采样不完整：`return-to-observing`。
- 存在已证实实现偏差：`return-to-correcting`。
- 传感器、因果链或扰动模型失效：`return-to-modeling`。
- 缺少权限、用户决定或外部条件：`blocked`。
- 全部阻塞条件消失：`pass`，随后仍必须运行账本 `gate` 和 `transition --to converged`。

`gate` 拒绝时，以脚本输出为准修正报告，不得手工修改账本绕过。
