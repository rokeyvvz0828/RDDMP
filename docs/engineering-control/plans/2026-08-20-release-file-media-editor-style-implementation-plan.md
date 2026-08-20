# 文件介质编辑区样式整理实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

**目标：** 将版本申请中的文件介质编辑区整理为与相邻字段一致的紧凑表单布局，同时保持全部业务行为不变。

**架构：** 只调整 `ReleaseApplicationDrawer.vue` 的文件介质局部语义结构，并由 `release-prototype.css` 提供标题操作行、三列路径行和移动端约束。现有 `draft.fileMedia`、事件处理、校验和 API payload 原样复用。

**技术栈：** Vue 3、TypeScript、Element Plus、现有发布模块 CSS 变量、Vite。

## 全局约束

- 仅修改文件介质局部模板和对应 CSS，不修改其他表单区域、API、状态模型或后端。
- 保留 `toggleContentTypes`、`addFileMedia`、`removeFileMedia` 和 `file.filePath` 的现有行为。
- 沿用 `Plus`、`Delete` 图标和现有语义颜色，不增加依赖、卡片或说明文案。
- 桌面和 `375x812`、`390x844`、`430x932` 不得出现页面级横向溢出或操作裁切。
- 保护 `rokey` 工作区内所有其他未提交修改，不提交、不推送、不清理。

---

## 文件职责地图

| 路径 | 状态 | 职责 | 事实依据 |
| --- | --- | --- | --- |
| `web/src/modules/release/components/ReleaseApplicationDrawer.vue` | existing | 文件介质标题、添加操作、路径输入和删除操作结构 | 当前第 197 行包含完整文件介质编辑器 |
| `web/src/modules/release/release-prototype.css` | existing | 发布模块申请抽屉及文件介质响应式样式 | 当前第 339-354 行定义文件介质布局 |
| `.ai-control/requirements/req-20260820-040-release-file-media-editor-style/*.json` | candidate-new | 本需求控制证据 | 新需求前缀 |

## 任务依赖图与并行策略

仅有 T1，一个任务内部串行执行。模板和 CSS 共同构成同一可验收界面，不安全并行拆分。

## 需求覆盖表

| 需求 | 任务 |
| --- | --- |
| R1 | T1 |
| R2 | T1 |
| R3 | T1 |
| R4 | T1 |

### T1：统一文件介质字段与路径行样式

**需求映射：** R1, R2, R3, R4

**前置任务：** 无

**文件：**
- 修改：`web/src/modules/release/components/ReleaseApplicationDrawer.vue:197`
- 修改：`web/src/modules/release/release-prototype.css:339`
- 新建：`.ai-control/requirements/req-20260820-040-release-file-media-editor-style/execution-T1.json`
- 新建：`.ai-control/requirements/req-20260820-040-release-file-media-editor-style/observation-T1.json`
- 新建：`.ai-control/requirements/req-20260820-040-release-file-media-editor-style/convergence.json`

**接口：**
- 消费：`draft.contentTypes`、`draft.fileMedia`、`addFileMedia()`、`removeFileMedia(id)` 和 `file.filePath` 现有契约。
- 产出：`.release-file-media-editor` 内紧凑标题操作行与稳定的三列路径行；不产生新的 TypeScript 或 API 接口。

- [ ] **步骤 1：记录当前失败信号**

  使用用户截图和当前 DOM 作为基准，记录标题字号脱离表单层级、添加按钮视觉权重过高、路径行侧列尺寸不一致三个偏差。

  预期：当前页面可稳定复现三个视觉偏差，业务增删路径仍可用。

  证据：基准截图、DOM 类名和控件尺寸。

- [ ] **步骤 2：调整文件介质局部模板**

  在 `ReleaseApplicationDrawer.vue` 中保留现有条件渲染和事件，将标题与添加入口组织为语义明确的紧凑字段标题行；为序号、路径输入和行级操作增加职责单一的局部类名。添加按钮继续显示 `Plus + 添加路径`，删除按钮继续使用圆形 `Delete` 图标。

  预期：所有 `v-model`、`v-for key`、添加和删除处理器保持原签名；没有 payload 或校验变化。

  证据：限定模板 diff 和 TypeScript 构建结果。

- [ ] **步骤 3：实施稳定桌面与移动端布局**

  在 `release-prototype.css` 中将标题设为标准字段字号，添加按钮改为紧凑低权重操作；路径行采用固定序号列、`minmax(0, 1fr)` 输入列和固定操作列，统一垂直居中与 8px 左右间距。移动端仅压缩侧列和间距，不隐藏操作、不改变字号随视口缩放。

  预期：单条和多条路径均对齐；长路径不扩张抽屉；悬停和内容变化不引发布局跳动。

  证据：CSS diff、桌面及移动端 DOM 几何测量。

- [ ] **步骤 4：运行构建与静态检查**

  运行：`npm --prefix web run build`

  预期：`vue-tsc --noEmit` 和 Vite 构建退出码为 0，只允许既有包体积告警。

  运行：`git diff --check`

  预期：退出码为 0，无空白错误。

  证据：命令退出码和关键汇总。

- [ ] **步骤 5：浏览器验证视觉与交互**

  在桌面视口打开版本申请抽屉，勾选文件介质，验证首行、添加第二行、删除一行和长路径输入；再在 `375x812`、`390x844`、`430x932` 重复几何检查。

  预期：标题和按钮处于同一字段层级；每行控件对齐；增删只影响目标行；所有视口 `document.documentElement.scrollWidth <= window.innerWidth`；控制台无 error。

  证据：桌面/移动端截图、DOM 尺寸、行数变化和控制台结果。

**验收检查：** R1-R4 全部满足；构建和 diff 检查通过；桌面及三种手机视口可操作且无横向溢出。

**风险：** Element Plus 按钮默认边距可能干扰标题行；窄屏下三列可能压缩输入区域。

**回滚：** 仅恢复 `ReleaseApplicationDrawer.vue` 文件介质局部模板和 `release-prototype.css` 对应规则，不触碰其他未提交内容。

**停止条件：** 修复必须修改全局 Element Plus 主题、共享组件或业务状态才能完成；现有增删路径行为在修改前已失效。

**升级条件：** 用户要求改变路径录入流程、业务文案、校验或其他表单区域；移动端必须更改全局抽屉布局才能避免溢出。

## 集成检查

完成 T1 后运行 `npm --prefix web run build`、`git diff --check`，并检查本次实际产品 diff 仅落在两个批准文件的文件介质局部。服务保持在 `http://127.0.0.1:5173/release` 供验收。

## 控制模型种子

以下仅为 `hypotheses-only` 候选，由 `$model-engineering-system` 复核：

- 被控边界候选：申请抽屉文件介质局部 DOM 与 CSS。
- 状态变量候选：内容类型、路径行数量、路径文本、视口宽度、抽屉滚动位置。
- 接口候选：现有添加、删除、双向绑定和表单提交契约。
- 传感器候选：前端构建、限定 diff、浏览器截图、DOM 尺寸、横向溢出和控制台。
- 执行器候选：局部模板类名与 CSS 网格、间距、字号和按钮属性。
- 扰动候选：Element Plus 默认按钮边距、主题/密度、长路径、多行和窄屏。
- 时延候选：Vite 热更新、抽屉过渡和视口重排。
- 假设候选：局部类名足以修复问题，无需全局样式或业务逻辑变更。

## 风险与用户批准

本计划不包含数据库、API、依赖、Git 分支或业务数据写入。用户在被告知“确认设计后直接编写计划并修复样式”后明确回复“确认”，据此批准按本计划进入开发。
