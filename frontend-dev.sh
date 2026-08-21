#!/usr/bin/env bash
# 前端服务热启动脚本（Vite 调试模式）
# 用法:
#   ./frontend-dev.sh            # 等价于 start
#   ./frontend-dev.sh start      # 启动（5173 被占用则提示并退出）
#   ./frontend-dev.sh restart    # 重启（5173 被占用则先停止旧进程再启动）
#   ./frontend-dev.sh stop       # 停止（按 PID 文件与 5173 端口清理进程）
# 说明: 以 Vite dev server 前台启动前端（默认 5173），支持 HMR 热更新：
#       修改 .vue/.ts/.css 后浏览器即时生效，无需打包。
#       /api 与 /actuator 自动代理到 http://127.0.0.1:8080（见 web/vite.config.ts）。
#       启动后保持前台运行，日志实时输出到终端，Ctrl+C 停止。
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR/web"

PORT=5173
PID_FILE="/tmp/frontend-dev.pid"

usage() {
  cat <<EOF
用法: $(basename "$0") [命令]

命令:
  start     启动服务（端口被占用则提示并退出；前台运行，Ctrl+C 停止）
  restart   重启服务（端口被占用则先停止旧进程再启动；前台运行）
  stop      停止服务

不传命令等价于 start。
EOF
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
  # 2) 按端口停止监听进程（vite dev server）
  pid=$(port_pid)
  if [ -n "$pid" ]; then
    echo "[停止] 端口 ${PORT} 监听进程 PID=$pid"
    kill_pid "$pid"
    killed=1
  fi
  # 3) 兜底清理本项目残留的 vite 进程（按仓库路径限定，避免误杀）
  pkill -f "$ROOT_DIR/web/node_modules/.bin/vite" 2>/dev/null || true
  if [ "$killed" = 0 ]; then
    echo "[提示] 未发现运行中的前端进程（端口 ${PORT} 空闲）"
  else
    echo "[完成] 前端已停止"
  fi
}

start_service() {
  if [ -n "$(port_pid)" ]; then
    echo "[错误] 端口 ${PORT} 已被占用 (PID=$(port_pid))，请先执行: $(basename "$0") stop 或 $(basename "$0") restart" >&2
    exit 1
  fi
  if ! command -v npm >/dev/null 2>&1; then
    echo "[错误] 未找到 npm 命令。" >&2
    exit 1
  fi

  # 首次运行自动安装依赖
  if [ ! -d node_modules ]; then
    echo "[提示] 首次运行，执行 npm ci 安装依赖..."
    npm ci
  fi

  echo "[启动] 前端: http://127.0.0.1:${PORT}  (前台运行，Ctrl+C 停止)"
  echo "[提示] 修改代码后浏览器自动热更新(HMR)，无需打包。"

  # 记录当前 shell PID，供 stop 命令使用；前台模式下 Ctrl+C 由 shell 收到后清理
  rm -f "$PID_FILE"
  echo "$$" >"$PID_FILE"
  trap 'rm -f "$PID_FILE"' INT TERM EXIT

  # 前台运行：npm/vite 日志实时输出到终端，保持进程不退出
  npm run dev

  # npm 退出后（非 Ctrl+C 的异常退出）走到这里，清理并退出
  trap - INT TERM EXIT
  rm -f "$PID_FILE"
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

ACTION="${1:-start}"
case "$ACTION" in
  start)   start_service ;;
  restart) restart_service ;;
  stop)    stop_service ;;
  *)       usage >&2; exit 1 ;;
esac
