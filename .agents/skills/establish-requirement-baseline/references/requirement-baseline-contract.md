# RequirementBaseline 产物契约

在生成 baseline 阶段 JSON 前读取本文件。示例可直接作为结构基线，实际内容必须来自用户确认或可追踪事实。

## 完整示例

```json
{
  "objective": "用户能够通过受支持入口获得可验证的目标结果",
  "requirements": [
    {
      "id": "R1",
      "statement": "系统必须在有效输入下返回目标结果",
      "acceptance": ["运行目标检查时退出码为 0", "输出包含目标字段"],
      "priority": "must",
      "source": "用户确认",
      "counterexample": ["命令成功但缺少目标字段"]
    }
  ],
  "invariants": ["保留现有公开接口"],
  "constraints": ["不增加未经批准的运行时依赖"],
  "non_goals": ["不重构无关模块"],
  "assumptions": [
    {
      "text": "现有入口可以复用",
      "basis": "只读工程勘察",
      "falsifier": "建模发现入口不存在或不受支持"
    }
  ],
  "unknowns": [
    {
      "question": "是否存在未记录的外部消费者",
      "impact": "可能改变接口边界",
      "owner": "modeling",
      "blocking": false
    }
  ],
  "decisions": [
    {
      "question": "目标入口是否保持兼容",
      "options": ["保持兼容", "创建新版本入口"],
      "selected": "保持兼容",
      "blocking": false,
      "source": "用户确认"
    }
  ],
  "measurement_intents": [
    {
      "requirement_id": "R1",
      "observable": "目标字段和退出码",
      "candidate_sensor": "目标集成检查"
    }
  ],
  "baseline_status": "ready"
}
```

## 字段规则

- `objective` 只描述一个外部可见结果，不嵌入实现方案。
- `requirements[].id` 在整个闭环中保持稳定；修订需求内容时不要复用 ID 表示另一件事。
- `acceptance` 描述可判别信号，`counterexample` 描述明确失败结果。
- `priority` 使用 `must`、`should` 或 `could`；至少存在一条 `must`。
- `assumptions` 必须给出依据和推翻条件，不能伪装成已证实事实。
- `unknowns[].blocking=true` 表示不能进入后续阶段。
- `measurement_intents` 是候选观测意图，不能在本阶段宣称传感器已经有效。

## 状态判定

只有需求冲突已解决、must 需求可验收且没有阻塞未知项时使用 `ready`。否则使用 `blocked`，并把缺失授权或事实写入 `unknowns` 或 `decisions`。

