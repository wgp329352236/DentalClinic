#!/usr/bin/env bash
# 本地启动脚本：读取 server/.env.local 并注入环境变量后启动后端。
# 仅用于本地联调；生产请在部署平台直接配置环境变量，不要拷贝本文件与 .env.local。
set -euo pipefail
cd "$(dirname "$0")"

if [[ ! -f .env.local ]]; then
  echo "缺少 server/.env.local，请先执行：cp .env.local.example .env.local 并填入真实凭证" >&2
  exit 1
fi

set -a
# shellcheck disable=SC1091
source .env.local
set +a

cd ..
exec ./gradlew :server:run
