# 业务表单元数据规约

## 1. 目的与适用范围

本规约将 RADAR 中“输入项配置”的设计思想抽象为 RDDMP 的平台公共契约。后续所有业务模块的表单、详情、列表、筛选和统计字段，必须先登记业务表单范围（scope）和字段定义，再由页面和服务端消费配置。

本规约适用于：

- 新建、编辑、查询、审批和只读详情表单；
- 列表列、列表筛选和统计维度；
- 状态驱动的必填、可见和可编辑规则；
- 业务实体的可配置扩展字段。

本规约不要求业务主表改造成 EAV，也不替代工作流、权限、附件和字典的既有公共契约。

## 2. 核心原则

1. **定义与值分离**：字段定义描述字段是什么、如何展示和如何校验；字段值描述某个业务实体当前填了什么。
2. **核心字段留在主表**：实体身份、状态、主关联、并发控制、审计字段和高频检索字段继续放在业务主表。元数据表只承载可配置或变化频繁的字段。
3. **配置优先，代码兜底**：页面从公开配置渲染可配置字段；配置加载失败时保持原页面可读。服务端必须再次校验字段存在、类型、权限、状态规则和数据范围。
4. **状态不写死中文**：必填、可编辑和可见规则绑定稳定的状态编码，禁止绑定状态名称或前端显示文案。
5. **版本可追溯**：元数据编辑阶段使用当前工作区状态；发布时保存完整的 `published` 快照，并将上一发布版本标记为 `archived`。当前 API 没有独立的草稿 revision 编辑接口，不能把数据库中的 `draft` 状态描述为已实现的编辑流程。业务数据保存字段定义 ID，配置变更不得改写历史值。
6. **外部引用受控**：人员、组织、角色、字典、附件等引用字段只保存受控引用和必要的名称快照，不保存任意外部对象或底层存储路径。
7. **查询白名单**：扩展字段的排序、筛选和统计必须由字段定义的 `filterable`、`sortable`、`dashboard_dimension` 控制，服务端禁止拼接客户端传来的表名、列名或 SQL。
8. **移动端优先**：表单栅格配置只描述桌面布局，移动端统一降为单列；字段标签、错误信息和操作区不得遮挡或溢出，具体遵循根目录 `design-h5.md`。

## 2.1 Agent 自动维护规则（强制）

业务表单元数据的首次登记和后续初始化维护由实现该业务需求的 Agent 随代码一并完成，禁止把初始化工作转交给研发人员手工逐项录入。系统设置中的“输入项配置”页面用于审阅、纠偏、发布和应急维护，不是新业务上线的前置人工配置步骤。字段语义、初始化范围和验收要求以本节为准，Mock 文件只提供可重复加载的样例数据。

每次新增或改造业务功能时，Agent 必须：

1. 在需求设计阶段识别该功能的 `scope`、分区、内置字段、扩展字段、列表列、筛选项、统计维度、选项来源和状态规则。
2. 在同一任务中登记或更新 `biz_form_scope`、`biz_form_section`、`biz_form_field_definition`、`biz_form_field_rule` 和 `biz_form_field_option` 的初始化数据；不得只实现页面而留下空配置。
3. 同步维护 `mock/mock-data.json` 的功能目录和最小可验证数据。若 mock loader 尚未覆盖 `biz_form_*` 表，必须先扩展白名单、字段校验和幂等同步能力，再登记数据。
4. 对配置变化使用追加式 Flyway/受控种子数据或版本化 mock 数据，不能通过手工操作本地页面制造唯一初始化状态；发布配置必须留下可追溯的版本快照。
5. 在任务验收中验证：全新本地数据库启动后无需人工配置即可看到该业务表单的字段、规则和列表/筛选效果；重复启动不会产生重复配置。
6. 在需求、任务范围和 `.ai-control` 证据中列出元数据初始化文件、数据来源、版本、回退方式和验证命令。

Agent 不得以“配置页面已提供”为理由跳过上述初始化。若需求确实不需要表单、详情、列表或筛选配置，必须在需求记录中明确写出“不适用原因”，不能默认留空。

## 3. 配置层级

```text
biz_form_scope
  ├── biz_form_section
  │     └── biz_form_field_definition
  │            ├── biz_form_field_rule
  │            └── biz_form_field_option
  ├── biz_form_config_revision
  └── biz_form_field_value  (scope + entity + field)
```

### 3.1 Scope：业务表单范围

Scope 是一个业务模块对一个实体/页面配置的唯一边界，至少需要登记：

- `scope_key`：稳定编码，例如 `delivery.work-order`；
- `module_key`：所属业务模块；
- `entity_type`：值表中的实体类型；
- `form_key`：表单或页面类型，例如 `create`、`detail`、`approval`；
- `status_field`：状态字段编码，用于匹配状态规则；
- `permission_prefix`：服务端权限校验的前缀。

一个业务模块可以有多个 scope，但一个 scope 不得跨越多个实体类型或权限边界。

### 3.2 Section：表单分区与布局

分区用于组织字段，不承载业务值。`layout_mode` 仅允许：

- `left`：桌面左右布局的左侧；
- `right`：桌面左右布局的右侧；
- `full`：整行展示。

`column_span` 使用 24 栅格，推荐 `12` 和 `24`。移动端所有字段按单列展示。`sort_no` 决定同级分区和字段顺序，禁止使用前端数组顺序作为隐式配置。

### 3.3 Field Definition：字段定义

必填属性：

| 属性 | 说明 |
| --- | --- |
| `field_key` | scope 内唯一编码，接口、前端和规则均使用此编码 |
| `label` | 展示名称，不作为规则主键 |
| `field_kind` | `builtin` 或 `extension` |
| `input_type` | 输入控件类型 |
| `value_type` | 存储类型，服务端据此校验和值列 |
| `visible` | 详情/表单是否展示 |
| `list_visible` | 是否自动生成列表列 |
| `filterable` | 是否允许列表筛选 |
| `sortable` | 是否允许排序 |
| `dashboard_dimension` | 是否可作为统计维度 |
| `sort_no` | 同分区内顺序 |

推荐 `input_type`：`text`、`textarea`、`number`、`date`、`datetime`、`select`、`radio`、`checkbox`、`boolean`、`person`、`organization`、`user`、`attachment`、`rich_text`、`json`。新增类型必须同时补充前后端组件、值类型映射、校验和移动端验收，不得仅插入数据库字符串。

`source_type` 用于受控选项来源：`none`、`static`、`dict`、`user`、`organization`、`role`、`attachment`、`api`。`source_key` 必须是已登记的字典编码、公共资源编码或后端白名单 API key。

内置字段可以由业务专业组件负责渲染，但仍需登记可见、列表、筛选、排序和规则元数据；扩展字段由公共动态表单组件渲染并写入值表。

### 3.4 Field Rule：条件规则

规则按 `field + action + condition` 唯一。当前支持：

- `action_code`：`create`、`edit`、`submit`、`approve`、`view`；
- `condition_type`：`status`、`role`、`expression`；
- `condition_key`：稳定状态编码、角色编码或受控表达式编码；
- `required`、`editable`、`visible`：该条件下的行为；
- `validation_json`：长度、范围、格式等结构化校验参数。

规则合并顺序由服务端定义：先验证 scope 和字段，再按动作、状态、角色计算有效规则，最后执行字段类型和业务规则校验。前端规则只用于交互提示，不能替代服务端校验。

### 3.5 Field Option：静态选项

只有 `source_type=static` 的字段可以使用本表。字典、人员、组织和其他动态来源不得复制为静态选项；如需保留历史展示名称，在值表保存 `value_label_snapshot`。

### 3.6 Field Value：扩展字段值

值表使用 `scope_id + entity_type + entity_id + field_definition_id + ordinal` 唯一定位一个值。单值字段使用 `ordinal=0`，多值字段按顺序使用 `ordinal>=0`。

服务端只允许与 `value_type` 对应的值列有内容：

| `value_type` | 值列 |
| --- | --- |
| `string`、`text` | `value_text` |
| `code` | `value_code` |
| `integer`、`decimal` | `value_number` |
| `date` | `value_date` |
| `datetime` | `value_datetime` |
| `boolean` | `value_boolean` |
| `reference` | `value_ref_id`、`value_ref_type` |
| `json` | `value_json` |

不要把所有字段都放进 `value_json`。只有确实无法结构化查询的复合控件允许使用 JSON，并且字段定义必须标记 `input_type=json`。

## 4. 前后端接口契约

配置查询返回公开字段白名单，不返回数据库表名、内部 SQL、敏感默认值或存储凭据。建议结构：

```json
{
  "scopeKey": "delivery.work-order",
  "revision": 3,
  "sections": [
    {"sectionKey": "basic", "title": "基本信息", "layoutMode": "left", "sortNo": 10}
  ],
  "fields": [
    {
      "fieldKey": "priority",
      "label": "优先级",
      "fieldKind": "extension",
      "inputType": "select",
      "valueType": "code",
      "sourceType": "dict",
      "sourceKey": "work_order_priority",
      "multiple": false,
      "visible": true,
      "listVisible": true,
      "filterable": true,
      "sortable": false,
      "columnSpan": 12,
      "rules": {"submit": {"required": true, "editable": true}}
    }
  ]
}
```

业务模块接入步骤：

1. 登记 scope、分区和字段定义，明确字段类型、来源、布局、列表和筛选标记。
2. Agent 或系统设置页面维护工作区中的规则和选项；系统设置页面可审阅、纠偏和发布，但不能替代 Agent 的首次初始化责任。发布操作生成完整快照并切换当前 `published` 版本。当前实现不提供独立草稿 revision 的编辑接口。
3. 页面读取配置生成表单、列表列和筛选项；专业内置字段仍可复用原组件。
4. 保存主记录后，再保存扩展字段值；扩展值保存必须和主记录操作关联 trace/audit。
5. 查询时由服务端根据字段白名单生成条件，返回稳定的字段编码和值，不允许前端直接访问值表。
6. 新字段同步补充 mock 数据、正常/空/失败/无权限测试和桌面/移动端验收。
7. 新业务首次启动不应依赖人工打开系统设置页面；初始化元数据必须由对应业务需求的 Agent 交付并可重复加载。若仅修改字段定义而不新增业务表，也必须同步更新 Mock 版本和快照。

## 5. 数据库约束与运维

- 当前数据库为 MySQL 8.4，迁移文件使用 Flyway `V32__business_form_metadata.sql`；迁移只追加，不修改已发布脚本。
- 所有表保留 `tenant_id`、软删除标记和创建/更新时间；唯一键必须包含租户和软删除维度。
- 配置删除采用软删除。已发布版本和业务值不物理删除，回滚通过切换发布版本完成。
- 值表查询必须优先使用 `scope_id/entity_type/entity_id` 或 `field_definition_id + value_*` 索引；大规模统计不得无条件扫描整个值表。
- 外部引用保存前必须检查目标对象属于当前租户、当前权限范围且处于有效状态。
- 迁移回退只允许停止使用新表并回滚应用版本；不得在生产手工删除表或清理业务值。

## 6. 禁止事项

- 在业务页面复制一份字段配置并长期硬编码；
- 用字段中文名称、数组下标或数据库自增 ID 作为跨系统契约；
- 为每个扩展字段修改业务主表；
- 把客户端传入的表名、列名或 SQL 片段直接用于查询；
- 只做前端必填校验而不做服务端校验；
- 让研发人员在首次启动后手工录入新业务的字段、分区或规则作为上线前置条件；
- 把真实人员、附件、密钥或生产数据写入 mock、配置快照和测试数据。
