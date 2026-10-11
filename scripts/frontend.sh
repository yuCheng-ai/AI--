#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
command -v pnpm >/dev/null 2>&1 || { printf '请先安装 pnpm 10.28.1。\n' >&2; exit 1; }
cd "$PROJECT_ROOT/frontend"
case "${1:-dev}" in
  install) exec pnpm install --frozen-lockfile ;;
  dev|build|preview|typecheck|format:check) exec pnpm "${1:-dev}" ;;
  *) printf '用法：./scripts/frontend.sh [install|dev|build|preview|typecheck|format:check]\n' >&2; exit 2 ;;
esac
