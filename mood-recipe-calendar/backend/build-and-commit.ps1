# 后端构建并提交 jar 到 git
# 用法：
#   .\build-and-commit.ps1           # 仅构建并提交 jar
#   .\build-and-commit.ps1 -Push     # 构建、提交并推送到远程
#
# 说明：
#   - env 配置（DB / 微信 / Agnes 等密钥）由 jar 在【运行时】通过环境变量或 backend/.env 读取，
#     不会写入 jar，因此提交 jar 不会泄露密钥。
#   - 构建产物中仅 target/*.jar 会被 git 追踪（见 backend/.gitignore）。
param(
  [switch]$Push
)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$jar = 'target/backend-0.0.1-SNAPSHOT.jar'

Write-Host '[1/3] 使用 Maven 构建 jar（env 配置为运行时注入，不写入 jar）...'
& .\mvnw.cmd package -DskipTests
if ($LASTEXITCODE -ne 0) {
  throw 'Maven 构建失败'
}

Write-Host '[2/3] 提交 jar 到 git...'
git add $jar
$date = Get-Date -Format 'yyyy-MM-dd'
git commit -m "build(backend): 更新后端 jar 包 ($date)"
if ($LASTEXITCODE -ne 0) {
  Write-Host '无变更，跳过提交'
}

if ($Push) {
  Write-Host '[3/3] 推送到远程...'
  git push
}

Write-Host '完成。'
