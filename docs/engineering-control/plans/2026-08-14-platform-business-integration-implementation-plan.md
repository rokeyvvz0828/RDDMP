# 平台业务接入、附件与内部电子签名实施计划

> 执行要求：使用 control-engineering 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

**目标：** 先于版本发布业务模块交付按编码启动、持久生命周期事件、首页任务工作台、内部电子签名、持久附件和可替换项目 Mock。

**架构：** 增量扩展 ccb-workflow 的公开契约和任务投影，新建独立 ccb-attachment 平台模块，前端 Dashboard 复用工作流任务 API。项目 Mock 只提供展示上下文，正式项目权限继续作为外部依赖。

**技术栈：** Java 17、Spring Boot 3.4.4、Spring JDBC、Flowable 7.0.1、MySQL 8.4、Flyway、MinIO、kkFileView、Vue 3、Pinia、TypeScript、Element Plus。

## 状态与来源

- 计划修订：1
- 设计修订：1
- 设计文档：docs/engineering-control/designs/2026-08-14-platform-business-integration-design.md
- 需求文档：docs/requirements/REQ-20260814-022-platform-business-integration/requirement.md
- 任务范围：docs/requirements/REQ-20260814-022-platform-business-integration/codex-task-scope.yaml
- 状态：待确认

## 全局约束

- 平台能力先实施，REQ-20260814-021 版本发布业务代码不进入本计划。
- 从最新 origin/main 创建独立分支 feat/REQ-20260814-022-platform-business-integration 和独立 worktree。
- Java 固定使用 17，不使用系统 Java 26。
- Flyway 只新增 V38 和 V39，不修改 V1 至 V37；V35—V37 已分配给先实施的架构子系统需求。
- 保持现有按 definitionId 启动、流程设计、待办已办、通知和 file-preview 接口兼容。
- 平台模块不得依赖具体业务模块。
- 签署人只来自 AuthUser；客户端不提交 signerId、tenantId 或 dataDigest。
- Mock 项目不参与服务端授权。
- 所有写入限制在本需求 codex-task-scope.yaml。
- 每个任务完成局部验证后建立独立提交检查点，不混入 REQ-20260814-021 文档。

---

## 文件职责地图

| 路径 | 状态 | 单一职责 | 事实来源 |
|---|---|---|---|
| server/src/platform/workflow/src/main/java/com/ccb/workflow/integration/ | candidate-new | 工作流公开业务 Gateway、事件和签名 DTO/SPI | 已批准设计 |
| server/src/platform/workflow/src/main/java/com/ccb/workflow/service/WorkflowBusinessIntegrationService.java | candidate-new | 按编码解析、启动、业务终止和进度查询 | 已批准设计 |
| server/src/platform/workflow/src/main/java/com/ccb/workflow/service/WorkflowLifecycleEventService.java | candidate-new | 同事务记录生命周期事件 | 已批准设计 |
| server/src/platform/workflow/src/main/java/com/ccb/workflow/service/WorkflowLifecycleDispatcher.java | candidate-new | 订阅者发现、投递、重试和 DEAD 管理 | 已批准设计 |
| server/src/platform/workflow/src/main/java/com/ccb/workflow/service/WorkflowSignatureService.java | candidate-new | 校验并保存不可变内部签名 | 已批准设计 |
| server/src/platform/workflow/src/main/java/com/ccb/workflow/service/WorkflowService.java | existing | 兼容流程运行和旧版状态转换 | 代码勘察 |
| server/src/platform/workflow/src/main/java/com/ccb/workflow/service/FlowableWorkflowService.java | existing | Flowable 流程运行和任务状态转换 | 代码勘察 |
| server/src/platform/workflow/src/main/java/com/ccb/workflow/service/WorkflowMonitorService.java | existing | 实例、时间线、待办已办和终止 | 代码勘察 |
| server/src/platform/workflow/src/main/java/com/ccb/workflow/web/WorkflowController.java | existing | 现有工作流 HTTP API | 代码勘察 |
| server/src/platform/workflow/src/main/java/com/ccb/workflow/web/WorkflowBusinessEventController.java | candidate-new | 事件投递查询和授权重试 API | 已批准设计 |
| server/src/platform/attachment/ | candidate-new | 持久附件 Maven 模块 | 已批准设计 |
| server/src/platform/file-preview/src/main/java/com/ccb/filepreview/model/FilePreviewUrlProvider.java | candidate-new | 对外提供受控预览 URL 生成契约 | 已批准设计 |
| server/src/platform/file-preview/src/main/java/com/ccb/filepreview/service/KkFileViewUrlBuilder.java | existing | 可信 MinIO 地址到 kkFileView URL 的适配 | 代码勘察 |
| web/src/api/workflow.ts | existing | 工作流前端类型和请求 | 代码勘察 |
| web/src/api/attachments.ts | candidate-new | 持久附件前端请求 | 已批准设计 |
| web/src/stores/project-context.ts | candidate-new | 当前 Mock 项目和 Provider | 已批准设计 |
| web/src/types/project-context.ts | candidate-new | 项目展示上下文类型 | 已批准设计 |
| web/src/views/DashboardView.vue | existing | 首页工作台 | 代码勘察 |
| web/src/views/WorkflowView.vue | existing | 流程设计、待办已办、监控和审批操作 | 代码勘察 |
| web/src/components/workflow/WorkflowNodeInspector.vue | existing | 审批节点配置 | 代码勘察 |
| V38__workflow_business_integration.sql | candidate-new | 工作流业务上下文、事件和签名结构 | 已批准设计 |
| V39__persistent_attachments.sql | candidate-new | 附件元数据、绑定、日志和清理结构 | 已批准设计 |
| governance/modules.yaml | existing | 模块、依赖和公开包事实源 | 项目契约 |

## 任务依赖与并行策略

~~~text
T1 工作流按编码接入与数据基础 ──> T2 生命周期事件 ──> T3 内部电子签名 ──> T5 工作台与项目 Mock ──> T6 集成验收
T4 持久附件中心 ────────────────────────────────────────────────────────> T6 集成验收
~~~

- 第一并行组：T1 与 T4。写入目录和迁移文件不同，仅 T4 修改 Maven 聚合。
- 第二至第五组：T2、T3、T5、T6 串行。
- T2 与 T3 都修改 WorkflowService 和 FlowableWorkflowService，不允许并行。
- T3 与 T5 都修改 web/src/api/workflow.ts 和 WorkflowView.vue，不允许并行。

## 需求覆盖

| 需求 | 任务 |
|---|---|
| R1, R2 | T1 |
| R3, R4, R5 | T2 |
| R6, R7 | T5 |
| R8, R9 | T3 |
| R10, R11, R12 | T4 |
| R13, R14 | T5 |
| R15 | T1, T3, T4, T5, T6 |
| R16 | T1, T2, T4, T6 |

### T1：按流程编码启动并固化业务上下文

**需求映射：** R1、R2、R15、R16

**前置任务：** 无

**已证实输入：**

- 当前 WorkflowController 只接受 definitionId。
- wf_definition 已保存租户、编码、状态和 current_version。
- WorkflowService 和 FlowableWorkflowService 分别写入 wf_instance。
- 旧实例必须继续可读。

**文件：**

- 新建：server/src/platform/infrastructure/src/main/resources/db/migration/V38__workflow_business_integration.sql
- 新建：server/src/platform/workflow/src/main/java/com/ccb/workflow/integration/WorkflowBusinessGateway.java
- 新建：server/src/platform/workflow/src/main/java/com/ccb/workflow/integration/WorkflowBusinessContext.java
- 新建：server/src/platform/workflow/src/main/java/com/ccb/workflow/integration/WorkflowStartCommand.java
- 新建：server/src/platform/workflow/src/main/java/com/ccb/workflow/integration/WorkflowStartResult.java
- 新建：server/src/platform/workflow/src/main/java/com/ccb/workflow/integration/WorkflowTerminateCommand.java
- 新建：server/src/platform/workflow/src/main/java/com/ccb/workflow/integration/WorkflowProgress.java
- 新建：server/src/platform/workflow/src/main/java/com/ccb/workflow/service/WorkflowBusinessIntegrationService.java
- 修改：server/src/platform/workflow/src/main/java/com/ccb/workflow/service/WorkflowService.java
- 修改：server/src/platform/workflow/src/main/java/com/ccb/workflow/service/FlowableWorkflowService.java
- 修改：server/src/platform/workflow/src/main/java/com/ccb/workflow/service/WorkflowMonitorService.java
- 测试：server/src/platform/workflow/src/test/java/com/ccb/workflow/service/WorkflowBusinessIntegrationServiceTest.java
- 测试：server/src/platform/workflow/src/test/java/com/ccb/workflow/service/WorkflowServiceTest.java
- 证据：.ai-control/requirements/req-20260814-022-platform-business-integration/execution-T1.json
- 证据：.ai-control/requirements/req-20260814-022-platform-business-integration/observation-T1.json

**接口产出：**

~~~java
public interface WorkflowBusinessGateway {
    WorkflowStartResult startByCode(WorkflowStartCommand command, AuthUser actor);
    void terminate(WorkflowTerminateCommand command, AuthUser actor);
    WorkflowProgress progress(long instanceId, AuthUser actor);
}
~~~

WorkflowStartCommand 必须携带 definitionCode、businessType、businessKey、businessTitle、businessRound、projectRef、projectName、actionPath、dataDigest 和 variables。

- [ ] **步骤 1：建立失败测试**

新增测试覆盖：按编码解析当前发布版本、未知编码、未发布编码、非法 actionPath、空业务字段、旧按 ID 启动兼容和固定版本。

运行：

~~~bash
JAVA_HOME=/Users/zhangwei/Library/Java/JavaVirtualMachines/openjdk-17.0.18/Contents/Home mvn -pl :ccb-workflow -am -Dtest=WorkflowBusinessIntegrationServiceTest,WorkflowServiceTest test
~~~

预期：新增测试因 Gateway、DTO、服务或数据库字段不存在而失败；原有测试保持通过到编译失败点。

- [ ] **步骤 2：新增 V38 数据结构**

为 wf_instance 增加可空 business_type、business_title、business_round、project_ref、project_name、action_path 和 data_digest；同时创建 T2/T3 将使用的 wf_business_event、wf_business_event_delivery 和 wf_task_signature 表及唯一索引。

预期：旧实例无需回填即可查询；新业务启动字段有长度和索引约束。

- [ ] **步骤 3：实现公开 Gateway 与按编码解析**

WorkflowBusinessIntegrationService 在租户内查询状态为 PUBLISHED 的定义及当前发布版本，校验业务上下文后调用既有执行路径。不得把 JdbcTemplate、wf 表 Map 或 Flowable 类型暴露到 integration 包。

- [ ] **步骤 4：统一两条启动写入**

为 WorkflowService 和 FlowableWorkflowService 增加内部业务上下文参数并写入同一组 wf_instance 字段。原 start(definitionId, businessKey, variables, user) 保持签名和行为。

- [ ] **步骤 5：实现业务终止与进度**

Gateway.terminate 校验 tenant、instanceId、businessType 和 businessKey 一致；业务模块负责申请人和业务状态授权。progress 返回公开 DTO，不返回私有数据库列。

- [ ] **步骤 6：运行局部回归**

运行：

~~~bash
JAVA_HOME=/Users/zhangwei/Library/Java/JavaVirtualMachines/openjdk-17.0.18/Contents/Home mvn -pl :ccb-workflow -am test
~~~

预期：ccb-workflow 全部测试通过，0 个失败；按 ID 和按编码两种启动均有断言。

- [ ] **步骤 7：提交检查点**

~~~bash
git add server/src/platform/infrastructure/src/main/resources/db/migration/V38__workflow_business_integration.sql server/src/platform/workflow
git commit -m "feat: add workflow business gateway"
~~~

**验收证据：** 测试退出码、解析到的定义版本断言、零实例失败断言、旧 API 回归结果。

**回滚：** 回退 T1 提交；保留已执行的 V38 新字段和空表，不执行 DROP。

**停止条件：** origin/main 新增 V38；wf_definition 编码不能在租户内唯一解析；旧启动 API 必须破坏才能实现。

**升级条件：** 需要修改 security 私有实现；需要业务模块直接访问 workflow 表；需要改变既有流程定义发布语义。

### T2：持久生命周期事件与幂等投递

**需求映射：** R3、R4、R5、R16

**前置任务：** T1

**文件：**

- 新建：server/src/platform/workflow/src/main/java/com/ccb/workflow/integration/WorkflowLifecycleEventType.java
- 新建：server/src/platform/workflow/src/main/java/com/ccb/workflow/integration/WorkflowLifecycleEvent.java
- 新建：server/src/platform/workflow/src/main/java/com/ccb/workflow/integration/WorkflowLifecycleConsumer.java
- 新建：server/src/platform/workflow/src/main/java/com/ccb/workflow/service/WorkflowLifecycleEventService.java
- 新建：server/src/platform/workflow/src/main/java/com/ccb/workflow/service/WorkflowLifecycleDispatcher.java
- 新建：server/src/platform/workflow/src/main/java/com/ccb/workflow/service/WorkflowBusinessEventQueryService.java
- 新建：server/src/platform/workflow/src/main/java/com/ccb/workflow/web/WorkflowBusinessEventController.java
- 修改：server/src/platform/workflow/src/main/java/com/ccb/workflow/service/WorkflowService.java
- 修改：server/src/platform/workflow/src/main/java/com/ccb/workflow/service/FlowableWorkflowService.java
- 修改：server/src/platform/workflow/src/main/java/com/ccb/workflow/service/WorkflowMonitorService.java
- 测试：server/src/platform/workflow/src/test/java/com/ccb/workflow/service/WorkflowLifecycleEventServiceTest.java
- 测试：server/src/platform/workflow/src/test/java/com/ccb/workflow/service/WorkflowLifecycleDispatcherTest.java
- 测试：server/src/platform/workflow/src/test/java/com/ccb/workflow/service/WorkflowLifecycleParityTest.java
- 证据：.ai-control/requirements/req-20260814-022-platform-business-integration/execution-T2.json
- 证据：.ai-control/requirements/req-20260814-022-platform-business-integration/observation-T2.json

**接口产出：**

~~~java
public interface WorkflowLifecycleConsumer {
    String subscriberKey();
    boolean supports(String businessType);
    void consume(WorkflowLifecycleEvent event);
}
~~~

事件投递语义为 at-least-once。消费者必须用 eventId 和 businessRound 阻止重复及状态倒退。

- [ ] **步骤 1：建立事务、重复和双引擎失败测试**

覆盖 STARTED、RETURNED、APPROVED、REJECTED、TERMINATED；断言回滚不留事件、提交保留事件、重复投递只保留一条成功 delivery、旧流程与 Flowable 事件字段一致。

- [ ] **步骤 2：实现统一事件工厂**

所有事件从 wf_instance 固化上下文生成。WorkflowService、FlowableWorkflowService 和 WorkflowMonitorService 只能调用同一 WorkflowLifecycleEventService，不各自拼 payload。

- [ ] **步骤 3：接入状态转换事务**

在实例启动、通过、退回、拒绝和终止状态写入后，同事务插入 wf_business_event。eventId 使用服务端 UUID，事件记录不可更新正文。

- [ ] **步骤 4：实现 dispatcher**

通过 Spring Bean 列表发现 WorkflowLifecycleConsumer，使用 subscriberKey 建 delivery；PENDING/RETRY_WAIT 采用有界批次处理，失败记录 attempts、last_error、next_retry_at，超过配置阈值进入 DEAD。

- [ ] **步骤 5：实现事件监控与重试**

新增分页查询和指定 eventId + subscriberKey 重试 API，权限为 workflow:event:manage；重试复用原 eventId，不复制事件。

- [ ] **步骤 6：运行局部测试**

~~~bash
JAVA_HOME=/Users/zhangwei/Library/Java/JavaVirtualMachines/openjdk-17.0.18/Contents/Home mvn -pl :ccb-workflow -am -Dtest=WorkflowLifecycleEventServiceTest,WorkflowLifecycleDispatcherTest,WorkflowLifecycleParityTest test
~~~

预期：事件事务、双路径一致性、重复、退避、DEAD 和人工重试全部通过。

- [ ] **步骤 7：运行工作流回归并提交**

~~~bash
JAVA_HOME=/Users/zhangwei/Library/Java/JavaVirtualMachines/openjdk-17.0.18/Contents/Home mvn -pl :ccb-workflow -am test
git add server/src/platform/workflow
git commit -m "feat: add durable workflow lifecycle events"
~~~

**回滚：** 停止 dispatcher 并回退 T2 代码；保留事件和 delivery 表证据。

**停止条件：** 状态变化无法与事件写入同一事务；Spring 消费者调用形成 workflow 对业务模块的编译依赖；测试发现旧引擎没有稳定终态。

**升级条件：** 需要外部消息队列；需要跨进程订阅协议；需要删除或重写 Flowable 运行表。

### T3：工作流节点内部电子签名

**需求映射：** R8、R9、R15

**前置任务：** T2

**文件：**

- 新建：server/src/platform/workflow/src/main/java/com/ccb/workflow/integration/WorkflowSignatureItem.java
- 新建：server/src/platform/workflow/src/main/java/com/ccb/workflow/service/WorkflowSignatureService.java
- 修改：server/src/platform/workflow/src/main/java/com/ccb/workflow/service/WorkflowDefinitionValidator.java
- 修改：server/src/platform/workflow/src/main/java/com/ccb/workflow/service/WorkflowModelValidator.java
- 修改：server/src/platform/workflow/src/main/java/com/ccb/workflow/service/WorkflowService.java
- 修改：server/src/platform/workflow/src/main/java/com/ccb/workflow/service/FlowableWorkflowService.java
- 修改：server/src/platform/workflow/src/main/java/com/ccb/workflow/service/WorkflowMonitorService.java
- 修改：server/src/platform/workflow/src/main/java/com/ccb/workflow/web/WorkflowController.java
- 修改：web/src/api/workflow.ts
- 修改：web/src/components/workflow/WorkflowNodeInspector.vue
- 修改：web/src/views/WorkflowView.vue
- 测试：server/src/platform/workflow/src/test/java/com/ccb/workflow/service/WorkflowSignatureServiceTest.java
- 测试：server/src/platform/workflow/src/test/java/com/ccb/workflow/service/WorkflowSignatureDecisionTest.java
- 测试：server/src/platform/workflow/src/test/java/com/ccb/workflow/service/WorkflowDefinitionValidatorTest.java
- 证据：.ai-control/requirements/req-20260814-022-platform-business-integration/execution-T3.json
- 证据：.ai-control/requirements/req-20260814-022-platform-business-integration/observation-T3.json

**接口变化：**

WorkflowNodeConfig 增加 signatureRequired?: boolean。decideWorkflowTask 的 options 增加 signatureConfirmed?: boolean。服务端不接受 signerId 和 dataDigest。

- [ ] **步骤 1：建立签名失败测试**

覆盖默认 false、required true 未确认、当前用户确认、非办理人、轮次不一致、实例摘要缺失、签名写入失败事务回滚和旧流程兼容。

- [ ] **步骤 2：扩展节点模型校验**

设计器只在 APPROVAL 节点展示“需要内部电子签名”开关。后端拒绝非布尔值，缺省按 false。

- [ ] **步骤 3：实现 WorkflowSignatureService**

从 AuthUser、当前任务和 wf_instance 读取签署人、业务轮次及 data_digest；生成 SESSION_CONFIRM 证据。对 tenantId + taskId 建唯一约束，禁止更新和删除。

- [ ] **步骤 4：原子接入两条审批路径**

要求签名时先校验确认，再在同一事务中完成任务动作、签名和生命周期事件。任一写入失败整体回滚，任务保持 PENDING。

- [ ] **步骤 5：扩展详情和时间线**

instance detail 和 timeline 返回 WorkflowSignatureItem；旧实例没有签名时返回空数组。

- [ ] **步骤 6：实现确认交互**

WorkflowView 审批提交前展示当前账号、业务标题、动作和内部签署说明。用户点击确认后才提交 signatureConfirmed=true；关闭对话框不发请求。

- [ ] **步骤 7：运行验证并提交**

~~~bash
JAVA_HOME=/Users/zhangwei/Library/Java/JavaVirtualMachines/openjdk-17.0.18/Contents/Home mvn -pl :ccb-workflow -am test
npm --prefix web run build
git add server/src/platform/workflow web/src/api/workflow.ts web/src/components/workflow web/src/views/WorkflowView.vue
git commit -m "feat: add internal workflow signatures"
~~~

预期：工作流测试和前端构建通过；旧流程无需签名；要求签名的节点生成不可变证据。

**回滚：** 回退 T3 代码并保持 signatureRequired 缺省 false；已写签名记录保留只读。

**停止条件：** 必须修改 AuthUser 或认证机制；审批和签名不能处于同一事务；客户端必须提交待签摘要。

**升级条件：** 用户改为要求密码复核、CA、短信或法律数字签章；需要管理员代签或补签。

### T4：持久附件中心

**需求映射：** R10、R11、R12、R15、R16

**前置任务：** 无

**文件：**

- 新建：server/src/platform/infrastructure/src/main/resources/db/migration/V39__persistent_attachments.sql
- 新建：server/src/platform/attachment/pom.xml
- 新建：server/src/platform/attachment/src/main/java/com/ccb/attachment/config/AttachmentProperties.java
- 新建：server/src/platform/attachment/src/main/java/com/ccb/attachment/model/AttachmentGateway.java
- 新建：server/src/platform/attachment/src/main/java/com/ccb/attachment/model/AttachmentAccessPolicy.java
- 新建：server/src/platform/attachment/src/main/java/com/ccb/attachment/model/AttachmentOperation.java
- 新建：server/src/platform/attachment/src/main/java/com/ccb/attachment/model/AttachmentItem.java
- 新建：server/src/platform/attachment/src/main/java/com/ccb/attachment/model/AttachmentBindingCommand.java
- 新建：server/src/platform/attachment/src/main/java/com/ccb/attachment/repository/AttachmentRepository.java
- 新建：server/src/platform/attachment/src/main/java/com/ccb/attachment/service/AttachmentService.java
- 新建：server/src/platform/attachment/src/main/java/com/ccb/attachment/service/AttachmentAccessPolicyRegistry.java
- 新建：server/src/platform/attachment/src/main/java/com/ccb/attachment/service/AttachmentCleanupService.java
- 新建：server/src/platform/attachment/src/main/java/com/ccb/attachment/web/AttachmentController.java
- 新建：server/src/platform/attachment/src/test/java/com/ccb/attachment/service/AttachmentServiceTest.java
- 新建：server/src/platform/attachment/src/test/java/com/ccb/attachment/service/AttachmentAccessPolicyTest.java
- 新建：server/src/platform/attachment/src/test/java/com/ccb/attachment/service/AttachmentCleanupServiceTest.java
- 新建：server/src/platform/file-preview/src/main/java/com/ccb/filepreview/model/FilePreviewUrlProvider.java
- 修改：server/src/platform/file-preview/src/main/java/com/ccb/filepreview/service/KkFileViewUrlBuilder.java
- 修改：server/src/platform/file-preview/src/test/java/com/ccb/filepreview/service/KkFileViewUrlBuilderTest.java
- 新建：web/src/api/attachments.ts
- 修改：pom.xml
- 修改：server/src/platform/boot/pom.xml
- 证据：.ai-control/requirements/req-20260814-022-platform-business-integration/execution-T4.json
- 证据：.ai-control/requirements/req-20260814-022-platform-business-integration/observation-T4.json

**公开接口：**

~~~java
public interface AttachmentGateway {
    void bind(AttachmentBindingCommand command, AuthUser actor);
    AttachmentItem get(long attachmentId, AuthUser actor);
    void deleteBound(long attachmentId, String businessType, String businessKey, AuthUser actor);
}

public interface AttachmentAccessPolicy {
    String businessType();
    boolean canAccess(AuthUser actor, String businessKey, AttachmentOperation operation);
}
~~~

- [ ] **步骤 1：建立附件生命周期失败测试**

覆盖 TEMP 上传人访问、跨租户拒绝、绑定幂等、重复绑定不同对象冲突、无策略拒绝、策略异常拒绝、预览、下载、逻辑删除、对象删除失败和孤立清理。

- [ ] **步骤 2：新增 V39 和 Maven 模块**

创建 att_file、att_binding、att_operation_log、att_cleanup_job；对象键、绑定和清理任务建立唯一索引。根 POM、dependencyManagement 和 Boot 增加 ccb-attachment。

- [ ] **步骤 3：公开预览 URL Provider**

在 com.ccb.filepreview.model 暴露 FilePreviewUrlProvider；KkFileViewUrlBuilder 实现该接口并保持现有 build 行为和安全校验。

- [ ] **步骤 4：实现上传和绑定**

上传使用服务端文件名、类型、大小校验和不可预测对象键，创建 TEMP。业务 Gateway 绑定为 BOUND；相同绑定幂等成功，不同绑定冲突。

- [ ] **步骤 5：实现 fail-closed 访问**

TEMP 只允许同租户上传人。BOUND 根据 businessType 查找策略；无策略、重复策略或策略异常全部拒绝。浏览器接口不提供绑定和已绑定强制删除。

- [ ] **步骤 6：实现预览、下载和清理**

预览使用短时 MinIO URL 和 FilePreviewUrlProvider；下载返回短时地址。清理任务只处理过期 TEMP 或已逻辑删除对象，失败写入重试任务。

- [ ] **步骤 7：运行局部与兼容测试**

~~~bash
JAVA_HOME=/Users/zhangwei/Library/Java/JavaVirtualMachines/openjdk-17.0.18/Contents/Home mvn -pl :ccb-attachment,:ccb-file-preview -am test
npm --prefix web run build
~~~

预期：附件和原 file-preview 测试通过，前端类型构建通过。

- [ ] **步骤 8：提交检查点**

~~~bash
git add pom.xml server/src/platform/boot/pom.xml server/src/platform/attachment server/src/platform/file-preview server/src/platform/infrastructure/src/main/resources/db/migration/V39__persistent_attachments.sql web/src/api/attachments.ts
git commit -m "feat: add persistent attachment center"
~~~

**回滚：** 关闭新附件入口并回退模块装配；保留 V39 表和已绑定元数据，不删除对象证据。

**停止条件：** origin/main 已占用 V39；对象存储无法生成短时 URL；访问策略需要 attachment 模块依赖具体业务模块。

**升级条件：** 需要病毒扫描、跨系统文件传输、内容审查或制品仓库；需要修改 MinIO 凭据和生产配置。

### T5：首页任务工作台与项目 Mock

**需求映射：** R6、R7、R13、R14、R15

**前置任务：** T3

**文件：**

- 新建：web/src/types/project-context.ts
- 新建：web/src/stores/project-context.ts
- 修改：web/src/views/AppLayout.vue
- 修改：web/src/views/DashboardView.vue
- 修改：web/src/api/workflow.ts
- 修改：web/src/styles.css
- 修改：server/src/platform/workflow/src/main/java/com/ccb/workflow/service/WorkflowService.java
- 修改：server/src/platform/workflow/src/main/java/com/ccb/workflow/service/WorkflowMonitorService.java
- 测试：server/src/platform/workflow/src/test/java/com/ccb/workflow/service/WorkflowTaskProjectionTest.java
- 证据：.ai-control/requirements/req-20260814-022-platform-business-integration/execution-T5.json
- 证据：.ai-control/requirements/req-20260814-022-platform-business-integration/observation-T5.json

**接口产出：**

~~~ts
export interface ProjectContextProvider {
  list(): Promise<ProjectContextItem[]>
  current(): ProjectContextItem
  select(projectRef: string): void
}
~~~

WorkflowTask 和 WorkflowDoneItem 增加 business_type、business_title、business_round、project_ref、project_name、action_path、starter_name 和签名摘要字段。

- [ ] **步骤 1：建立任务投影测试**

断言 inbox、done、detail 和 timeline 返回相同业务上下文；旧实例回退 businessKey；actionPath 非站内路由不能写入。

- [ ] **步骤 2：扩展后端待办已办投影**

查询 wf_instance 新字段和发起人，保持分页与当前任务用户范围。不得根据 Mock projectRef 扩大查询权限。

- [ ] **步骤 3：实现项目 Provider 和 Store**

项目列表集中在 project-context Store 的 Mock Provider 中，当前项目写入 localStorage。AppLayout 和 Dashboard 只消费 Store。

- [ ] **步骤 4：在 AppLayout 增加顶级项目切换**

使用下拉选择器展示项目名称和编码；切换后更新工作台显示筛选。控件尺寸固定，长名称截断并提供完整提示。

- [ ] **步骤 5：重构 Dashboard**

保留平台状态信息的必要部分，新增“我的待办”和“最近已办”两个区域，每类最多五条，支持加载、空、错误和查看全部。业务详情通过 router.push(actionPath + task/instance 查询参数) 导航。

- [ ] **步骤 6：验证前端和桌面交互**

~~~bash
npm --prefix web run build
~~~

预期：TypeScript 和 Vite 构建通过。启动服务后在 1440x900 与 1280x720 验证无重叠、任务跳转正确、项目切换刷新后保留。

- [ ] **步骤 7：运行后端回归并提交**

~~~bash
JAVA_HOME=/Users/zhangwei/Library/Java/JavaVirtualMachines/openjdk-17.0.18/Contents/Home mvn -pl :ccb-workflow -am test
git add server/src/platform/workflow web/src/api/workflow.ts web/src/stores/project-context.ts web/src/types/project-context.ts web/src/views/AppLayout.vue web/src/views/DashboardView.vue web/src/styles.css
git commit -m "feat: add dashboard workflow workbench"
~~~

**回滚：** 回退 Dashboard 和项目 Store；后端新增上下文字段继续保留，原工作流页面不受影响。

**停止条件：** Dashboard 必须依赖正式项目权限才能展示；现有路由无法安全消费 actionPath；页面调整需要删除现有通知或主题能力。

**升级条件：** 用户要求服务端正式项目隔离；需要修改 auth Store 或认证 Token 结构；需要新增移动端业务验收。

### T6：治理登记、契约文档与全量收敛

**需求映射：** R15、R16

**前置任务：** T2、T3、T4、T5

**文件：**

- 修改：governance/modules.yaml
- 修改：docs/architecture/MODULES.md
- 修改：.github/CODEOWNERS
- 修改：docs/integration/workflow-module-contract.md
- 新建：docs/integration/attachment-module-contract.md
- 修改：docs/requirements/REQ-20260814-022-platform-business-integration/requirement.md，仅在实现事实需要准确化且不改变已批准需求时
- 新建：.ai-control/requirements/req-20260814-022-platform-business-integration/execution-T6.json
- 新建：.ai-control/requirements/req-20260814-022-platform-business-integration/observation-T6.json
- 新建：.ai-control/requirements/req-20260814-022-platform-business-integration/convergence.json

- [ ] **步骤 1：更新治理事实源**

登记 platform/attachment 的路径、依赖、Maven artifact、公开 model 包和 Owner；扩展 platform/workflow 公开 integration 包；frontend/application 增加 attachment API 和项目 Store 路径。

- [ ] **步骤 2：更新公开接入契约**

工作流契约记录按编码启动、业务上下文、事件投递、签名和兼容策略。附件契约记录 TEMP/BOUND 状态、Gateway、AccessPolicy、浏览器 API、对象键保密和清理规则。

- [ ] **步骤 3：运行 Flyway 与模块验证**

~~~bash
JAVA_HOME=/Users/zhangwei/Library/Java/JavaVirtualMachines/openjdk-17.0.18/Contents/Home mvn -pl :ccb-workflow,:ccb-attachment,:ccb-file-preview -am test
JAVA_HOME=/Users/zhangwei/Library/Java/JavaVirtualMachines/openjdk-17.0.18/Contents/Home mvn test
npm --prefix web run build
node scripts/check-all-governance.mjs
node scripts/check-flyway-migrations.mjs
git diff --check
~~~

预期：所有命令退出码 0；V1 至 V37 校验未修改；V38、V39 顺序唯一；治理清单识别新模块和公开包。

- [ ] **步骤 4：运行桌面浏览器验收**

验证首页项目切换、待办已办、业务跳转、签名要求与不要求两条路径、附件 TEMP 上传和授权访问。检查浏览器控制台无错误，页面在浅色和深色主题下无重叠。

- [ ] **步骤 5：独立观测与收敛**

按 control-engineering 记录执行、观测和收敛证据。重点复验重复事件、签名原子性、跨租户附件拒绝、Mock 项目篡改和旧流程兼容。

- [ ] **步骤 6：提交最终检查点**

~~~bash
git add governance/modules.yaml docs/architecture/MODULES.md .github/CODEOWNERS docs/integration .ai-control/requirements/req-20260814-022-platform-business-integration
git commit -m "docs: register platform business integration contracts"
~~~

**回滚：** 回退治理和文档提交不会删除运行证据；产品回退按 T1 至 T5 各自边界执行。

**停止条件：** 任一 must 需求没有自动化或浏览器证据；全量测试失败；scope 检查发现越界文件；迁移编号冲突。

**升级条件：** 需要修改本任务 read_only_paths；发现签名或附件越权；最新 main 引入不兼容的工作流、数据库或 Dashboard 变更。

## 集成检查

| 检查点 | 命令或传感器 | 通过信号 |
|---|---|---|
| T1 后 | ccb-workflow tests | 按编码与按 ID 启动同时通过 |
| T2 后 | lifecycle focused tests | 两引擎、重复、重试和 DEAD 通过 |
| T3 后 | workflow tests + frontend build | 签名原子性与兼容通过 |
| T4 后 | attachment + file-preview tests | 生命周期、策略和预览通过 |
| T5 后 | frontend build + desktop browser | 工作台和 Mock 项目可用 |
| T6 后 | full Maven + frontend + governance + Flyway | 全部退出码 0 |

## 控制模型种子

以下内容均为 hypotheses-only，必须在 control-engineering 的 modeling 阶段验证：

- 被控边界候选：ccb-workflow、ccb-attachment、file-preview 公开适配、Dashboard 和 Mock ProjectContextProvider。
- 状态变量候选：工作流实例状态、事件投递状态、任务签名状态、附件状态、清理任务状态、当前 Mock 项目。
- 接口候选：WorkflowBusinessGateway、WorkflowLifecycleConsumer、AttachmentGateway、AttachmentAccessPolicy、ProjectContextProvider。
- 传感器候选：Maven 测试、前端构建、Flyway 门禁、治理门禁、数据库唯一约束、浏览器路径、事件积压与清理失败日志。
- 执行器候选：工作流状态事务、事件 dispatcher、签名写入、附件 bind/delete、cleanup job、前端 Store select。
- 扰动候选：重复和乱序事件、消费者异常、MinIO/kkFileView 不可用、并发审批、浏览器存储篡改、最新 main 迁移冲突。
- 时延候选：事件重试退避、附件清理周期、工作台刷新、预签名 URL 有效期。
- 假设：Spring Bean 可发现消费者和访问策略；若部署改为跨进程，该假设失效并必须重新设计传输协议。

## 风险与用户批准

高风险动作：

- 修改工作流核心状态转换并增加同事务事件与签名。
- 新增两个 Flyway 迁移和一个 Maven 平台模块。
- 新增持久对象存储所有权与访问策略。
- 修改首页和流程审批交互。

进入开发前必须由用户批准当前计划修订。批准后将计划和 handoff 状态改为 approved/ready，导入 control-engineering，并在独立 worktree 中执行。
