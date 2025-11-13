#!/bin/bash
# ============================================
# Script de Deploy - Git (Versionado)
# Pull direto do GitHub na VPS
# Grug Brain: Simples, versionado
# ============================================

set -e

# Configuracoes
GIT_REPO="https://github.com/SEU_USUARIO/SEU_REPO.git"
SERVER_DIR="~/services/server"
BRANCH="main"

echo "============================================"
echo "  Deploy Minecraft Server - Git"
echo "  Paper 1.8.8 - Production"
echo "============================================"
echo ""

cd $SERVER_DIR

# Verifica se e repositorio git
if [ ! -d ".git" ]; then
    echo "[INFO] Inicializando repositorio git..."
    git init
    git remote add origin $GIT_REPO
    git fetch origin
    git checkout -b $BRANCH origin/$BRANCH
else
    echo "[INFO] Atualizando do repositorio..."
    git fetch origin
    git reset --hard origin/$BRANCH
fi

echo ""
echo "[INFO] Deploy concluido!"
echo "[INFO] Reiniciando servidor..."
sudo systemctl restart minecraft.service

echo "[INFO] Status do servidor:"
sudo systemctl status minecraft.service --no-pager

