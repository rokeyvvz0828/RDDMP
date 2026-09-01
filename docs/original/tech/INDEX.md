

1. # 研发管理

   

   

2. # 架构资产管理（耿俊辉）

   架构视图，除了数据项是企业级的，不归属到具体的物理子系统，其他均是归属到具体的物理子系统。或者广义上讲，数据项仍有数据项产生者和使用者区分。如果按照数据项产生者这个角度，这个仍是关联到具体的物理子系统， 我们本次不考虑数据项。

   ![图片](http://www.kdocs.cn/api/v3/office/copy/dUFkWjhMSWpoRldKT3d2QXpnZm8yZzI2aVpwR25BUzJudWRibUpRV3liWVBjRmVtcHlUUVJqWHZ2cVZzVkhRc3grcUNMaHQ0c2RQVkU1ZzkrbW9mNTZzK1hDUFMrMjFZUjlPazFZRVpVc3dteXc1dHVNUVhmYlAxRWZvQ3NmVExqaTB6aU9SVU5GY3FwaGMzdXQrRDZqS3BHZlFtQ2tsUmNQMVU0RHNJSnhZZUlNbnNMNVBjWkFZTXRwOVZPMk1DOGt2SHl0ZmRnbnhza1MxOUh4c3psZW5xU2g5emNQcmpVNzAxbm9UYXhyTXZFaDRSVm5MMFN6NFk3UzVPMEpxRDNQMkQ5QkVtdkpVPQ==/attach/object/QCGVAOBJAAAHK?&kso_type=image&kso_extra=eyJ0eXBlIjoiaW1hZ2UiLCJpZCI6IlFDR1ZBT0JKQUFBSEsiLCJvd25lciI6IjU1MDg4NzM0MjU2MSIsInJvdGF0ZSI6MCwic3RvcmFnZSI6ImJhc2UiLCJ3aWR0aCI6MTg5MiwiaGVpZ2h0Ijo3ODN9)

   图2. 基于物理子系统的架构资产视图

   上图主要是归纳的ITM上登记的架构资产，还少部署单元、数据库、技术栈信息等。少的这部分信息暂没有在ITM上登记的地方。需要修改这个图。

   1. ## 系统基础信息基线（蒋善东）

      - 系统物理子系统编号、中文名、英文名、系统功能描述、所属应用（预留）、所属事业群

      - 安全节点号、文件传输节点号（如有）

        这些信息是所有技术相关工作的基础，特别重要。

      1. ### **统一视图**

         具体功能点：

         功能1：物理子系统维度清单展示

         功能2：支持点开指定物理子系统，展示所有相关信息，包含2.1系统基础信息和2.2应用资产信息

      2. ### **多维度的统计**

         具体功能点:

         功能1：多维度查询

      3. ### **变更流程**

         步骤1：项目提交变更申请，包含新增物理子系统、修改相关信息、删除物理子系统。

         步骤2：应用架构师审核

         步骤3：结果反馈

   2. ## 应用资产信息

      1. ### 部署单元（李彪）

         1. #### 公告信息

            部署单元相关澄清公告，直接展示或者PDF。不可下载。

            - 关键术语或者技术说明，如部署单元类型、操作系统、部署实例数。

            - 资源测算方法，包含计算、存储，边车占用、本地日志等

            - 部署单元中运行的数据库、中间件、MQ、边车等

              具体功能点：

              以上三点内容均以PDF文件的形式承载。 

              功能1：文档上传

              上传者角色控制为技术架构师，上传文本格式为PDF

              功能2：文档阅读和展示

              阅读者为所有技术人员，业务人员和其他人员不可见。不支持下载

         2. #### **部署单元信息统一视图**

            部署单元基线信息展示，支持多个维度的汇总。比如按事业群、系统、部署单元类型、规格、数据库类型、使用的数据库类型、是否用到特定的中间件、边车组件等

         3. #### **部署单元的初始化导入**

            支持现有Excel导入，解析

            导入权限只有技术架构有，其他人无法使用该功能

         4. #### **变更流程**

            部署单元的变更流程，包括新增、修改、删除

            步骤1：开发人员提交变更

            步骤2：技术架构审核

            步骤3：结果反馈，处理结果需要反馈给所有路径上前序节点的人

      2. ### 联机接口（蒋善东，以ITM为准，这边暂不考虑，可以呈现视图信息）

         对外发布的联机接口

      3. ### EDA（蒋善东，以ITM为准，这边暂不考虑，可以呈现视图信息）

         对外发布的消息事件

         订阅的消息事件

      4. ### 交换接口（蒋善东，以ITM为准，这边暂不考虑，可以呈现视图信息）

         向数仓供数

      5. ### 集成请求（蒋善东，以ITM为准，这边暂不考虑，可以呈现视图信息）

         订阅数仓数据

      6. ### **批处理（马晓峰）**

         所有本地定时任务、鲁班作业、百川作业

   3. ## 网络管理（重点设计不同是呈现视图和数据落地存储，数据不冗余，保证一致性）

      1. ### 网络规划、分区原则、IP段规划、域名规划（耿俊辉）

         - 发布整体网络规划，包含网络规划。主要核心网络，不必写骨干网和广域网。

         - 划分具体的分区情况，互联DMZ区、核心开发区（容器应用和虚机应用）、P5外联区、办公区。

         - 域名命名规则，如如何分段，每段表示什么意思，再如sys / com / net结尾。

         - 每个区使用的IP段，掩码。

           具体系统功能：

           以上三点内容均在一份网络规划方案中，以PDF文件的形式承载。 

           功能1：网络规划文档上传

           上传者角色控制为网络架构师，上传文本格式为PDF

           功能2：网络规划文档阅读和展示

           阅读者为所有技术人员，业务人员和其他人员不可见。不支持下载。

      2. ### 网络访问关系基线（盛赛荣）

         包含源物理子系统、源系统部署单元（源地址），目的物理子系统、目的系统部署单元（目的地址、目的端口）、协议、NAT映射地址、映射端口、用途

         具体系统功能：

         以上信息的呈现模式，以形成的网络访问关系为颗粒度。每一条网络访问关系包含的

      3. ### 网络开通流程（盛赛荣）

         步骤1：申请，通常由需要访问对方的系统负责人申请。申请过程中，必须填写信息内容为源地址、目的地址、目的端口、协议、用途

         步骤2：审核，由网络架构师对申请内容进行审核，保证其完整性和合理性。

         步骤3：资源分配，如有需要分配域名、CLB、映射地址、端口

         ![图片](http://www.kdocs.cn/api/v3/office/copy/dUFkWjhMSWpoRldKT3d2QXpnZm8yZzI2aVpwR25BUzJudWRibUpRV3liWVBjRmVtcHlUUVJqWHZ2cVZzVkhRc3grcUNMaHQ0c2RQVkU1ZzkrbW9mNTZzK1hDUFMrMjFZUjlPazFZRVpVc3dteXc1dHVNUVhmYlAxRWZvQ3NmVExqaTB6aU9SVU5GY3FwaGMzdXQrRDZqS3BHZlFtQ2tsUmNQMVU0RHNJSnhZZUlNbnNMNVBjWkFZTXRwOVZPMk1DOGt2SHl0ZmRnbnhza1MxOUh4c3psZW5xU2g5emNQcmpVNzAxbm9UYXhyTXZFaDRSVm5MMFN6NFk3UzVPMEpxRDNQMkQ5QkVtdkpVPQ==/attach/object/PTNGYMZJADAEA?&kso_type=image&kso_extra=eyJ0eXBlIjoiaW1hZ2UiLCJpZCI6IlBUTkdZTVpKQURBRUEiLCJvd25lciI6IjU1MDg4NzM0MjU2MSIsInJvdGF0ZSI6MCwic3RvcmFnZSI6ImJhc2UiLCJ3aWR0aCI6MTcwOCwiaGVpZ2h0Ijo1Mjh9)

         

         步骤4：实施，由网络工程师，按照审核结果和资源分配结果执行一下操作。

         配置域名，配置CLB（CLB的策略，包含负载后面的具体服务、轮询策略、会话保持时间等），配置映射地址和端口，开通网络访问关系。

         步骤5：结果反馈，处理结果需要反馈给所有路径上前序节点的人

      4. ### **微隔离特殊处理（盛赛荣，**后续需求**）**

      5. ### CLB管理（李彪）

      6. ### DNS域名管理（李彪）

      7. ### SSL证书管理（张恒）

      8. ### 外联证书管理（张恒）

   4. ## 相关角色和人员

      应用架构：耿俊辉

      数据架构：洪鹿平

      技术架构：李彪

      安全架构：张恒

3. # 环境管理（马晓峰）

   1. ## 环境整体规划（马晓峰）

      1. ### **环境基础信息**

         包含架构资产按多套系统的实例化信息。

         环境的规划，多套环境的名称、环境用途（如SIT、UAT、非功能、数据线、账务线、某某专题环境）、每套环境使用方信息

      2. ### **计算和存储**

         环境的整体信息，物理机型号、规格、台数、存储，汇总后的资源总数（CPU、MEM、存储）

      3. ### **数据库**

         数据库环境的整体信息，物理机、存储，以及上层搭建的集群信息

   2. ## 环境使用情况（马晓峰）

      1. ### **计算和存储**

         计算和存储资源，按系统维度已经使用的资源总数和详细清单

      2. ### 数据库

         数据库资源，按系统维度已分配的实例、资源、数据库链接、用户名

         

4. # 架构管理（耿俊辉）

   1. ## 架构原则发布（耿俊辉）

      四大架构规范（耿俊辉）

      编码规范（张伟伟）

      数据库规范（马晓峰）

   2. ## 架构决策流程管理（王畅）

      1. ### 架构决策流程发布

         流程公告信息，已PDF或者的在线图片展示

         模板管理

      2. ### 决策结论视图

         

         统计分析

      3. ### 架构决策流程

         

5. # 配置管理（田振）

   

