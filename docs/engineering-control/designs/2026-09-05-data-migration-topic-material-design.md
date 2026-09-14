# 数据迁移专题材料优化工程设计

## 文档状态
- 修订：1
- 状态：已确认
- 用户确认依据：用户确认专题类型统一由“系统管理 / 参数管理”维护，并允许在追加迁移中幂等初始化两个参数类别及 23 个初始参数项。
- 需求：`REQ-20260820-031`
- 控制前缀：`req-20260820-031-data-migration-asset-library-v3`

## 目标与成功信号

将“数据迁移 / 数迁资产内容 / 专题材料”从通用文件资产薄页升级为专属域功能，支持项目级/系统级专题、参数管理中的专题类型、多个涉及系统、多个源文件、查看、编辑、下载、逻辑删除及统一回收站。

成功信号：

1. 列表可以按颗粒度、专题类型、系统和专题名称/简述组合筛选并服务端分页。
2. 新增和编辑能够保存完整元数据及多个附件；系统级专题至少关联一个当前项目有效系统。
3. 普通用户只能编辑/删除本人上传记录，管理员可管理全部记录和回收站。
4. 删除、恢复、彻底清理和附件绑定保持数据库、对象存储、审计一致。
5. 桌面端与 375x812、390x844、430x932 手机视口无页面级横向滚动。

## 使用者与场景

- 数据迁移管理员：维护专题材料、回收站和系统参数。
- 普通数据迁移人员：查看、下载、上传并维护本人专题材料。
- 入口：现有数据迁移菜单和全局项目切换器；页面不再自行选择项目。

## 必须需求与验收条件

### R1 专题元数据

`dm_topic` 保存 `granularity`、`topic_type_code`、`topic_summary`；名称、简述、颗粒度、类型和至少一个附件在新增/编辑时非空。既有 `doc_code`、租户、项目、逻辑删除和审计字段保持不变。

验收：缺少任一必填项返回 400；系统级缺少系统关联返回 400；项目级提交系统关联返回 400。

### R2 参数管理类型

专题类型只来自系统管理参数：

- `DM_TOPIC_PROJECT_TYPE`
- `DM_TOPIC_SYSTEM_TYPE`

参数项 `config_key` 是稳定编码，`config_value` 是展示名称；仅启用参数可用于新增、编辑和筛选。追加迁移幂等初始化两个类别及 23 个初始参数项。

验收：颗粒度切换后只返回对应类别；停用或不存在的编码不能写入；前端不硬编码类型名称。

### R3 多系统和多附件

新增 `dm_topic_system` 关系表保存 `(topic_id, project_id, system_code)`；系统编号来源当前项目活动 `dm_component`。附件统一使用 `dm_content_attachment` 的 `business_type='TOPIC'`，一个专题可绑定多个文件，`sort_order=0` 为主文件。

验收：同一专题可绑定多个文件；重复系统编号被拒绝；删除/恢复/彻底清理不产生悬挂关系或附件绑定。

### R4 查询与页面交互

列表展示专题名称、颗粒度、专题类型、专题简述、附件数量和操作；支持名称/简述关键字、颗粒度、类型、系统组合筛选、20/50/100 分页、详情查看、下载、编辑和逻辑删除。

验收：筛选回到第一页；加载、空、无结果、失败、无权限、提交中、重复提交和项目切换状态可观察且可恢复。

### R5 权限与审计

服务端执行认证、RBAC、项目可达性、实体归属、系统编号和附件状态校验。所有写操作写入 `dm_operation_log`，使用 `project_id` 和专题操作码。

验收：伪造 `tenantId`/`ownerId`/项目或跨项目 ID 无效；普通用户越权返回 403；管理员回收站操作可审计。

### R6 范围控制

专题外的必要邻接代码只限于解除通用 TOPIC 路由/回收站重复认领，并切换前端 API 契约；不修改平台 Java 业务、全局项目切换器或公共附件/审计表结构。

验收：通用资产链路不再认领 TOPIC；统一回收站仅由 `TopicRecycleBinSource` 提供 TOPIC；范围检查无越界文件。

## 不变量与约束

- 单租户；每个查询和写入绑定当前项目与 `tenant_id`。
- Flyway 只追加，建议使用 `V181__data_migration_topic_domain.sql`。
- 不读取或写入 `biz_form_*`；不修改 `platform/system` 私有实现。
- 文件单个不超过 50MB；对象引用不返回前端。
- 软删行仍占用 `(tenant_id, project_id, doc_code)` 唯一名额，彻底清理后才能重建相同编号。
- 页面遵循 `design-h5.md`、`UiToolbar`、`UiDataTable`、`UiFormDrawer` 和统一状态组件。

## 方案比较与选择

| 方案 | 结论 | 原因 |
|---|---|---|
| 专题专属服务/控制器 + 参数管理 + 关系表 | 选择 | 与会议纪要/迁移方案一致，字段和关系可验证，回收站边界清晰 |
| 继续复用 `AssetListView`，把新字段塞进 JSON | 不选 | 无法表达多系统、多附件和类型联动，弱化校验与审计 |
| 新建 `dm_topic_type` 业务字典表 | 不选 | 与用户确认的系统管理/参数管理归属冲突，产生重复维护入口 |

## 架构边界与组件职责

### 范围内

- `TopicService`：专题 CRUD、参数编码校验、系统关系、附件集合、权限和审计。
- `TopicController`：专题列表、详情、新增、编辑、下载、删除及选项接口。
- `TopicRecycleBinSource`：统一回收站 TOPIC 来源。
- `TopicsPage.vue`：专属列表/详情/编辑交互和移动端卡片。
- `V180`：专题字段、系统关系表及系统参数幂等初始化。

### 复用但不改表结构

- `SystemReferenceQuery.activeParameters`：读取参数管理启用项。
- `DataMigrationPermissionService`：项目可达性、管理员和实体授权。
- `ContentAttachmentService`、`dm_content_attachment`、`att_file`：附件生命周期。
- `ContentRecycleBinService`、`dm_operation_log`：统一回收站和审计。
- `dm_component`：当前项目系统清单只读来源。

### 必要邻接变更

- `ContentAssetController` 移除通用 `/topics` 映射。
- `ContentFileAssetService.MANAGED_TYPES` 和 `ContentAssetRecycleBinSource` 移除 TOPIC。
- `ContentAssetTables`、看板类型统计和模块注册测试保留 TOPIC 类型语义并切换到专题来源。
- `web/src/api/data-migration.ts` 将 TOPIC 切换为专属 API；`TopicsPage.vue` 不再使用通用薄壳。

不修改 `pm_project`、`dm_component`、`att_file`、`dm_content_attachment`、`dm_operation_log` 的表结构，不修改 `server/src/platform`、`server/src/shared` 和全局项目切换器。

## 数据库设计

### `dm_topic` 新增列

| 列 | 类型 | 说明 |
|---|---|---|
| `granularity` | `VARCHAR(16)` | `PROJECT` 或 `SYSTEM` |
| `topic_type_code` | `VARCHAR(64)` | 参数管理中的 `config_key` |
| `topic_summary` | `VARCHAR(2000)` | 专题材料简述 |

新增索引：`(tenant_id, project_id, granularity, deleted, updated_at)`、`(tenant_id, project_id, topic_type_code, deleted)`。现有 `component_id` 保留用于历史兼容，不再作为“涉及系统”字段。

### `dm_topic_system`

```text
id BIGINT PRIMARY KEY
tenant_id BIGINT NOT NULL
topic_id BIGINT NOT NULL
project_id BIGINT NOT NULL
system_code VARCHAR(64) NOT NULL
created_by BIGINT NOT NULL
created_at TIMESTAMP NOT NULL
UNIQUE (tenant_id, topic_id, system_code)
INDEX (tenant_id, project_id, system_code)
```

关系行仅服务 `SYSTEM` 专题；专题彻底清理时级联删除，专题软删时关系保留。

### 参数初始化

V180 使用 `INSERT IGNORE`/存在性判断初始化 `sys_dict_type` 与 `sys_config`，不改变系统管理服务代码。参数项按用户给出的 19 个项目级、4 个系统级专题名称建立稳定编码；后续名称、状态和排序由系统管理维护。

## 接口、数据和状态流

| 接口 | 用途 | 权限 |
|---|---|---|
| `GET /topics` | 项目内组合筛选分页 | topics/access |
| `GET /topics/{id}` | 详情（含附件与系统） | topics/access |
| `POST /topics` | 新增元数据、系统和附件集合 | topics:create/write/manage |
| `PUT /topics/{id}` | 编辑元数据和附件集合 | topics:update/write/manage |
| `DELETE /topics` | 批量逻辑删除 | topics:delete/write/manage |
| `GET /topics/options/types` | 按颗粒度读取参数管理类型 | topics/access |
| `GET /topics/options/systems` | 当前项目系统清单 | topics/access |
| `GET /topics/{id}/attachments` | 附件列表 | topics/access |
| `GET /topics/{id}/download` | 主文件受控下载 | topics/access |

新增请求使用 `projectId` 作为当前项目校验输入；编辑不接受可信项目归属字段。附件先经公共附件接口上传为临时附件，再在专题保存请求中提交 `files[]`。成功后返回统一 `ApiResponse`；下载使用现有受认证的流式附件能力。

状态流：

```text
参数/系统选项加载 -> 表单校验 -> 附件临时上传 -> 专题事务保存 -> 附件绑定/关系替换 -> 审计 -> 刷新列表
                                             |失败
                                             v
                                       回滚专题与关系
```

## 错误、降级与恢复

- 参数类别缺失：选项接口返回可识别错误，页面显示配置缺失，不提交自由文本。
- 参数项停用：服务端返回 400，前端刷新选项后要求重新选择。
- 系统不可达或已删除：返回 400；不保存关系行。
- 重复编号、并发更新或状态变化：返回 409，保留表单并提示刷新。
- 附件超限、临时附件失效或绑定失败：返回 400/413，清理未绑定临时附件并允许重试。
- 项目未选择、切换失败或无权访问：清空旧数据，不回退历史项目。

## 安全、性能、兼容性与运维

- SQL 所有实体查询均包含租户和项目条件；类型和系统选项不接受前端展示名称作为可信值。
- 列表关键字只匹配 `doc_name`、`topic_summary`，使用分页和新增索引，禁止无界全表扫描。
- `dm_topic_system` 使用项目/系统索引；附件绑定沿用公共附件去重和生命周期规则。
- V180 幂等、追加；应用回退不反向执行 Flyway。若迁移前发现专题存量无法回填，迁移 fail-closed 并停止发布。
- 参数初始化写入平台参数表属于已确认的外部数据库影响，但不改平台代码和公共接口。

## 验证策略

1. Flyway MySQL 8.4：V179→V180，断言新增列、关系表、参数类别/23 项、幂等重跑和历史行回填。
2. 后端单元/集成：必填校验、颗粒度与参数联动、系统关系、附件集合、租户/项目/实体授权、409 并发、软删/恢复/purge、审计。
3. API：未认证 401、无权限 403、非法类型/系统 400、重复和状态冲突 409、文件超限 413。
4. 前端：`npm --prefix web run build`，覆盖筛选、详情、新增、多附件、编辑、下载、删除、回收站和项目切换。
5. 浏览器：管理员和普通用户在桌面 1280x800、移动 375x812/390x844/430x932 验收，无页面级横向溢出。
6. 范围/治理：`node scripts/check-all-governance.mjs`、当前 scope 检查和 JSON 解析检查。

## 假设、未知项与决策记录

- 假设：参数管理允许使用两个独立类别表达两种颗粒度；依据用户确认。
- 假设：专题类型编码由参数管理维护且不在专题表保存名称；依据系统公开参数引用契约。
- 未决但非阻塞：列表是否显示当前项目名称。当前遵守 REQ-031 T31-r1，不在页面增加项目选择或项目字段；若产品仍要求展示，需要另行确认局部例外。
- D1：不新建 `dm_topic_type`，统一使用系统管理参数。
- D2：允许 V180 幂等初始化 `DM_TOPIC_PROJECT_TYPE`、`DM_TOPIC_SYSTEM_TYPE` 及 23 个参数项。
- D3：涉及系统使用专题专属多对多关系表，不复用会议关系表。
- D4：专题专属链路替代通用 TOPIC 链路，必要邻接代码变更限于路由、类型注册、回收站来源和前端契约。

## 风险与回退原则

主要风险是平台参数初始数据缺失、旧 TOPIC 路由重复映射、附件关系替换不完整和移动端弹层溢出。通过 V180 幂等校验、注册测试、事务级关系替换、真实附件测试和三组手机视口验收缓解。

回退只回退应用提交并保留追加迁移；不执行反向 Flyway。若需删除新列或关系表，必须另行审批补偿迁移；参数初始值不由应用回退删除，避免破坏管理员后续维护。
