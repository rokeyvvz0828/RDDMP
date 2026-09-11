---
id: REQ-20260910-068
status: ready
owner: rokeyvvz0828
module: business/data-migration
---

# 数据迁移「迁移过程依赖文件」菜单优化

## 业务目标

将「数据迁移 > 数迁资产内容 > 迁移过程依赖文件」（菜单 750）从通用文件上传页改造为迁移参数与使用方系统的依赖关系管理页。维护人员可单条新增、编辑、逻辑删除和批量导入依赖关系；查询人员可直接比较参数中英文名、使用方和提供方组件以及更新信息，不再上传、替换或下载文件。

## 范围

### 本次实施

- `dm_dependency` 增加迁移参数主键 `parameter_id`，将现有 `system_code` 明确为使用方系统编号。
- 删除 `dm_dependency.doc_code`、`dm_dependency.doc_name` 及其相关索引；取消依赖文件上传、替换和下载接口与页面入口。
- 新增依赖关系专属 Service、Controller 和统一回收站来源，提供分页列表、详情、单条新增/编辑、逻辑删除、模板下载和 Excel 批量导入。
- 列表通过 `parameter_id` 关联 `dm_parameter` 展示参数英文名、参数中文名和提供方系统编号，并分别投影使用方、提供方系统中文名。
- 增加菜单 750 的 `create/update/delete` 细粒度权限节点；服务端继续执行认证、RBAC、项目范围、实体权限和审计。
- 保持看板 DEPENDENCY 指标可计数和下钻，调整删除旧列后的展示表达式。

### 本次不实施

- 不处理、转换、回填或删除任何历史 `dm_dependency` 行；存在历史行时迁移失败关闭。
- 不改变迁移参数的名称、英文名或唯一性规则。
- 不提供依赖关系导出，不新增公共前端组件或样式体系。
- 不改造系统/组件清单和系统管理/参数管理。
- 不删除需求未点名的 `dm_dependency.component_id`；新链路不读写该兼容列。

## 现状与规则

- 当前入口为 `/data-migration/content/dependencies`，权限节点为 `data-migration:content:dependencies`；当前页面复用通用 `AssetListView`，后端由 `ContentAssetController` 和 `ContentFileAssetService` 承接附件上传、替换、下载和逻辑删除。
- 数据 Owner 为 `business/data-migration`。所属项目取全局项目上下文，页面不提供项目下拉。
- 参数必须是当前租户、当前项目中未删除的 `dm_parameter`；使用方系统必须来自当前项目启用的 `dm_component.system_code`。
- 提供方组件编号为 `dm_parameter.system_code`，提供方组件名称为该编号对应的系统中文名。
- 使用方系统编号为 `dm_dependency.system_code`，使用方系统名称为该编号对应的系统中文名。
- 同一租户、项目内，`parameter_id + system_code` 唯一；逻辑删除记录仍占用唯一关系，需恢复或彻底删除后才能重新新增。
- 写操作记录 `dm_operation_log`，`entity_type='DEPENDENCY'`；创建、更新、删除、导入、恢复和彻底删除均保留操作人及结果。
- 参数存在活动依赖时不得逻辑删除；存在任意依赖记录时不得彻底删除参数。

## 接口与数据

- 专属 REST 前缀保持 `/api/data-migration/dependencies`：分页列表、详情、参数选项、新增、编辑、删除、模板下载和导入。
- 删除旧 `/dependencies/upload`、`/dependencies/{id}/upload`、`/dependencies/{id}/download` 端点。
- 列表字段：参数英文名、参数中文名、使用方系统编号、使用方系统名称、提供方组件编号、提供方组件名称、更新时间、更新人。
- Excel 模板固定为“参数英文名、使用方系统编号”。参数英文名按当前租户和项目做不区分大小写的精确查询；恰好命中一条时解析为 `parameter_id`，零条或多条均作为该行错误。
- 导入限制 `.xlsx`、50 MB、5000 行；逐行校验并部分成功，返回总行数、成功数、失败数和逐行错误，不覆盖已有关系。
- 数据库迁移只追加 `V199__data_migration_dependency_domain.sql`，不修改已发布脚本；迁移前断言 `dm_dependency` 为空。
- 不使用生产数据、真实个人信息、真实附件或未脱敏日志。

## 验收标准

1. 列表按当前项目服务端分页，展示参数中英文名、使用方编号/名称、提供方编号/名称、更新时间和更新人；关键字可匹配参数中英文名及两侧系统编号/名称，使用方系统可单独筛选。
2. 单条新增/编辑只能选择当前项目有效参数和启用系统；同一参数与使用方系统的重复关系由应用校验和数据库唯一键共同拒绝。
3. Excel 模板仅含“参数英文名、使用方系统编号”；导入对不存在、多义、停用、跨项目和重复数据逐行报错，合法行继续写入并记录审计。
4. 依赖关系逻辑删除后进入统一回收站，支持管理员恢复和彻底删除；活动依赖阻止其参数被删除。
5. 页面不再出现文件选择、上传、替换或下载动作，旧上传与下载端点不可用。
6. 服务端强制认证、细粒度 RBAC、租户和项目范围、实体 Owner/管理员写权限；前端权限显隐不替代后端校验。
7. 前端覆盖加载、空、筛选无结果、失败、无权限、保存中、导入中和重复提交；桌面及 375/390/430 移动视口无页面级横向溢出，明暗主题可读。
8. 空 `dm_dependency` 可应用 V199；存在任意行时迁移失败且不删除旧列、不生成占位关系；看板 DEPENDENCY 指标在新结构上仍可计数和下钻。

## 测试与发布

- 必须执行：`mvn -pl :ccb-data-migration -am test`、聚焦依赖服务和 MySQL 迁移测试、`npm --prefix web run build`、`node scripts/check-all-governance.mjs`、当前任务范围检查和 `git diff --check`。
- 浏览器验收：启动本地 MySQL/MinIO/后端/前端，按有权限和只读角色走通筛选、新增、编辑、导入部分失败、删除和回收站，并检查桌面、`375x812`、`390x844`、`430x932` 及明暗主题。
- 上线前确认目标库 `dm_dependency` 为空，先应用 V199 再部署新应用；不得绕过空表断言。
- V199 尚未执行时可回退本需求应用代码。V199 已执行但尚无新记录时，需经 Owner 批准的前向补偿迁移恢复旧列后才能降级旧应用；已有新关系后优先修复前进，不做直接应用降级。
- 数据库迁移、权限目录和破坏性旧接口移除需 `business/data-migration` 与 `platform/infrastructure` Owner 专项复核。
