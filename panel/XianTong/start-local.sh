#!/usr/bin/env bash
# 本地一键启动脚本：拉起管理端后端(8080) 与 管理端前端(5666)，用于本地联调与小程序测试。
# 已启动的服务会被复用，不会重复拉起。
#
# 用法：
#   ./start-local.sh                  # 同时启动前后端
#   ./start-local.sh --backend-only   # 只启动后端
#   ./start-local.sh --frontend-only  # 只启动前端
#
# 日志：logs/backend.log、logs/frontend.log（已被根 .gitignore 的 *.log 忽略）
set -euo pipefail
cd "$(dirname "$0")"

readonly BACKEND_PORT=8080
readonly FRONTEND_PORT=5666
readonly LOG_DIR="logs"
readonly BACKEND_LOG="$LOG_DIR/backend.log"
readonly FRONTEND_LOG="$LOG_DIR/frontend.log"

START_BACKEND=1
START_FRONTEND=1
for arg in "$@"; do
  case "$arg" in
    --backend-only) START_FRONTEND=0 ;;
    --frontend-only) START_BACKEND=0 ;;
    -h | --help)
      sed -n '2,9p' "$0"
      exit 0
      ;;
    *)
      echo "未知参数：$arg（可用：--backend-only / --frontend-only）" >&2
      exit 1
      ;;
  esac
done

# 本次脚本启动的子进程 PID（空格分隔；macOS 自带 bash 3.2 下空数组配合 set -u 不安全）
STARTED_PIDS=""

add_pid() {
  STARTED_PIDS="$STARTED_PIDS $1"
}

port_pid() {
  lsof -nP -iTCP:"$1" -sTCP:LISTEN -t 2>/dev/null | head -1
}

wait_port() {
  local port=$1 timeout=$2 waited=0
  while [[ $waited -lt $timeout ]]; do
    if [[ -n "$(port_pid "$port")" ]]; then return 0; fi
    sleep 2
    waited=$((waited + 2))
  done
  return 1
}

cleanup() {
  trap - INT TERM EXIT
  [[ -z "$STARTED_PIDS" ]] && return 0
  echo ""
  echo "正在停止本次启动的服务..."
  for pid in $STARTED_PIDS; do
    kill "$pid" 2>/dev/null || true
  done
  sleep 3
  # gradle / vite 会派生子进程，按端口兜底回收
  for port in "$BACKEND_PORT" "$FRONTEND_PORT"; do
    local pid
    pid=$(port_pid "$port") || true
    [[ -n "${pid:-}" ]] && kill "$pid" 2>/dev/null || true
  done
  echo "已停止。"
}

start_backend() {
  if [[ -n "$(port_pid "$BACKEND_PORT")" ]]; then
    echo "[后端] 端口 $BACKEND_PORT 已在监听，复用现有服务。"
    return 0
  fi
  if [[ ! -f server/.env.local ]]; then
    echo "[后端] 缺少 server/.env.local，请先执行：cp server/.env.local.example server/.env.local 并填入凭证" >&2
    exit 1
  fi
  echo "[后端] 启动中（首次编译可能需要几分钟），日志：$BACKEND_LOG"
  (bash server/run-local.sh >"$BACKEND_LOG" 2>&1) &
  add_pid "$!"
  if ! wait_port "$BACKEND_PORT" 300; then
    echo "[后端] 启动超时，请查看 $BACKEND_LOG" >&2
    tail -30 "$BACKEND_LOG" >&2 || true
    exit 1
  fi
  echo "[后端] 就绪：http://localhost:$BACKEND_PORT"
}

start_frontend() {
  if [[ -n "$(port_pid "$FRONTEND_PORT")" ]]; then
    echo "[前端] 端口 $FRONTEND_PORT 已在监听，复用现有服务。"
    return 0
  fi
  if ! command -v pnpm >/dev/null 2>&1; then
    echo "[前端] 未找到 pnpm，请先安装并执行 client 目录下的 pnpm install" >&2
    exit 1
  fi
  echo "[前端] 启动中，日志：$FRONTEND_LOG"
  (cd client && pnpm -F @vben/web-antd run dev >"../$FRONTEND_LOG" 2>&1) &
  add_pid "$!"
  if ! wait_port "$FRONTEND_PORT" 120; then
    echo "[前端] 启动超时，请查看 $FRONTEND_LOG" >&2
    tail -30 "$FRONTEND_LOG" >&2 || true
    exit 1
  fi
  echo "[前端] 就绪：http://localhost:$FRONTEND_PORT"
}

print_summary() {
  local lan_ip
  lan_ip=$(ipconfig getifaddr en0 2>/dev/null || true)
  echo ""
  echo "===== panel 本地服务 ====="
  if [[ -n "$(port_pid "$BACKEND_PORT")" ]]; then
    echo "后端 API   : http://localhost:$BACKEND_PORT"
  fi
  if [[ -n "$(port_pid "$FRONTEND_PORT")" ]]; then
    echo "管理端页面 : http://localhost:$FRONTEND_PORT （admin / admin123）"
  fi
  if [[ -n "$lan_ip" ]]; then
    echo "局域网地址 : http://$lan_ip:$BACKEND_PORT （小程序真机联调用）"
  fi
  echo "========================="
}

mkdir -p "$LOG_DIR"
trap cleanup INT TERM

[[ $START_BACKEND -eq 1 ]] && start_backend
[[ $START_FRONTEND -eq 1 ]] && start_frontend
print_summary

if [[ -z "$STARTED_PIDS" ]]; then
  echo "服务均已在运行，无需启动。"
  exit 0
fi

echo "按 Ctrl+C 停止本次启动的服务。"
wait
