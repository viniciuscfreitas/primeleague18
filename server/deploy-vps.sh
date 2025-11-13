#!/bin/bash
# ============================================
# Script de Deploy - VPS Hostinger KVM 2
# Servidor: Paper 1.8.8 - Full PvP
# Grug Brain: Deploy direto, sem Docker
# ============================================

set -e

echo "============================================"
echo "  Deploy Minecraft Server - VPS Hostinger"
echo "  Paper 1.8.8 - Production"
echo "============================================"
echo ""

# Verifica se esta rodando como root
if [ "$EUID" -ne 0 ]; then
    echo "[ERRO] Execute como root ou com sudo"
    exit 1
fi

# Variaveis
SERVER_USER="minecraft"
SERVER_DIR="/opt/minecraft"
JAVA_VERSION="8"

# Atualiza sistema
echo "[INFO] Atualizando sistema..."
apt-get update -qq
apt-get upgrade -y -qq

# Instala dependencias
echo "[INFO] Instalando dependencias..."
apt-get install -y -qq \
    openjdk-${JAVA_VERSION}-jdk \
    curl \
    wget \
    unzip \
    screen \
    htop \
    ufw

# Cria usuario do servidor
echo "[INFO] Criando usuario ${SERVER_USER}..."
if ! id "$SERVER_USER" &>/dev/null; then
    useradd -r -m -s /bin/bash -d /home/${SERVER_USER} ${SERVER_USER}
    echo "[INFO] Usuario ${SERVER_USER} criado"
else
    echo "[INFO] Usuario ${SERVER_USER} ja existe"
fi

# Cria diretorio do servidor
echo "[INFO] Criando diretorio ${SERVER_DIR}..."
mkdir -p ${SERVER_DIR}
chown ${SERVER_USER}:${SERVER_USER} ${SERVER_DIR}

# Configura firewall
echo "[INFO] Configurando firewall..."
ufw --force enableimage.png
ufw allow 22/tcp    # SSH
ufw allow 25565/tcp # Minecraft
ufw allow 25565/udp # Minecraft
echo "[INFO] Firewall configurado"

# Otimizacoes do sistema para Minecraft
echo "[INFO] Aplicando otimizacoes do sistema..."

# Aumenta limites de arquivos abertos
cat >> /etc/security/limits.conf << EOF
${SERVER_USER} soft nofile 65536
${SERVER_USER} hard nofile 65536
EOF

# Otimiza kernel para servidor de jogos
cat >> /etc/sysctl.conf << EOF
# Otimizacoes para Minecraft Server
net.core.rmem_max = 16777216
net.core.wmem_max = 16777216
net.ipv4.tcp_rmem = 4096 87380 16777216
net.ipv4.tcp_wmem = 4096 65536 16777216
net.ipv4.tcp_congestion_control = bbr
net.ipv4.tcp_fastopen = 3
vm.swappiness = 10
vm.max_map_count = 262144
EOF

sysctl -p > /dev/null

echo "[INFO] Otimizacoes aplicadas"
echo ""
echo "[INFO] Deploy base concluido!"
echo "[INFO] Proximo passo: Copie os arquivos do servidor para ${SERVER_DIR}"
echo "[INFO] Use: scp -r server/* ${SERVER_USER}@IP_DA_VPS:${SERVER_DIR}/"

