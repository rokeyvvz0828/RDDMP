# 平台业务接入、附件与内部电子签名工程设计

## 文档状态

- 需求编号：REQ-20260814-022
- 主题：platform-business-integration
- 修订：1
- 状态：已确认
- 用户确认依据：用户确认项目上下文和项目权限先使用 Mock；同意按流程编码启动、幂等生命周期事件、首页工作台业务信息、持久附件和平台内部电子签名；电子签名采用当前登录身份点击确认，并明确要求先实施平台设计。

## 1. 目标与成功信号

在不把平台实现复制到业务模块的前提下，为 RDDMP 提供稳定的工作流业务接入、统一工作台、持久附件和内部电子签名能力，并为后续正式项目上下文保留替换边界。

成功信号：

1. 业务模块按稳定编码启动当前已发布流程，不依赖数据库定义 ID。
2. 工作流完成、退回和终止后，即使进程重启或消费者短暂失败，业务模块最终仍能收到且只生效一次。
3. 首页工作台能够用真实流程任务展示业务标题、项目、节点和详情入口。
4. 需要签名的审批节点生成与当前登录用户、任务和业务摘要绑定的不可变记录。
5. 附件从临时上传到业务绑定、访问、删除和清理形成完整生命周期。
6. 项目 Mock 可替换，但不能成为正式环境的服务端授权依据。
7. 现有按定义 ID 启动、流程设计、待办已办、通知和临时预览行为保持兼容。

## 2. 现状核验

2026-08-14 已拉取 origin/main。当前 rokey 包含 origin/main 的全部提交，未发现需要合并的新提交。

最新 main 的现状：

- WorkflowController 启动实例仍要求 definitionId，没有按编码启动入口。
- 工作流状态变更没有面向业务模块的持久生命周期事件和重试投递。
- 待办已办主要返回 businessKey，缺少业务标题、业务类型、项目和详情路由。
- 首页仍是平台状态和快捷入口，没有我的待办、最近已办。
- 工作流模型和任务没有内部电子签名配置及签名记录。
- file-preview 只管理 file-preview 前缀下的预览对象，没有业务归属、附件权限和孤立文件治理。
- SystemNotificationPublisher 已提供幂等站内消息发布能力，本需求直接复用。
- 尚无正式当前项目上下文和项目级服务端权限能力。

## 3. 使用者与场景

### 3.1 业务模块研发

- 通过公开 Java 契约按流程编码启动和终止流程。
- 保存业务标题、详情路由、轮次和数据摘要。
- 幂等消费工作流生命周期事件。
- 上传临时附件，并在业务保存成功后绑定。

### 3.2 审批人

- 在首页查看自己的待办和最近已办。
- 点击任务进入对应业务详情。
- 在要求电子签名的节点使用当前登录身份确认签署并提交审批。

### 3.3 普通业务用户

- 上传、预览、下载和删除自己有权限访问的业务附件。
- 在页面顶部使用 Mock 项目切换当前展示上下文。

### 3.4 平台管理员

- 配置流程、节点电子签名要求和审批人。
- 查询生命周期事件投递、签名证据和附件清理异常。
- 不代替业务审批人签署或修改签名记录。

## 4. 必须需求与验收条件

| ID | 必须需求 | 可验收结果 |
|---|---|---|
| R1 | 按租户内稳定流程编码启动当前已发布版本 | 未发布或无效编码零实例写入，成功实例固定版本 |
| R2 | 流程实例保存完整业务上下文 | 待办、已办、详情和事件返回一致业务信息 |
| R3 | 生命周期事件与工作流状态同事务持久化 | 工作流已完成时不会因为进程退出丢失业务事件 |
| R4 | 事件按订阅者至少一次投递并可重试 | 消费者短暂失败后恢复，重复投递不重复生效 |
| R5 | 旧流程和 Flowable 流程产生统一事件 | 两条运行路径通过同一消费者契约 |
| R6 | 首页展示我的待办和最近已办 | 每类最多五条，可进入完整列表和业务详情 |
| R7 | 工作台展示业务标题、类型、项目、节点和时间 | 不再只显示难以识别的 businessKey |
| R8 | 节点可配置内部电子签名 | 未确认签署不能执行要求签名的审批 |
| R9 | 签名使用当前登录身份并绑定业务摘要 | 客户端不能伪造签署人，签名写入后不可变 |
| R10 | 新附件模块拥有持久附件元数据和绑定关系 | 业务模块不保存对象键、不直接写附件表 |
| R11 | TEMP 和 BOUND 附件执行不同访问策略 | 临时附件仅上传人访问，绑定附件调用业务策略 |
| R12 | 附件支持预览、下载、受控删除和孤立清理 | 未授权访问被拒绝，清理和删除失败可重试 |
| R13 | Mock 项目通过可替换 Provider 提供 | 页面不直接硬编码项目，后续接入不改消费接口 |
| R14 | Mock 项目不承担服务端正式授权 | 修改浏览器项目值不能获得额外数据权限 |
| R15 | 现有工作流、通知和预览接口保持兼容 | 原有用户路径和自动化测试继续通过 |
| R16 | 平台私有表和实现不暴露给业务模块 | 业务模块只依赖公开 gateway、consumer 和 policy |

## 5. 不变量与约束

### 5.1 不变量

- 工作流实例固定已发布定义版本，后续发布不改变运行实例。
- 工作流终态和对应业务事件不能相互矛盾。
- eventId 全局稳定，单订阅者对单事件最多产生一个成功投递结果。
- 签署人永远来自服务端认证主体。
- 签名证据和已绑定附件记录不可物理删除。
- 附件对象键不返回给业务前端。
- 平台模块不得依赖 ccb-release 或其他具体业务模块。
- Mock 项目不参与正式权限判断。

### 5.2 工程约束

- Java 17、Spring Boot 3.4.4、MySQL 8.4、Vue 3 和 TypeScript。
- 保持现有 com.ccb 包名和 ApiResponse。
- Flyway 只新增 V38 和 V39，不修改 V1 至 V37；V35—V37 已分配给先实施的架构子系统需求。
- 平台公开能力需要治理登记、Owner 审批和完整回归。
- 站内通知继续使用 SystemNotificationPublisher。
- 当前验收以桌面工作台和审批路径为主，不新增版本业务移动端范围。

## 6. 非目标

- 正式项目管理和项目成员权限。
- 外部 CA 或法律数字签章。
- 密码二次校验、短信、人脸或指纹签署。
- 通用跨系统消息总线。
- 版本申请、纳基和报表业务。
- 制品仓库、制品文件上传和制品校验。
- 重写 Flowable 或替换现有工作流设计器。

## 7. 方案比较与选择

### 7.1 已选：增量扩展平台公开契约，新建独立附件模块

- 在 ccb-workflow 内新增公开业务 Gateway、生命周期事件、业务上下文和签名能力。
- 新建 ccb-attachment，独立拥有附件状态、绑定、权限和清理。
- Dashboard 直接消费工作流待办已办 API。
- 项目上下文使用可替换前端 Provider 和本地 Mock。

该方案兼容现有能力，业务模块只依赖稳定契约，附件职责也不会继续挤入临时预览模块。

### 7.2 未选：全部在 ccb-release 内实现适配

开发速度较快，但需求、测试和投产模块会重复建设流程回调、签名和附件，且业务模块容易读取平台私有表。

### 7.3 未选：一次建设通用事件总线和低代码集成中心

扩展性较高，但当前只有单体模块内协作需求，引入通用订阅管理、跨系统协议和运维面会显著扩大范围。

## 8. 架构边界

~~~text
business modules
  |-- WorkflowBusinessGateway
  |-- WorkflowLifecycleConsumer
  |-- AttachmentGateway
  |-- AttachmentAccessPolicy
  v
platform
  |-- ccb-workflow
  |     |-- code resolver
  |     |-- business context
  |     |-- lifecycle outbox and dispatcher
  |     |-- internal signature
  |     '-- inbox/done projection
  |-- ccb-attachment
  |     |-- metadata and binding
  |     |-- access policy dispatcher
  |     |-- storage and preview adapters
  |     '-- orphan cleanup
  '-- frontend application
        |-- dashboard workbench
        '-- mock project provider
~~~

### 8.1 组件职责

| 组件 | 职责 | 不负责 |
|---|---|---|
| WorkflowBusinessGateway | 按编码启动、业务终止、进度查询 | 业务状态机和业务权限判断 |
| WorkflowLifecycleOutbox | 同事务记录事件、调度和重试 | 解释业务事件含义 |
| WorkflowLifecycleConsumer | 业务模块实现的幂等消费 SPI | 查询工作流私有表 |
| WorkflowSignatureService | 校验节点要求并保存签名证据 | 外部 CA 和密码验证 |
| WorkflowTaskProjection | 为待办已办提供业务上下文 | 业务详情数据 |
| AttachmentGateway | 临时上传后的绑定、查询和受控操作 | 业务对象状态判断 |
| AttachmentAccessPolicy | 业务模块实现对象访问判断 | 对象存储操作 |
| AttachmentCleanupJob | 清理过期未绑定对象和重试失败删除 | 删除已绑定业务证据 |
| ProjectContextProvider | 向页面暴露当前 Mock 项目 | 服务端正式授权 |

## 9. 工作流公开契约

### 9.1 WorkflowBusinessGateway

~~~text
resolvePublishedByCode(tenantId, definitionCode)
startByCode(StartWorkflowCommand)
terminate(TerminateWorkflowCommand)
progress(tenantId, instanceId)
~~~

StartWorkflowCommand 包含：

- definitionCode
- businessType、businessKey、businessTitle
- businessRound
- projectRef、projectName
- actionPath
- dataDigest
- variables
- authenticated actor context

平台在事务内再次校验编码、发布状态、当前版本和模型有效性。启动返回 instanceId、definitionId、definitionVersion、status 和 startedAt。

### 9.2 兼容策略

- 现有 POST /api/workflows/instances 和按 definitionId 的服务方法保留。
- 新业务模块使用 Java Gateway，不通过浏览器直接调用按编码启动。
- 旧实例业务字段允许为空，待办已办使用 businessKey 回退展示。
- 新实例必须填写全部必填业务上下文。

### 9.3 业务终止

业务页面不调用管理员 terminate API。业务模块先验证申请人、业务状态和原因，再调用 Gateway。平台校验租户、实例、businessType 和 businessKey 一致后终止，并生成 TERMINATED 事件。

## 10. 生命周期事件

### 10.1 事件内容

~~~text
eventId
eventType
tenantId
instanceId
definitionCode
definitionVersion
businessType
businessKey
businessRound
result
operatorId
occurredAt
payload
~~~

### 10.2 事件类型

| 类型 | 产生时机 |
|---|---|
| STARTED | 流程实例创建成功 |
| RETURNED | 流程动作要求退回修改 |
| APPROVED | 流程全部通过 |
| REJECTED | 流程以不通过终止 |
| TERMINATED | 业务撤回或管理员终止 |

业务模块决定 RETURNED 或 REJECTED 如何映射自身状态，平台不直接写业务表。

### 10.3 投递状态

~~~text
PENDING -> DELIVERING -> SUCCEEDED
                   '--> RETRY_WAIT -> DELIVERING
                   '--> DEAD
~~~

- 工作流状态变更和 wf_business_event 同事务写入。
- dispatcher 在提交后查找支持该 businessType 的消费者。
- 每个消费者使用稳定 subscriberKey。
- wf_business_event_delivery 唯一键为 eventId + subscriberKey。
- 失败按上限退避重试；达到阈值进入 DEAD 并允许管理员重新执行。
- 消费者必须在自己的业务表上对 eventId 建唯一约束。
- 事件投递为至少一次，不承诺恰好一次。

## 11. 工作台设计

首页新增两个非嵌套区域：

~~~text
我的待办                         最近已办
业务标题 | 项目 | 当前节点       业务标题 | 处理结果
发起人   | 到达时间 | 操作       处理时间 | 查看
~~~

- 每组默认五条，并提供“查看全部”进入原工作流页面。
- actionPath 必须为站内路由，点击时附带 taskId 和 instanceId 查询参数。
- 待办操作以“查看并处理”为主，不在首页塞入完整审批表单。
- Mock 项目筛选只是显示筛选，服务端仍按当前用户任务关系返回数据。
- 加载、空、失败、无权限和路由失效均有稳定状态。

工作流实例新增业务上下文字段，inbox、done、detail 和 timeline 返回同一套投影。

## 12. 内部电子签名

### 12.1 节点配置

审批节点增加 signatureRequired 布尔配置，默认 false。旧流程和未配置节点不要求签名。

### 12.2 用户流程

1. 审批人选择通过、退回或不通过并填写所需意见。
2. 若节点要求签名，页面弹出确认框。
3. 确认框展示当前账号、审批动作、业务标题和“使用当前登录身份签署”的说明。
4. 用户点击确认，客户端提交 signatureConfirmed=true。
5. 服务端从 AuthUser 取得签署人，校验任务归属、令牌有效性、任务状态和流程轮次，并直接读取实例中已固化的 dataDigest；客户端不提交待签摘要。
6. 审批动作和签名证据在同一事务内写入。

### 12.3 签名证据

wf_task_signature 保存：

- tenantId、instanceId、taskId
- businessType、businessKey、businessRound
- signerId、signerName
- authMethod 固定为 SESSION_CONFIRM
- actionCode、commentSnapshot
- dataDigest、signedAt
- traceId

签名记录不可修改或删除。审批动作失败时不保留孤立签名；签名写入失败时审批动作整体回滚。

该能力是平台内部审批确认，不宣称 CA 或法律数字签章效力。

## 13. 持久附件中心

### 13.1 模块结构

~~~text
server/src/platform/attachment/
├── model          公开命令、结果、Gateway 和 AccessPolicy
├── service        上传、绑定、授权和生命周期
├── repository     附件、绑定和操作日志
├── storage        MinIO 与预览适配
├── cleanup        孤立文件和失败删除重试
└── web            上传、元数据、预览、下载和删除 API
~~~

ccb-attachment 依赖 ccb-common、ccb-infrastructure、ccb-security 和 ccb-file-preview 的公开预览接口，不依赖任何业务模块。

### 13.2 状态

~~~text
TEMP -> BOUND -> DELETED
  '--> DELETED
~~~

- 上传创建 TEMP，记录上传人和过期时间。
- 业务保存成功后调用 AttachmentGateway.bind。
- 同一附件只能绑定一个业务对象；重复相同绑定幂等成功。
- 不允许从 BOUND 退回 TEMP。
- 删除标记和对象删除可分步重试，但对用户表现为不可访问。

### 13.3 数据表

| 表 | 责任 |
|---|---|
| att_file | 文件元数据、对象键、状态、上传人和过期时间 |
| att_binding | businessType、businessKey、项目展示快照和绑定时间 |
| att_operation_log | 上传、绑定、预览、下载、删除和清理审计 |
| att_cleanup_job | 对象删除和孤立附件清理重试 |

对象键使用租户和随机 ID 生成，不包含原始文件名。原始文件名、扩展名、Content-Type 和大小都经过服务端校验。

### 13.4 访问策略

- TEMP：仅同租户上传人可查询、预览、下载和删除。
- BOUND：附件模块根据 businessType 找到 AttachmentAccessPolicy。
- 策略输入为认证用户、businessKey 和操作类型，不传对象键。
- 没有注册策略、策略异常或业务对象不存在时默认拒绝。
- 业务模块授权解除绑定或删除后调用 Gateway，浏览器不能直接绕过业务状态。

### 13.5 预览与下载

- 支持类型使用 kkFileView 生成短时预览地址。
- 不支持类型返回“不可预览”，但有权限时仍可下载。
- URL 短时有效，不写入业务表或日志。
- 业务前端只保存 attachmentId。

## 14. Mock 项目上下文

前端新增 ProjectContextProvider 和 project-context Store：

~~~text
currentProject
availableProjects
selectProject(projectRef)
refresh()
~~~

- 初始 Provider 返回虚构项目列表。
- 当前选择保存到 localStorage。
- AppLayout 顶部展示项目切换器。
- Dashboard 和后续 release 页面只使用 Store，不读取 Mock 文件。
- 项目管理完成后新增 RemoteProjectContextProvider，并保持 Store API 不变。
- Mock projectRef 可以作为展示快照传入本地工作流，但服务端不得用它扩大权限。

## 15. 数据库迁移

### V38 workflow_business_integration

- 为 wf_instance 增加可空业务上下文字段，兼容旧实例。
- 新建 wf_business_event。
- 新建 wf_business_event_delivery。
- 新建 wf_task_signature。
- 增加事件调度、业务查询和签名唯一索引。

### V39 persistent_attachments

- 新建 att_file。
- 新建 att_binding。
- 新建 att_operation_log。
- 新建 att_cleanup_job。
- 初始化附件平台权限，不创建具体业务附件。

版本发布模块迁移顺延为 V37，确保平台能力先落地。

## 16. API 与前端契约

### 16.1 工作流

~~~text
GET  /api/workflows/inbox
GET  /api/workflows/done
GET  /api/workflows/instances/{id}/detail
GET  /api/workflows/instances/{id}/timeline
POST /api/workflows/tasks/{id}/decision
GET  /api/workflows/business-events
POST /api/workflows/business-events/{eventId}/deliveries/{subscriberKey}/retry
~~~

现有路径保留，响应增加业务上下文和签名数据。decision 增加 signatureConfirmed；只有节点要求签名时才强制为 true。事件查询和重试进入现有工作流监控，不新增独立一级菜单。

### 16.2 附件

~~~text
POST   /api/attachments
GET    /api/attachments/{id}
GET    /api/attachments/{id}/preview
GET    /api/attachments/{id}/download
DELETE /api/attachments/{id}
~~~

绑定、解除绑定和已绑定删除主要通过服务端 AttachmentGateway 完成，不暴露可绕过业务授权的浏览器接口。

## 17. 权限与审计

- workflow:task:view：查看本人待办已办和详情。
- workflow:task:decision：处理本人任务。
- workflow:event:manage：查看并重试 DEAD 事件。
- attachment:upload：上传临时附件。
- attachment:view：在所有权或业务策略允许时访问附件。
- attachment:manage：平台附件异常管理，不替代业务授权。

工作台本身不新增越权查询，复用本人任务范围。管理员权限不能伪造他人签名。

审计覆盖：

- 按编码解析和启动结果。
- 生命周期事件投递和人工重试。
- 电子签名。
- 附件上传、绑定、访问、删除和清理。
- Mock 项目切换仅保留前端本地状态，不写平台审计。

## 18. 错误、降级与恢复

| 条件 | 外部行为 | 恢复 |
|---|---|---|
| 流程编码不存在或未发布 | 不创建实例，返回明确冲突 | 发布正确流程后重试 |
| 业务事件消费者失败 | 流程状态保留，投递进入重试 | 自动退避或管理员重试 |
| 事件重复或乱序 | 消费者幂等拒绝倒退 | 无需人工修改状态 |
| 签名未确认 | 不执行审批 | 用户确认后重新提交 |
| 签名写入失败 | 审批和签名整体回滚 | 恢复数据库后重试 |
| 附件存储失败 | 不创建有效附件或清理残留对象 | 单文件重试 |
| BOUND 附件无访问策略 | 默认拒绝 | 注册业务策略后重试 |
| 预览服务不可用 | 保留附件和下载能力 | 恢复 kkFileView 后重试预览 |
| 孤立文件清理失败 | 记录任务并重试 | 自动或人工重试 |
| Mock 项目数据损坏 | 回退默认 Mock 项目，仅影响展示 | 清理本地存储后刷新 |

## 19. 安全、性能、兼容性与运维

### 19.1 安全

- 所有身份来自 AuthUser，不接受客户端 actor、tenant 或 signer。
- 内部路由和文件名执行白名单式校验。
- 对象键、预签名源地址、Token 和不必要正文不写日志。
- 附件读取默认拒绝，业务策略异常不降级为允许。
- 签名数据和事件数据不可由普通管理页面修改。

### 19.2 性能

- 首页每类最多查询五条，完整列表继续服务端分页。
- 为待办人、业务类型、项目展示字段、事件状态和下次重试时间建立索引。
- 事件和清理任务采用有界批次与抢占锁，避免多实例重复处理。
- 附件下载使用短时预签名 URL，不经应用内存转发大文件。

### 19.3 兼容与运维

- 旧 wf_instance 新字段可空，旧 API 不删除字段。
- signatureRequired 默认 false。
- 原 file-preview 上传和删除接口保持不变。
- 事件 dispatcher、附件清理和 Mock 项目 Provider 均可独立关闭。
- 提供事件积压、DEAD 数量、附件清理失败和签名写入失败日志指标。

## 20. 验证策略

### 20.1 工作流

- 编码唯一、未发布、重发版本和并发启动测试。
- 旧流程和 Flowable 流程统一事件测试。
- 事务回滚不产生事件、提交后事件不丢失测试。
- 重复、乱序、重试、DEAD 和人工重试测试。
- 旧 API 兼容和旧实例空字段测试。

### 20.2 签名

- required 为 true 和 false 的审批测试。
- 未确认、伪造签署人、非任务办理人、令牌失效、轮次不一致和实例摘要缺失测试。
- 审批与签名事务原子性测试。
- 时间线和详情签名展示测试。

### 20.3 附件

- TEMP 所有权、绑定幂等、无策略默认拒绝测试。
- 预览、下载、逻辑删除、对象删除失败和重试测试。
- 文件名、类型、大小和对象键安全测试。
- 孤立文件过期和并发清理测试。

### 20.4 前端

- 项目 Mock 切换和本地恢复。
- 首页待办已办加载、空、错误和跳转。
- 签名确认框和重复提交保护。
- 附件上传、进度、预览、下载、删除和失败恢复。
- TypeScript、生产构建和桌面浏览器验收。

### 20.5 集成

- Flyway 从 V37 升级到 V39 和全新安装。
- Maven 模块聚合与 Boot 装配。
- 通知发布能力回归。
- 全量 Maven、前端构建和治理检查。

## 21. 假设、未知项与决策记录

### 21.1 假设

- 业务模块能够提供稳定 businessType、businessKey、actionPath 和 dataDigest。
- 业务模块会实现幂等生命周期消费者和附件访问策略。
- 当前单体部署可通过 Spring Bean 发现消费者和策略。
- 正式项目管理后能够提供与 ProjectContextProvider 对等的远程接口。

### 21.2 非阻塞未知项

- 正式项目 API 的 DTO 和权限模型；不影响 Mock Provider 边界。
- 附件全局最大大小和保留天数最终生产值；首期由配置提供。
- 生命周期 DEAD 阈值和退避参数最终运维值；使用外部配置。

### 21.3 决策

1. 最新 main 不具备所需业务集成功能，需要增量完善。
2. 工作流按固定编码解析当前发布版本。
3. 生命周期事件持久化并至少一次投递。
4. 工作台摘要放在首页，完整任务列表保留原入口。
5. 持久附件使用独立 ccb-attachment 模块。
6. 项目和项目权限先 Mock，正式实现后替换 Provider。
7. 内部电子签名使用当前登录身份点击确认，不再次输入密码。
8. 站内通知直接复用现有公开 Publisher。

## 22. 风险与回退

| 风险 | 影响 | 缓解 |
|---|---|---|
| 双工作流路径事件不一致 | 业务状态遗漏或分叉 | 共用事件工厂和契约测试 |
| 事件重复或乱序 | 业务状态倒退 | eventId 唯一、轮次校验和状态前置条件 |
| 当前身份确认签名强度有限 | 不具备外部法律签章效力 | 明确内部用途、绑定摘要并保留完整审计 |
| 附件策略缺失 | 合法用户无法访问 | 默认拒绝并提供可观测错误，不放宽权限 |
| Mock 项目被误当权限 | 跨项目数据泄露 | 后端明确忽略 Mock 授权，正式环境接入前不得宣称项目隔离完成 |
| 新模块装配影响启动 | 平台不可用 | 独立模块测试、Boot 上下文测试和可关闭入口 |

回退原则：

- 可关闭首页工作台新增区域、事件 dispatcher 和附件入口。
- 保留旧工作流启动和原文件预览接口。
- 不删除已生成的事件、签名、附件绑定和审计数据。
- 回退应用后新增表保留只读，不执行生产 DROP。
