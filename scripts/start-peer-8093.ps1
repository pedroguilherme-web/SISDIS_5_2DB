
# Inicia o Peer 2 na porta 8093

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot

$env:SERVER_PORT = "8093"
$env:P2P_PEER_ID = "peer-2"
$env:P2P_PEERS = "peer-1=http://localhost:8092"

# Credenciais e chave fornecidas pelo ambiente
$required = @(
    "P2P_JWT_SECRET",
    "APP_DEV_ADMIN_USERNAME",
    "APP_DEV_ADMIN_PASSWORD"
)

foreach ($name in $required) {
    if (-not [Environment]::GetEnvironmentVariable($name)) {
        throw "Falta definir a variavel de ambiente: $name"
    }
}

$env:APP_DEV_ADMIN_ENABLED = "true"

Push-Location (Join-Path $projectRoot "airports-routes-service")
try {
    & (Join-Path $projectRoot "mvnw.cmd") spring-boot:run
}
finally {
    Pop-Location
}
