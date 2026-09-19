#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT_DIR/.env}"
HEALTH_URL="${HEALTH_URL:-http://127.0.0.1:8123/api/health}"
RELEASES_DIR="$ROOT_DIR/.deploy/releases"
BACKEND_JAR="$ROOT_DIR/backend/target/backend-0.0.1-SNAPSHOT.jar"
FRONTEND_DIST="$ROOT_DIR/frontend/dist/build/h5"

usage() {
  echo "用法: bash scripts/deploy.sh <check|deploy|health|rollback|logs> [release]"
  echo "  check               校验 Docker、环境变量和部署文件"
  echo "  deploy              测试、构建、部署，并在健康检查失败时自动回滚"
  echo "  health              检查容器状态和 HTTP 健康接口"
  echo "  rollback [release]  回滚到指定备份；省略时使用最近一次备份"
  echo "  logs                查看后端和 Nginx 最近日志"
}

compose() {
  docker compose --project-directory "$ROOT_DIR" --env-file "$ENV_FILE" "$@"
}

env_value() {
  sed -n "s/^$1=//p" "$ENV_FILE" | tail -n 1 | tr -d '\r'
}

require_env() {
  local name="$1" value
  value="$(env_value "$name")"
  if [[ -z "$value" || "$value" == *"你的"* || "$value" == *"Base64编码"* ]]; then
    echo "缺少有效环境变量: $name" >&2
    return 1
  fi
}

preflight() {
  local aes_key session_encryption_key
  command -v docker >/dev/null || { echo "未安装 Docker" >&2; return 1; }
  command -v curl >/dev/null || { echo "未安装 curl" >&2; return 1; }
  command -v java >/dev/null || { echo "未安装 Java 21" >&2; return 1; }
  command -v node >/dev/null || { echo "未安装 Node.js 20.19+" >&2; return 1; }
  [[ "$(java -XshowSettings:properties -version 2>&1 | awk -F'= ' '/java.specification.version/{print $2; exit}')" == "21" ]] || {
    echo "后端构建必须使用 Java 21" >&2
    return 1
  }
  node -e "const [a,b]=process.versions.node.split('.').map(Number); process.exit((a===20&&b>=19)||(a===22&&b>=12)||a>=24?0:1)" || {
    echo "前端构建必须使用 Node.js 20.19+、22.12+ 或 24+" >&2
    return 1
  }
  if ! command -v corepack >/dev/null && ! command -v pnpm >/dev/null; then
    echo "未安装 corepack 或 pnpm" >&2
    return 1
  fi
  docker compose version >/dev/null
  [[ -f "$ROOT_DIR/docker-compose.yml" ]] || { echo "缺少 docker-compose.yml" >&2; return 1; }
  [[ -f "$ENV_FILE" ]] || { echo "缺少 $ENV_FILE，请从 .env.example 复制并填写" >&2; return 1; }

  local required=(
    DB_PASSWORD WECHAT_APPID WECHAT_SECRET
    WECHAT_VIRTUAL_PAYMENT_OFFER_ID WECHAT_VIRTUAL_PAYMENT_APP_KEY
    WECHAT_MESSAGE_PUSH_TOKEN WECHAT_MESSAGE_PUSH_ENCODING_AES_KEY
    WECHAT_SESSION_KEY_ENCRYPTION_KEY
  )
  local name
  for name in "${required[@]}"; do require_env "$name"; done

  [[ "$(env_value WECHAT_VIRTUAL_PAYMENT_ENV)" == "0" ]] || {
    echo "个人小程序 iOS 支付必须设置 WECHAT_VIRTUAL_PAYMENT_ENV=0" >&2
    return 1
  }
  aes_key="$(env_value WECHAT_MESSAGE_PUSH_ENCODING_AES_KEY)"
  session_encryption_key="$(env_value WECHAT_SESSION_KEY_ENCRYPTION_KEY)"
  [[ "${#aes_key}" -eq 43 ]] || {
    echo "WECHAT_MESSAGE_PUSH_ENCODING_AES_KEY 必须是 43 位" >&2
    return 1
  }
  [[ "$session_encryption_key" =~ ^[A-Za-z0-9+/]{43}=$ ]] || {
    echo "WECHAT_SESSION_KEY_ENCRYPTION_KEY 必须是 Base64 编码的 32 字节密钥" >&2
    return 1
  }

  if [[ "$(env_value TENCENT_COS_ENABLED)" == "true" ]]; then
    require_env TENCENT_COS_SECRET_ID
    require_env TENCENT_COS_SECRET_KEY
    require_env TENCENT_COS_BUCKET
  fi
  echo "部署前检查通过"
}

snapshot_current() {
  local name="$1" target="$RELEASES_DIR/$name"
  mkdir -p "$target"
  [[ -f "$BACKEND_JAR" ]] && cp "$BACKEND_JAR" "$target/backend.jar"
  [[ -d "$FRONTEND_DIST" ]] && cp -a "$FRONTEND_DIST" "$target/h5"
  if [[ ! -f "$target/backend.jar" && ! -d "$target/h5" ]]; then
    rmdir "$target"
    return 1
  fi
  echo "$target"
}

restore_release() {
  local release="$1"
  [[ -d "$release" ]] || { echo "备份不存在: $release" >&2; return 1; }
  [[ -f "$release/backend.jar" ]] || { echo "备份缺少 backend.jar" >&2; return 1; }
  [[ -d "$release/h5" ]] || { echo "备份缺少 h5" >&2; return 1; }

  mkdir -p "$(dirname "$BACKEND_JAR")" "$(dirname "$FRONTEND_DIST")"
  cp "$release/backend.jar" "$BACKEND_JAR"
  if [[ -d "$FRONTEND_DIST" ]]; then
    mv "$FRONTEND_DIST" "$ROOT_DIR/.deploy/h5-before-restore-$(date +%Y%m%d%H%M%S)"
  fi
  cp -a "$release/h5" "$FRONTEND_DIST"
  compose up -d --build --remove-orphans
}

wait_for_health() {
  local attempts="${1:-30}" response
  for ((i = 1; i <= attempts; i++)); do
    if response="$(curl --fail --silent --show-error --max-time 5 "$HEALTH_URL" 2>/dev/null)"; then
      echo "健康检查通过: $response"
      return 0
    fi
    sleep 2
  done
  echo "健康检查失败: $HEALTH_URL" >&2
  compose ps >&2 || true
  return 1
}

run_pnpm() {
  if command -v corepack >/dev/null; then corepack pnpm "$@"; else pnpm "$@"; fi
}

deploy() {
  preflight
  local stamp backup=""
  stamp="$(date +%Y%m%d%H%M%S)"
  backup="$(snapshot_current "$stamp" || true)"

  echo "[1/4] 后端测试与构建"
  (cd "$ROOT_DIR/backend" && ./mvnw clean package)
  echo "[2/4] 前端依赖与 H5 构建"
  (cd "$ROOT_DIR/frontend" && run_pnpm install --frozen-lockfile && run_pnpm build:h5)
  echo "[3/4] 更新容器"
  compose up -d --build --remove-orphans
  echo "[4/4] 健康检查"
  if wait_for_health 45; then
    echo "部署完成；备份: ${backup:-首次部署无旧版本}"
    return 0
  fi

  if [[ -n "$backup" ]]; then
    echo "新版本不健康，自动回滚到 $backup" >&2
    restore_release "$backup"
    wait_for_health 45
  fi
  return 1
}

rollback() {
  preflight
  local release="${1:-}"
  if [[ -z "$release" ]]; then
    release="$( { find "$RELEASES_DIR" -mindepth 1 -maxdepth 1 -type d 2>/dev/null || true; } | sort | tail -n 1)"
  elif [[ "$release" != /* ]]; then
    release="$RELEASES_DIR/$release"
  fi
  [[ -n "$release" ]] || { echo "没有可回滚的备份" >&2; return 1; }
  snapshot_current "$(date +%Y%m%d%H%M%S)-pre-rollback" >/dev/null || true
  restore_release "$release"
  wait_for_health 45
  echo "已回滚到: $release"
}

case "${1:-}" in
  check) preflight ;;
  deploy) deploy ;;
  health) preflight; compose ps; wait_for_health 1 ;;
  rollback) rollback "${2:-}" ;;
  logs) preflight; compose logs --tail=200 backend nginx ;;
  *) usage; exit 2 ;;
esac
