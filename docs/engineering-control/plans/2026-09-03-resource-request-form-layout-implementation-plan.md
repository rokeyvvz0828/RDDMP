# 资源申请表单主从布局实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

## 状态与来源

- 计划修订：1
- 设计修订：1
- 设计文档：`docs/engineering-control/designs/2026-09-03-resource-request-form-layout-design.md`
- 需求文档：`docs/requirements/REQ-20260903-059-resource-request-form-layout/requirement.md`
- 任务范围：`docs/requirements/REQ-20260903-059-resource-request-form-layout/codex-task-scope.yaml`
- 状态：可移交（用户于 2026-09-03 批准修订 1）

**目标：** 将资源申请新建/编辑弹窗改为基础信息与申请项登记的两级主从布局，保留重复部署单元多规格和现有 API 契约。

**架构：** 在 `ResourceRequestPage.vue` 内为申请项增加稳定的前端临时 ID、当前项选择、状态判定、错误定位、物理子系统切换保护和脏表单关闭保护；模板只渲染当前申请项的完整字段。`architecture.css` 负责桌面双层分栏、中等宽度重排和手机单列，不修改公共组件。

**技术栈：** Vue 3、TypeScript、Element Plus、Vite、现有架构模块 API/类型与语义主题变量。

## 全局约束

- 只修改 scope 的 `writable_paths`，不修改 `api.ts`、`types.ts`、后端、数据库、路由或公共 UI。
- 保持 `ResourceRequestPayload`、`ResourceRequestItemPayload`、`items[]` 顺序和重复 deploymentUnitId 行为。
- 保持 DB 与 APPLICATION/WEB 字段分流、容量计算、现有服务端校验和权限语义。
- 申请项视图身份必须使用稳定临时 ID，不使用数组索引作为 key 或当前项标识。
- 物理子系统变更有内容时必须确认；确认后重建单个空项，取消恢复原值。
- 保存期间禁止会改变申请项集合、当前项或物理子系统的操作。
- 响应式遵循 `design-h5.md`，禁止页面级横向滚动、裁剪内容或缩小字号。
- 每个实现任务完成局部构建和小提交检查点，不混入现有 T11 未跟踪账本文件。

---

## 文件职责地图

| 路径 | 状态 | 职责与证据 |
| --- | --- | --- |
| `web/src/modules/architecture/ResourceRequestPage.vue` | existing | 当前资源申请列表、详情、新建/编辑、申请项字段、校验和提交均在此文件。 |
| `web/src/modules/architecture/architecture.css` | existing | 当前登记项网格、移动端断点和架构模块语义样式位于此文件。 |
| `docs/requirements/REQ-20260903-059-resource-request-form-layout/*` | existing | 本需求目标、范围和路径授权。 |
| `.ai-control/requirements/req-20260903-059-resource-request-form-layout/design.json` | existing | 已批准机器设计。 |
| `.ai-control/requirements/req-20260903-059-resource-request-form-layout/handoff.json` | existing | 当前待审批开发前交接包。 |
| `.ai-control/requirements/req-20260903-059-resource-request-form-layout/execution-T*.json` | candidate-new | 各任务实际执行证据。 |
| `.ai-control/requirements/req-20260903-059-resource-request-form-layout/observation-T*.json` | candidate-new | 各任务独立观察证据。 |
| `.ai-control/requirements/req-20260903-059-resource-request-form-layout/convergence.json` | candidate-new | 最终收敛结论。 |

## 任务依赖图与并行策略

```text
T1 申请项状态与保护逻辑
  -> T2 两级布局与响应式呈现
    -> T3 集成、浏览器验收与证据
```

三个任务串行。T1 和 T2 都修改 `ResourceRequestPage.vue`，且模板依赖 T1 产出的 `currentItem`、临时 ID 和状态函数；并行会造成共享文件冲突和中间构建失败。

## 需求覆盖表

| 需求 | 任务 |
| --- | --- |
| R1 外层两栏 | T2、T3 |
| R2 申请项主从编辑 | T1、T2、T3 |
| R3 重复部署单元多规格 | T1、T3 |
| R4 状态与错误定位 | T1、T2、T3 |
| R5 物理子系统切换保护 | T1、T3 |
| R6 API 与业务兼容 | T1、T3 |
| R7 全状态与响应式 | T1、T2、T3 |

### T1：申请项状态、校验定位与切换保护

**需求映射：** R2、R3、R4、R5、R6、R7

**前置任务：** 无

**文件：**

- 修改：`web/src/modules/architecture/ResourceRequestPage.vue:73`
- 修改：`web/src/modules/architecture/ResourceRequestPage.vue:117`
- 修改：`web/src/modules/architecture/ResourceRequestPage.vue:445`
- 修改：`web/src/modules/architecture/ResourceRequestPage.vue:530`
- 修改：`web/src/modules/architecture/ResourceRequestPage.vue:650`
- 测试：无新增测试文件；使用 TypeScript 构建和 T3 浏览器传感器。

**接口：**

- 消费：现有 `ResourceFormItem`、`blankItem()`、`loadDeploymentUnits()`、`requestItemPayload()`、`itemHasDemand()`、`isDatabaseRecord()` 和 Element Plus `ElMessageBox`。
- 产出：
  - `ResourceFormItem.clientId: string`，仅前端使用。
  - `selectedItemId: Ref<string>` 与 `currentItem: ComputedRef<ResourceFormItem | null>`。
  - `itemValidationError(item): string | null` 和 `itemCompletionState(item): 'UNSELECTED' | 'INCOMPLETE' | 'COMPLETE'`。
  - `handlePhysicalSubsystemChange(nextId)`、`addItem()`、`removeItem(clientId)`、`selectItem(clientId)`。
  - `handleFormBeforeClose(done)` 和成功保存绕过脏表单确认的关闭标志。

- [ ] **步骤 1：建立当前构建和行为基线**

运行：

```powershell
npm --prefix web run build
rg -n "form.items = \[blankItem\(\)\]|:key=\"index\"|removeItem\(index\)|form.items.map\(requestItemPayload\)" web/src/modules/architecture/ResourceRequestPage.vue
```

预期：构建退出 0；扫描证明当前项使用数组索引身份、全部明细同时渲染且提交按数组顺序执行。

证据：构建摘要、扫描行号和退出码。

- [ ] **步骤 2：增加稳定申请项身份和当前项状态**

将表单项扩展为：

```ts
type ResourceFormItem = ResourceRequestItemPayload & UnitShape & {
  clientId: string
  deploymentUnitCode: string | null
  deploymentUnitName: string | null
  deploymentUnitDescription: string | null
  networkZoneName: string | null
}

let resourceItemSequence = 0
function nextResourceItemId() {
  resourceItemSequence += 1
  return `resource-item-${resourceItemSequence}`
}
```

`blankItem()` 每次生成新 ID；`openCreate()` 和 `openEdit()` 在填充后设置 `selectedItemId`。新增后立即选中新项；删除按原索引选择下一项或上一项，只剩一项时拒绝删除。`currentItem` 只通过 `clientId` 查找，找不到时返回第一项并同步选择。

预期：同一 deploymentUnitId 可存在多次，每条申请项仍有独立 clientId 和资源字段。

证据：TypeScript diff 和 `form.items`/`selectedItemId` 状态快照。

- [ ] **步骤 3：抽取单项校验并实现状态与错误定位**

把现有循环内规则收敛为纯函数：

```ts
function itemValidationError(item: ResourceFormItem) {
  if (!item.deploymentUnitId) return '请选择部署单元'
  if (hasNegativeOrFractionalResource(item)) return '资源容量、CPU、内存、组数和节点数必须为非负整数'
  if (!itemHasDemand(item)) return isDatabaseRecord(item)
    ? 'DB 明细至少填写数据库存储需求、数据库或数据库版本'
    : '非 DB 明细至少填写一项资源容量或附加需求'
  if (!isDatabaseRecord(item) && !item.networkZoneId) return '非 DB 明细必须选择网络分区'
  return null
}
```

`validateForm()` 保持申请级字段优先；单项失败时设置 `selectedItemId = item.clientId`，错误文案包含“申请项 N”。`itemCompletionState()` 复用 `itemValidationError()`，未选部署单元单独返回 `UNSELECTED`。

预期：列表状态与保存校验不会使用两套规则；服务端仍执行最终校验。

证据：构建结果及 T3 三种错误旅程。

- [ ] **步骤 4：实现物理子系统切换与脏表单保护**

在打开表单时记录 `acceptedPhysicalSubsystemId` 和序列化初始快照。物理子系统选择器通过 `@change` 调用异步处理函数：单个完全空白项允许直接重建；其他情况弹出确认。确认后取消旧候选请求、清空展开状态、重建并选中一个空项、更新已接受 ID、加载新候选；取消时恢复 `form.physicalSubsystemId`。

关闭保护使用 `el-dialog` 的 `before-close`：比较申请级字段和去除 clientId 后的 `items[]` 快照；保存成功设置一次性 `allowFormClose` 后关闭，用户取消关闭则保留上下文。

预期：确认、取消、候选失败和保存失败都不会产生旧物理子系统部署单元与新选择混合的状态。

证据：T3 浏览器确认/取消/失败路径和 Network 请求。

- [ ] **步骤 5：运行局部构建并建立提交检查点**

运行：

```powershell
npm --prefix web run build
git diff --check -- web/src/modules/architecture/ResourceRequestPage.vue
git add web/src/modules/architecture/ResourceRequestPage.vue
git commit -m "feat(architecture-web): add resource request item state"
```

预期：构建和 diff 检查退出 0；提交只包含 T1 状态与行为修改，不包含布局 CSS。

**验收检查：** 稳定临时 ID、默认空项、增删切换、重复部署单元、状态复用校验、错误定位、物理子系统确认/取消、保存与关闭保护。

**回滚：** revert T1 提交；无数据或接口补偿。

**停止条件：** 实现需要修改 `types.ts`/`api.ts`；现有 payload 无法剥离前端临时 ID；物理子系统切换无法在不改后端的情况下恢复旧状态；构建出现 scope 外契约错误。

**升级条件：** 用户要求自动保存、申请项排序、删除最后一项或公共表单组件；现有服务端实际禁止重复 deploymentUnitId。

### T2：两级主从布局和响应式呈现

**需求映射：** R1、R2、R4、R7

**前置任务：** T1

**文件：**

- 修改：`web/src/modules/architecture/ResourceRequestPage.vue:1242`
- 修改：`web/src/modules/architecture/architecture.css:375`
- 测试：无新增测试文件；使用构建、DOM 和 T3 多视口浏览器传感器。

**接口：**

- 消费：T1 的 `currentItem`、`selectedItemId`、`itemCompletionState()`、`addItem()`、`removeItem(clientId)`、`selectItem(clientId)`、保存中状态和关闭保护。
- 产出：外层基础信息/登记表布局、申请项列表、当前项编辑器，以及桌面、中等宽度、手机三档布局。

- [ ] **步骤 1：重组弹窗外层布局**

给表单弹窗增加业务类名和稳定宽高约束：

```vue
<el-dialog
  v-model="formOpen"
  class="architecture-resource-request-form-dialog"
  width="min(1480px, calc(100vw - 32px))"
  top="3vh"
  :before-close="handleFormBeforeClose"
  destroy-on-close
>
```

正文改为 `architecture-resource-request-layout`：左侧 `architecture-resource-request-basics` 包含四个选择器、申请原因和紧凑物理子系统摘要；右侧 `architecture-resource-request-registration` 包含标题、数量和申请项主从区域。物理子系统选择器连接 T1 的切换处理函数，保存时禁用。

预期：基础信息不再与登记项纵向混排，现有字段和只读摘要无遗漏。

证据：Vue 模板 diff 和桌面 DOM 结构。

- [ ] **步骤 2：实现申请项列表和当前项编辑器**

左列使用语义化 `button type="button"` 列表项，以 `clientId` 为 key；显示“申请项 N”、部署单元名称/编号、类型和文字状态。当前项具有 `aria-current="true"`，删除使用图标按钮、Tooltip 和 aria-label；新增按钮位于列表头。

右列只对 `currentItem` 渲染原有部署单元、DB 或非 DB、技术栈和附加需求字段。所有 `item` 绑定改为 `currentItem`，附加需求 collapse 名称使用 `clientId`，删除旧的 `v-for` 登记卡片和数组索引 key。

预期：切换列表只改变右侧绑定对象，不重建或覆盖其他项；状态文字不只依赖颜色。

证据：DOM、Vue Devtools/页面输入切换结果和键盘可达性。

- [ ] **步骤 3：增加桌面和移动端局部样式**

默认样式：

```css
.architecture-resource-request-layout {
  display: grid;
  grid-template-columns: minmax(280px, .34fr) minmax(0, 1fr);
  gap: 16px;
  min-width: 0;
}
.architecture-resource-request-master-detail {
  display: grid;
  grid-template-columns: minmax(210px, 250px) minmax(0, 1fr);
  gap: 12px;
  min-width: 0;
}
```

列表按钮使用现有 `--panel-bg`、`--line`、`--text`、`--muted`、`--brand`、`--success`、`--warning`，圆角不超过 8px。中等宽度将内层改为单列且列表使用紧凑网格；`760px` 以下外层单列，申请项列表改为有明确边界的横向局部滚动，当前项字段单列，弹窗正文高度受视口约束，页面本身不横向滚动。

预期：1280 桌面信息密度合理；375/390/430 无页面级横向溢出，底部操作始终可达。

证据：计算样式、`scrollWidth` 和四视口截图。

- [ ] **步骤 4：运行构建和样式静态检查**

运行：

```powershell
npm --prefix web run build
rg -n "#[0-9a-fA-F]{3,8}|rgb\(|hsl\(|letter-spacing:\s*-|min-width:\s*[5-9][0-9]{2}px" web/src/modules/architecture/architecture.css
git diff --check -- web/src/modules/architecture/ResourceRequestPage.vue web/src/modules/architecture/architecture.css
```

预期：构建和 diff 检查退出 0；新增样式不硬编码颜色、不使用负字距、不引入阻塞手机的固定最小宽度。

证据：构建摘要、扫描结果和 diff。

- [ ] **步骤 5：建立提交检查点**

```powershell
git add web/src/modules/architecture/ResourceRequestPage.vue web/src/modules/architecture/architecture.css
git commit -m "feat(architecture-web): redesign resource request form"
```

预期：提交只包含 T2 模板和样式改动，`git diff --cached --check` 退出 0。

**验收检查：** 外层两栏、内层主从、申请项状态、图标可访问名称、当前项单独渲染、桌面/中等宽度/手机重排、弹窗滚动和底部操作。

**回滚：** revert T2 提交恢复 T1 的状态逻辑；若需要完全恢复原表单，再 revert T1。

**停止条件：** 必须修改公共弹窗组件；手机端仍出现页面级横向滚动；当前项表单字段遗漏；布局要求改变现有业务顺序或 API。

**升级条件：** 用户要求桌面固定像素宽度、移动端保留双栏、申请项列表改为公共组件或新增视觉规范例外。

### T3：集成验证、浏览器验收与交付证据

**需求映射：** R1、R2、R3、R4、R5、R6、R7

**前置任务：** T1、T2

**文件：**

- 证据：`.ai-control/requirements/req-20260903-059-resource-request-form-layout/execution-T1.json`
- 证据：`.ai-control/requirements/req-20260903-059-resource-request-form-layout/execution-T2.json`
- 证据：`.ai-control/requirements/req-20260903-059-resource-request-form-layout/observation-T1.json`
- 证据：`.ai-control/requirements/req-20260903-059-resource-request-form-layout/observation-T2.json`
- 证据：`.ai-control/requirements/req-20260903-059-resource-request-form-layout/convergence.json`
- 产品修正：仅在观察发现偏差且纠偏批准后修改 T1/T2 两个授权文件。

**接口：**

- 消费：T1/T2 已构建实现和现有资源申请本地 API。
- 产出：请求兼容、权限、四视口、明暗主题、错误恢复、范围和回退证据。

- [ ] **步骤 1：执行静态、构建、治理和范围检查**

运行：

```powershell
git diff --check
npm --prefix web run build
node scripts/check-all-governance.mjs
node scripts/check-codex-scope.mjs --scope docs/requirements/REQ-20260903-059-resource-request-form-layout/codex-task-scope.yaml --base HEAD~2 --head HEAD
```

预期：本需求 diff、构建和 scope 退出 0；治理检查若仍仅报告既有历史账本错误，必须逐项标记为基线噪声，不得描述为通过；本前缀不得新增错误。

证据：退出码、构建摘要、实际文件清单和历史噪声对照。

- [ ] **步骤 2：运行真实浏览器功能旅程**

按 README 启动本地前后端，使用虚构测试账号进入 `/architecture/resource-requests`：

1. 新建默认一个空申请项；新增至三项，逐项填写并来回切换，确认值保留。
2. 两项选择相同部署单元，填写不同 CPU/内存/节点数并保存；检查 Network 中 `items[]` 数量、顺序和字段值。
3. 编辑已保存草稿，检查原顺序、重复部署单元和默认第一项回显。
4. 删除首项、中间项和末项，检查相邻选中与至少保留一项。
5. 构造未选择、DB 无需求、非 DB 缺网络分区，检查列表状态和首错定位。
6. 有内容时切换物理子系统：分别确认清空和取消恢复；模拟候选失败时检查内容保留和重试。
7. 保存失败、重复点击保存、关闭脏表单和保存成功关闭分别验证。
8. 用无权限角色验证无权限状态；apply/manage 角色保持现有操作边界。

预期：无 500、无重复保存请求、无旧部署单元与新物理子系统混合；服务端响应和页面状态一致。

证据：脱敏 Network 请求/响应、Console、页面状态和操作步骤。

- [ ] **步骤 3：执行四视口与明暗主题验收**

视口：`1280x800`、`375x812`、`390x844`、`430x932`；每个视口检查浅色和深色主题。验证长部署单元名称、三个以上申请项、DB/非 DB 长表单、错误提示、候选加载、附加需求展开和底部操作。

在页面执行：

```js
document.documentElement.scrollWidth <= window.innerWidth
```

预期：桌面双层布局可扫描；中等宽度和手机按设计重排；页面级无横向滚动，只有申请项列表允许有边界的局部滚动；Console 无新增错误。

证据：视口、主题、DOM 宽度、必要截图和 Console 摘要。

- [ ] **步骤 4：记录执行、独立观察和收敛结论**

为 T1/T2 写实际修改、命令和结果；由独立观察上下文复验请求兼容、物理切换和四视口，并写 observation。存在 P0/P1 时回到 correcting/executing，不直接标记 converged。

预期：R1-R7 均有执行与观察证据，范围一致，无开放 P0/P1；未执行检查明确列为残余风险。

证据：当前前缀 execution、observation、convergence 和 state phase。

- [ ] **步骤 5：建立证据提交检查点**

```powershell
git add docs/requirements/REQ-20260903-059-resource-request-form-layout docs/engineering-control/designs/2026-09-03-resource-request-form-layout-design.md docs/engineering-control/plans/2026-09-03-resource-request-form-layout-implementation-plan.md .ai-control/requirements/req-20260903-059-resource-request-form-layout
git commit -m "docs(architecture): close resource request form layout"
```

预期：提交前 scope 与 `git diff --cached --check` 通过；不包含其他需求 T11 文件；不自行合并、推送或发布。

**验收检查：** 全部 must 需求、API 请求兼容、权限无回归、四视口明暗主题、无页面级横向溢出、范围一致、无开放 P0/P1。

**回滚：** revert T3 证据提交；产品按 T2、T1 逆序回退，无数据库补偿。

**停止条件：** API 请求字段或顺序发生变化；重复 deploymentUnitId 被服务端拒绝；权限边界放松；页面白屏、提交不可达或产生页面级横向滚动；需要 scope 外修改。

**升级条件：** 需要后端支持新状态；需要公共 UI 改造；历史治理错误成为不可绕过 Required Check；真实环境无法提供资源申请测试数据。

## 集成检查

| 完成任务 | 命令/传感器 | 通过信号 |
| --- | --- | --- |
| T1 | `npm --prefix web run build` + 状态旅程 | 申请项状态、校验和切换保护编译并可操作 |
| T1、T2 | 前端构建 + DOM/样式检查 | 两级主从布局可渲染且无硬编码主题回归 |
| T1—T3 | build、governance、scope、Network、Console、四视口 | R1-R7 有证据，无本需求 P0/P1 |

## 控制模型种子

以下仅为 `hypotheses-only` 候选，必须由 `$model-engineering-system` 验证：

- 被控边界候选：资源申请弹窗表单状态、申请项数组、当前项选择、部署单元候选请求、主从 DOM 和架构局部 CSS。
- 状态变量候选：`formOpen`、`formSubmitting`、`physicalSubsystemId`、`acceptedPhysicalSubsystemId`、`items[]`、`selectedItemId`、`currentItem`、`expandedItemExtras`、候选加载错误、初始快照和脏状态。
- 接口候选：`ResourceRequestPayload`、`ResourceRequestItemPayload`、`loadResourceDeploymentUnitOptions`、Element Plus dialog `before-close` 和 select `change`。
- 传感器候选：TypeScript/Vite build、源码扫描、Network payload、Console、DOM `scrollWidth`、多视口截图、scope 和 diff 检查。
- 执行器候选：申请项临时 ID/选择函数、单项校验函数、物理子系统切换处理、模板主从重组、局部响应式 CSS。
- 扰动候选：部署单元候选请求失败或乱序、长名称、多个申请项、保存慢响应、用户快速切换或关闭、现有历史治理噪声。
- 时延候选：候选 API 响应、Vite 构建、浏览器全旅程和四视口验收。
- 假设：申请项顺序即提交顺序；服务端继续允许重复 deploymentUnitId；现有 API/类型不需要改变。

## 风险与用户批准

- 高关注动作：重写资源申请弹窗的核心状态与模板；物理子系统切换会主动清空申请项；新增脏表单关闭拦截。
- 兼容边界：API、数据库和权限不变；任何需要修改 `api.ts`、`types.ts` 或后端的情况都必须停止并重新批准范围。
- 用户于 2026-09-03 回复“批准”，确认按计划修订 1 创建隔离工作树、导入 control-engineering 交接并按 T1 → T2 → T3 串行实施。
