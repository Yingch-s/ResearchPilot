$ErrorActionPreference = 'Stop'

Write-Host 'Checking release layout...'
$required = @(
  'compose.yaml', '.env.example', 'README.md',
  'backend/pom.xml', 'backend/Dockerfile.release',
  'frontend/package.json', 'frontend/Dockerfile',
  'sidecar/pyproject.toml', 'sidecar/Dockerfile',
  'backend/src/main/resources/model-config.json'
)
foreach ($path in $required) {
  if (-not (Test-Path (Join-Path (Join-Path $PSScriptRoot '..') $path))) {
    throw "Missing release file: $path"
  }
}

if (Test-Path (Join-Path (Join-Path $PSScriptRoot '..') '.env')) {
  throw '.env must not be included in a release directory'
}

docker compose -f (Join-Path (Join-Path $PSScriptRoot '..') 'compose.yaml') config --quiet
Write-Host 'Release layout and Compose configuration are valid.'
