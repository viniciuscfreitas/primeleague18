# ============================================
# Script de Upload - Windows PowerShell
# Upload servidor Minecraft para VPS
# Grug Brain: Simples, direto
# ============================================

param(
    [Parameter(Mandatory=$true)]
    [string]$VpsIp,

    [Parameter(Mandatory=$false)]
    [string]$VpsUser = "minecraft",

    [Parameter(Mandatory=$false)]
    [string]$ServerPath = ".\server"
)

Write-Host "============================================" -ForegroundColor Green
Write-Host "  Upload Minecraft Server para VPS" -ForegroundColor Green
Write-Host "  Paper 1.8.8 - Production" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Green
Write-Host ""

# Verifica se scp esta disponivel (OpenSSH no Windows)
if (-not (Get-Command scp -ErrorAction SilentlyContinue)) {
    Write-Host "[ERRO] SCP nao encontrado!" -ForegroundColor Red
    Write-Host "[INFO] Instale OpenSSH: Add-WindowsCapability -Online -Name OpenSSH.Client~~~~0.0.1.0" -ForegroundColor Yellow
    exit 1
}

# Verifica se diretorio existe
if (-not (Test-Path $ServerPath)) {
    Write-Host "[ERRO] Diretorio $ServerPath nao encontrado!" -ForegroundColor Red
    exit 1
}

Write-Host "[INFO] Compactando servidor..." -ForegroundColor Yellow
$ZipFile = "minecraft-server-$(Get-Date -Format 'yyyyMMdd-HHmmss').zip"

# Compacta servidor (exclui logs e cache)
$Exclude = @("logs\*", "cache\*", "world\*.mca", "world\*.mcr", "world_nether\*", "world_the_end\*")
Compress-Archive -Path "$ServerPath\*" -DestinationPath $ZipFile -Force

Write-Host "[INFO] Arquivo criado: $ZipFile" -ForegroundColor Green
Write-Host "[INFO] Tamanho: $([math]::Round((Get-Item $ZipFile).Length / 1MB, 2)) MB" -ForegroundColor Green
Write-Host ""

Write-Host "[INFO] Enviando para VPS..." -ForegroundColor Yellow
Write-Host "[INFO] Destino: ${VpsUser}@${VpsIp}:/home/${VpsUser}/" -ForegroundColor Cyan
Write-Host ""

# Upload para VPS
scp $ZipFile "${VpsUser}@${VpsIp}:/home/${VpsUser}/"

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "[INFO] Upload concluido!" -ForegroundColor Green
    Write-Host "[INFO] Proximo passo na VPS:" -ForegroundColor Yellow
    Write-Host "  ssh ${VpsUser}@${VpsIp}" -ForegroundColor Cyan
    Write-Host "  sudo unzip /home/${VpsUser}/$ZipFile -d /opt/minecraft/" -ForegroundColor Cyan
    Write-Host "  sudo chown -R minecraft:minecraft /opt/minecraft" -ForegroundColor Cyan
} else {
    Write-Host ""
    Write-Host "[ERRO] Falha no upload!" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "[INFO] Removendo arquivo local..." -ForegroundColor Yellow
Remove-Item $ZipFile -Force

Write-Host "[INFO] Concluido!" -ForegroundColor Green

