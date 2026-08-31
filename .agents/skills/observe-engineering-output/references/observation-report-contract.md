# ObservationReport 产物契约

在独立采样和生成 observing 阶段 JSON 前读取本文件。观察只报告测量结果，不直接修改实现或裁决反馈。

## 完整示例

```json
{
  "sample_label": "T1 完成采样",
  "task_ids": ["T1"],
  "baseline_revision": "baseline-1",
  "measurements": [
    {
      "requirement_id": "R1",
      "sensor": "S1 目标集成检查",
      "result": "fail",
      "raw_evidence": "断言缺少目标字段",
      "limitation": ["未覆盖外部数据库故障恢复"]
    }
  ],
  "feedback": [
    {
      "requirement_id": "R1",
      "expected": "响应包含目标字段",
      "observed": "有效输入的响应缺少目标字段",
      "reproduction": "运行目标集成检查",
      "evidence": "失败断言和退出码 1",
      "severity": "P1",
      "confidence": "high",
      "sensor_limit": "只覆盖本地集成环境",
      "causal_hypothesis": "响应转换遗漏字段",
      "correction_check": "重新运行目标集成检查",
      "classification": "deviation"
    }
  ],
  "disturbances": [],
  "coverage_gaps": ["外部数据库故障恢复尚未采样"],
  "error_counts": {"P0": 0, "P1": 1, "P2": 0, "P3": 0},
  "observation_status": "complete"
}
```

## 原子反馈规则

- 一个反馈只描述一个可以独立裁决的“预期与观察差异”。
- `evidence` 保存原始、可复现信号；`causal_hypothesis` 始终是可被推翻的假设。
- 同一因果表现已存在反馈时标记 `duplicate`，不要重复增加账本项。
- 传感器无判别力时标记 `sensor-fault` 或 `unobservable`，不能伪造产品偏差。
- 没有偏差时返回空 `feedback`，但仍填写测量结果、限制和覆盖缺口。

## 严重度与完整性

- P0：不可逆破坏、安全或系统不可用。
- P1：must 需求、公共契约或关键流程失败。
- P2：重要健壮性、性能、维护性或体验偏差。
- P3：不影响必须需求的轻微质量偏差。
- `error_counts` 必须与本次有效反馈逐项一致，并包含 P0-P3 四个键。
- `complete` 表示计划内测量都有结果或明确缺口，不表示没有偏差。
- 传感器整体失效时使用 `sensor-invalid`，并路由回 modeling。

