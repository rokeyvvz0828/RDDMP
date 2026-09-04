# 跨平台开发环境启动器工程设计

## 文档状态

- 修订：2
- 状态：已确认
- 用户确认依据：用户确认 Windows、macOS、Linux 均需支持，固定账号为 `admin/admin123`，并批准采用 PowerShell/Bash 薄入口加无第三方依赖 Node.js 编排器的方案。
- 修订 2：为保证 Linux 容器访问宿主机 MinIO，将既有 `KK_FILE_VIEW_HOST_ALIAS` 接入 Compose `host-gateway`，不改变应用容器化边界。

## 目标与成功信号

研发人员从仓库根目录执行一个平台对应命令，即可启动固定、可重复的本地开发环境。基础设施就绪后，Flyway 自动迁移，管理员可以使用 `admin/admin123` 登录，前端修改由 Vite HMR 更新，后端修改经增量编译后由 DevTools 自动重启。

## 使用者与场景

- Windows 研发人员执行 `.\scripts\dev.ps1`。
- macOS/Linux 研发人员执行 `./scripts/dev.sh`。
- 研发人员在同一终端观察分组日志，使用 `Ctrl+C` 结束前后端进程。
- 需要仅停止基础设施时执行平台入口并传入 `--down`。

## 必须需求与验收条件

### R1 跨平台统一行为

- 两个平台入口都只负责定位仓库和调用 `node scripts/dev.mjs`。
- 公共编排器只使用 Node.js 20 标准库，不新增 npm 包。
- 任一必需工具缺失时，在启动子进程前明确失败。

### R2 固定且隔离的开发配置

- 编排器为 Docker、Maven 和 npm 子进程构造显式环境变量。
- Spring profile 固定为 `local`，目标地址固定为回环地址或 Docker 本地服务。
- 不读取、生成或覆盖 `.env`，不接受生产 profile 和远程服务地址。
- 固定管理员账号为 `admin/admin123`，使用仓库既有演示 BCrypt 哈希。

### R3 基础设施与 Flyway

- 启动 MySQL、MinIO、kkFileView，并在启动后端前等待 MySQL 可用。
- 空库由现有 Flyway V1 创建管理员；已有库在表存在时只更新 `tenant_id=1 AND id=1 AND username='admin'` 的密码哈希。
- 后端启动必须保持 `spring.flyway.enabled=true`，健康检查成功才报告环境可用。

### R4 前后端热重载

- 前端使用现有 `npm run dev` 和 Vite HMR。
- `ccb-boot` 增加可选、运行时 Spring Boot DevTools 依赖，不传递给业务模块。
- 编排器监听后端 `src/main/java`、`src/main/resources` 和相关 Maven POM；变更经防抖后串行执行 `mvn -pl :ccb-boot -am -DskipTests compile`。
- 编译失败时保留当前后端进程并报告错误；下一次文件变化可再次编译恢复。

### R5 生命周期与提示

- 编排器统一转发前后端输出，并使用稳定前缀区分来源。
- `Ctrl+C` 停止监听器、前端和后端，不默认停止基础设施。
- `--down` 执行 `docker compose down`，禁止附加 `--volumes`。
- 根 `AGENTS.md` 提示平台命令、开发账号和仅限本地的安全约束。

## 不变量与约束

- 不修改已发布迁移和数据库结构。
- 不连接生产或共享环境，不接受真实凭据。
- 不覆盖用户 `.env`，环境变量只存在于编排器及其子进程。
- Docker Compose、Spring Boot Flyway 和 Vite 仍是各自能力的事实源，Node 编排器不复制其配置语义。
- 默认退出不删除容器或数据卷。
- 只修改任务范围列出的文件。

## 非目标

- 不将前后端迁入 Docker。
- 不实现通用进程管理器、守护服务或 GUI。
- 不自动安装 JDK、Maven、Node.js、npm 或 Docker。
- 不增加 Flyway repair、clean 或生产迁移能力。
- 不修改业务 API、权限规则或页面。

## 方案比较与选择

选择“薄入口脚本 + Node.js 公共编排器”。项目已要求 Node.js 20，因此没有新增平台前置条件；公共编排器可以集中处理工具探测、子进程、日志、防抖监听和信号清理。

- 独立 PowerShell 与 Bash 完整实现：不增加公共编排文件，但重复维护数据库等待、密码恢复、热重载和进程清理，平台行为容易漂移。
- Compose Watch 全容器方案：环境更封闭，但需要前后端镜像、挂载和构建缓存设计，显著扩大改动范围和日常资源开销。

## 架构边界与组件职责

- `scripts/dev.ps1`：Windows 参数透传、退出码返回，不实现业务逻辑。
- `scripts/dev.sh`：macOS/Linux 参数透传、退出码返回，不实现业务逻辑。
- `scripts/dev.mjs`：工具检查、固定环境构造、Docker 生命周期、MySQL 等待、管理员密码恢复、前后端进程、后端监听、信号清理和日志。
- `docker-compose.yml`：为 kkFileView 增加由 `KK_FILE_VIEW_HOST_ALIAS` 控制的宿主机 `host-gateway` 映射，保证 Windows、macOS 和 Linux 使用一致的 MinIO 预签名主机名。
- `server/src/platform/boot/pom.xml`：仅为组合根提供 DevTools 开发期自动重启能力。
- `AGENTS.md`：提供推荐入口及开发凭据边界，不替代正式需求和 README。

## 接口、数据和状态流

正常启动状态流：

1. wrapper 将参数原样传给 Node 编排器。
2. 编排器验证版本和命令可用性，建立固定本地子进程环境。
3. Docker Compose 启动三个基础设施服务；轮询容器内 `mysqladmin ping`。
4. 若 `sys_user` 已存在，使用容器内 MySQL 客户端执行带精确主键和租户条件的密码哈希更新；空库跳过。
5. Maven 以 `local` profile 启动后端，Spring Boot 自动运行 Flyway。
6. npm 启动 Vite；编排器并行等待后端健康检查。
7. 后端源码变化触发防抖编译；DevTools 检测 classpath 变化并重启。
8. 收到退出信号后先停止监听，再终止前后端进程并返回可判别退出码。

`--down` 状态流只检查 Docker Compose 并执行 `docker compose down`，不启动 Maven、npm 或文件监听。

## 错误、降级与恢复

- 工具或版本缺失：列出缺失项和最低版本，非零退出，不产生部分服务。
- Docker 启动失败或 MySQL 等待超时：停止启动后续进程，保留 Docker 日志供排查。
- 管理员表查询失败：只有“表不存在”允许按空库继续；认证、连接或 SQL 错误均停止启动。
- Maven 或 npm 初始进程退出：终止另一应用进程并返回失败。
- 后端增量编译失败：打印失败，不杀死当前可用后端；后续变化重新尝试。
- 健康检查超时：报告后端日志仍保持可见，随后清理应用子进程并失败退出。

## 安全、性能、兼容性与运维

- 固定配置和密码属于公开开发夹具，编排器强制本地地址和 `local` profile，不提供覆盖为远程地址的参数。
- JWT、数据库和 MinIO 使用固定开发值，不得在任何共享环境复用；日志不打印 JWT 固定值或 BCrypt 哈希。
- 文件监听使用单一防抖队列，编译期间的新变化合并为下一轮，避免并发 Maven 进程。
- Windows 终止使用进程树语义，macOS/Linux 使用进程组信号；均设置强制终止超时。
- Node 编排器保持标准库实现，兼容项目要求的 Node.js 20 及以上版本。

## 验证策略

- 静态：`node --check`、PowerShell AST、`bash -n`、治理和范围检查。
- 构建：`mvn -pl :ccb-boot -am test`、`npm --prefix web run build`。
- 运行：Docker 就绪、Flyway 日志和历史表、健康接口、前端入口、登录接口。
- 热重载：修改并恢复受控后端资源和前端文件，观察编译、后端重启与 Vite HMR 信号。
- 生命周期：验证 `Ctrl+C` 后应用端口释放、容器仍运行；验证 `--down` 后容器停止且卷仍存在。

## 假设、未知项与决策记录

- 假设 Docker Compose V2 可通过 `docker compose` 调用；若目标环境只有旧版 `docker-compose`，启动器明确报错，不增加兼容分支。
- 假设本地端口 3306、8080、5173、9000、9001、8012 可用；冲突时明确失败，不自动改端口以保持环境确定性。
- D1：固定账号采用用户确认的 `admin/admin123`。
- D2：采用 PowerShell/Bash 薄入口与 Node.js 公共编排器。
- D3：Flyway 继续由 Spring Boot 启动管理，不引入 Flyway CLI。
- D4：默认保留基础设施和数据卷，显式 `--down` 也不删除卷。

## 风险与回退原则

- 跨平台进程树处理可能存在平台差异：以 Windows 真实运行和 macOS/Linux 人工复核关闭风险。
- 后端源码树较大时监听可能触发频繁编译：使用路径白名单、防抖和单并发队列限制负载。
- 固定开发密码可能被误用于其他环境：强制 local profile、本地地址，并在脚本输出和 `AGENTS.md` 标记公开开发凭据。
- DevTools 可能改变开发期重启行为：依赖仅放在 boot 运行时，回退该依赖和启动脚本即可恢复手工启动。
