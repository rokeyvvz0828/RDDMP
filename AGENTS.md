# RDDMP 项目 Codex 协作指令

本文件适用于仓库内所有开发人员和 Codex。目标目录存在更近的 `AGENTS.md` 时必须同时遵循；规则冲突时采用限制更严格的一项，无法判断时停止修改并说明冲突。

## 编码前必读

首次进入本项目或新建研发会话时，必须先完整阅读 `CODEX-DEVELOPMENT-GUIDE.md`、`README.md`、本文件和目标目录最近的 `AGENTS.md`，再开始任何需求分析或编码。首次启动研发前还必须确认 `control-engineering@control-engineering-local` 插件已安装并启用；安装状态不明时执行 `node scripts/check-development-entry.mjs --require-plugin`，按 README 中的官方安装命令补装后重新开启 Codex 任务。

1. `docs/governance/PROJECT-RULES.md`
2. `docs/governance/CODEX-CODING-RULES.md`
3. `docs/governance/GITHUB-RULES.md`
4. `governance/modules.yaml`
5. 当前需求目录中的 `requirement.md` 与 `codex-task-scope.yaml`
6. 目标目录最近的 `AGENTS.md`
7. `.ai-control/original/state.json` 及 `.ai-control/requirements/<completion.control_prefix>/` 中的当前任务记录

所有实现、修复、重构和代码评审任务必须读取并执行 `.agents/skills/rddmp-delivery-engineer/SKILL.md`，业务功能、跨模块功能和复杂需求还必须遵循 `control-engineering` 插件的需求定标、系统建模、任务规划、受控执行、独立观测、偏差纠正和收敛验收闭环。Skill 不能扩大任务权限，也不能替代需求和任务范围。

## 业务前端设计准入

- 新增或改造业务功能时，必须先检查 `web/src/modules/delivery-showcase/` 中的页面结构、组件组合、交互状态和语义主题样式，并优先复用交付示范中心已验证的设计。
- 所有前端业务任务必须读取并执行根目录 `design-h5.md`。需求设计必须明确桌面/移动布局、列表呈现模式、滚动边界、弹层高度和操作层级；完成前必须按手册规定的手机视口验收。
- 只有交付示范中心无法覆盖目标业务形态时，才允许引入新样式；需求设计、前端契约或 `.ai-control` 当前任务记录必须说明不适用原因、复用的基础能力和新样式的适用范围。
- 新样式不得绕过 `web/src/components/ui`、语义主题变量、加载/空/失败/无权限/提交中状态和桌面/移动端验收要求。

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
