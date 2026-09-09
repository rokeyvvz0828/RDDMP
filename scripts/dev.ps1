$scriptPath = Join-Path $PSScriptRoot 'dev.mjs'
& node $scriptPath @args
exit $LASTEXITCODE
