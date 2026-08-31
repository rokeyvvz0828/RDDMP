---
name: writing-plans
description: 在已有用户确认的设计或规格、且任务需要多个实施步骤时使用，在修改代码之前把设计转换为文件路径、接口、测试、命令、证据和提交边界明确的可执行计划，并生成可导入 control-engineering 的交接包。
---

# 编写实施计划

## 概述

假设执行者熟悉软件开发，但不了解当前代码库、工具和领域。把所需上下文、文件、接口、步骤、验证和风险写全，将工作拆成小而可验收的任务。遵循 DRY、YAGNI、项目既有工程规范和频繁检查点。

开始时说明：“正在使用 writing-plans Skill 编写实施计划。”

本 Skill 只编写计划和交接包，不修改产品代码、依赖、配置或运行环境。计划默认保存到：

```text
docs/engineering-control/plans/YYYY-MM-DD-<feature-name>-implementation-plan.md
```

用户或项目指定的位置优先。完整交接字段以 [references/plan-and-handoff-contract.md](references/plan-and-handoff-contract.md) 为准。

## 进入门禁

- 存在经用户确认的设计或规格，且当前请求与其一致。
- 使用机器可读设计时，`design_status=approved`、审批证据可追踪，并且没有 `blocking=true` 的未知项。
- 设计范围可以由一份计划交付。

不满足时回到 `$brainstorming`，不要在计划中偷偷补产品决策。

## 范围检查

若规格包含多个独立子系统，应在设计阶段拆成多个子项目。尚未拆分时，建议每个子系统使用独立规格和计划，使每份计划都能产生可运行、可验证的软件。

## 文件结构

拆任务前先映射拟创建或修改的文件及单一职责：

- 单元边界清楚，接口定义明确，可独立理解和测试。
- 文件因同一职责共同变化时放在一起，按职责拆分，不机械按技术层拆分。
- 沿用代码库既有模式；只在当前文件职责失控并直接阻碍目标时规划最小拆分。
- 区分已证实路径和 `candidate-new` 候选路径，不把猜测写成事实。

这个文件地图决定任务边界。

## 任务大小

任务是“拥有完整验证周期、值得独立接受或拒绝”的最小单元。把脚手架、配置、文档和准备步骤并入真正需要它们的交付任务。只有审阅者可能接受一个任务而拒绝相邻任务时才拆分。每个任务必须产生可独立测试的结果。

任务内部每一步只做一个动作，通常能在几分钟内完成：

- 建立失败或基准验证；
- 运行并记录预期信号；
- 实施最小变更；
- 运行局部和回归验证；
- 保存证据并建立提交检查点。

是否测试先行由项目规范和闭环任务包决定，但所有行为变化必须有可重复的验收传感器。

## 计划头部

每份计划必须以以下结构开始：

```markdown
# <功能名称> 实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

**目标：** <一句话说明要构建什么>

**架构：** <2-3 句说明实施方向和关键边界>

**技术栈：** <关键技术和库>

## 全局约束

<逐行抄录规格中的版本下限、依赖限制、命名、文案、平台和禁止修改范围。每个任务都隐式继承。>

---
```

## 任务结构

每个任务使用以下结构，不得保留模板占位符：

````markdown
### T1：<组件或可验收结果>

**需求映射：** R1, R2

**前置任务：** 无 | T0

**文件：**
- 新建：`准确/路径/file.py`
- 修改：`准确/路径/existing.py:123`
- 测试：`tests/准确/路径/test_file.py`

**接口：**
- 消费：<来自前序任务的准确签名、格式或契约>
- 产出：<后续任务依赖的名称、参数和返回类型>

- [ ] **步骤 1：建立失败或基准检查**

```python
def test_specific_behavior():
    result = function(input_value)
    assert result == expected
```

- [ ] **步骤 2：运行检查并确认当前信号**

运行：`pytest tests/path/test.py::test_name -v`
预期：失败，错误包含 `function not defined`
证据：保存退出码和关键断言

- [ ] **步骤 3：实施最小变更**

```python
def function(input_value):
    return expected
```

- [ ] **步骤 4：运行局部与相关回归**

运行：`pytest tests/path/test.py::test_name -v`
预期：通过，0 个失败

- [ ] **步骤 5：建立提交检查点**

```bash
git add tests/path/test.py src/path/file.py
git commit -m "feat: add specific feature"
```

**回滚：** <准确回滚点或方式>

**停止条件：** <必须停止、重新建模或请求用户决定的条件>

**升级条件：** <必须升级给主 Agent 或用户的条件>
````

## 禁止占位符

以下内容表示计划失败，必须改为实际细节：

- `TBD`、`TODO`、“以后实现”或“补充细节”；
- “添加适当的错误处理”“处理边界情况”；
- “为上述内容编写测试”但不给测试位置、输入和断言；
- “类似任务 N”，执行者可能只看到当前任务，必须重复必要契约；
- 只描述目标、不说明如何实施和验证；
- 引用从未在任何任务中定义的类型、函数、路径或接口。

始终写出准确路径、完整步骤、可复制命令和可判别预期。代码步骤要给出实现所需的实际片段；代码庞大时可引用已确认接口和精确修改位置，但不能用空泛描述替代。

## 工程控制交接扩展

在上游计划结构之外，为每个任务补充闭环所需字段：

- 稳定任务 ID、需求 ID 和前置任务；
- 已证实输入事实及来源；
- `create`、`modify`、`test` 文件边界；
- `consumes` 和 `produces` 接口；
- 每一步的动作、命令、预期和证据；
- 验收检查、风险、回滚、停止和升级条件。

建立任务依赖边和并行组。只有写入面、接口、数据库、端口、生成物和测试环境互不冲突时才允许并行；无法证明安全时标为串行。

从设计和只读工程勘察中提取 `control_seed`：被控边界、状态变量、接口、传感器、执行器、扰动、时延和假设。它们必须标记为 `hypotheses-only`，供 `$model-engineering-system` 验证，不能宣称已经可观测或可控。

## 自检

完成计划后从头检查：

1. 规格覆盖：每条需求能否指向至少一个任务，所有 `must` 是否覆盖？
2. 占位符：搜索并消除所有禁止模式。
3. 类型一致：后续任务中的函数、类型和属性名称是否与产出任务完全一致？
4. 文件事实：候选文件是否明确标记，是否伪造了不存在的接口？
5. 依赖正确：是否存在未知任务、自依赖、循环依赖或不安全并行？
6. 可判别性：命令、预期、证据、回滚、停止和升级条件是否具体？
7. 控制种子：所有系统模型信息是否仍标记为假设？

发现问题直接修正。多 Agent 可用且项目允许时，可使用 [plan-document-reviewer-prompt.md](plan-document-reviewer-prompt.md) 做独立计划复核；复核不替代用户确认。

## 写入交接包

除人可读计划外，生成：

```text
.ai-control/<topic>-handoff.json
```

交接包嵌入完整 `design`、`implementation_plan` 和 `control_seed`，不能只保存易失路径。用户确认前使用 `handoff_status=awaiting-user-approval`，不得伪造审批时间或证据。

向用户展示计划路径、任务摘要、并行策略和高风险动作，请其复核落盘计划并明确是否按此计划进入开发。用户要求修改时修订计划并重新自检。

只有用户批准当前修订后，才能设置：

```text
implementation_plan.plan_status = ready
implementation_plan.approval.status = approved
handoff_status = approved
```

然后校验并导入：

```powershell
python <control-engineering-skill>/scripts/control_loop.py import-handoff `
  --state .ai-control/state.json `
  --input .ai-control/<topic>-handoff.json `
  --mode standard
```

导入成功必须显示：`账本阶段=baseline；下一阶段=modeling`。随后调用 `$control-engineering`。若开始实施前需要隔离 Git 工作区，先调用 `$using-git-worktrees`，再把控制权交给闭环主 Agent。

## 完成门禁

- 设计状态、修订和批准证据有效。
- 每条 `must` 需求至少由一个任务覆盖。
- 每个任务含完整文件、接口、步骤、验证、回滚、停止和升级条件。
- 依赖无环，并行组没有已知冲突。
- 命令与预期信号具体，计划没有占位符。
- 控制模型种子只包含候选与假设。
- 用户批准了落盘计划和高风险动作。
- 交接包通过 `import-handoff`，账本仍位于 `baseline`。
- 已显式调用 `$control-engineering` 接管。

