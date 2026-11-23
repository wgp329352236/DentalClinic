#!/usr/bin/env bash
# 本地一键启动脚本：拉起管理端后端(8080) 与 管理端前端(5666)，用于本地联调与小程序测试。
# 默认复用已运行的服务；加 --restart 先停掉本项目启动的服务再重启。
#
# 用法：
#   ./start-local.sh                            # 启动前后端，已运行则复用
#   ./start-local.sh --restart                  # 先停后启（前后端）
#   ./start-local.sh --backend-only             # 只启动后端
#   ./start-local.sh --restart --frontend-only  # 只重启前端
#
# 端口被非本项目进程占用时只提示并跳过，不会误杀；本项目进程通过 logs/*.pid 识别。
# 日志：logs/backend.log、logs/frontend.log
# 详细说明：doc/本地启动脚本使用说明.md
set -euo pipefail
cd "$(dirname "$0")"

readonly LOG_DIR="logs"
readonly BACKEND_PORT=8080
readonly FRONTEND_PORT=5666

RESTART=0
START_BACKEND=1
START_FRONTEND=1
for arg in "$@"; do
  case "$arg" in
    --restart) RESTART=1 ;;
    --backend-only) START_FRONTEND=0 ;;
    --frontend-only) START_BACKEND=0 ;;
    -h | --help)
      sed -n '2,13p' "$0"
      exit 0
      ;;
    *)
      echo "未知参数：${arg}（可用：--restart / --backend-only / --frontend-only）" >&2
      exit 1
      ;;
  esac
done

# 本次脚本启动的服务与子进程 PID（空格分隔；macOS 自带 bash 3.2 下空数组配合 set -u 不安全）
STARTED_SVCS=""
STARTED_PIDS=""
SKIPPED_SVCS=""

svc_label() { if [[ "$1" == backend ]]; then echo "后端"; else echo "前端"; fi; }
svc_port() { if [[ "$1" == backend ]]; then echo "$BACKEND_PORT"; else echo "$FRONTEND_PORT"; fi; }
svc_log() { echo "$LOG_DIR/$1.log"; }
svc_pidfile() { echo "$LOG_DIR/$1.pid"; }
svc_timeout() { if [[ "$1" == backend ]]; then echo 300; else echo 120; fi; }

port_pid() {
  lsof -nP -iTCP:"$1" -sTCP:LISTEN -t 2>/dev/null | head -1 || true
}

pid_alive() { kill -0 "$1" 2>/dev/null; }

# 判断 $2 是否等于 $1 或为 $1 的后代进程（gradle/vite 会派生子进程）
is_descendant() {
  local ancestor=$1 current=$2 hops=0
  while [[ -n "$current" && "$current" != "1" && $hops -lt 15 ]]; do
    [[ "$current" == "$ancestor" ]] && return 0
    current=$(ps -o ppid= -p "$current" 2>/dev/null | tr -d ' ')
    hops=$((hops + 1))
  done
  return 1
}

# 监听该端口的进程是否由本项目启动（依据 logs/<svc>.pid）
owns_service() {
  local name=$1 listen_pid=$2 pidfile pid
  pidfile=$(svc_pidfile "$name")
  [[ -f "$pidfile" ]] || return 1
  pid=$(tr -d ' ' <"$pidfile")
  [[ -n "$pid" ]] || return 1
  pid_alive "$pid" || return 1
  is_descendant "$pid" "$listen_pid"
}

wait_port_up() {
  local port=$1 timeout=$2 waited=0
  while [[ $waited -lt $timeout ]]; do
    [[ -n "$(port_pid "$port")" ]] && return 0
    sleep 2
    waited=$((waited + 2))
  done
  return 1
}

wait_port_down() {
  local port=$1 timeout=$2 waited=0
  while [[ $waited -lt $timeout ]]; do
    [[ -z "$(port_pid "$port")" ]] && return 0
    sleep 1
    waited=$((waited + 1))
  done
  return 1
}

stop_service() {
  local name=$1 port listen_pid pidfile pid
  port=$(svc_port "$name")
  pidfile=$(svc_pidfile "$name")
  listen_pid=$(port_pid "$port")

  if [[ -z "$listen_pid" ]]; then
    rm -f "$pidfile"
    return 0
  fi
  if ! owns_service "$name" "$listen_pid"; then
    echo "[$(svc_label "$name")] 端口 $port 被非本项目进程占用（PID ${listen_pid}），跳过不停止。" >&2
    return 1
  fi

  echo "[$(svc_label "$name")] 停止中（PID ${listen_pid}）..."
  kill "$listen_pid" 2>/dev/null || true
  pid=$(tr -d ' ' <"$pidfile" 2>/dev/null || true)
  [[ -n "${pid:-}" ]] && kill "$pid" 2>/dev/null || true
  wait_port_down "$port" 30 || echo "[$(svc_label "$name")] 端口 $port 未在 30 秒内释放，请手动检查。" >&2
  rm -f "$pidfile"
  return 0
}

svc_launch() {
  local name=$1 log
  log=$(svc_log "$name")
  if [[ "$name" == backend ]]; then
    bash server/run-local.sh >"$log" 2>&1 &
  else
    (cd client && pnpm -F @vben/web-antd run dev >"../$log" 2>&1) &
  fi
}

start_service() {
  local name=$1 label port listen_pid log timeout wrapper
  label=$(svc_label "$name")
  port=$(svc_port "$name")
  log=$(svc_log "$name")
  timeout=$(svc_timeout "$name")
  listen_pid=$(port_pid "$port")

  if [[ -n "$listen_pid" ]]; then
    if [[ $RESTART -eq 0 ]]; then
      echo "[$label] 端口 $port 已在监听，复用现有服务（如需重启请加 --restart）。"
      return 0
    fi
    if ! stop_service "$name"; then
      SKIPPED_SVCS="$SKIPPED_SVCS $name"
      return 0
    fi
  fi

  if [[ "$name" == backend && ! -f server/.env.local ]]; then
    echo "[$label] 缺少 server/.env.local，请先执行：cp server/.env.local.example server/.env.local 并填入凭证" >&2
    exit 1
  fi
  if [[ "$name" == frontend ]] && ! command -v pnpm >/dev/null 2>&1; then
    echo "[$label] 未找到 pnpm，请先安装并执行 client 目录下的 pnpm install" >&2
    exit 1
  fi

  echo "[$label] 启动中，日志：$log"
  svc_launch "$name"
  wrapper=$!
  STARTED_PIDS="$STARTED_PIDS $wrapper"
  STARTED_SVCS="$STARTED_SVCS $name"

  if ! wait_port_up "$port" "$timeout"; then
    echo "[$label] 启动超时，请查看 $log" >&2
    tail -30 "$log" >&2 || true
    exit 1
  fi
  port_pid "$port" >"$(svc_pidfile "$name")"
  echo "[$label] 就绪：http://localhost:$port"
}

cleanup() {
  trap - INT TERM EXIT
  [[ -z "$STARTED_SVCS" ]] && return 0
  echo ""
  echo "正在停止本次启动的服务..."
  for name in $STARTED_SVCS; do
    stop_service "$name" || true
  done
  echo "已停止。"
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
  if [[ -n "$SKIPPED_SVCS" ]]; then
    echo "跳过       :$SKIPPED_SVCS （端口被其他进程占用，请手动处理）"
  fi
  echo "========================="
}

mkdir -p "$LOG_DIR"
trap cleanup INT TERM

[[ $START_BACKEND -eq 1 ]] && start_service backend
[[ $START_FRONTEND -eq 1 ]] && start_service frontend
print_summary

if [[ -z "$STARTED_PIDS" ]]; then
  echo "无需启动新进程。"
  exit 0
fi

echo "按 Ctrl+C 停止本次启动的服务。"
wait
