# RDDMP

RDDMP（R&D Delivery Management Platform）是一个面向研发团队的单租户企业级管理平台基础框架。项目采用前后端分离架构，提供组织、账号、权限、主题布局、参数、工作流和 AI 模型配置能力，并通过共享组件和接入契约支持后续业务模块快速接入。

## 当前能力

- 账号登录、JWT 会话、登录超时续期、登出和登录审计。
- 组织树、用户与组织关联、头像上传、角色关联和 RBAC 权限并集。
- 菜单树、动态路由、菜单图标、页面级增删改查权限和中文面包屑。
- 参数管理，支持自定义参数类别、参数项、状态和登录时效配置。
- 多套系统配色、科技蓝主题、深浅模式、侧边/顶部/混合布局和多页签管理。
- 统一前端页面、表格、工具栏、表单抽屉、状态标签、空状态、组织树、用户身份等组件，并提供组件示例页。
- 基于 MinIO 和 kkFileView 的受控文件在线预览，提供动态能力配置、公共预览弹窗和组件示例。
- 自定义中文流程设计器与 Flowable 7.0.1 运行引擎，支持审批、条件网关、并行分支/汇聚、会签、同意、拒绝、退回、加签、抄送、转交、委托和终止。
- AI 提供商、模型、能力路由和统一执行入口；模型密钥仅保存在服务端。
- Flyway 数据库迁移、中文表和字段注释、结构快照及可重复执行的导出脚本。

当前首期范围是单租户。所有平台业务表、工作流业务表、AI 配置和审计数据都通过服务端租户上下文隔离。

## 技术栈

| 层次 | 技术 |
| --- | --- |
| 后端 | JDK 17、Spring Boot 3.4.4、Spring Security、MyBatis-Plus 3.5.12、Flyway |
| 工作流 | Flowable 7.0.1、业务模型到 BPMN 2.0 编译 |
| 数据库 | MySQL 8.4 |
| 前端 | Vue 3、TypeScript、Vite、Element Plus、Pinia、Vue Router、Vue Flow |
| 基础设施 | Docker Compose、MinIO、kkFileView 5.0.1、JWT |

## 目录结构

~~~text
web/             Vue 前端应用、路由、主题、共享组件和页面
server/src/platform/boot/            Spring Boot 启动模块和统一 Web 配置
server/src/shared/common/          通用响应、分页、租户和基础模型
server/src/platform/infrastructure/  数据库迁移、存储和基础设施适配
server/src/platform/file-preview/    文件上传、预览源校验和 kkFileView 适配
server/src/platform/security/        JWT 认证、授权和登录审计
server/src/platform/system/          组织、用户、角色、菜单、参数和系统管理
server/src/platform/workflow/        流程设计模型、BPMN 编译和 Flowable 运行服务
server/src/modules/ai/              AI 提供商、模型、路由和执行服务
docs/                数据库、模块接入契约、设计和实施计划
.ai-control/original/              初始平台建设历史账本
.ai-control/requirements/          按需求隔离的任务与验证证据
~~~

## 开发前置条件

需要安装：

- JDK 17
- Maven 3.9 或更高版本
- Node.js 20 LTS 或更高版本及 npm
- Docker Desktop 或 Docker Engine
- Git
- Python 3（用于安装工程控制插件）

### 项目开始时安装工程控制 Skill

开始新的项目任务前，先安装 [wjzxc123/control-engineering-skills](https://github.com/wjzxc123/control-engineering-skills)。该仓库包含本项目使用的需求基线、系统建模、任务规划、受控执行、观测、纠偏和收敛验证流程。

~~~powershell
git clone https://github.com/wjzxc123/control-engineering-skills.git
Set-Location control-engineering-skills
python .\scripts\install_plugin.py --dry-run
python .\scripts\install_plugin.py
~~~

macOS/Linux 将 python 替换为 python3。安装完成后重新打开 Codex 任务，使新 Skill 生效。安装器会把仓库中的 plugins/control-engineering 注册为 Codex 插件；不要把仓库根目录直接传给只接受单个 Skill 路径的通用安装器。

## 本地启动

### 1. 配置环境变量

在项目根目录执行：

~~~powershell
Copy-Item .env.example .env
~~~

然后编辑 .env，至少设置数据库密码、JWT 密钥、MinIO 密钥和管理员 BCrypt 密码哈希。.env 已被 Git 忽略，不要提交真实密钥。

管理员账号默认为 admin，显示名为“管理员”。初始密码由 BOOTSTRAP_ADMIN_PASSWORD_HASH 决定，不在代码中保存默认明文密码。

### 2. 启动基础设施

~~~powershell
docker compose --env-file .env up -d mysql minio kkfileview
docker ps --filter "name=ccb-platform-"
~~~

MySQL 使用端口 3306，MinIO API/控制台使用 9000/9001，kkFileView 使用 8012。首次启动后，后端会通过 Flyway 自动执行迁移。

### 3. 文件预览配置

本地后端运行在宿主机、kkFileView 运行在容器时，MinIO 预签名地址必须同时能被两者访问：

~~~dotenv
MINIO_ENDPOINT=http://host.docker.internal:9000
MINIO_BUCKET=ccb-platform
FILE_PREVIEW_ENABLED=true
KK_FILE_VIEW_BASE_URL=http://127.0.0.1:8012
KK_FILE_VIEW_TRUST_HOST=host.docker.internal
~~~

只有后端生成的 MinIO 预签名 URL 会交给 kkFileView；浏览器不能提交任意远程 URL。生产环境必须将 `KK_FILE_VIEW_TRUST_HOST` 收窄到对象存储域名，使用独立密钥、受控网络和桶生命周期，并定期清理 `file-preview/` 临时对象。

### 4. 启动后端

~~~powershell
mvn -pl :ccb-boot -am spring-boot:run -Dspring-boot.run.profiles=local
~~~

后端地址为 http://127.0.0.1:8080，健康检查地址为 http://127.0.0.1:8080/actuator/health。

### 5. 启动前端

另开终端：

~~~powershell
Set-Location web
npm ci
npm run dev
~~~

前端地址为 http://127.0.0.1:5173。Vite 会将 /api 和 /actuator 代理到本地后端。

## 环境变量

| 变量 | 作用 | 默认/说明 |
| --- | --- | --- |
| DB_URL | 后端 JDBC 地址 | 指向 ccb_platform |
| DB_USERNAME / DB_PASSWORD | 后端数据库账号密码 | 与 Docker MySQL 一致 |
| MYSQL_PASSWORD / MYSQL_ROOT_PASSWORD | Docker MySQL 初始化密码 | 必填 |
| JWT_SECRET | JWT 签名密钥 | 必须使用长随机值 |
| JWT_ACCESS_TTL_MILLIS | Access Token 时效 | 默认 900000 毫秒 |
| JWT_REFRESH_TTL_MILLIS | Refresh Token 时效 | 默认 604800000 毫秒 |
| BOOTSTRAP_ADMIN_PASSWORD_HASH | 管理员 BCrypt 密码哈希 | 必填，不填后端不启动 |
| MINIO_ENDPOINT | MinIO 服务地址 | 默认 http://127.0.0.1:9000 |
| MINIO_ACCESS_KEY / MINIO_SECRET_KEY | MinIO 访问凭据 | 必填 |
| MINIO_BUCKET | 头像对象桶 | 默认 ccb-platform |
| MINIO_PRESIGNED_EXPIRY_SECONDS | 预签名地址有效期 | 默认 3600 秒 |
| FILE_PREVIEW_ENABLED | 是否启用文件预览接口 | 默认 false |
| FILE_PREVIEW_MAX_FILE_SIZE / FILE_PREVIEW_MAX_FILE_SIZE_BYTES | multipart 与业务校验大小 | 示例为 50MB / 52428800 |
| FILE_PREVIEW_ALLOWED_EXTENSIONS | 可预览扩展名白名单 | 逗号分隔，通过能力接口返回前端 |
| KK_FILE_VIEW_IMAGE | kkFileView 容器镜像 | 默认 keking/kkfileview:5.0.1；旧版本仅允许本地临时验证 |
| KK_FILE_VIEW_BASE_URL / KK_FILE_VIEW_PORT | 浏览器访问 kkFileView 的地址和端口 | 示例为 http://127.0.0.1:8012 / 8012 |
| KK_FILE_VIEW_HOST_ALIAS | 容器访问宿主机 MinIO 时使用的本地域名 | 仅本地 Docker 联调需要，映射到 host-gateway |
| KK_FILE_VIEW_TRUST_HOST | kkFileView 可拉取文件的主机白名单 | 本地为 host.docker.internal，生产必须收窄 |
| KK_FILE_VIEW_NOT_TRUST_HOST | kkFileView 禁止访问的主机和网段 | 黑名单优先于白名单 |

## 数据库结构与导出

数据库迁移位于 server/src/platform/infrastructure/src/main/resources/db/migration。V23__database_comments.sql 和 V24__metadata_table_comments.sql 维护中文表注释、字段注释和元数据表注释。

结构快照位于 docs/database/ccb_platform_schema.sql，不包含业务数据、密码、JWT 密钥、头像内容或其他敏感值。使用以下命令从 ccb-platform-mysql 导出最新结构：

~~~powershell
$env:DB_PASSWORD = '数据库密码'
.\docs\database\export-schema.ps1
~~~

更完整的数据库约束和恢复说明见 docs/database/README.md。

## 模块接入规范

新增模块应遵守以下边界：

1. 前端使用 web/src/components/ui 下的共享组件和语义主题变量，不在业务页面重复定义颜色、表格、抽屉和状态样式。
2. 后端接口统一使用 /api 前缀、统一响应结构、服务端租户上下文和权限校验；不能从浏览器接收或保存服务商密钥。
3. 动态菜单必须提供中文名称、路由、图标和 children 树结构；页面通过菜单权限控制访问，操作权限使用 create/read/update/delete。
4. 工作流模块只接收中文业务流程模型，由后端编译和部署 BPMN；前端禁止直接操作 Flowable ACT_* 表。
5. AI 模块以“能力”请求为边界，由服务端按路由选择模型并写入审计；业务模块不能绑定具体服务商或执行任意浏览器代码。
6. 文件预览通过 `api/file-preview.ts` 获取能力并上传，通过 `UiFilePreview` 展示；业务模块不得拼接 kkFileView 地址或把任意外部 URL 交给预览服务。

具体契约见：

- docs/integration/frontend-ui-contract.md
- docs/integration/workflow-module-contract.md
- docs/integration/ai-module-contract.md

## 构建与测试

后端完整测试和打包：

~~~powershell
mvn test
mvn -DskipTests package
~~~

前端类型检查和生产构建：

~~~powershell
Set-Location web
npm run build
~~~

工作流核心测试：

~~~powershell
mvn -pl ccb-workflow -am test
~~~

## 工程控制账本

.ai-control/ 是本项目的工程控制账本，记录需求基线、系统模型、受控任务、执行证据、独立观测、反馈和收敛结果。后续任务开始时应先阅读 `.ai-control/original/state.json` 了解初始历史，再读取 `.ai-control/requirements/<control-prefix>/` 的当前任务证据，不要绕过账本直接修改阶段状态。

账本的核心恢复顺序：

~~~text
读取 .ai-control/original/state.json
  -> 确认当前阶段与目标
  -> 阅读对应 handoff / plan / observation
  -> 执行受控任务
  -> 记录构建、接口、数据库或浏览器证据
  -> 观测偏差并纠正
  -> 收敛验证后再交付
~~~

账本与源码一起提交，便于在新环境中恢复项目上下文和验证依据。

## 注意事项

- 不要提交 .env、本地密码、JWT 密钥、MinIO 私钥、target/、node_modules/ 或 .m2/。
- 数据库导出只提交结构快照，不提交业务数据。
- 当前仓库主分支为 main；推送前先确认远程账号具有仓库写权限。
