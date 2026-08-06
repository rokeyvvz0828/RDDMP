# RDDMP 项目 Codex 协作指令

本文件适用于仓库内所有开发人员和 Codex。目标目录存在更近的 `AGENTS.md` 时必须同时遵循；规则冲突时采用限制更严格的一项，无法判断时停止修改并说明冲突。

## 编码前必读

1. `docs/governance/PROJECT-RULES.md`
2. `docs/governance/CODEX-CODING-RULES.md`
3. `docs/governance/GITHUB-RULES.md`
4. `governance/modules.yaml`
5. 当前需求目录中的 `requirement.md` 与 `codex-task-scope.yaml`
6. 目标目录最近的 `AGENTS.md`
7. `.ai-control/original/state.json` 及 `.ai-control/requirements/<completion.control_prefix>/` 中的当前任务记录

所有实现、修复、重构和代码评审任务必须读取并执行 `.agents/skills/rddmp-delivery-engineer/SKILL.md`。Skill 不能扩大任务权限，也不能替代需求和任务范围。

## 强制约束

- 只修改 `codex-task-scope.yaml` 中 `writable_paths` 覆盖的文件；`read_only_paths` 只能读取；不得读取或修改 `forbidden_paths`。
- `.ai-control` 只修改当前任务前缀文件；无前缀全局账本和其他任务账本只读。
- 不连接或修改生产系统，不向 Codex 提供生产数据、真实个人信息、口令、Token、Cookie、密钥、证书、内网地址、真实日志、真实附件或未脱敏截图。
- 保持现有 Java 包名和业务逻辑。目录、包名、模块或契约调整必须有单独的架构决策与迁移任务，不能在普通需求中顺手实施。
- 模块只能依赖 `governance/modules.yaml` 声明的模块和公开包；不得绕过服务契约直接写其他模块负责的数据表。
- `server/src/platform` 是平台能力，业务任务默认只读；`server/src/shared` 是公共能力，变更必须声明公共能力影响并完成 Owner 审批和回归测试。
- 受保护接口必须执行服务端认证、RBAC、数据范围和实体授权；写操作应保留审计。前端权限不能替代后端校验。
- Flyway 迁移只追加，不修改已发布脚本；生产数据库不得手工改表。
- 不绕过自动化测试、CODEOWNERS、Required Checks 或分支保护；Codex 不得自行批准、合并或发布。
- 不覆盖或回退其他人员的未关联改动，不用批量格式化扩大变更范围。

## 实施与完成

编码前说明目标模块、拟改文件、复用能力、数据/权限/审计/迁移影响、测试和未决问题。完成后如实报告实际文件、范围一致性、执行过的命令及结果、`.ai-control` 前缀与 phase、已知风险、上线验证和回退方式。未执行的验证不得描述为通过。
