#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# 一键部署服务器 PostgreSQL：安装 → 启动 → 建角色/建库 → 导入 SQL → 校验
#
# 适用：Debian/Ubuntu、RHEL/CentOS/Rocky/AlmaLinux
# 用法：sudo ./deploy-db.sh --dump dental_clinic_20261124_full.sql
#       sudo ./deploy-db.sh                 # 只建库不导数据
# 说明：以 root 执行；默认仅监听 127.0.0.1，不向公网暴露数据库端口。
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

DB_NAME="${DB_NAME:-dental_clinic}"
DB_USER="${DB_USER:-dental_clinic}"
DUMP_FILE=""
FORCE=false
SKIP_INSTALL=false

log()  { printf '[deploy-db] %s\n' "$*"; }
warn() { printf '[deploy-db][WARN] %s\n' "$*" >&2; }
die()  { printf '[deploy-db][ERROR] %s\n' "$*" >&2; exit 1; }

usage() {
  cat <<'EOF'
用法: sudo ./deploy-db.sh [选项]

  --dump <file>   建库后导入的 SQL 文件（full dump）
  --force         库中已有表时仍然导入（可能主键冲突，先备份）
  --no-install    跳过 PostgreSQL 安装，仅建库并导入
  -h, --help      显示帮助

环境变量:
  DB_NAME= dental_clinic    数据库名
  DB_USER= dental_clinic    数据库账号
  DB_PASSWORD               账号密码；未设置时交互输入
EOF
}

parse_args() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --dump)       DUMP_FILE="${2:-}"; shift 2 ;;
      --force)      FORCE=true; shift ;;
      --no-install) SKIP_INSTALL=true; shift ;;
      -h|--help)    usage; exit 0 ;;
      *)            die "未知参数: $1" ;;
    esac
  done
}

require_root() {
  [[ "$(id -u)" -eq 0 ]] || die "请使用 root 或 sudo 执行"
}

read_password() {
  if [[ -z "${DB_PASSWORD:-}" ]]; then
    [[ -t 0 ]] || die "非交互环境请通过环境变量 DB_PASSWORD 传入密码"
    read -r -s -p "请输入 ${DB_USER} 的数据库密码: " DB_PASSWORD
    echo
  fi
  [[ -n "$DB_PASSWORD" ]] || die "密码不能为空"
}

install_postgres() {
  if command -v psql >/dev/null 2>&1; then
    log "已安装 PostgreSQL: $(psql --version)"
    return
  fi
  log "安装 PostgreSQL ..."
  if command -v apt-get >/dev/null 2>&1; then
    export DEBIAN_FRONTEND=noninteractive
    apt-get update -qq
    apt-get install -y -qq postgresql postgresql-contrib
  elif command -v dnf >/dev/null 2>&1; then
    dnf install -y -q postgresql-server postgresql-contrib
    [[ -d /var/lib/pgsql/data ]] || postgresql-setup --initdb
  elif command -v yum >/dev/null 2>&1; then
    yum install -y -q postgresql-server postgresql-contrib
    [[ -d /var/lib/pgsql/data ]] || postgresql-setup --initdb
  else
    die "未识别的包管理器，请手动安装 PostgreSQL（>= 14）"
  fi
}

start_postgres() {
  local unit
  unit=$(systemctl list-unit-files 'postgresql*.service' --no-legend 2>/dev/null \
    | awk '{print $1}' | grep -v '@' | head -1) || true
  [[ -n "$unit" ]] || unit=postgresql
  systemctl enable --now "$unit" >/dev/null 2>&1 || true
  for _ in $(seq 1 20); do
    pg_isready -q && return 0
    sleep 1
  done
  die "PostgreSQL 启动失败，请查看 journalctl -u ${unit}"
}

sql_escape() { printf '%s' "${1//\'/\'\'}"; }

create_role_and_db() {
  local esc_pw
  esc_pw=$(sql_escape "$DB_PASSWORD")
  log "创建/更新角色 ${DB_USER} ..."
  sudo -u postgres psql -v ON_ERROR_STOP=1 -q <<SQL
DO \$\$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${DB_USER}') THEN
    ALTER ROLE ${DB_USER} WITH LOGIN PASSWORD '${esc_pw}';
  ELSE
    CREATE ROLE ${DB_USER} WITH LOGIN PASSWORD '${esc_pw}';
  END IF;
END
\$\$;
SQL

  if sudo -u postgres psql -tAc "SELECT 1 FROM pg_database WHERE datname='${DB_NAME}'" | grep -q 1; then
    log "数据库 ${DB_NAME} 已存在，跳过创建"
  else
    log "创建数据库 ${DB_NAME}（owner=${DB_USER}）..."
    sudo -u postgres createdb -O "$DB_USER" "$DB_NAME"
  fi
}

import_dump() {
  [[ -n "$DUMP_FILE" ]] || { log "未指定 --dump，跳过数据导入"; return; }
  [[ -f "$DUMP_FILE" ]] || die "找不到 SQL 文件: $DUMP_FILE"

  local tables
  tables=$(sudo -u postgres psql -tA -d "$DB_NAME" \
    -c "SELECT count(*) FROM information_schema.tables WHERE table_schema='public'")
  if [[ "$FORCE" == true ]]; then
    warn "FORCE 模式：将清空 ${DB_NAME} 的 public schema 后重新导入"
    sudo -u postgres psql -q -v ON_ERROR_STOP=1 -d "$DB_NAME" -c \
      "DROP SCHEMA public CASCADE; CREATE SCHEMA public; ALTER SCHEMA public OWNER TO ${DB_USER};"
  elif [[ "$tables" -gt 0 ]]; then
    die "库中已有 ${tables} 张表，已跳过导入；确认覆盖请加 --force（建议先 pg_dump 备份）"
  fi

  log "导入 $DUMP_FILE（以 ${DB_USER} 身份，表归其所有）..."
  PGPASSWORD="$DB_PASSWORD" psql -h 127.0.0.1 -U "$DB_USER" -d "$DB_NAME" \
    -v ON_ERROR_STOP=1 -q -f "$DUMP_FILE"
}

verify() {
  local tables version
  tables=$(sudo -u postgres psql -tA -d "$DB_NAME" \
    -c "SELECT count(*) FROM information_schema.tables WHERE table_schema='public'")
  version=$(sudo -u postgres psql -tA -c 'SHOW server_version')
  log "校验通过：PostgreSQL ${version}，public schema 共 ${tables} 张表"
  printf '\n'
  log "连接信息（仅本机监听，公网不可直连）"
  printf '  数据库: %s\n  账号:   %s\n  本地:   psql -h 127.0.0.1 -U %s -d %s\n' \
    "$DB_NAME" "$DB_USER" "$DB_USER" "$DB_NAME"
  printf '\n远程调试请用 SSH 隧道：\n'
  printf '  ssh -N -L 5433:127.0.0.1:5432 root@<服务器IP>\n'
  printf '  客户端连接 localhost:5433（库名 %s）\n' "$DB_NAME"
}

main() {
  parse_args "$@"
  require_root
  [[ "$DB_NAME" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || die "DB_NAME 仅允许字母/数字/下划线: $DB_NAME"
  [[ "$DB_USER" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || die "DB_USER 仅允许字母/数字/下划线: $DB_USER"
  read_password
  [[ "$SKIP_INSTALL" == true ]] || install_postgres
  start_postgres
  local pg_ver
  pg_ver=$(psql --version | grep -oE '[0-9]+' | head -1)
  [[ "$pg_ver" -ge 14 ]] || warn "当前 PostgreSQL ${pg_ver} 低于 14，导入可能失败"
  create_role_and_db
  import_dump
  verify
}

main "$@"
