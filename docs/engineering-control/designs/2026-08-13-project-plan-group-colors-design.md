# 项目计划分组配色设计

## 方案

`pm_project_plan_group.color_key` 保存已登记的 `PaletteKey`。服务端使用固定白名单 `ocean/emerald/sunset/graphite/tech-blue/violet/amber` 校验输入；查询将空值归一为 `ocean`。前端直接复用 `web/src/types/ui.ts` 的 `PaletteKey` 和 `paletteOptions`，不复制颜色字典。

分组表格行通过 `data-plan-palette` 使用主题颜色变量生成浅色背景、左侧色条和拖放高亮；计划行沿用所属分组色标，保证分组与计划关系可扫描，同时保留文字和状态标签，不以颜色作为唯一信息。

## 数据与权限

追加 V41，仅增加可空 `color_key` 字段并带中文注释。新增和修改接口继续执行 `plan:create/update`、项目访问校验、租户条件和审计。历史空值不回填，读取时默认 ocean，便于回滚和兼容旧数据。

## 前端交互

分组新建/编辑表单使用主题色选择卡，展示颜色圆点、名称和说明；保存中禁用提交。移动端表单保持单列，表格维持既有局部横向滚动边界。
