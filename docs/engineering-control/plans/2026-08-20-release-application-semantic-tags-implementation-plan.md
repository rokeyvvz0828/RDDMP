# 版本申请三列语义标签实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

**目标：** 将版本申请列表的版本类型、申请特征和状态统一为尺寸、对齐和语义色一致的小标签。

**架构：** 保留版本类型和状态现有 `UiStatusTag` 渲染，只将申请特征从“追加申请标签 / 普通裸文本”改为统一的 `UiStatusTag`。不修改公共组件、DTO、接口或后端。

**技术栈：** Vue 3、TypeScript、Element Plus、现有 `UiStatusTag`、Vite。

## 全局约束

- 仅修改 `web/src/modules/release/components/ReleaseApplicationView.vue` 的申请特征列。
- “普通”改为“普通申请”，使用 `info` 色调；“追加申请”使用 `warning` 色调。
- 保持版本类型和状态的现有文案、色调函数、列宽及行为。
- 不修改 API、数据、筛选、权限、后端、公共组件或全局样式。
- 保护 `rokey` 工作区内其他未提交修改，不提交、不推送、不清理。

---

## 文件职责地图

| 路径 | 状态 | 职责 | 事实依据 |
| --- | --- | --- | --- |
| `web/src/modules/release/components/ReleaseApplicationView.vue` | existing | 版本申请列表三列内容渲染和语义映射 | 当前版本类型、申请特征、状态三列位于同一表格模板 |
| `web/src/components/ui/UiStatusTag.vue` | existing-readonly | 提供 `small`、`plain` 语义标签 | 当前版本类型和状态列已使用该组件 |

## 任务依赖图与并行策略

仅有 T1，内部串行执行。没有需要并行的独立写入面。

## 需求覆盖表

| 需求 | 任务 |
| --- | --- |
| R1 | T1 |
| R2 | T1 |
| R3 | T1 |
| R4 | T1 |

### T1：统一三列语义标签

**需求映射：** R1, R2, R3, R4

**前置任务：** 无

**文件：**
- 修改：`web/src/modules/release/components/ReleaseApplicationView.vue:87`
- 新建：`.ai-control/requirements/req-20260820-041-release-application-semantic-tags/execution-T1.json`
- 新建：`.ai-control/requirements/req-20260820-041-release-application-semantic-tags/observation-T1.json`
- 新建：`.ai-control/requirements/req-20260820-041-release-application-semantic-tags/convergence.json`

**接口：**
- 消费：`scope.row.characteristic` 和 `UiStatusTag(value, tone)` 现有契约。
- 产出：`ADDITIONAL -> 追加申请 / warning`，其他值 `-> 普通申请 / info` 的局部展示映射。

- [ ] **步骤 1：建立基准检查**

  运行：`rg -n "characteristic.*ADDITIONAL.*el-tag.*普通" web/src/modules/release/components/ReleaseApplicationView.vue`

  预期：当前申请特征列同时存在默认尺寸 `el-tag` 和 `release-muted` 裸文本，证明三列展示不一致。

  证据：命中行及当前三列模板。

- [ ] **步骤 2：实施最小模板修改**

  将申请特征列改为单个 `UiStatusTag`：

  ```vue
  <UiStatusTag
    :value="scope.row.characteristic === 'ADDITIONAL' ? '追加申请' : '普通申请'"
    :tone="scope.row.characteristic === 'ADDITIONAL' ? 'warning' : 'info'"
  />
  ```

  预期：三列均由同一组件渲染；版本类型和状态函数、列宽、数据及事件无变化。

  证据：限定模板 diff 和源码断言。

- [ ] **步骤 3：运行构建与静态检查**

  运行：`npm --prefix web run build`

  预期：`vue-tsc --noEmit` 和 Vite 构建退出码为 0，只允许既有包体积及依赖注释告警。

  运行：`git diff --check`

  预期：退出码为 0，无空白错误。

  证据：命令退出码和构建汇总。

- [ ] **步骤 4：浏览器验证三列组合**

  在版本申请列表检查至少两种代表组合：“常规版本 / 普通申请 / 审批中”和“常规版本 / 追加申请 / 草稿”。

  预期：三个标签高度、字号、圆角和左侧起点一致；普通申请为中性色，追加申请为警示色；100px 申请特征列无裁切，表格无新增横向溢出，控制台无新增 error。

  证据：截图、标签文本与 DOM 几何、表格宽度和控制台结果。

**验收检查：** R1-R4 全部满足；三列无文本/标签混排；构建与空白检查通过。

**风险：** 四字“普通申请”在 100px 列内可能偏紧。

**回滚：** 仅恢复 `ReleaseApplicationView.vue` 申请特征列原模板。

**停止条件：** 浏览器证据表明现有 `UiStatusTag` 无法在当前列宽内完整显示，且解决需要修改全局组件或其他列。

**升级条件：** 用户要求把相同规范扩展到详情、统计或其他业务模块，或要求改变语义色映射。

## 集成检查

- `npm --prefix web run build`：退出码 0。
- `git diff --check`：退出码 0。
- `rg -n "申请特征" web/src/modules/release/components/ReleaseApplicationView.vue`：该列只使用 `UiStatusTag`。

## 控制模型种子

- 被控边界候选：版本申请列表三列的 DOM 表现。
- 状态候选：三列标签文本、色调、尺寸、单元格对齐和列内裁切。
- 传感器候选：源码断言、TypeScript/Vite 构建、浏览器截图、DOM 几何、控制台。
- 执行器候选：申请特征列的 `UiStatusTag` 文案和色调绑定。
- 扰动候选：Element Plus 主题变量、浏览器缩放、中文字体宽度和现有列宽。
- 上述内容仅为 `hypotheses-only`，由系统建模阶段验证。

## 风险与用户批准

无数据库、权限、接口、依赖或部署风险。计划修订 1 已由用户确认，可进入受控实施。
