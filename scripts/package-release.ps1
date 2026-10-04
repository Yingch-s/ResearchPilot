$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$parent = Split-Path $root -Parent
$name = Split-Path $root -Leaf
$archive = Join-Path $parent ($name + '.zip')
if (Test-Path $archive) { Remove-Item -LiteralPath $archive -Force }
Compress-Archive -Path (Join-Path $root '*') -DestinationPath $archive -CompressionLevel Optimal
$hash = Get-FileHash -Algorithm SHA256 $archive
$hash.Hash + '  ' + (Split-Path $archive -Leaf) | Set-Content -Encoding ascii ($archive + '.sha256')
Write-Host "Created $archive"
Write-Host "SHA-256 $($hash.Hash)"
