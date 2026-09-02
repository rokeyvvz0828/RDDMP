#!/usr/bin/env bash
# 后端服务热启动脚本（调试模式）
# 用法:
#   ./backend-dev.sh                 # 等价于 start
#   ./backend-dev.sh start          # 启动（8080 被占用则提示并退出）
#   ./backend-dev.sh restart        # 重启（8080 被占用则先停止旧进程再启动）
#   ./backend-dev.sh stop           # 停止（按 PID 文件与 8080 端口清理进程）
#   ./backend-dev.sh clean          # 仅清理构建产物（ccb-boot 及依赖模块的 target）
#   ./backend-dev.sh start --clean   # 先 clean 再编译安装并启动（全新一遍）
#   ./backend-dev.sh restart --clean # 停止旧实例后 clean 编译并重启
# 说明: 以 spring-boot:run 调试模式启动后端。修改 Java 代码后执行 restart 即可生效，
#       无需 mvn package。启动后保持前台运行，编译与启动日志实时输出到终端，
#       Ctrl+C 停止。前端 /api 与 /actuator 由 Vite 代理到本服务。
#       增量 restart 不会删除已从源码移除的旧资源副本；当重命名/删除 Flyway 迁移或
#       其他 classpath 资源后出现“重复版本/陈旧产物”类启动错误时，用 --clean 或 clean 清理。
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

PORT=8080
PID_FILE="/tmp/backend-dev.pid"
CLEAN=0   # 1=先清理各模块 target 再构建（start/restart --clean 或 clean 命令）

usage() {
  cat <<EOF
用法: $(basename "$0") [命令] [--clean]

命令:
  start     启动服务（端口被占用则提示并退出；前台运行，Ctrl+C 停止）
  restart   重启服务（端口被占用则先停止旧进程再启动；前台运行）
  stop      停止服务
  clean     仅清理 ccb-boot 及其依赖模块的 target 构建产物，不启动

选项:
  --clean   与 start/restart 组合：构建前先 clean，移除陈旧编译产物
            （如重命名/删除 Flyway 迁移后 target/classes 残留的旧文件）

不传命令等价于 start。
EOF
}

# 加载环境变量（.env 含 BCrypt 哈希等特殊字符，用 source 防止 shell 展开破坏）
load_env() {
  if [ ! -f .env ]; then
    echo "[错误] 未找到 .env 文件，请先按 README 配置环境变量。" >&2
    exit 1
  fi
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a

  # Linux 本机通常无法解析 Docker Desktop 专用的 host.docker.internal。
  # 后端回退到本机 MinIO 时，预览端点仍需保留为容器可访问的地址。
  if [[ "${MINIO_ENDPOINT:-}" == *host.docker.internal* ]] \
    && ! getent hosts host.docker.internal >/dev/null 2>&1; then
    local container_minio_endpoint="${MINIO_ENDPOINT}"
    if [[ -z "${MINIO_PREVIEW_ENDPOINT:-}" \
      || "${MINIO_PREVIEW_ENDPOINT}" == *127.0.0.1* \
      || "${MINIO_PREVIEW_ENDPOINT}" == *localhost* ]]; then
      MINIO_PREVIEW_ENDPOINT="${container_minio_endpoint}"
      export MINIO_PREVIEW_ENDPOINT
    fi
    MINIO_ENDPOINT="${MINIO_ENDPOINT//host.docker.internal/127.0.0.1}"
    export MINIO_ENDPOINT
    echo "[兼容] host.docker.internal 不可解析，后端使用 ${MINIO_ENDPOINT}，预览使用 ${MINIO_PREVIEW_ENDPOINT:-未配置}"
  fi
}

# 返回监听 PORT 的进程 PID（无则输出空）
port_pid() {
  local p=""
  if command -v ss >/dev/null 2>&1; then
    p=$(ss -tlnp 2>/dev/null | grep ":${PORT} " | grep -oP 'pid=\K[0-9]+' | head -1 || true)
  elif command -v lsof >/dev/null 2>&1; then
    p=$(lsof -t -iTCP:"${PORT}" -sTCP:LISTEN 2>/dev/null | head -1 || true)
  fi
  printf '%s' "$p"
}

# 先 TERM 再 KILL 终止单个进程并等待退出
kill_pid() {
  local pid="$1" i
  kill -TERM "$pid" 2>/dev/null || true
  for i in $(seq 1 20); do
    kill -0 "$pid" 2>/dev/null || return 0
    sleep 0.5
  done
  kill -KILL "$pid" 2>/dev/null || true
}

stop_service() {
  local pid="" killed=0
  # 1) 按 PID 文件停止主进程
  if [ -f "$PID_FILE" ]; then
    pid=$(cat "$PID_FILE" 2>/dev/null || true)
    if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
      echo "[停止] 主进程 PID=$pid"
      kill_pid "$pid"
      killed=1
    fi
    rm -f "$PID_FILE"
  fi
  # 2) 按端口停止监听进程（spring-boot:run fork 出的 java 子进程）
  pid=$(port_pid)
  if [ -n "$pid" ]; then
    echo "[停止] 端口 ${PORT} 监听进程 PID=$pid"
    kill_pid "$pid"
    killed=1
  fi
  # 3) 兜底清理本项目残留的 mvn spring-boot:run 进程
  pkill -f 'spring-boot:run' 2>/dev/null || true
  if [ "$killed" = 0 ]; then
    echo "[提示] 未发现运行中的后端进程（端口 ${PORT} 空闲）"
  else
    echo "[完成] 后端已停止"
  fi
}

start_service() {
  if [ -n "$(port_pid)" ]; then
    echo "[错误] 端口 ${PORT} 已被占用 (PID=$(port_pid))，请先执行: $(basename "$0") stop 或 $(basename "$0") restart" >&2
    exit 1
  fi
  if ! command -v mvn >/dev/null 2>&1; then
    echo "[错误] 未找到 mvn 命令。" >&2
    exit 1
  fi
  load_env

  # 编译并安装全部依赖模块到本地仓库（含本次代码改动，跳过测试）
  # --clean 时先清理各模块 target，避免重命名/删除资源后残留旧产物导致 Flyway 重复版本等
  local goals="install"
  if [ "$CLEAN" = 1 ]; then
    goals="clean install"
    echo "[清理] --clean：构建前先清理各模块 target 目录..."
  fi
  echo "[步骤1/2] 编译并安装依赖模块(跳过测试)..."
  # shellcheck disable=SC2086
  mvn -DskipTests $goals -pl :ccb-boot -am -Dspring-boot.repackage.skip=true

  echo "[步骤2/2] 启动后端: http://127.0.0.1:${PORT}  (前台运行，Ctrl+C 停止)"

  # 记录当前 shell PID，供 stop 命令使用；前台模式下 Ctrl+C 由 shell 收到后清理
  rm -f "$PID_FILE"
  echo "$$" >"$PID_FILE"
  trap 'rm -f "$PID_FILE"' INT TERM EXIT

  # 在 ccb-boot 模块目录单独以调试模式前台运行（reactor 仅含 boot，能正确定位主类）
  # 编译与启动日志实时输出到终端，保持进程不退出
  cd "$ROOT_DIR/server/src/platform/boot"
  mvn spring-boot:run -Dspring-boot.run.profiles=local

  # mvn 退出后（非 Ctrl+C 的异常退出）走到这里，清理并退出
  trap - INT TERM EXIT
  rm -f "$PID_FILE"
}

# 仅清理构建产物（ccb-boot 及其上游依赖模块的 target），不启动服务
clean_build() {
  if ! command -v mvn >/dev/null 2>&1; then
    echo "[错误] 未找到 mvn 命令。" >&2
    exit 1
  fi
  echo "[清理] 清理 ccb-boot 及其依赖模块的 target 目录..."
  mvn clean -pl :ccb-boot -am
  echo "[完成] 已清理构建产物，下次 start 将从干净状态重新编译。"
}

restart_service() {
  if [ -n "$(port_pid)" ] || [ -f "$PID_FILE" ]; then
    echo "[重启] 停止旧实例..."
    stop_service
    local i
    for i in $(seq 1 20); do
      [ -z "$(port_pid)" ] && break
      sleep 0.5
    done
  fi
  start_service
}

ACTION="start"
for arg in "$@"; do
  case "$arg" in
    start|restart|stop|clean) ACTION="$arg" ;;
    --clean)                  CLEAN=1 ;;
    -h|--help)                usage; exit 0 ;;
    *)                        echo "[错误] 未知参数: $arg" >&2; usage >&2; exit 1 ;;
  esac
done

case "$ACTION" in
  start)   start_service ;;
  restart) restart_service ;;
  stop)    stop_service ;;
  clean)   clean_build ;;
  *)       usage >&2; exit 1 ;;
esac
