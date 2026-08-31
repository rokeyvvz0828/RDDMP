# 实施计划与交接包契约

## 人可读计划结构

```markdown
# <主题> 实施计划

## 状态与来源
- 计划修订：1
- 设计修订：1
- 设计文档：<路径>
- 状态：草稿 | 待确认 | 可移交

## 目标与全局约束
## 文件职责地图
## 任务依赖图与并行策略
## 需求覆盖表

### T1 <可独立验收结果>
#### 需求映射与前置事实
#### 文件边界与接口
#### 操作步骤、命令和预期信号
#### 验收、证据与回滚
#### 停止和升级条件

## 集成检查
## 控制模型种子
## 风险与用户批准
```

## PredevelopmentHandoff

```json
{
  "schema_version": 1,
  "topic": "stable-topic",
  "handoff_status": "approved",
  "created_at": "ISO-8601 时间",
  "design": {
    "schema_version": 1,
    "topic": "stable-topic",
    "design_revision": 1,
    "design_status": "approved",
    "human_document": "docs/engineering-control/designs/YYYY-MM-DD-topic-design.md",
    "objective": "唯一目标",
    "users": [],
    "requirements": [
      {
        "id": "R1",
        "statement": "必须行为",
        "acceptance": ["验收条件"],
        "priority": "must",
        "source": "用户确认",
        "counterexample": ["失败反例"]
      }
    ],
    "invariants": [],
    "constraints": [],
    "non_goals": [],
    "selected_approach": {"name": "方案", "reason": "原因", "alternatives": []},
    "architecture": {"boundary": {"inside": [], "outside": []}, "components": [], "interfaces": [], "data_flows": []},
    "error_handling": [],
    "quality_attributes": {"security": [], "performance": [], "compatibility": [], "operations": []},
    "verification_strategy": [],
    "assumptions": [],
    "unknowns": [],
    "decisions": [],
    "risks": [],
    "approval": {"status": "approved", "evidence": "确认摘要", "confirmed_at": "ISO-8601 时间"}
  },
  "implementation_plan": {
    "plan_revision": 1,
    "human_document": "docs/engineering-control/plans/YYYY-MM-DD-topic-implementation-plan.md",
    "plan_status": "ready",
    "global_constraints": [],
    "file_map": [{"path": "src/example.py", "status": "existing", "responsibility": "单一职责", "evidence": "来源"}],
    "tasks": [
      {
        "id": "T1",
        "goal": "可独立验收的结果",
        "requirement_ids": ["R1"],
        "prerequisites": [],
        "input_facts": ["已证实事实及来源"],
        "files": {"create": [], "modify": ["src/example.py"], "test": []},
        "interfaces": {"consumes": [], "produces": []},
        "steps": [{"id": "T1-S1", "action": "单一动作", "command": null, "expected": "可判别结果", "evidence": "需要保留的证据"}],
        "acceptance_checks": ["正常、边界或失败路径检查"],
        "risks": [],
        "rollback": "明确回滚点或方式",
        "stop_conditions": ["必须停止的条件"],
        "escalation_conditions": ["必须升级主 Agent 或用户的条件"]
      }
    ],
    "dependency_edges": [],
    "parallel_groups": [["T1"]],
    "integration_checks": [{"after_tasks": ["T1"], "command": "验证命令", "expected": "通过信号"}],
    "coverage": [{"requirement_id": "R1", "task_ids": ["T1"]}],
    "approval": {"status": "approved", "evidence": "用户许可推进的摘要", "confirmed_at": "ISO-8601 时间"}
  },
  "control_seed": {
    "seed_status": "hypotheses-only",
    "plant_boundary_candidates": [],
    "state_variable_candidates": [],
    "interface_candidates": [],
    "sensor_candidates": [],
    "actuator_candidates": [],
    "disturbance_candidates": [],
    "delay_candidates": [],
    "assumptions": [{"text": "假设", "basis": "依据", "falsifier": "推翻证据"}]
  }
}
```

## 结构规则

- 顶层和设计的 `topic` 必须一致。
- `handoff_status` 仅允许 `approved`、`awaiting-user-approval`、`blocked`；只有 `approved` 可导入。
- `design.design_status`、`design.approval.status` 和 `implementation_plan.approval.status` 必须同时为 `approved`。
- `implementation_plan.plan_status` 必须为 `ready`。
- 任务 ID 唯一；所有依赖、并行组和覆盖项必须引用已知任务或需求。
- 每条 `must` 需求至少有一个覆盖任务，每个任务至少映射一条需求。
- `steps`、`acceptance_checks`、`stop_conditions`、`escalation_conditions` 和 `rollback` 不得为空。
- 未知文件路径使用 `candidate-new`，不能伪装为 `existing`。
- `control_seed.seed_status` 固定为 `hypotheses-only`，导入后由系统建模 Skill 验证。

## 导入映射

`import-handoff` 会：

- 将设计目标和需求转换为当前 `RequirementBaseline`；
- 保存设计假设、未知项、决策和验证意图；
- 把完整计划和 `control_seed` 保存到账本 `predevelopment` 区域；
- 保持账本阶段为 `baseline`，不自动生成 `EngineeringSystemModel` 或 `ControlPlan`；
- 允许主编排器在基准门禁后进入 `modeling`。
