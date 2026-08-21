# 追加申请相关历史申请参考实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

**目标：** 在追加申请详情中安全、独立地展示持久化直接关联的历史申请和实时版本变化。

**架构：** Release Store 按租户、当前申请、`ADDITIONAL` 类型和当前活动交付项查询历史申请 ID；Service 读取历史申请实时状态并重算差异；Controller 暴露新增只读端点。Vue 详情页通过独立请求状态渲染追加申请专属区块，失败不影响主详情和审批面板。

**技术栈：** Java 17、Spring Boot、Spring JDBC、JUnit 5、Mockito、Vue 3、TypeScript、Element Plus、Vite。

## 全局约束

- 保持 `rokey` 分支和现有脏工作区，不覆盖无关修改，不提交或推送。
- 不新增依赖、数据库迁移、审批写逻辑或附件聚合。
- 端点沿用 `release:application:view` 和服务层租户隔离。
- 移动端采用单列历史记录，不产生页面级横向滚动。

---

### T1：后端直接关联历史只读契约

**需求映射：** R1、R2、R3、R4、R5

**前置任务：** 无

**文件：**
- 修改：`server/src/modules/release/src/main/java/com/ccb/release/application/model/ReleaseApplicationModels.java`
- 修改：`server/src/modules/release/src/main/java/com/ccb/release/application/persistence/ReleaseApplicationStore.java`
- 修改：`server/src/modules/release/src/main/java/com/ccb/release/application/service/ReleaseApplicationService.java`
- 修改：`server/src/modules/release/src/main/java/com/ccb/release/application/web/ReleaseApplicationController.java`
- 测试：`server/src/modules/release/src/test/java/com/ccb/release/application/service/ReleaseApplicationServiceTest.java`
- 测试：`server/src/modules/release/src/test/java/com/ccb/release/application/service/ReleaseApplicationPersistenceContractTest.java`
- 测试：`server/src/modules/release/src/test/java/com/ccb/release/application/web/ReleaseApplicationControllerSecurityTest.java`

**接口：**
- 消费：`rel_application_relation`、当前活动 `rel_application_delivery`、`ReleaseApplicationStore.findById`
- 产出：`GET /api/release/applications/{code}/related-history -> List<RelatedHistoryView>`

- [ ] **步骤 1：建立服务、持久层和权限失败测试**

  普通申请断言空列表；追加申请断言按历史单聚合、状态实时、差异重算和排序；SQL 契约断言租户、关系类型及活动项过滤；控制器映射断言查看权限。

- [ ] **步骤 2：运行聚焦检查并确认当前信号**

  运行：`JAVA_HOME=/Users/zhangwei/Library/Java/JavaVirtualMachines/openjdk-17.0.18/Contents/Home mvn -pl :ccb-release -am -Dtest=ReleaseApplicationServiceTest,ReleaseApplicationPersistenceContractTest,ReleaseApplicationControllerSecurityTest test`

  预期：新增类型或方法不存在导致失败；保留退出码和关键编译/断言。

- [ ] **步骤 3：实施最小后端变更**

  新增扁平 `RelatedHistoryView`；Store 返回去重历史 ID；Service 复用 `versionChanges` 聚合、过滤、排序；Controller 新增只读路由。

- [ ] **步骤 4：运行聚焦与 Release 回归**

  运行：`JAVA_HOME=/Users/zhangwei/Library/Java/JavaVirtualMachines/openjdk-17.0.18/Contents/Home mvn -pl :ccb-release -am test`

  预期：0 个失败，新增权限和边界测试通过。

- [ ] **步骤 5：记录执行检查点**

  记录实际文件、命令和结果到当前需求前缀账本；不创建 Git 提交。

**回滚：** 仅移除新增 DTO、Store 查询、Service 方法、Controller 路由和对应测试。

**停止条件：** 必须修改数据库结构、跨业务模块私有表或现有审批/冲突语义。

**升级条件：** 有效追加申请没有持久关系，或旧数据必须保留不可跳转审计快照。

### T2：详情页独立历史参考区块

**需求映射：** R1、R2、R4、R6

**前置任务：** T1

**文件：**
- 修改：`web/src/api/release.ts`
- 修改：`web/src/modules/release/ReleaseApplicationDetailPage.vue`
- 修改：`web/src/modules/release/release-prototype.css`

**接口：**
- 消费：`GET /api/release/applications/{code}/related-history`
- 产出：追加申请专属加载、数据、空、失败及重试视图；申请单号详情链接

- [ ] **步骤 1：固定前端 DTO 与独立请求状态**

  声明扁平 DTO 和 API 函数；详情主请求成功后仅对追加申请发起历史请求，路由变化清空旧状态。

- [ ] **步骤 2：实现数据、空、失败和重试视图**

  在“申请信息”和“制品登记”之间展示紧凑历史记录；错误仅重试历史接口，空状态明确说明暂无直接相关历史。

- [ ] **步骤 3：实现响应式局部样式**

  桌面显示可扫描摘要和变化列表，`760px` 以下单列重排，长单号、路径和说明可换行，无页面横向滚动。

- [ ] **步骤 4：运行前端构建**

  运行：`npm --prefix web run build`

  预期：TypeScript 和 Vite 构建通过，0 个错误。

- [ ] **步骤 5：记录执行检查点**

  记录实际文件、构建和局部状态证据；不创建 Git 提交。

**回滚：** 移除 API DTO/函数、详情区块和 `release-related-history` 局部样式。

**停止条件：** 需要改变通用详情 DTO、共享 UI 组件或审批面板行为。

**升级条件：** 产品要求在参考区直接操作历史申请或加载历史附件。

### T3：集成与浏览器验收

**需求映射：** R1、R2、R3、R4、R5、R6

**前置任务：** T1、T2

**文件：**
- 测试：`.ai-control/requirements/req-20260820-044-release-additional-related-history/*.json`

**接口：**
- 消费：T1 端点和 T2 页面
- 产出：自动化、运行和浏览器收敛证据

- [ ] **步骤 1：运行完整验证命令**

  运行 Release 模块测试、`npm --prefix web run build`、治理检查、范围检查和 `git diff --check`。

- [ ] **步骤 2：确认服务使用当前构建**

  检查 `8080` 健康接口与 `5173` 页面；必要时按仓库方式重启后端和前端。

- [ ] **步骤 3：浏览器验收**

  在桌面及 `375x812`、`390x844`、`430x932` 检查追加、普通、空、失败状态、详情链接、审批可用性、控制台错误和页面横向溢出。

- [ ] **步骤 4：收敛审计**

  确认 R1-R6 均有证据、无 P0/P1 反馈、修改范围一致，再更新当前前缀收敛账本。

**回滚：** 删除无效验收证据；产品偏差则按 T1/T2 各自回滚。

**停止条件：** 服务无法启动、无权限建立可验证本地数据，或存在未关闭 P0/P1 偏差。

**升级条件：** 验收需要生产数据、越权修改或平台公共能力变更。

## 任务依赖与覆盖

依赖：`T1 -> T2 -> T3`，共享 API 契约且工作区脏，串行执行。R1-R6 均由 T3 集成覆盖，T1 覆盖后端 R1-R5，T2 覆盖前端 R1/R2/R4/R6。

## 控制模型种子

以下均为 `hypotheses-only`：被控边界为 Release 关系查询、历史聚合和详情区块；状态变量包括申请特征、有效关系、历史实时状态和前端局部请求状态；传感器为 Mockito/JDBC 契约测试、Maven、Vite、浏览器 DOM/网络/控制台；执行器为新增只读查询、聚合方法和局部 Vue/CSS；扰动包括旧关系、脏工作区、登录过期和空测试数据。

## 风险与批准

用户已确认只展示直接相关历史申请，并在当前修订设计后明确“确认，开始开发”和“继续”。无数据库、发布或不可逆高风险动作。
