#!/bin/bash
# ============================================
# Script de inicializacao - Production Ready
# Servidor: Paper 1.8.8 - Full PvP
# Grug Brain: Simples, direto, sem abstracoes
# ============================================

# Muda para o diretorio do script
cd "$(dirname "$0")"

# Verifica Java 8
if ! command -v java &> /dev/null; then
    echo "[ERRO] Java nao encontrado no PATH!"
    echo "[INFO] Instale Java 8 ou configure JAVA_HOME"
    exit 1
fi

# Verifica versao Java (deve ser 8)
JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | sed '/^1\./!d;s/^1\.\([0-9]*\).*/\1/;s/[^0-9]//g')
if [ -z "$JAVA_VERSION" ] || [ "$JAVA_VERSION" -lt 8 ] || [ "$JAVA_VERSION" -ge 9 ]; then
    echo "[ERRO] Java 8 e necessario! Versao encontrada: $(java -version 2>&1 | head -n 1)"
    exit 1
fi

# Verifica se o JAR existe
if [ ! -f "paper-1.8.8-445.jar" ]; then
    echo "[ERRO] Arquivo paper-1.8.8-445.jar nao encontrado!"
    exit 1
fi

# Configuracoes de memoria (ajuste conforme RAM disponivel)
# Grug Brain: Valores inline, sem config externa
# VPS KVM 2: 8GB RAM total, usar 6GB para servidor (deixar 2GB para sistema)
MIN_MEM="6G"
MAX_MEM="6G"

# Flags JVM otimizadas para producao (Java 8)
# Grug Brain: Flags inline, sem abstracoes
JVM_FLAGS="-Xms${MIN_MEM} -Xmx${MAX_MEM}"
JVM_FLAGS="${JVM_FLAGS} -XX:+UseG1GC"
JVM_FLAGS="${JVM_FLAGS} -XX:+ParallelRefProcEnabled"
JVM_FLAGS="${JVM_FLAGS} -XX:MaxGCPauseMillis=200"
JVM_FLAGS="${JVM_FLAGS} -XX:+UnlockExperimentalVMOptions"
JVM_FLAGS="${JVM_FLAGS} -XX:+DisableExplicitGC"
JVM_FLAGS="${JVM_FLAGS} -XX:+AlwaysPreTouch"
JVM_FLAGS="${JVM_FLAGS} -XX:G1NewSizePercent=30"
JVM_FLAGS="${JVM_FLAGS} -XX:G1MaxNewSizePercent=40"
JVM_FLAGS="${JVM_FLAGS} -XX:G1HeapRegionSize=8M"
JVM_FLAGS="${JVM_FLAGS} -XX:G1ReservePercent=20"
JVM_FLAGS="${JVM_FLAGS} -XX:G1HeapWastePercent=5"
JVM_FLAGS="${JVM_FLAGS} -XX:G1MixedGCCountTarget=4"
JVM_FLAGS="${JVM_FLAGS} -XX:InitiatingHeapOccupancyPercent=15"
JVM_FLAGS="${JVM_FLAGS} -XX:G1MixedGCLiveThresholdPercent=90"
JVM_FLAGS="${JVM_FLAGS} -XX:G1RSetUpdatingPauseTimePercent=5"
JVM_FLAGS="${JVM_FLAGS} -XX:SurvivorRatio=32"
JVM_FLAGS="${JVM_FLAGS} -XX:+PerfDisableSharedMem"
JVM_FLAGS="${JVM_FLAGS} -XX:MaxTenuringThreshold=1"
JVM_FLAGS="${JVM_FLAGS} -Dusing.aikars.flags=https://mcflags.emc.gs"
JVM_FLAGS="${JVM_FLAGS} -Daikars.new.flags=true"

# Flags para producao (sem debugging)
JVM_FLAGS="${JVM_FLAGS} -Dfile.encoding=UTF-8"
JVM_FLAGS="${JVM_FLAGS} -Djava.awt.headless=true"

# Logs GC para producao (Java 8)
# Grug Brain: Logs inline, sem config externa
if [ ! -d "logs" ]; then
    mkdir -p logs
fi
JVM_FLAGS="${JVM_FLAGS} -Xloggc:logs/gc-$(date +%Y%m%d-%H%M%S).log"
JVM_FLAGS="${JVM_FLAGS} -XX:+PrintGCDetails"
JVM_FLAGS="${JVM_FLAGS} -XX:+PrintGCDateStamps"
JVM_FLAGS="${JVM_FLAGS} -XX:+UseGCLogFileRotation"
JVM_FLAGS="${JVM_FLAGS} -XX:NumberOfGCLogFiles=5"
JVM_FLAGS="${JVM_FLAGS} -XX:GCLogFileSize=10M"

# Banner
echo "============================================"
echo "  Minecraft Server - Production Full PvP"
echo "  Paper 1.8.8 - Production Mode"
echo "============================================"
echo ""
echo "[INFO] Java: $(java -version 2>&1 | head -n 1)"
echo "[INFO] Memoria: ${MIN_MEM} inicial, ${MAX_MEM} maximo"
echo "[INFO] Iniciando servidor..."
echo ""

# Inicia o servidor
# Grug Brain: Execucao direta, sem abstracoes
exec java ${JVM_FLAGS} -jar paper-1.8.8-445.jar nogui
