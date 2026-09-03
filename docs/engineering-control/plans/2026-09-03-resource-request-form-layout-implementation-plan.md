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

---

# 修订 2：UAT 紧凑布局实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。修订 1 的 `state.json` 已处于 `converged`，必须建立独立的 UAT 修订控制前缀，不得回退或覆盖历史账本。

## 状态与来源

- 计划修订：2
- 设计修订：2
- 机器设计：`.ai-control/requirements/req-20260903-059-resource-request-form-layout-uat-compact/design.json`
- 状态：可移交（用户于 2026-09-03 批准修订 2）

**目标：** 保持修订 1 主从状态和请求契约不变，压缩基础信息高度，让登记表占满弹窗可用空间并内部滚动，以部署单元名称标识当前申请项，并缩短桌面数值输入框。

**架构：** `ResourceRequestPage.vue` 只调整申请原因行数和两处标题绑定；`architecture.css` 建立 Dialog 到主从容器的可收缩高度链、独立滚动区域和桌面短数值输入。手机断点恢复自然高度和数值输入满宽。

**技术栈：** Vue 3、TypeScript、Element Plus、Vite、现有语义主题变量和真实浏览器尺寸传感器。

## 全局约束

- 产品修改仅限 `ResourceRequestPage.vue` 和 `architecture.css`。
- 保留 R1-R7、稳定临时 ID、状态、首错定位、切换保护和脏表单保护。
- 保持 `ResourceRequestPayload`、`items[]` 顺序、重复 `deploymentUnitId`、DB/非 DB 分流和校验。
- 不修改 API、DTO、后端、数据库、权限、审计、工作流、公共 UI 或应用壳。
- 桌面数值输入为 140-180px；手机端恢复满宽。
- 页面根不得横向滚动；Dialog 标题和 footer 保持可见；超高内容在登记区局部滚动。
- 修订 1 账本只读；实施前建立 `req-20260903-059-resource-request-form-layout-uat-compact` 独立控制前缀。

## 文件职责地图

| 路径 | 状态 | 职责 |
| --- | --- | --- |
| `web/src/modules/architecture/ResourceRequestPage.vue` | existing | 申请原因行数和申请项标题。 |
| `web/src/modules/architecture/architecture.css` | existing | Dialog 高度链、滚动边界、数值输入和移动断点。 |
| `docs/requirements/REQ-20260903-059-resource-request-form-layout/codex-task-scope.yaml` | existing | 声明 UAT 控制前缀和 1920x1080 验收，不扩大产品边界。 |
| `.ai-control/requirements/req-20260903-059-resource-request-form-layout-uat-compact/handoff.json` | existing | 修订 2 开发前交接包。 |
| `.ai-control/requirements/req-20260903-059-resource-request-form-layout-uat-compact/*.json` | candidate-new | 修订 2 独立控制账本和阶段证据。 |

## 依赖与覆盖

```text
T4 建立修订控制入口 -> T5 紧凑模板与滚动布局 -> T6 浏览器验收与收敛
```

三个任务串行。T4 是 scope 和控制门禁；T5 是单一可运行布局切片；T6 依赖完整实现。R8-R12 均由 T5 实现、T6 验收。

### T4：建立 UAT 修订控制入口

**需求映射：** R8、R9、R10、R11、R12

**前置任务：** 无

**文件：**
- 修改：`docs/requirements/REQ-20260903-059-resource-request-form-layout/codex-task-scope.yaml`
- 新建：`.ai-control/requirements/req-20260903-059-resource-request-form-layout-uat-compact/state.json`

**接口：** 消费已批准设计修订 2 和旧账本 `phase=converged`；产出独立 UAT 账本，阶段 `baseline`、下一阶段 `modeling`。

- [ ] **步骤 1：确认旧账本和开发入口**

```powershell
git status --short
(Get-Content -Raw .ai-control/requirements/req-20260903-059-resource-request-form-layout/state.json | ConvertFrom-Json).phase
node scripts/check-development-entry.mjs --require-plugin
```

预期：旧账本为 `converged`，开发入口退出 0，产品文件无未识别改动。

- [ ] **步骤 2：扩展控制证据范围**

在 scope 中增加 UAT 前缀 `.json` 写入路径和证据清单，并将 `1920x1080` 加入浏览器验收；两个产品写入路径不变。

- [ ] **步骤 3：导入交接包**

```powershell
python .agents/skills/control-engineering/scripts/control_loop.py import-handoff `
  --state .ai-control/requirements/req-20260903-059-resource-request-form-layout-uat-compact/state.json `
  --input .ai-control/requirements/req-20260903-059-resource-request-form-layout-uat-compact/handoff.json `
  --mode standard
```

预期：退出 0，输出 `账本阶段=baseline；下一阶段=modeling`，旧账本 revision 不变。

- [ ] **步骤 4：验证 JSON、scope 和 diff**

运行新旧账本 JSON 解析、`node scripts/check-codex-scope.mjs ... --working-tree` 和 `git diff --check`。预期全部退出 0。

**验收检查：** 新前缀可独立推进；旧账本终态不变；产品写入范围未扩大。

**回滚：** 在尚未执行产品修改时回退 scope 并删除新前缀文件；保留旧前缀全部证据。

**停止条件：** 导入要求 `--force`；治理要求覆盖旧账本；新前缀无法满足布局规则。

**升级条件：** Owner 要求使用新需求编号；需要扩大产品写入范围。

### T5：实现紧凑模板、标题和局部滚动

**需求映射：** R8、R9、R10、R11、R12

**前置任务：** T4 完成，control-engineering 经过 baseline、modeling、planning 门禁进入 executing

**文件：**
- 修改：`web/src/modules/architecture/ResourceRequestPage.vue:1447`
- 修改：`web/src/modules/architecture/ResourceRequestPage.vue:1495`
- 修改：`web/src/modules/architecture/ResourceRequestPage.vue:1519`
- 修改：`web/src/modules/architecture/architecture.css:405`
- 证据：`.ai-control/requirements/req-20260903-059-resource-request-form-layout-uat-compact/execution-T5.json`

**接口：** 消费 `deploymentUnitName`、现有状态函数和主从 DOM；产出两处一致的业务标题、2 行原因、桌面高度链、独立滚动和短数值输入。

- [ ] **步骤 1：建立源码和构建基线**

```powershell
rg -n ':rows="4"|申请项 \{\{ index \+ 1 \}\}|申请项 \{\{ currentItemIndex \+ 1 \}\}|max-height: 680px|el-input-number' web/src/modules/architecture/ResourceRequestPage.vue web/src/modules/architecture/architecture.css
npm --prefix web run build
```

预期：定位旧行数、序号标题、固定列表高度和数值框规则；构建退出 0。

- [ ] **步骤 2：调整原因和标题**

将申请原因设为 `:rows="2"`；列表和编辑器主标题分别使用 `item.deploymentUnitName || '未选择部署单元'` 与 `currentItem.deploymentUnitName || '未选择部署单元'`。编码、类型和状态保留为次要信息，错误与删除提示继续使用申请项序号。

- [ ] **步骤 3：建立桌面高度链**

让 Dialog body、表单、外层布局、登记区和主从容器形成带 `min-height: 0` 的 flex/grid 高度链。列表面板和编辑器分别 `overflow-y: auto`，移除 `max-height: 680px`；标题区和 footer 不进入滚动节点。

- [ ] **步骤 4：增加短数值输入和移动覆盖**

当前项编辑器内 `el-input-number` 桌面宽度设为 160px；`760px` 以下恢复 `width: 100%`。选择器、文本输入和备注继续使用可用列宽。

- [ ] **步骤 5：构建、静态检查和提交**

```powershell
npm --prefix web run build
git diff --check -- web/src/modules/architecture/ResourceRequestPage.vue web/src/modules/architecture/architecture.css
rg -n "#[0-9a-fA-F]{3,8}|rgb\(|hsl\(|letter-spacing:\s*-" web/src/modules/architecture/architecture.css
git add web/src/modules/architecture/ResourceRequestPage.vue web/src/modules/architecture/architecture.css
git commit -m "style(architecture-web): compact resource request form"
```

预期：构建和 diff 退出 0，无新增硬编码颜色或负字距，提交仅含两个产品文件。

**验收检查：** 2 行原因、部署单元标题、独立滚动、footer 可见、桌面短数值框、手机满宽、状态和请求不变。

**回滚：** revert T5 提交，恢复修订 1 界面；无数据补偿。

**停止条件：** 必须修改公共 Dialog/应用壳；字段不可达；手机页面根溢出；请求或状态逻辑需改变。

**升级条件：** 要求折叠字段、数值框小于 140px，或取消错误提示序号。

### T6：浏览器验收与收敛

**需求映射：** R8、R9、R10、R11、R12

**前置任务：** T5

**文件：**
- 新建：`.ai-control/requirements/req-20260903-059-resource-request-form-layout-uat-compact/execution-T6.json`
- 新建：`.ai-control/requirements/req-20260903-059-resource-request-form-layout-uat-compact/observation-T5.json`
- 新建：`.ai-control/requirements/req-20260903-059-resource-request-form-layout-uat-compact/observation-T6.json`
- 新建：`.ai-control/requirements/req-20260903-059-resource-request-form-layout-uat-compact/convergence.json`

**接口：** 消费 T5 提交和本地 Mock API；产出 R8-R12 的尺寸、交互、主题、请求兼容、范围和收敛证据。

- [ ] **步骤 1：运行工程传感器**

执行 `git diff --check`、`npm --prefix web run build`、治理检查和以 `fad0585` 为基线的 scope 检查。预期 diff/build/scope 退出 0；历史治理噪声与本需求结果分开记录。

- [ ] **步骤 2：验收桌面高度和滚动**

在 `1920x1080`、`1280x800` 明暗主题打开新建/编辑弹窗，覆盖空项、三项、DB、非 DB、附加需求和错误状态。采集 Dialog、body、layout、registration、master-detail、item-panel、editor、footer 的 `clientHeight`、`scrollHeight` 和 `overflow`。

预期：1920 下标题、主要基础信息、登记区和 footer 同屏；1280 下 footer 可见，超高内容在列表或编辑器内部可达；页面根无横向滚动。

- [ ] **步骤 3：验收标题、数值框和手机**

验证空项、选择、重复部署单元和切换时两处标题同步；错误和删除提示保留序号。桌面数字输入计算宽度在 140-180px；`375x812`、`390x844`、`430x932` 明暗主题恢复满宽，无遮挡或页面级溢出。

- [ ] **步骤 4：回归修订 1 行为与请求**

复验新增、删除、切换、物理子系统确认/取消、候选失败、保存失败、脏关闭和权限显隐；检查 POST/PUT 中 `items[]` 数量、顺序、重复部署单元和字段值，确认无 `clientId`。

- [ ] **步骤 5：记录观察和收敛**

按 control-engineering 写 execution、observation、convergence；偏差进入 correcting/executing。最终预期 R8-R12 全部 pass、新账本 `converged`、旧账本 revision 和终态不变。

**验收检查：** R8-R12、修订 1 回归、请求兼容、权限不变、两桌面与三手机视口明暗主题、滚动尺寸和 scope。

**回滚：** revert 证据提交；产品只需 revert T5。

**停止条件：** footer/字段不可达；页面根溢出；请求或权限变化；需要 scope 外修改；UAT 服务无法恢复。

**升级条件：** 必须修改公共 Dialog/应用壳；真实后端与 Mock 结论冲突；历史治理错误成为硬门禁。

## 控制模型种子

以下仅为 `hypotheses-only`：被控边界为 Dialog 高度链、基础列、登记标题、申请项列表、当前项编辑器和数字输入；状态变量为视口、可用高度、列表数量、当前项类型、展开状态和滚动尺寸；传感器为 build、源码扫描、计算样式、DOM 尺寸、页面根宽度、Network、Console 和截图；执行器为 textarea 行数、`min-height: 0`、局部 overflow、数字宽度和移动覆盖；扰动为长名称、多申请项、DB/非 DB 高度差、错误提示、附加需求和字体渲染。

## 风险与用户批准

- 高关注动作：改变 Dialog 内部滚动所有权，错误高度链可能造成 footer 或字段不可达。
- 兼容边界：数据、权限和请求不变；公共组件、后端或应用壳修改必须重新批准。
- 用户于 2026-09-03 回复“可以”，批准实施计划修订 2，并同意建立独立 UAT 控制前缀后按 T4 → T5 → T6 串行实施。

---

# 修订 3：容量与部署 Flex 布局实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。修订 1、修订 2 的账本均已 `converged`，必须建立 `req-20260903-059-resource-request-form-layout-capacity-flex` 独立控制前缀，不得覆盖历史证据。

## 状态与来源

- 计划修订：3
- 设计修订：3
- 机器设计：`.ai-control/requirements/req-20260903-059-resource-request-form-layout-capacity-flex/design.json`
- 状态：可移交（用户于 2026-09-03 批准修订 3）

**目标：** 保持资源申请字段、状态、计算、校验、请求和紧凑滚动布局不变，将非数据库申请项的“容量与部署”字段区改为 Flex 自适应换行，桌面端每行尽量展示 3–4 项，手机端保持单列满宽。

**架构：** `ResourceRequestPage.vue` 仅为非数据库容量区增加专用容器类，并按选择、数值、布尔和摘要四类标注字段。`architecture.css` 仅在资源申请编辑器范围内定义 Flex basis、换行和手机覆盖；数据库资源区及其他通用网格继续使用现有 Grid。

**技术栈：** Vue 3、TypeScript、Element Plus、Vite、架构模块局部 CSS、真实浏览器计算样式与尺寸传感器。

## 全局约束

- 产品修改仅限 `web/src/modules/architecture/ResourceRequestPage.vue` 和 `web/src/modules/architecture/architecture.css`。
- 保留 R1-R12、主从状态、部署单元标题、短数值输入、内部滚动、首错定位和脏表单保护。
- 保持字段顺序、条件显示、`ResourceRequestPayload`、`items[]`、重复 `deploymentUnitId`、DB/非 DB 分流、计算和校验。
- 不修改 API、DTO、后端、数据库、权限、审计、工作流、公共 UI、应用壳或通用布局断点。
- 容量区使用 Flex 自适应换行；不按固定行拆模板，不缩小字体，不引入页面级横向滚动。
- 已收敛的两个旧账本只读；本轮只维护新控制前缀。

## 文件职责地图

| 路径 | 状态 | 职责与证据 |
| --- | --- | --- |
| `web/src/modules/architecture/ResourceRequestPage.vue` | existing | 非数据库容量区位于约 1549–1576 行，当前使用通用 `architecture-registration-grid--numbers`。 |
| `web/src/modules/architecture/architecture.css` | existing | 约 503 行把资源申请编辑器数字网格覆盖为两列，约 507 行保持数字输入 160px，760px 断点恢复满宽。 |
| `.ai-control/requirements/req-20260903-059-resource-request-form-layout-capacity-flex/design.json` | existing | 已批准设计修订 3 和 R13。 |
| `.ai-control/requirements/req-20260903-059-resource-request-form-layout-capacity-flex/handoff.json` | candidate-new | 待用户批准的开发前交接包。 |
| `.ai-control/requirements/req-20260903-059-resource-request-form-layout-capacity-flex/execution-T7.json` | candidate-new | Flex 实现的实际执行证据。 |
| `.ai-control/requirements/req-20260903-059-resource-request-form-layout-capacity-flex/execution-T8.json` | candidate-new | 集成与浏览器回归的执行证据。 |
| `.ai-control/requirements/req-20260903-059-resource-request-form-layout-capacity-flex/observation-T7.json` | candidate-new | T7 独立观察证据。 |
| `.ai-control/requirements/req-20260903-059-resource-request-form-layout-capacity-flex/observation-T8.json` | candidate-new | T8 独立观察证据。 |
| `.ai-control/requirements/req-20260903-059-resource-request-form-layout-capacity-flex/convergence.json` | candidate-new | R13 最终收敛结论。 |

## 任务依赖图与并行策略

```text
T7 容量区 Flex 实现 -> T8 五视口回归与收敛
```

两个任务串行。T8 必须消费 T7 的实际 DOM、计算样式和提交，不能与实现并行；本轮不拆分 Vue/CSS 并行任务，避免共享布局契约在中间状态下不可验证。

## 需求覆盖表

| 需求 | 任务 |
| --- | --- |
| R13 容量与部署 Flex 自适应换行 | T7、T8 |

### T7：实现容量与部署专用 Flex 布局

**需求映射：** R13

**前置任务：** control-engineering 完成 baseline、modeling、planning 门禁并进入 executing

**文件：**

- 修改：`web/src/modules/architecture/ResourceRequestPage.vue:1550`
- 修改：`web/src/modules/architecture/architecture.css:503`
- 证据：`.ai-control/requirements/req-20260903-059-resource-request-form-layout-capacity-flex/execution-T7.json`
- 测试：无新增测试文件；使用构建、源码扫描和 T8 真实浏览器传感器。

**接口：**

- 消费：现有非数据库容量字段 DOM、`.architecture-resource-request-editor` 局部作用域、修订 2 的 `el-input-number { width: 160px; }` 和 760px 手机覆盖。
- 产出：`.architecture-resource-request-capacity-fields` 容器，以及选择、数值、布尔、摘要字段的局部 Flex 分类契约。

- [ ] **步骤 1：建立源码、构建和运行布局基线**

运行：

```powershell
rg -n "architecture-registration-grid--numbers|architecture-registration-computed|el-input-number" web/src/modules/architecture/ResourceRequestPage.vue web/src/modules/architecture/architecture.css
npm --prefix web run build
```

在 `1920x1080` 页面读取容量容器 `display`、字段 `offsetTop/offsetWidth` 和页面根 `scrollWidth`。预期：源码证明资源申请编辑器当前覆盖为两列；构建退出 0；运行基线显示红框区域每行两项。

证据：扫描行号、构建退出码和容量字段位置数组。

- [ ] **步骤 2：为容量字段增加最小语义分类**

将非数据库容量容器改为：

```vue
<div class="architecture-registration-grid architecture-resource-request-capacity-fields">
```

服务器类型、网络分区增加选择类名；八个资源数字项增加数值类名；有边车增加布尔类名；计算摘要保留现有类并由容量容器作用域识别。不得调整字段顺序、`v-model`、`required`、`disabled`、`@change` 或计算调用。

预期：数据库资源区仍使用 `architecture-registration-grid--numbers`；只有非数据库容量区获得 Flex 分类。

证据：模板 diff 和字段绑定前后扫描对照。

- [ ] **步骤 3：增加局部 Flex 与移动覆盖**

在资源申请编辑器作用域内增加：

```css
.architecture-resource-request-capacity-fields {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  gap: 10px 12px;
}
.architecture-resource-request-capacity-fields > .is-select { flex: 1 1 280px; min-width: 240px; }
.architecture-resource-request-capacity-fields > .is-number { flex: 0 1 200px; min-width: 180px; }
.architecture-resource-request-capacity-fields > .is-boolean { flex: 0 1 180px; min-width: 160px; }
.architecture-resource-request-capacity-fields > .architecture-registration-computed { flex: 1 0 100%; }
```

实际类名需保持 `architecture-resource-request-capacity-*` 前缀，避免通用 `is-*` 污染。`760px` 以下所有直接字段项和摘要设为 `flex-basis: 100%; min-width: 0`，数字输入继续使用现有满宽覆盖。不得删除数据库区的两列规则。

预期：Flex 规则只作用于容量区；桌面短数字框仍为 160px；手机字段项满宽。

证据：CSS diff、选择器命中元素数量和计算样式。

- [ ] **步骤 4：运行局部检查并建立提交检查点**

```powershell
npm --prefix web run build
git diff --check -- web/src/modules/architecture/ResourceRequestPage.vue web/src/modules/architecture/architecture.css
rg -n "#[0-9a-fA-F]{3,8}|rgb\(|hsl\(|letter-spacing:\s*-" web/src/modules/architecture/architecture.css
git add web/src/modules/architecture/ResourceRequestPage.vue web/src/modules/architecture/architecture.css
git commit -m "style(architecture-web): flex resource capacity fields"
```

预期：构建和 diff 退出 0，无新增硬编码颜色或负字距；提交只包含两个产品文件。

**验收检查：** 容量容器计算样式为 Flex 且可换行；选择字段宽于数值字段；摘要独占整行；数据库区和字段绑定无变化；桌面数字输入仍为 160px。

**回滚：** revert T7 产品提交，恢复修订 2 的固定两列容量区；无数据补偿。

**停止条件：** 必须修改公共组件、通用 Grid、API 或状态逻辑；字段顺序或绑定必须改变；Flex 导致字段不可达或 footer 被遮挡；手机页面根溢出。

**升级条件：** 用户要求固定每行数量而非自适应；选择字段必须小于 240px；需要新增公共布局 token 或应用级断点。

### T8：五视口回归与工程控制收敛

**需求映射：** R13

**前置任务：** T7

**文件：**

- 新建：`.ai-control/requirements/req-20260903-059-resource-request-form-layout-capacity-flex/execution-T8.json`
- 新建：`.ai-control/requirements/req-20260903-059-resource-request-form-layout-capacity-flex/observation-T7.json`
- 新建：`.ai-control/requirements/req-20260903-059-resource-request-form-layout-capacity-flex/observation-T8.json`
- 新建：`.ai-control/requirements/req-20260903-059-resource-request-form-layout-capacity-flex/convergence.json`
- 产品纠偏：仅在观察确认 R13 偏差且控制计划批准后修改 T7 的两个产品文件。

**接口：**

- 消费：T7 提交、运行中的 Vite/Mock API、现有新建与编辑资源申请流程。
- 产出：R13 的字段位置、计算样式、响应式、业务回归、范围和收敛证据。

- [ ] **步骤 1：执行工程传感器**

```powershell
node scripts/check-development-entry.mjs --require-plugin
git diff --check
npm --prefix web run build
node scripts/check-all-governance.mjs
node scripts/check-codex-scope.mjs --scope docs/requirements/REQ-20260903-059-resource-request-form-layout/codex-task-scope.yaml --base 813ad56 --head HEAD --working-tree
```

预期：开发入口、diff、build 和 scope 退出 0；治理结果与历史噪声分开记录，本前缀不得新增错误。

- [ ] **步骤 2：验收桌面 Flex 排列和滚动边界**

在 `1920x1080`、`1280x800` 的浅色和深色主题打开非数据库申请项，采集容量容器的 `display/flexWrap/gap`、每个字段的 `offsetTop/offsetWidth`、摘要宽度、数字输入宽度、编辑器滚动尺寸和 footer 可见性。

预期：容器为 `flex` 且 `wrap`；1920 下每行尽量 3–4 项，1280 下按空间自然换行；选择字段宽于数值字段；摘要独占整行；数字输入在 140–180px；footer 可见且超高内容在编辑器内部可达。

- [ ] **步骤 3：验收手机、条件状态和长内容**

在 `375x812`、`390x844`、`430x932` 的浅色和深色主题检查空项、长部署单元名称、有边车开关、禁用边车数字项和错误提示。运行：

```js
document.documentElement.scrollWidth <= window.innerWidth
```

预期：容量字段单列满宽，数字输入可操作，页面根无横向溢出，申请项列表局部滚动和 footer 仍可达，Console 无新增错误。

- [ ] **步骤 4：回归业务和请求不变量**

验证 DB 申请项仍使用原网格；非 DB 字段顺序、网络分区必填、有边车联动、总 CPU/总内存/占比计算、首错定位和申请项切换保持。检查新建 POST 与编辑 PUT 的 `items[]` 数量、顺序、字段值和无 `clientId`。

预期：只存在视觉排列变化，无状态、计算、校验、权限或请求契约变化。

- [ ] **步骤 5：记录独立观察、收敛与证据提交**

按 control-engineering 写入 T7/T8 execution、observation 和 convergence。存在偏差时进入 correcting/executing，不直接标记 `converged`。全部通过后提交新前缀证据、设计、计划和 scope：

```powershell
git add docs/requirements/REQ-20260903-059-resource-request-form-layout docs/engineering-control/designs/2026-09-03-resource-request-form-layout-design.md docs/engineering-control/plans/2026-09-03-resource-request-form-layout-implementation-plan.md .ai-control/requirements/req-20260903-059-resource-request-form-layout-capacity-flex
git commit -m "docs(architecture): close resource capacity flex UAT"
```

预期：R13 pass，新账本为 `converged`，两个旧账本的 revision、哈希和终态不变。

**验收检查：** 两桌面和三手机视口明暗主题、Flex 计算样式、字段位置、摘要整行、短数字框、滚动边界、DB/非 DB、计算、校验、请求、权限、Console、scope 和旧账本不变。

**回滚：** revert T8 证据提交；产品仅需 revert T7 提交，无迁移或数据补偿。

**停止条件：** 页面白屏、footer 或字段不可达、页面根溢出、请求/权限/计算变化、需要 scope 外修改、UAT 服务无法恢复。

**升级条件：** 真实后端与 Mock 结论冲突；历史治理成为硬门禁；Element Plus 默认样式无法在局部作用域内覆盖。

## 集成检查

| 完成任务 | 命令或传感器 | 通过信号 |
| --- | --- | --- |
| T7 | build、diff、源码与计算样式 | 容量区局部 Flex 生效，数据库区和短数字输入不变 |
| T7、T8 | 五视口明暗主题、Network、Console、scope | R13 有运行证据，前两轮行为无回归，无开放 P0/P1 |

## 控制模型种子

以下仅为 `hypotheses-only` 候选，必须由 `$model-engineering-system` 复核：

- 被控边界候选：非数据库容量字段 DOM、资源申请编辑器局部 CSS、移动端断点和内部滚动容器。
- 状态变量候选：视口宽高、编辑器可用宽度、字段类型、字段 `offsetTop/offsetWidth`、容器 `display/flexWrap`、数字输入宽度、根页面宽度和滚动尺寸。
- 接口候选：容量区专用类、字段分类类、`.architecture-registration-computed`、修订 2 数字输入规则和现有 760px 断点。
- 传感器候选：Vite build、源码扫描、计算样式、字段位置数组、DOM `scrollWidth`、Network、Console、五视口截图、scope 和 diff。
- 执行器候选：容量容器类、字段分类类、Flex basis/min-width、摘要 100% basis 和手机覆盖。
- 扰动候选：Element Plus 表单项默认宽度、长标签/选项、条件禁用状态、不同视口、字体渲染和历史治理噪声。
- 时延候选：Vite HMR、Dialog 初次布局、候选接口响应和主题切换重排。
- 假设：1920 宽桌面可稳定容纳 3–4 项；局部选择器可覆盖 Grid；现有 Mock 足以回归请求结构。

## 风险与用户批准

- 高关注动作：改变容量区字段的布局模型，错误 basis 可能造成异常换行、字段过窄或手机溢出。
- 兼容边界：数据、权限、状态、计算和请求不变；公共组件、通用 Grid、后端或应用壳修改必须重新批准。
- 用户于 2026-09-03 回复“批准”，确认按计划修订 3 导入独立控制账本，并按 T7 → T8 串行实施和验收。

---

# 修订 4：字段顺序与一屏密度实施计划

> 执行要求：使用 `$control-engineering` 在独立前缀 `req-20260903-059-resource-request-form-layout-one-screen` 下实施，不覆盖已收敛账本。

## 状态与来源

- 计划修订：4
- 设计修订：4
- 状态：已批准执行（用户于 2026-09-03 确认方案并要求“速度做出来给我看看”）

**目标：** 重排非数据库容量字段并压缩计算摘要和技术栈占行，使宽桌面表单尽量一屏完整展示。

**架构：** 在 `ResourceRequestPage.vue` 中只调整字段 DOM 顺序、增加容量标题行和技术栈局部类；在 `architecture.css` 中定义标题摘要、三列技术栈和紧凑分区间距。移动端沿用现有单列断点。

### T9：重排并验证一屏表单

**需求映射：** R14

**文件：**

- 修改：`web/src/modules/architecture/ResourceRequestPage.vue`
- 修改：`web/src/modules/architecture/architecture.css`
- 证据：`.ai-control/requirements/req-20260903-059-resource-request-form-layout-one-screen/*.json`

**步骤：**

1. 调整容量字段顺序为部署条件、核心容量、边车配置，并把边车开关放在两个边车数值前。
2. 将计算摘要移入容量标题行；为技术栈增加三列局部类；适度压缩编辑器标题和分区间距。
3. 执行 `npm --prefix web run build`、`git diff --check`、scope 检查和样式扫描。
4. 在 1920×1080、1280×800、375×812、390×844、430×932 检查排列、滚动、footer、根宽和控制台。

**验收检查：** 宽桌面容量区为 3/4/3 分组，摘要与标题同行，技术栈三列；手机单列；业务绑定、边车联动、计算、校验和请求不变。

**回滚：** 回退 T9 产品提交即可；无数据补偿。

**停止条件：** 需要修改公共组件或业务逻辑；页面根溢出；字段或 footer 不可达；请求契约变化。

**升级条件：** 必须新增公共断点、改变字段含义或后端契约。
