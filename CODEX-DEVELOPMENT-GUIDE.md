# RDDMP Codex 协同开发文件清单与操作指引

本文面向参与 RDDMP 开发的需求人员、研发人员、测试人员、模块 Owner 和 Codex，说明项目内 Codex 文件、Skill、研发规约和自动门禁各自负责什么，以及如何用它们完成一次可评审、可验证、可回退的开发任务。

## 首次启动研发硬门禁

首次参与 RDDMP 或重新建立研发环境时，必须按以下顺序完成，不得直接开始业务编码：

1. 阅读本文件、`README.md`、根目录 `AGENTS.md`、`docs/governance/` 下正式规约和目标目录最近的 `AGENTS.md`。
2. 确认仓库 Skill `.agents/skills/rddmp-delivery-engineer/SKILL.md` 可读。
3. 安装并启用 [control-engineering-skills](https://github.com/wjzxc123/control-engineering-skills) 提供的 `control-engineering@control-engineering-local` 插件。安装完成后重新打开 Codex 任务，使 Skill 被重新加载。
4. 执行 `node scripts/check-development-entry.mjs --require-plugin`，只有检查通过后才进入需求分析和编码。

业务前端任务还必须先检查 `web/src/modules/delivery-showcase/`，将其作为列表、表单、流程、图表和响应式设计的首选参考，并读取根目录 `design-h5.md`，按其中的移动端布局、滚动边界、操作层级和视口验收规则实施。

涉及业务表单、详情、列表、筛选或统计的任务还必须读取并执行 `docs/integration/business-form-metadata-contract.md`。Agent 负责在同一任务中自动登记和维护业务表单元数据及 mock/初始化数据；不得把新业务首次启动后的字段配置留给研发人员手工完成。验收必须证明全新本地数据库启动后无需人工配置即可呈现该业务功能。

插件安装由外部 Codex 环境完成，不将插件源码复制进业务仓库。macOS/Linux 官方安装方式：

```bash
git clone https://github.com/wjzxc123/control-engineering-skills.git
cd control-engineering-skills
python3 scripts/install_plugin.py --dry-run
python3 scripts/install_plugin.py
```

Windows 使用 `python` 替换 `python3`。安装脚本必须报告 `installed=true enabled=true`。

## 一、先理解六层治理关系

| 层级 | 解决的问题 | 事实来源 |
| --- | --- | --- |
| 正式规约 | 项目长期必须遵守什么 | `docs/governance/` |
| 长期边界 | 模块归谁、能依赖谁、哪些包公开 | `governance/modules.yaml` |
| 单任务授权 | 本次需求允许 Codex 和研发改什么 | `requirement.md`、`codex-task-scope.yaml` |
| Codex 指令 | Codex 进入仓库或具体目录后必须做什么 | 根目录及最近目录的 `AGENTS.md` |
| 交付方法 | 如何分析、设计、实施和逐级验收 | `rddmp-delivery-engineer` Skill |
| 执行证据 | 当前做到哪一步、运行了什么、结果如何 | `.ai-control/`、PR 和 CI |

规则优先级不是“后读覆盖前读”。长期规约、模块边界和任务范围必须同时满足；冲突时采用限制更严格的一项，无法判断时暂停编码并由需求负责人或模块 Owner 澄清。

## 二、公共入口文件

| 文件 | 作用 | 谁使用 | 使用时机 |
| --- | --- | --- | --- |
| `CODEX-DEVELOPMENT-GUIDE.md` | Codex 文件清单和从需求到 PR 的完整操作手册 | 全体研发参与者 | 新人入项及开始需求前 |
| `AGENTS.md` | Codex 和研发必须遵守的仓库级硬约束及必读清单 | 研发、Codex | 每个任务开始时必读 |
| `CONTRIBUTING.md` | 创建需求、分支、测试和 PR 的简明入口 | 研发人员 | 新人入项和日常开发 |
| `COLLABORATION.md` | 协作规则兼容入口，指向 GitHub 规约和任务边界 | 多人协作参与者 | 拆分任务、交接和合并前 |

这些入口文件只负责导航和关键摘要。发现入口与正式规约不一致时，应修改正式规约并同步入口，不能只在 Codex 提示词里增加一套新规则。

## 三、正式规约与模板

| 文件 | 管理内容 | 普通需求是否修改 |
| --- | --- | --- |
| `docs/governance/README.md` | 正式规约导航和唯一事实源说明 | 否 |
| `docs/governance/PROJECT-RULES.md` | 架构、API、权限、数据、前端、质量和发布规则 | 否 |
| `docs/governance/CODEX-CODING-RULES.md` | Codex 准入、数据安全、修改边界、预检和完成报告 | 否 |
| `design-h5.md` | 页面、列表、弹框、流程图、表单和图表的移动端工程规则 | 前端适配规则变更时走治理变更 |
| `docs/governance/GITHUB-RULES.md` | 分支、PR、CODEOWNERS、审批和 Required Checks | 否 |
| `docs/architecture/MODULES.md` | 后端分层、前端归属及契约原则的人类可读说明 | 仅架构任务 |
| `docs/requirements/TEMPLATE.md` | 标准需求模板 | 不直接修改，复制使用 |
| `docs/requirements/codex-task-scope.template.yaml` | Codex 任务授权模板 | 不直接修改，复制使用 |
| `governance/modules.yaml` | 模块、Owner、目录、Maven artifact、依赖和公开包 | 仅治理或架构任务 |

`modules.yaml` 和任务范围文件采用 JSON 兼容的 YAML 格式，必须保持可由 JSON 解析器读取。普通业务需求不应顺手修改模块定义、Owner 或公共包范围。

## 四、分层 AGENTS.md

`AGENTS.md` 按目录继承。修改某文件时，需要同时遵循根目录规则和从根到该文件所在目录沿途最近的规则。

| 文件 | 生效范围 | 重点 |
| --- | --- | --- |
| `server/src/AGENTS.md` | 全部后端代码 | Java、Spring、API、权限、迁移和测试 |
| `server/src/modules/AGENTS.md` | 所有业务模块 | 业务模块独立性和跨模块契约 |
| `server/src/modules/ai/AGENTS.md` | ai 模块 | 模型路由、外部访问、密钥和调用审计 |
| `server/src/platform/AGENTS.md` | platform 模块 | 高风险公共平台能力和反向依赖限制 |
| `server/src/platform/system/AGENTS.md` | system 平台模块 | 组织、用户、角色、菜单、参数和数据范围 |
| `server/src/platform/workflow/AGENTS.md` | workflow 平台模块 | 流程模型、BPMN、Flowable 和状态流转 |
| `server/src/shared/AGENTS.md` | shared 模块 | 无业务数据所有权和公共兼容性 |
| `web/src/AGENTS.md` | Vue 前端 | 公共组件、全状态、权限体验和浏览器验收 |

示例：修改 `server/src/platform/workflow/...` 时，至少读取根 `AGENTS.md`、`server/src/AGENTS.md`、`server/src/platform/AGENTS.md` 和 workflow 的 `AGENTS.md`。

## 五、仓库级 Skill

### 5.1 主 Skill

`.agents/skills/rddmp-delivery-engineer/SKILL.md` 是 RDDMP 的标准交付方法。所有实现、修复、重构和评审任务都应使用它。它负责：

1. 检查 ready 需求、任务范围、分支和 Owner 是否齐全。
2. 从用户任务出发分析入口、状态、数据来源和权限，而不是直接生成代码。
3. 优先搜索并复用现有 Controller、Service、API、组件、store 和测试。
4. 按 Maven 模块、公开包和 `writable_paths` 实施。
5. 依次执行静态检查、测试、构建、运行验证和浏览器验收。
6. 区分“已实现”“检查通过”“运行可达”和“浏览器已验收”。

Skill 规定的是工作方法，不能扩大 `codex-task-scope.yaml` 的授权范围。

### 5.2 Skill 辅助文件

| 文件 | 作用 |
| --- | --- |
| `agents/openai.yaml` | Skill 在 Codex 中的展示名称和默认提示词 |
| `references/frontend-usability.md` | 列表、表单、状态、响应式和可访问性检查 |

架构与所有权直接读取 `governance/modules.yaml` 和 `docs/architecture/MODULES.md`；协作规则与证据等级已合并进主 Skill，避免短 reference 之间反复跳转和规则重复。

使用 Codex 时，可直接说明：

```text
使用 $rddmp-delivery-engineer 完成 REQ-20260806-001。
先读取需求、codex-task-scope.yaml、根目录及目标模块 AGENTS.md，
暂不编码，先输出预检结果。
```

Codex 未自动发现 Skill 时，应显式读取 `.agents/skills/rddmp-delivery-engineer/SKILL.md` 并按步骤执行。

### 5.3 工程控制插件

`control-engineering` 是复杂业务开发的外部闭环编排器。所有业务功能、跨模块改造、数据库/权限/公共能力变更和需求存在实质歧义的任务，必须遵循其七阶段：需求定标、系统建模、任务规划、受控执行、独立观测、偏差纠正、收敛验收。对应 Skill 为 `$establish-requirement-baseline`、`$model-engineering-system`、`$plan-controlled-tasks`、`$execute-controlled-task`、`$observe-engineering-output`、`$correct-engineering-deviation` 和 `$verify-control-convergence`。

简单问答或单个显然的低风险修改可由主 Agent 选择轻量模式，但仍必须遵守本项目的需求范围、AGENTS 和交付示范中心前端设计准入。外部插件不能扩大 `codex-task-scope.yaml` 授权范围。

## 六、Codex 开发入口

| 内容 | 入口 | 研发人员怎么用 |
| --- | --- | --- |
| Codex 项目指令 | 根及最近目录的 `AGENTS.md` | 从仓库根目录启动 Codex，使目录规则按层级生效 |
| Codex 交付 Skill | `.agents/skills/rddmp-delivery-engineer/SKILL.md` | 在提示中指定需求编号并调用 `$rddmp-delivery-engineer` |
| 当前任务 | `requirement.md`、`codex-task-scope.yaml` | 要求 Codex 编码前读取并输出预检 |
| 工程证据 | `.ai-control/original/` 及 `.ai-control/requirements/<prefix>/` | 区分初始历史与当前需求，记录执行、观测和验收证据 |

RDDMP 只维护 Codex 开发入口，不维护其他 Coding 工具的仓库适配文件。

### 6.1 `.ai-control` 工程控制账本

`.ai-control/` 是随源码版本化的工程控制账本，用来回答：需求基线是什么、系统为什么这样设计、任务如何拆分、实际执行了什么、独立观测发现了什么，以及是否满足收敛条件。它不是业务代码目录，也不是 Codex 的目录授权配置。

事实源边界如下：

| 内容 | 唯一事实源 | `.ai-control` 的作用 |
| --- | --- | --- |
| 业务目标与验收 | `requirement.md` | 引用需求并记录阶段证据，不重新定义需求 |
| 可写、只读、禁止路径 | `codex-task-scope.yaml` | 记录实际修改面和范围审计，不能扩大权限 |
| 长期模块与 Owner | `governance/modules.yaml` | 记录本任务采用的模块和契约结论 |
| 人类可读设计与计划 | `docs/engineering-control/designs/`、`plans/` | 保存机器可读的状态、任务、观测和反馈 |
| 合并和发布结论 | PR、CI、发布记录 | 汇总实际证据和剩余风险，不替代审批 |

`.ai-control/original/` 中的 `state.json`、`baseline.json`、`model.json`、`control-plan.json`、`execution-T*.json` 和 `observation-T*.json` 属于初始平台建设历史账本；已有的旧任务 slug 目录同样按历史证据保留。新需求只能读取它们了解历史，不得继续覆盖。新任务统一使用需求前缀：

```text
req-20260806-001-menu-id
```

在 `codex-task-scope.yaml` 中只授权当前需求目录，例如 `.ai-control/requirements/req-20260806-001-menu-id/*.json`，不要授权 `.ai-control/**` 后修改其他需求证据。

#### 账本文件

| 文件 | 何时创建或更新 | 至少记录 |
| --- | --- | --- |
| `design.json` | 设计确认后 | 目标、范围、非目标、约束、决策、风险、批准证据和人类设计文档路径 |
| `handoff.json` | 进入实施前及最终交接时 | 设计/计划版本、恢复入口、当前结论、下一步和交接状态 |
| `state.json` | 建立账本并在阶段切换后更新 | objective、mode、phase、revision、阶段历史、当前任务和未关闭反馈 |
| `model.json` | 建模阶段 | 系统边界、状态变量、因果路径、传感器、执行器、扰动和不可控项 |
| `control-plan.json` | 计划批准后 | T1/T2 等任务、依赖、写入范围、验收检查、证据、停止条件和回退 |
| `execution-Tn.json` | 每个任务实际执行后 | 实际修改面、命令、结果、覆盖需求、约束检查、扰动和未决问题 |
| `observation-Tn.json` | 对对应执行进行独立验证后 | 测量结果、错误数、覆盖缺口、偏差、反馈和是否允许继续 |
| `convergence.json` | 全部验收门禁完成后 | 需求结果、任务结果、回归、范围审计、剩余风险和最终 gate 结论 |

不要为了凑齐文件而创建空 JSON。简单文档或拼写修改可不建立完整账本，但必须在 `codex-task-scope.yaml` 中设置 `completion.control_mode=minimal` 并填写 `control_justification`；门禁只允许该模式修改需求元数据、Markdown 和账本证据。涉及源码、配置、脚本、权限、数据库、公共能力、跨模块、用户流程或发布时必须使用 `control_mode=full` 的任务前缀账本。

#### 阶段流转

```text
baseline -> modeling -> planning -> executing -> observing -> converged
                                      ^              |
                                      |-- 有偏差 -----|
```

- `baseline`：需求 ready，范围和设计已确认。
- `modeling`：边界、状态、风险、可观测信号和控制动作明确。
- `planning`：任务依赖、写入范围、测试、停止条件和回退可执行。
- `executing`：按 control-plan 执行一个或多个 Tn，写 execution 证据。
- `observing`：由独立验证者检查 execution，不直接复述执行者结论。
- `converged`：全部 must 验收通过、范围一致且剩余风险可接受。

观测发现偏差时，将反馈写入 observation/state，再回到 `planning` 或 `executing`。不能为了结束任务直接把 phase 改成 `converged`。`state.json` 只在对应阶段产物已经存在后更新，并递增 `revision`、追加 `phase_history`，不删除历史记录。

#### 谁负责写

| 角色 | 账本责任 |
| --- | --- |
| 需求负责人/Owner | 确认 design、计划、风险接受和最终 gate，不伪造技术执行证据 |
| 研发人员/Codex 执行任务 | 写对应 execution，记录真实文件、命令、结果和未决问题 |
| 独立验证者或独立 Codex 上下文 | 写 observation，验证权限、异常、运行和浏览器结果 |
| 任务协调者 | 在证据完整后更新 state，维护 revision、phase 和 open feedback |
| 发布人员 | 补充 convergence 中的上线验证、剩余风险和回退前提 |

同一个 Codex 上下文无法构成真正独立观测时，应明确标记“非独立验证”，并把独立复核留给另一任务或人工评审。

#### 一次任务怎么操作

1. 从需求编号生成唯一前缀，并写入 `codex-task-scope.yaml` 的 `completion.control_prefix`。
2. 读取 `.ai-control/original/state.json` 了解初始历史，再只读取 `.ai-control/requirements/<prefix>/`；不要把其他需求目录的 `state.json` 当作当前任务。
3. 设计和计划批准后，建立 design/handoff/state/model/control-plan；人类文档仍写在 `docs/engineering-control/`。
4. 每完成 Tn，立即写 execution-Tn；不要等任务结束后凭记忆补命令和结果。
5. 对 execution-Tn 执行独立检查并写 observation-Tn；偏差进入反馈闭环。
6. 全部门禁通过后写 convergence，最后将 state phase 更新为 `converged` 并形成最终 handoff。
7. PR 中列出 control prefix、当前 phase、execution/observation/convergence 文件和未关闭风险。

查看当前任务账本：

```bash
PREFIX=req-20260806-001-menu-id
find ".ai-control/requirements/$PREFIX" -maxdepth 1 -name "*.json" -print | sort
```

提交前检查当前前缀 JSON 可解析：

```bash
node -e "const fs=require('fs'); for (const f of process.argv.slice(1)) JSON.parse(fs.readFileSync(f,'utf8')); console.log('ledger JSON passed')" .ai-control/requirements/$PREFIX/*.json
```

账本统一使用 UTF-8 JSON 和 ISO 8601 时间；命令结果必须来自实际执行。不要写入密钥、Token、生产数据、完整敏感日志或大段可重新生成的构建输出。历史文件中的旧目录只能作为当时证据，新任务记录路径前必须以当前仓库为准重新确认。

## 七、标准开发操作流程

开始第 1 步前，先通过 `node scripts/check-development-entry.mjs --require-plugin`；未通过时停止编码，先补齐契约阅读和插件安装。

### 第 1 步：创建并评审需求

```bash
REQ=REQ-20260806-001
mkdir -p "docs/requirements/$REQ"
cp docs/requirements/TEMPLATE.md "docs/requirements/$REQ/requirement.md"
cp docs/requirements/codex-task-scope.template.yaml "docs/requirements/$REQ/codex-task-scope.yaml"
```

先填写 `requirement.md` 的业务目标、实施/非实施范围、业务规则、权限、数据、验收、测试和回退。需求负责人、研发和模块 Owner 评审通过后，将文档头部 `status` 从 `draft` 改为 `ready`。

### 第 2 步：确认目标模块和 Owner

读取 `governance/modules.yaml` 和 `docs/architecture/MODULES.md`，确认：

- 需求属于 `platform/system`、`platform/workflow`、`business/ai` 或其他模块。
- 模块 Owner 和风险等级。
- 可以依赖的 platform/shared 模块。
- 是否涉及公共包、公共前端组件、数据库迁移或组合根。

跨模块需求应先明确契约和数据 Owner，不要让多个 Codex 任务各自猜字段。

### 第 3 步：填写 Codex 任务范围

编辑本需求的 `codex-task-scope.yaml`：

- `assignment`：开发者、模块、Owner、分支和独立工作区。
- `writable_paths`：本次确实允许修改的最小路径。
- `read_only_paths`：可用于理解但不能修改的代码和文档。
- `forbidden_paths`：密钥、生产配置、无关模块等禁止范围。
- `public_capability_change`：涉及 platform、shared、公开契约或公共前端时，填写已批准需求编号、审批、兼容和回归测试。
- `database`、`external_access`、`required_tests` 和 `risk`：如实填写影响与验证要求。
- `completion`：选择 `full` 或 `minimal`，完整模式填写唯一 `control_prefix` 和预期证据，最小模式填写免建账本理由。

需求文档、任务范围和当前任务前缀的 `.ai-control` 证据路径也应包含在 `writable_paths`。不要用 `**` 授权整个仓库或账本目录。

### 第 4 步：创建分支和独立工作区

```bash
git fetch origin
git switch -c feat/REQ-20260806-001-short-name origin/main
```

多人或多个 Codex 任务并行时，使用独立 Git worktree，并确保各任务的 `writable_paths` 尽量不重叠。不要直接在 `main` 开发。

### 第 5 步：启动 Codex 并执行编码前预检

向 Codex 提供需求编号，不粘贴生产数据、真实口令、Token、内网地址或未脱敏日志。要求 Codex 在编码前先输出：

1. 对目标和验收标准的理解。
2. 目标模块及 Owner。
3. 拟修改文件。
4. 只读和禁止路径。
5. 计划复用的现有能力。
6. 公共能力、数据库、权限、审计和外部访问影响。
7. 测试与浏览器验收计划。
8. 未决问题。

研发人员确认预检与任务范围一致后再允许编码。需要增加文件时，先更新并评审任务范围。

### 第 6 步：实施并维护证据

- Codex 和研发只修改 `writable_paths`，不顺手重构包名、目录或无关代码。
- 优先复用现有公共能力；公共能力变更先取得 Owner 审批。
- 后端落实服务端权限、数据范围、校验和审计；前端显隐不能代替后端权限。
- Flyway 迁移只追加，不修改已发布脚本。
- 在 `.ai-control/` 对应任务记录中维护计划、执行命令、观测结果、偏差和交接，不把聊天结论当作项目证据。

### 第 7 步：执行本地验证

仓库治理检查：

```bash
node scripts/check-all-governance.mjs
```

任务修改范围检查：

```bash
node scripts/check-codex-scope.mjs \
  --scope docs/requirements/REQ-20260806-001/codex-task-scope.yaml \
  --base origin/main --head HEAD --working-tree
```

后端聚焦测试按模块选择：

```bash
mvn -pl :ccb-system -am test
mvn -pl :ccb-workflow -am test
mvn -pl :ccb-ai -am test
```

完整构建：

```bash
mvn test
mvn -DskipTests package
npm --prefix web run build
```

涉及页面或用户流程时，还要启动本地依赖、后端和前端，在真实浏览器中验证指定角色、路由、桌面/移动视口、关键接口、控制台、刷新/返回、错误、无权限、重复提交、遮挡和溢出。

### 第 8 步：提交 PR

使用 `.github/pull_request_template.md` 填写需求、范围、Codex 参与、公共能力、权限、数据库、测试、发布和回退。PR 必须关联 `requirement.md` 与 `codex-task-scope.yaml`，并等待：

- `CODEOWNERS` 对应 Owner 审批。
- Governance、Codex Scope、Flyway、Maven 和 Vue 检查。
- 新依赖漏洞与许可证审查、密钥扫描。
- 高风险目录的人工专项复核。

Codex 可以辅助生成提交说明和 PR 内容，但不能自行批准、合并或发布。

## 八、不同角色怎么参与

| 角色 | 主要操作 |
| --- | --- |
| 需求负责人 | 填写并推动 `requirement.md` 达到 ready，确认验收与非范围 |
| 研发人员 | 确认模块、填写任务范围、管理分支、监督 Codex、执行验证并提交 PR |
| Codex | 遵守 AGENTS、Skill 和任务范围，实施并如实报告证据 |
| 模块 Owner | 审核边界、契约、公共能力、兼容性和模块风险 |
| 测试/验证人员 | 独立验证 API、权限、异常、浏览器路径和回归，不复述开发结论 |
| 发布人员 | 核对 CI、版本、迁移、配置、上线验证和回退，不在生产现场改代码 |

## 九、哪些文件普通需求不要修改

以下文件只有独立治理、架构或公共能力任务才能修改：

- `docs/governance/**`
- 根目录和分层 `AGENTS.md`
- `.agents/skills/**`
- `governance/modules.yaml`
- `.github/CODEOWNERS`、Workflows 和 Codex 治理文件
- `scripts/check-*.mjs`
- platform/shared 的未授权路径和已发布 Flyway 迁移

普通需求通常只需要修改本需求目录、任务授权的业务代码与测试，以及对应 `.ai-control` 证据。

## 十、常见错误

- 只有聊天需求，没有 ready 的 `requirement.md` 和任务范围就开始编码。
- 只读根 `AGENTS.md`，忽略目标模块更近的规则。
- 把 Skill 当成目录写权限，越过 `writable_paths`。
- 修改公共组件或 platform，却未声明公共能力变更和回归测试。
- 让前端隐藏按钮代替后端权限校验。
- 修改历史 Flyway 文件，而不是新增迁移。
- 只说“测试通过”，不记录实际命令、场景和结果。
- 将个人 Codex 对话、未脱敏日志或生产截图作为团队事实源。
- 只修改 Codex 提示词而不同步正式规约，造成团队规则失真。

## 十一、开发完成检查表

- [ ] 需求状态为 `ready`，目标、范围和验收可测试。
- [ ] 模块、Owner、分支、工作区和风险已确认。
- [ ] `codex-task-scope.yaml` 完整且路径授权最小化。
- [ ] 已读取根目录和目标目录的全部适用 `AGENTS.md`。
- [ ] 已使用 `rddmp-delivery-engineer` Skill 完成预检。
- [ ] 没有生产或敏感数据进入 Codex 上下文。
- [ ] 变更未越过模块、公开包和数据 Owner 边界。
- [ ] 治理、任务范围、聚焦测试、完整构建均有真实结果。
- [ ] 用户流程已按要求完成运行和浏览器验收。
- [ ] `.ai-control`、PR、发布验证和回退信息完整。
