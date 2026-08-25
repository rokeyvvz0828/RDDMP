# EngineeringSystemModel 产物契约

在生成 modeling 阶段 JSON 前读取本文件。只记录被代码、运行环境、接口或可重复证据支持的模型事实。

## 完整示例

```json
{
  "baseline_revision": "baseline-1",
  "plant_boundary": {
    "inside": ["应用入口", "目标服务", "持久化适配器"],
    "environment": ["外部数据库", "调用方"]
  },
  "state_variables": [
    {"name": "request_state", "owner": "目标服务", "valid_values": ["accepted", "rejected"]}
  ],
  "causal_paths": [
    {
      "requirement_id": "R1",
      "input": "有效请求",
      "transitions": ["入口校验", "目标服务处理"],
      "outputs": ["目标响应"]
    }
  ],
  "interfaces": [
    {
      "name": "target_response",
      "producers": ["目标服务"],
      "consumers": ["应用入口"],
      "invariant": "目标字段始终存在"
    }
  ],
  "sensors": [
    {
      "id": "S1",
      "target": "R1 输出",
      "method": "集成检查",
      "blind_spots": ["外部数据库不可用时的恢复行为"],
      "independence": "直接观察运行输出"
    }
  ],
  "actuators": [
    {
      "id": "A1",
      "target": "目标服务转换逻辑",
      "action": "修改局部实现和对应测试",
      "authority": "工作区写权限",
      "impact_radius": "目标模块",
      "rollback": "恢复任务开始前快照"
    }
  ],
  "measurements": [
    {"requirement_id": "R1", "sensor_ids": ["S1"], "observable": "yes"}
  ],
  "disturbances": [
    {"source": "外部数据库", "effect": "集成检查失败", "detectable": true, "controllable": false}
  ],
  "delays": [
    {"source": "异步持久化", "consequence": "立即采样可能读取旧值", "sampling_rule": "等待确认信号后采样"}
  ],
  "assumptions": [
    {"text": "测试环境代表目标运行时", "evidence": "版本一致", "falsifier": "运行时配置不同"}
  ],
  "control_assessment": [
    {"requirement_id": "R1", "observable": "yes", "controllable": "yes", "action": "进入 planning"}
  ],
  "model_status": "ready"
}
```

## 判定规则

- 每条 must 需求必须有 `causal_paths`、`measurements` 和至少一个真实传感器。
- 传感器说明测量对象、盲区和独立性；“阅读代码感觉正确”不是运行传感器。
- 执行器必须能触及偏差的因果来源，并说明权限、影响半径和回滚。
- `partial` 或 `no` 的可观测性、可控性必须对应补传感器、改边界或升级动作。
- 外部依赖、缓存、并行修改和反馈延迟分别写入 `disturbances` 或 `delays`。
- 任何从 `control_seed` 继承的内容都要重新取证，不能只引用开发前计划。

无法覆盖 must 需求或没有受权执行器时使用 `model_status=blocked`。

