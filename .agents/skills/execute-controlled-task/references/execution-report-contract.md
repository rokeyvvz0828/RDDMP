# ExecutionReport 产物契约

在执行任务和生成 executing 阶段 JSON 前读取本文件。报告只记录实际发生的动作和证据，不写计划中的预期结果。

## 完整示例

```json
{
  "task_id": "T1",
  "status": "implemented",
  "changed_surfaces": [
    {"path_or_resource": "src/target-service.py", "purpose": "修正目标响应转换"}
  ],
  "requirements_addressed": ["R1"],
  "commands": [
    {
      "command": "python -m unittest tests.test_target_service",
      "exit_code": 0,
      "salient_output": "2 tests passed"
    }
  ],
  "local_checks": [
    {
      "check": "目标服务单元测试",
      "result": "pass",
      "evidence": "命令退出码 0",
      "limitations": ["尚未运行完整集成检查"]
    }
  ],
  "diff_summary": ["目标响应增加必需字段", "补充失败路径测试"],
  "invariants_checked": [
    {"invariant": "公开入口保持兼容", "result": "pass", "evidence": "兼容性测试通过"}
  ],
  "disturbances": [],
  "assumptions_falsified": [],
  "unresolved_items": ["完整集成检查由 observing 阶段执行"],
  "recommended_next": "observe"
}
```

## 状态与路由

| `status` | 使用条件 | 推荐路由 |
| --- | --- | --- |
| `implemented` | 边界内动作完成，输出可独立测量 | `observe` |
| `partial` | 有可测输出，但仍有已披露未完成项 | `observe` 或 `replan` |
| `blocked` | 权限、依赖或环境阻止动作 | `escalate` 或 `remodel` |
| `boundary-invalid` | 任务需要越过批准写入或契约边界 | `replan` |

## 证据规则

- `commands` 保存实际命令、退出码和关键原始输出；未运行的命令不得出现为成功。
- `changed_surfaces` 必须能与真实 diff 或资源变化对应。
- `local_checks` 分离结果、证据和限制，不能用代码阅读替代运行证据。
- `invariants_checked` 对每个受影响不变量给出结果和证据。
- 新事实写入 `disturbances`、`assumptions_falsified` 或 `unresolved_items`，不要扩大任务自行处理。
- `recommended_next` 只能表达建议，阶段转移仍由主 Agent 和账本门禁决定。

