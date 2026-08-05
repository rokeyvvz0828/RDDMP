[CmdletBinding()]
param(
    [string]$ContainerName = 'ccb-platform-mysql',
    [string]$Database = 'ccb_platform',
    [string]$User = 'ccb',
    [string]$Password = $env:DB_PASSWORD,
    [string]$OutputFile = (Join-Path $PSScriptRoot 'ccb_platform_schema.sql')
)

if ([string]::IsNullOrWhiteSpace($Password)) {
    throw '请先设置 DB_PASSWORD 环境变量，或通过 -Password 传入数据库密码。'
}

$dump = docker exec $ContainerName mysqldump "-u$User" "-p$Password" `
    --no-data --no-tablespaces --skip-triggers --routines=false $Database

if ($LASTEXITCODE -ne 0) {
    throw "从容器 $ContainerName 导出数据库结构失败。"
}

$header = @(
    '-- CCB Platform 数据库结构快照。',
    '-- 仅包含表、字段、索引、约束和注释，不包含业务数据。',
    '-- 重新生成：$env:DB_PASSWORD = ''<数据库密码>''; .\export-schema.ps1',
    ''
)

($header + $dump) | Set-Content -LiteralPath $OutputFile -Encoding utf8
Write-Output "已生成数据库结构快照：$OutputFile"
