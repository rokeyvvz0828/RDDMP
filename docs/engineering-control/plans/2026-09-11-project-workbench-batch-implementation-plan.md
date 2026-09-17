# REQ-20260911-079 实施计划

1. 在 `ProjectService.workbench` 中替换逐行 `decorateProject` 为批量装饰，增加仅服务内部的统计组装辅助方法。
2. 在 `ProjectServiceTest` 增加多项目字段等价、批量查询和空列表不查询测试。
3. 运行工作台聚焦测试、`ccb-system` 模块回归、`git diff --check`，并记录控制账本执行/观测/收敛证据。

回退为恢复 `rows.forEach(row -> decorateProject(...))`；无数据库迁移和外部 I/O 影响。
