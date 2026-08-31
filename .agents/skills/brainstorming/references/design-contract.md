# DesignDocument 契约

## 人可读文档结构

```markdown
# <主题> 工程设计

## 文档状态
- 修订：1
- 状态：草稿 | 待确认 | 已确认
- 用户确认依据：<消息摘要或引用>

## 目标与成功信号
## 使用者与场景
## 必须需求与验收条件
## 不变量与约束
## 非目标
## 方案比较与选择
## 架构边界与组件职责
## 接口、数据和状态流
## 错误、降级与恢复
## 安全、性能、兼容性与运维
## 验证策略
## 假设、未知项与决策记录
## 风险与回退原则
```

## 机器可读结构

```json
{
  "schema_version": 1,
  "topic": "stable-topic",
  "design_revision": 1,
  "design_status": "approved",
  "human_document": "docs/engineering-control/designs/YYYY-MM-DD-topic-design.md",
  "objective": "唯一、可观察的目标",
  "users": ["目标使用者与场景"],
  "requirements": [
    {
      "id": "R1",
      "statement": "必须实现的外部行为",
      "acceptance": ["可重复判断通过或失败的检查"],
      "priority": "must",
      "source": "用户确认或工程事实来源",
      "counterexample": ["明确不满足该需求的行为"]
    }
  ],
  "invariants": ["实施期间始终成立的条件"],
  "constraints": ["技术、权限、兼容性、时间或合规约束"],
  "non_goals": ["明确排除的内容"],
  "selected_approach": {
    "name": "方案名",
    "reason": "选择依据",
    "alternatives": [{"name": "备选", "rejected_because": "排除原因"}]
  },
  "architecture": {
    "boundary": {"inside": ["范围内"], "outside": ["范围外"]},
    "components": [{"name": "组件", "responsibility": "单一职责", "depends_on": []}],
    "interfaces": [{"name": "接口", "producer": "来源", "consumers": [], "contract": "行为契约"}],
    "data_flows": [{"input": "输入", "transitions": [], "output": "输出"}]
  },
  "error_handling": [{"condition": "错误条件", "behavior": "外部行为", "recovery": "恢复"}],
  "quality_attributes": {
    "security": [], "performance": [], "compatibility": [], "operations": []
  },
  "verification_strategy": [{"requirement_ids": ["R1"], "signal": "验证信号", "method": "验证方式"}],
  "assumptions": [{"text": "假设", "basis": "依据", "falsifier": "推翻证据"}],
  "unknowns": [{"question": "未知项", "impact": "若不解决会影响什么", "owner": "决策者", "blocking": false}],
  "decisions": [{"id": "D1", "decision": "已确认选择", "reason": "原因", "source": "确认来源"}],
  "risks": [{"risk": "风险", "impact": "影响", "mitigation": "缓解"}],
  "approval": {
    "status": "approved",
    "evidence": "用户确认当前修订的消息摘要",
    "confirmed_at": "ISO-8601 时间"
  }
}
```

## 字段规则

- `schema_version` 固定为 `1`。
- `topic` 使用稳定的小写连字符标识，后续计划和交接包必须一致。
- 需求 ID 在同一主题内稳定；修改含义时增加修订，不复用 ID 表示不同需求。
- `priority` 允许 `must`、`should`、`could`，只有 `must` 驱动闭环硬门禁。
- `acceptance` 和 `counterexample` 必须为非空数组。
- `unknowns.blocking=true` 时，`design_status` 不能为 `approved`。
- `approval.evidence` 记录可公开的确认摘要，不记录完整思维链或敏感内容。
