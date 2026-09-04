# 跨平台开发环境启动器实施计划

> 执行要求：使用 `$control-engineering` 逐任务实施。开发前计划是候选控制输入，必须先完成需求基准复核和系统建模，不得直接跳到执行阶段。

## 状态与来源

- 计划修订：1
- 设计修订：2
- 设计文档：`docs/engineering-control/designs/2026-09-04-dev-environment-runner-design.md`
- 状态：可移交

## 目标与全局约束

**目标：** 提供 Windows PowerShell 和 macOS/Linux Bash 薄入口，通过无第三方依赖的 Node.js 编排器启动固定本地环境、Flyway、前端 HMR 和后端自动编译重启。

**架构：** wrapper 只定位仓库并转发参数；`scripts/dev.mjs` 集中管理固定开发环境、Docker Compose、MySQL 等待、管理员密码恢复、应用子进程、后端监听和退出清理。Flyway 继续由 Spring Boot `local` profile 管理，Vite 继续管理前端 HMR，DevTools 只加入 `ccb-boot`。

**技术栈：** Node.js 20 标准库、PowerShell、Bash、Docker Compose V2、Maven 3.9、Spring Boot 3.4.4 DevTools、Vite 6、MySQL 8.4、Flyway。

全局约束：

- 只修改任务范围的 `writable_paths`。
- 固定开发账号为 `admin/admin123`，仅限本地开发，禁止接受生产 profile、远程地址或真实凭据。
- 不读取、创建或覆盖用户 `.env`；所有必要配置由编排器对子进程显式覆盖。
- 不修改历史 Flyway 文件，不执行 `clean` 或 `repair`，不删除 Docker 数据卷。
- 不增加 npm 包、全局工具、前后端容器镜像或业务代码变更。
- 保留原有手工启动方式；新增入口是可回退的开发辅助能力。

## 文件职责地图

| 路径 | 状态 | 职责 | 事实依据 |
| --- | --- | --- | --- |
| `scripts/dev.ps1` | candidate-new | Windows 薄入口，定位仓库、调用 Node、透传参数和退出码 | `scripts/` 已是治理工具目录 |
| `scripts/dev.sh` | candidate-new | macOS/Linux 薄入口，定位仓库、调用 Node、透传参数和退出码 | 用户要求跨平台入口 |
| `scripts/dev.mjs` | candidate-new | 唯一编排实现：环境、Docker、Flyway 启动链、进程、监听、日志与清理 | Node.js 20 已是项目前置条件 |
| `docker-compose.yml` | existing | 为 kkFileView 增加可配置的宿主机 `host-gateway` 映射 | 已声明 `KK_FILE_VIEW_HOST_ALIAS`，当前未接入 compose |
| `server/src/platform/boot/pom.xml` | existing | 为组合根加入可选 runtime DevTools | 当前仅有 Spring Boot Maven 插件，无 DevTools |
| `AGENTS.md` | existing | 提示推荐命令、固定账号和仅限本地边界 | 用户明确要求增加提示 |
| `.ai-control/requirements/req-20260904-063-dev-environment-runner/*.json` | current-prefix | 保存执行、观测、反馈、收敛和交接证据 | full control 模式要求 |

## 任务依赖图与并行策略

```text
T1 跨平台启动与固定环境
  -> T2 后端热重载闭环
    -> T3 治理提示与集成验收
```

三项任务串行执行。T1 与 T2 都依赖 `scripts/dev.mjs` 的稳定接口，T3 必须引用最终命令和真实验证结果，不安排并行修改。

## 需求覆盖表

| 需求 | 任务 |
| --- | --- |
| R1 跨平台统一行为 | T1 |
| R2 固定且隔离的开发配置 | T1 |
| R3 基础设施与 Flyway | T1、T3 |
| R4 前后端热重载 | T1、T2、T3 |
| R5 生命周期与提示 | T1、T3 |

### T1：跨平台启动、固定环境与基础设施

**需求映射：** R1、R2、R3、R4、R5

**前置任务：** 无

#### 文件边界与接口

- 新建：`scripts/dev.ps1`
- 新建：`scripts/dev.sh`
- 新建：`scripts/dev.mjs`
- 修改：`docker-compose.yml`
- 消费：现有 `docker compose` 服务名、`application-local.yml` 环境变量、`npm --prefix web run dev`。
- 产出：CLI `scripts/dev.mjs [--down]`；托管子进程接口；后端变更编译触发接口，供 T2 的 DevTools 重启消费。

#### 操作步骤、命令和预期信号

1. 建立静态基线，确认三个目标脚本不存在，Compose 当前没有 `extra_hosts`，并记录当前手工启动命令。
   - 命令：`Test-Path scripts/dev.ps1; Test-Path scripts/dev.sh; Test-Path scripts/dev.mjs; docker compose config`
   - 预期：三个脚本为 `False`；Compose 配置可解析。
   - 证据：退出码和 Compose 配置检查摘要。

2. 新建两个薄 wrapper。
   - PowerShell 使用 `$PSScriptRoot` 定位 `dev.mjs`，通过 `& node ... @args` 调用并 `exit $LASTEXITCODE`。
   - Bash 使用脚本真实目录定位 `dev.mjs`，通过 `exec node "$SCRIPT_DIR/dev.mjs" "$@"` 调用。
   - 预期：wrapper 不包含 Docker、Maven、npm、密码或文件监听逻辑。

3. 在 `scripts/dev.mjs` 定义固定配置和 CLI。
   - 仅接受无参数启动或 `--down`；未知参数打印用法并以 2 退出。
   - 固定 `SPRING_PROFILES_ACTIVE=local`、本地 JDBC、MySQL、JWT、MinIO、Mock、kkFileView 和管理员 BCrypt 哈希。
   - 固定哈希使用仓库现有 `admin123` 演示哈希 `$2a$10$KqK1M7hsgCOD5bfzttFN0Out2SoIN.ppElHYrV1uYrZfvcWoQsu/.`，日志不得打印哈希或 JWT 值。
   - 对 Compose 设置全部插值变量及 `COMPOSE_DISABLE_ENV_FILE=1`；对 Spring 进程显式覆盖所有运行必需项，使用户 `.env` 不参与本入口的配置决策。

4. 实现工具与端口预检。
   - 依次验证 Node.js 20、Java 17、Maven 3.9、npm、`docker compose version`。
   - 启动前检查 3306、8080、5173、9000、9001、8012；若端口由本项目既有容器占用则允许复用，其他占用明确失败。
   - 预期：失败发生在应用子进程启动前，并列出具体工具、版本或端口。

5. 修改 `docker-compose.yml` 的 kkFileView 服务。
   - 增加 `extra_hosts: ["${KK_FILE_VIEW_HOST_ALIAS:-host.docker.internal}:host-gateway"]`。
   - 保持服务、镜像、端口、卷和启动命令不变。
   - 命令：`docker compose config`
   - 预期：配置解析成功，kkFileView 包含 host-gateway 映射。

6. 实现基础设施和管理员恢复。
   - 执行 `docker compose up -d mysql minio kkfileview` 并轮询 `docker exec ccb-platform-mysql mysqladmin ping`，超时 90 秒。
   - 使用容器内 MySQL 客户端查询 `information_schema.tables`；`sys_user` 不存在时按空库继续，存在时执行精确条件的 `UPDATE sys_user SET password_hash=... WHERE tenant_id=1 AND id=1 AND username='admin'`。
   - 认证失败、连接失败或非“表不存在”SQL 错误必须停止启动。

7. 实现前后端进程和健康检查。
   - 后端命令：`mvn -pl :ccb-boot -am spring-boot:run -Dspring-boot.run.profiles=local`。
   - 前端命令：`npm --prefix web run dev -- --host 127.0.0.1 --port 5173 --strictPort`。
   - 子进程输出按 `[backend]`、`[frontend]` 前缀逐行转发。
   - 轮询 `http://127.0.0.1:8080/actuator/health`，只在 HTTP 200 且状态为 `UP` 后打印访问地址和 `admin/admin123`。

8. 实现后端源码监听和退出清理。
   - 监听 `server/**/src/main/java`、`server/**/src/main/resources`、根 `pom.xml` 和 `server/**/pom.xml`，排除 `target`。
   - 500 毫秒防抖；编译命令为 `mvn -pl :ccb-boot -am -DskipTests compile`；编译串行，编译期间的新变化合并为下一轮。
   - Windows 使用 `taskkill /PID <pid> /T /F` 清理应用进程树；macOS/Linux 使用独立进程组并先 `SIGTERM`、超时后 `SIGKILL`。
   - `--down` 仅执行 `docker compose down`，不得包含 `--volumes`。

9. 执行 T1 静态与降级检查。
   - 命令：`node --check scripts/dev.mjs`
   - 命令：`powershell -NoProfile -Command "$e=$null;$t=$null;[System.Management.Automation.Language.Parser]::ParseFile('scripts/dev.ps1',[ref]$t,[ref]$e);if($e.Count){$e|% Message;exit 1}"`
   - 命令：`bash -n scripts/dev.sh`
   - 命令：`node scripts/dev.mjs --unknown`
   - 预期：三项语法检查通过；未知参数以 2 退出且不启动容器。

#### 验收、证据与回滚

- 验收：wrapper 无重复逻辑；固定配置完整；Docker/MySQL 等待和管理员恢复可判别；Flyway 启动链、日志、健康检查、退出和 `--down` 符合设计。
- 证据：语法检查、Compose 配置、工具版本、空库/已有库 SQL 断言、健康接口、登录接口、进程与容器状态。
- 回滚：删除三个新脚本，回退 `docker-compose.yml` 的单项 `extra_hosts`，恢复手工启动。
- 停止条件：需要真实凭据、远程地址、修改历史迁移、删除卷，或无法区分本项目与其他端口占用。
- 升级条件：Docker Desktop/Engine 对 `host-gateway` 行为不一致，或 Linux 无法同时满足宿主机后端和 kkFileView 对 MinIO 预签名地址的访问。

### T2：Spring Boot 后端自动重启

**需求映射：** R4

**前置任务：** T1

#### 文件边界与接口

- 修改：`server/src/platform/boot/pom.xml`
- 消费：T1 产出的 Maven 增量编译和后端长期运行进程。
- 产出：编译产物变化后 DevTools 自动重启应用上下文。

#### 操作步骤、命令和预期信号

1. 建立依赖基线。
   - 命令：`mvn -pl :ccb-boot dependency:tree -Dincludes=org.springframework.boot:spring-boot-devtools`
   - 预期：当前依赖树不包含 DevTools。

2. 在 `ccb-boot` 增加 `spring-boot-devtools`。
   - 依赖设置 `<scope>runtime</scope>` 和 `<optional>true</optional>`。
   - 不修改父 POM、不向业务模块添加依赖、不调整打包插件。

3. 执行构建和依赖边界检查。
   - 命令：`mvn -pl :ccb-boot -am -DskipTests compile`
   - 命令：`mvn -pl :ccb-boot dependency:tree -Dincludes=org.springframework.boot:spring-boot-devtools`
   - 预期：编译成功；DevTools 只出现在 `ccb-boot` 运行时依赖树。

4. 执行真实热重载验证。
   - 通过 T1 启动完整环境，记录后端进程和健康状态。
   - 对任务授权内的 `server/src/platform/boot/src/main/resources` 不允许新增文件，因此验证采用触发同一 Maven compile 命令并检查 DevTools restart 日志；源码变更的完整实测由人工在后续业务分支验证。
   - 预期：Maven compile 成功后 DevTools 输出 restart 信号，健康接口恢复为 `UP`，前端进程不中断。

#### 验收、证据与回滚

- 验收：DevTools 只作用于 boot；增量编译不并发；编译失败不会终止当前后端；恢复后可再次触发。
- 证据：依赖树、Maven compile、DevTools restart 日志、重启前后健康接口。
- 回滚：回退 `ccb-boot/pom.xml` 的 DevTools 依赖；T1 编排器仍可作为非后端热重载启动器运行。
- 停止条件：DevTools 被打入非开发产物或传递到业务模块，或重启导致 Flyway 重复异常。
- 升级条件：Maven compile 不会改变运行进程 classpath，需评估 Spring Boot Maven 插件的开发期配置。

### T3：治理提示与集成收敛

**需求映射：** R3、R4、R5

**前置任务：** T1、T2

#### 文件边界与接口

- 修改：`AGENTS.md`
- 写入：当前前缀的 execution、observation、convergence、state 和 handoff JSON。
- 消费：T1/T2 最终命令和真实验证结果。
- 产出：研发入口提示、范围审计和可交付证据。

#### 操作步骤、命令和预期信号

1. 在根 `AGENTS.md` 的实施指引附近增加“本地开发环境启动”提示。
   - Windows：`.\scripts\dev.ps1`。
   - macOS/Linux：`./scripts/dev.sh`。
   - 固定账号：`admin/admin123`，明确仅限本地开发，禁止复用于共享、测试或生产环境。
   - 明确脚本会启动 Docker 基础设施、Flyway、前后端热重载；`Ctrl+C` 不删除基础设施；`--down` 不删除卷。

2. 执行静态、治理和构建检查。
   - `node --check scripts/dev.mjs`
   - PowerShell AST 解析 `scripts/dev.ps1`
   - `bash -n scripts/dev.sh`
   - `docker compose config`
   - `node scripts/check-development-entry.mjs --require-plugin`
   - `node scripts/check-all-governance.mjs`
   - `mvn -pl :ccb-boot -am test`
   - `npm --prefix web run build`
   - `git diff --check`
   - 预期：全部退出码为 0。

3. 执行 Windows 运行验收。
   - 默认启动，验证 MySQL/MinIO/kkFileView 容器运行、Flyway 无失败、健康接口 `UP`、前端 HTTP 200、`admin/admin123` 登录成功。
   - 再次启动已有库，验证 admin 哈希更新行数为 1 或哈希已相同，其他用户不变。
   - 验证后端编译重启和 Vite 开发服务持续可用。
   - 发送 `Ctrl+C`，验证 8080/5173 释放而基础设施仍运行；执行 `--down` 后容器停止且命名卷仍存在。

4. 执行范围检查并记录平台限制。
   - 命令：`node scripts/check-codex-scope.mjs --scope docs/requirements/REQ-20260904-063-dev-environment-runner/codex-task-scope.yaml --base origin/main --head HEAD`
   - 预期：本需求分支仅包含授权文件；当前工作区已有的架构任务未跟踪文件不得纳入本需求提交或范围结论。
   - macOS/Linux 未真实运行时，只能报告 Shell 静态检查通过和剩余人工验收，不得声称跨平台运行已验收。

5. 写入当前前缀的执行、独立观测和收敛证据；只有 must 条件全部关闭后才将 phase 更新为 `converged`。

#### 验收、证据与回滚

- 验收：AGENTS 提示准确；静态、治理、后端、前端和 Windows 运行证据完整；范围无越界；未验证平台如实列为风险。
- 证据：全部命令退出码、健康和登录响应摘要、Flyway 版本、进程/容器/卷状态、当前前缀 JSON。
- 回滚：回退 `AGENTS.md` 和本需求实现提交；保留历史证据；不删除本地卷。
- 停止条件：治理、范围、后端测试、前端构建、登录、Flyway 或进程清理任一失败。
- 升级条件：需要修改 `application*.yml`、历史迁移、业务模块、共享能力或任务范围外文件。

## 集成检查

| 完成任务 | 命令或场景 | 预期 |
| --- | --- | --- |
| T1 | 三种脚本语法、`docker compose config`、未知参数 | 语法通过，未知参数无副作用失败 |
| T1、T2 | `mvn -pl :ccb-boot -am test` | reactor 成功，0 个失败 |
| T1、T2 | Windows 默认启动、健康、登录、编译重启、Ctrl+C、`--down` | 全链路可重复，卷保留 |
| T1、T2、T3 | 治理、前端构建、范围、diff | 全部退出码 0，无越界文件 |

## 控制模型种子

以下均为待 `$model-engineering-system` 验证的假设，不是已证实模型：

- 被控边界候选：wrapper、Node 编排器、Docker Compose 基础设施、Spring Boot/Flyway、Vite、DevTools。
- 状态变量候选：工具就绪、端口可用、容器状态、MySQL 就绪、迁移状态、后端健康、前端可达、编译队列、退出清理状态。
- 接口候选：CLI 参数、子进程环境、Compose 服务名、MySQL 容器 SQL、健康 HTTP、文件变化事件。
- 传感器候选：命令退出码、版本输出、Docker 状态、Flyway 日志、HTTP 响应、登录响应、DevTools 日志、端口探测。
- 执行器候选：启动/停止容器、启动/终止子进程、执行 SQL、触发 Maven compile、更新 AGENTS。
- 扰动候选：端口冲突、Docker 未运行、依赖缓存缺失、平台信号差异、文件事件风暴、已有本地数据状态。
- 时延候选：容器拉取、MySQL 初始化、首次 Maven 编译、Flyway 迁移、DevTools 重启、前端依赖解析。

## 风险与用户批准

- 高风险动作：修改治理入口、平台 boot 依赖、Compose 网络映射，以及更新本地 admin 哈希。
- 不可逆动作：无；不会删除卷或修改历史迁移。
- 用户已于 2026-09-04 确认实施计划和高风险动作，交接包可导入 `control-engineering`。
- `public_capability_change.owner_approved=true`，依据为用户对“实施计划及代表模块 Owner 批准公共能力变更”问题的确认回复。
