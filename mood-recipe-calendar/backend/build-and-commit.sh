#!/usr/bin/env bash
# 后端构建并提交 jar 到 git
# 用法：
#   ./build-and-commit.sh            # 仅构建并提交 jar
#   ./build-and-commit.sh --push     # 构建、提交并推送到远程
#
# 说明：
#   - env 配置（DB / 微信 / Agnes 等密钥）由 jar 在【运行时】通过环境变量或 backend/.env 读取，
#     不会写入 jar，因此提交 jar 不会泄露密钥。
#   - 构建产物中仅 target/*.jar 会被 git 追踪（见 backend/.gitignore）。
set -euo pipefail
cd "$(dirname "$0")"

JAR="target/backend-0.0.1-SNAPSHOT.jar"
PUSH=0
[[ "${1:-}" == "--push" ]] && PUSH=1

echo "[1/3] 使用 Maven 构建 jar（env 配置为运行时注入，不写入 jar）..."
./mvnw package -DskipTests

echo "[2/3] 提交 jar 到 git..."
git add "$JAR"
if git diff --cached --quiet; then
  echo "无变更，跳过提交"
else
  git commit -m "build(backend): 更新后端 jar 包 ($(date +%Y-%m-%d))"
fi

if [[ "$PUSH" == "1" ]]; then
  echo "[3/3] 推送到远程..."
  git push
fi

echo "完成。"
