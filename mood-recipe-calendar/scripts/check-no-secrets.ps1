$ErrorActionPreference = 'Stop'

$repo = Split-Path -Parent $PSScriptRoot
$files = git -C $repo ls-files --cached --others --exclude-standard |
    Where-Object { $_ -notmatch '(^|/)(target|dist|unpackage|node_modules)/' } |
    ForEach-Object { Get-Item -LiteralPath (Join-Path $repo $_) }

$patterns = @(
    '(?i)(WECHAT_SECRET|DB_PASSWORD|TENCENT_COS_SECRET_KEY|AGNES_API_KEY|WECHAT_VIRTUAL_PAYMENT_APP_KEY)\s*[=:]\s*["'']?(?!\$\{|你的|$)[A-Za-z0-9_\-]{8,}',
    '(?i)x-session-token\s*[=:]\s*["'']?[A-Za-z0-9_\-]{16,}'
)

$hits = foreach ($file in $files) {
    Select-String -LiteralPath $file.FullName -Pattern $patterns -AllMatches -ErrorAction SilentlyContinue |
        ForEach-Object { "{0}:{1}" -f $_.Path, $_.LineNumber }
}

if ($hits) {
    $hits | Sort-Object -Unique | ForEach-Object { Write-Error "疑似凭据: $_" }
    exit 1
}

Write-Host '凭据扫描通过'
