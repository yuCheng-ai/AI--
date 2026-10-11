#!/usr/bin/env bash
set +x
set -euo pipefail
umask 077

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
ENV_FILE="$ROOT_DIR/.env"

if [[ -e "$ENV_FILE" ]]; then
  chmod 600 "$ENV_FILE"
  printf '保留已有 .env，未覆盖配置或密码。\n'
  exit 0
fi

command -v openssl >/dev/null 2>&1 || { printf '需要 openssl 生成随机密码。\n' >&2; exit 1; }
# The generated file is compatible with both bash source and Compose dotenv.
case "$ROOT_DIR" in
  *"'"*|*$'\n'*|*$'\r'*) printf '项目路径不能包含单引号或换行，以保持 .env 与 Compose 兼容。\n' >&2; exit 1 ;;
esac

mkdir -p "$ROOT_DIR/.local/data/media" "$ROOT_DIR/.local/logs" "$ROOT_DIR/.local/run"
TEMP_FILE="$(mktemp "$ROOT_DIR/.env.tmp.XXXXXX")"
trap 'rm -f "$TEMP_FILE"' EXIT

{
  printf '# Generated locally. Do not commit or share this file.\n'
  printf "DRAMA_DB_PASSWORD='%s'\n" "$(openssl rand -hex 24)"
  printf "DRAMA_DB_APP_PASSWORD='%s'\n" "$(openssl rand -hex 24)"
  printf "DRAMA_BOOTSTRAP_USERNAME='admin'\n"
  printf "DRAMA_BOOTSTRAP_PASSWORD='%s'\n" "$(openssl rand -hex 24)"
  printf "DRAMA_DB_URL='jdbc:postgresql://127.0.0.1:15432/drama'\n"
  printf "DRAMA_HTTP_PORT='18080'\n"
  printf "DRAMA_STORAGE_ROOT='%s/.local/data/media'\n" "$ROOT_DIR"
  printf "DRAMA_UID='%s'\n" "$(id -u)"
  printf "DRAMA_GID='%s'\n" "$(id -g)"
} > "$TEMP_FILE"

# A hard link publishes the fully written file without overwriting a concurrent init.
if ! ln "$TEMP_FILE" "$ENV_FILE" 2>/dev/null; then
  printf '.env 已由其他进程创建，未覆盖。\n'
  exit 0
fi
chmod 600 "$ENV_FILE"
printf '已生成 .env（权限 600）、独立随机密码和本地数据目录。\n'
printf '启动命令：./scripts/backend.sh up\n'
