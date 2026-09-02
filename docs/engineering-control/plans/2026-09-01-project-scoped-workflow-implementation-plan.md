# 工作流按项目维度管理实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

**目标：** 建立从流程定义、项目人员解析、实例任务到审批操作的一致项目权限边界。

**架构：** workflow 负责流程状态和授权编排，system 负责项目目录，boot 通过公开接口组合两者。V134 追加范围和真实项目主键，前端使用顶层项目上下文驱动工作流页面。

**技术栈：** Java 17、Spring Boot 3.4.4、JdbcTemplate、Flowable、MySQL 8.4、Flyway、Vue 3、TypeScript、Element Plus、Pinia。

## 全局约束

- 不修改历史 Flyway 和 Flowable `ACT_*` 表。
- 不形成 workflow 与 system Maven 循环依赖。
- 旧业务启动 API、全局 USER/ROLE 节点和存量全局定义保持兼容。
- 所有项目授权必须由服务端执行，前端只负责体验。
- 只修改 REQ-20260901-060 范围文件。

### T1：数据库与项目契约

**需求映射：** R1, R2, R6

**前置任务：** 无

**文件：** V134、workflow integration 新契约、system capability 新契约和实现、boot 桥接、对应测试。

**接口：** 产出 `WorkflowProjectAccessGateway`、`WorkflowPendingTaskQuery`、`ProjectWorkflowDirectoryService`、`ProjectMemberRemovalGuard`。

- [ ] 建立迁移和桥接失败测试，确认当前缺少范围字段、项目目录接口和待办守卫。
- [ ] 追加 V134，新增定义范围、实例项目主键和业务绑定表，执行兼容回填。
- [ ] 实现 system 项目目录和 boot 双向桥接，保证无 Maven 循环。
- [ ] 运行 `mvn -pl :ccb-system,:ccb-boot -am test`，预期零失败。

**回滚：** 回退 Java 契约和桥接；V134 结构保留。

**停止条件：** 项目编号无法唯一映射，或桥接需要新增循环依赖。

**升级条件：** 需要改变项目成员/角色表所有权或修改历史迁移。

### T2：工作流项目范围与运行授权

**需求映射：** R1, R2, R3, R4, R5, R6

**前置任务：** T1

**文件：** WorkflowService、WorkflowBusinessIntegrationService、WorkflowMonitorService、WorkflowAssigneeResolver、FlowableWorkflowService、校验器、编译器、Controller、workflow 测试与契约文档。

**接口：** 消费 T1 项目访问契约；产出带 scope/project 的定义 API、项目人员选项 API和实例/任务授权。

- [ ] 建立项目定义读写、跨项目拒绝、PROJECT_MEMBER/PROJECT_ROLE 解析和审批二次校验测试。
- [ ] 调整定义 CRUD、发布生命周期和监控查询，叠加项目访问与管理权限。
- [ ] 调整业务启动顺序，实例插入时固化 project_id 和快照后再创建任务。
- [ ] 统一传统与 Flowable 两套人员解析，校验项目成员、角色和抄送人员。
- [ ] 运行 `mvn -pl :ccb-workflow -am test`，预期零失败。

**回滚：** 回退 workflow 代码，保留 V134 兼容列。

**停止条件：** 两套执行器无法从同一实例项目上下文解析，或现有业务启动契约必须破坏性修改。

**升级条件：** 发现运行中实例需要重写历史定义 JSON。

### T3：项目感知工作流页面与收敛验证

**需求映射：** R1, R2, R7, R8

**前置任务：** T2

**文件：** workflow.ts、WorkflowView.vue、WorkflowNode.vue、WorkflowNodeInspector.vue。

**接口：** 消费 T2 定义范围、项目成员和项目角色 API；产出项目感知的定义列表和编辑体验。

- [ ] 增加前端类型和请求参数，当前项目变化时重新加载列表与候选项。
- [ ] 新建流程提供范围选择；项目流程只展示项目成员/角色来源，列表和监控显示项目标签。
- [ ] 构建并修复 TypeScript、布局和加载/空/失败状态问题。
- [ ] 运行 `npm --prefix web run build`、`mvn test`、治理、范围和差异检查，预期全部通过。
- [ ] 在桌面和 375x812、390x844、430x932 验证核心路径，无页面级横向溢出和控制台错误。

**回滚：** 回退前端文件，后端项目权限继续有效。

**停止条件：** 顶层项目上下文为空且无法提供明确全局模式，或页面必须绕过后端权限才能工作。

**升级条件：** 需要修改公共 UI 组件或应用壳层交互。

## 依赖与并行

`T1 -> T2 -> T3` 串行。数据库和公共契约决定后续接口，前后端不能在字段未固定前并行写入。

## 需求覆盖

- R1-R2：T1、T2、T3
- R3-R6：T1、T2
- R7-R8：T3

## 控制模型种子

- 被控状态候选：定义范围、实例项目主键、任务办理人、成员有效状态。
- 传感器候选：数据库约束、服务单元测试、模块测试、全量测试、浏览器项目切换与越权请求。
- 执行器候选：Flyway 迁移、项目契约桥接、服务端实体授权、前端项目上下文参数。
- 扰动候选：存量空项目实例、成员状态并发变化、项目切换、两套执行器差异。
- 状态：以上仅为假设，进入高保证建模阶段复核。
