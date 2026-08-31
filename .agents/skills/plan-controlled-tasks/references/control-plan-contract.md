# ControlPlan 产物契约

在生成 planning 阶段 JSON 前读取本文件。任务按可独立验收结果划分，不按文件数或 Agent 数量机械拆分。

## 完整示例

```json
{
  "baseline_revision": "baseline-1",
  "model_revision": "model-1",
  "tasks": [
    {
      "id": "T1",
      "goal": "目标入口返回符合 R1 的响应",
      "requirement_ids": ["R1"],
      "prerequisites": [],
      "input_facts": ["目标服务是响应字段的唯一生产者"],
      "write_scope": ["src/target-service.py", "tests/test_target_service.py"],
      "invariants": ["保持现有公开入口兼容"],
      "interface_contracts": ["target_response 必须包含目标字段"],
      "non_goals": ["不修改外部数据库协议"],
      "action_bounds": ["只修改目标服务及直接测试"],
      "acceptance_checks": ["目标测试通过", "无效输入保持既有错误语义"],
      "evidence_required": ["实际 diff", "测试命令和退出码"],
      "rollback": "恢复 T1 开始前的工作区快照",
      "stop_conditions": ["发现未建模公共消费者"],
      "escalation_conditions": ["必须改变公开契约"]
    }
  ],
  "dependency_edges": [],
  "parallel_groups": [["T1"]],
  "integration_points": [
    {"after_tasks": ["T1"], "checks": ["运行目标集成检查"]}
  ],
  "sampling_plan": [
    {
      "label": "T1 完成采样",
      "trigger": "T1 返回执行报告",
      "observer": "独立观察者",
      "sensors": ["S1"],
      "requirement_ids": ["R1"]
    }
  ],
  "coverage": [
    {"requirement_id": "R1", "task_ids": ["T1"], "sensor_ids": ["S1"]}
  ],
  "plan_status": "ready"
}
```

## 任务边界规则

- `write_scope` 必须列出全部允许写入面；发现范围外修改需求时停止并重规划。
- `action_bounds` 描述允许动作的最大范围，不预写未经验证的微观实现。
- `acceptance_checks` 至少包含能区分正确与错误的检查。
- `evidence_required` 明确执行 Agent 必须返回的原始证据。
- `rollback`、`stop_conditions` 和 `escalation_conditions` 不得为空。
- 计划修订改变任一任务契约时，该任务在账本中会退回 `planned`。

## 依赖与并行

- `dependency_edges` 必须引用已知任务且不能形成环。
- 共享文件、接口、数据库、端口、生成物或测试环境存在冲突时不得并行。
- `parallel_groups` 只是安全声明，不是强制并行命令。
- 每条 must 需求必须同时出现在任务 `requirement_ids` 和 `coverage` 中。

