# ============================================
# Script de Deploy - rsync (Eficiente)
# Sincroniza apenas arquivos alterados
# Grug Brain: Simples, direto, eficiente
# ============================================

param(
    [Parameter(Mandatory=$false)]
    [string]$VpsUser = "primeleague",

    [Parameter(Mandatory=$false)]
    [string]$VpsHost = "primeleague.com.br",

    [Parameter(Mandatory=$false)]
    [string]$ServerPath = ".\server",

    [Parameter(Mandatory=$false)]
    [string]$RemotePath = "~/services/server"
)

Write-Host "============================================" -ForegroundColor Green
Write-Host "  Deploy Minecraft Server - rsync" -ForegroundColor Green
Write-Host "  Paper 1.8.8 - Production" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Green
Write-Host ""

# Verifica se rsync esta disponivel
if (-not (Get-Command rsync -ErrorAction SilentlyContinue)) {
    Write-Host "[ERRO] rsync nao encontrado!" -ForegroundColor Red
    Write-Host "[INFO] Instale: winget install rsync" -ForegroundColor Yellow
    Write-Host "[INFO] Ou use: choco install rsync" -ForegroundColor Yellow
    exit 1
}

# Verifica se diretorio existe
if (-not (Test-Path $ServerPath)) {
    Write-Host "[ERRO] Diretorio $ServerPath nao encontrado!" -ForegroundColor Red
    exit 1
}

Write-Host "[INFO] Sincronizando arquivos..." -ForegroundColor Yellow
Write-Host "[INFO] Origem: $ServerPath" -ForegroundColor Cyan
Write-Host "[INFO] Destino: ${VpsUser}@${VpsHost}:${RemotePath}" -ForegroundColor Cyan
Write-Host ""

# Arquivos/pastas a excluir (nao sincronizar)
$Exclude = @(
    "logs",
    "cache",
    "world",
    "world_nether",
    "world_the_end",
    "*.log",
    "*.zip",
    ".git"
)

# Monta comando rsync
$ExcludeArgs = $Exclude | ForEach-Object { "--exclude=$_" }
$RsyncArgs = @(
    "-avz",
    "--delete",
    "--progress"
) + $ExcludeArgs + @(
    "$ServerPath/",
    "${VpsUser}@${VpsHost}:${RemotePath}/"
)

# Executa rsync
Write-Host "[INFO] Executando rsync..." -ForegroundColor Yellow
rsync $RsyncArgs

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "[INFO] Deploy concluido!" -ForegroundColor Green
    Write-Host "[INFO] Arquivos sincronizados com sucesso" -ForegroundColor Green
    Write-Host ""
    Write-Host "[INFO] Proximo passo na VPS:" -ForegroundColor Yellow
    Write-Host "  ssh ${VpsUser}@${VpsHost}" -ForegroundColor Cyan
    Write-Host "  cd ${RemotePath}" -ForegroundColor Cyan
    Write-Host "  # Reiniciar servidor se necessario:" -ForegroundColor Cyan
    Write-Host "  sudo systemctl restart minecraft.service" -ForegroundColor Cyan
} else {
    Write-Host ""
    Write-Host "[ERRO] Falha no deploy!" -ForegroundColor Red
    exit 1
}

