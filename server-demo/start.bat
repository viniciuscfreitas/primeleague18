@echo off
setlocal enabledelayedexpansion
title Minecraft Server - Development Full PvP
color 0A

REM ============================================
REM Script de inicializacao otimizado para desenvolvimento
REM Servidor: Paper 1.8.8 - Full PvP
REM ============================================

REM Muda para o diretorio do script
cd /d "%~dp0"

REM Carrega configuracao do JDK 8 (formato simples: arquivo texto com caminho)
set JAVA_CMD=
if exist "jdk8.txt" (
    for /f "delims=" %%a in (jdk8.txt) do set JAVA_CMD=%%a
    if exist "!JAVA_CMD!" (
        echo [INFO] Usando JDK 8: !JAVA_CMD!
    ) else (
        echo [ERRO] Java nao encontrado em: !JAVA_CMD!
        pause
        exit /b 1
    )
) else (
    echo [ERRO] Arquivo jdk8.txt nao encontrado!
    echo [INFO] Execute setup-jdk8.bat para configurar o JDK 8.
    pause
    exit /b 1
)
endlocal & set JAVA_CMD=%JAVA_CMD%

REM Verifica se o arquivo JAR existe
if not exist "paper-1.8.8-445.jar" (
    echo [ERRO] Arquivo paper-1.8.8-445.jar nao encontrado!
    pause
    exit /b 1
)

echo ============================================
echo   Minecraft Server - Development Full PvP
echo   Paper 1.8.8 - Modo Desenvolvimento
echo ============================================
echo.

REM Configuracoes de memoria (ajuste conforme sua RAM disponivel)
REM Para desenvolvimento: 2GB inicial, 4GB maximo
set MIN_MEM=2G
set MAX_MEM=4G

REM Flags JVM otimizadas para desenvolvimento
REM Compativel com Java 8 (necessario para Minecraft 1.8.8)
set JVM_FLAGS=-Xms%MIN_MEM% -Xmx%MAX_MEM%
set JVM_FLAGS=%JVM_FLAGS% -XX:+UseG1GC
set JVM_FLAGS=%JVM_FLAGS% -XX:+ParallelRefProcEnabled
set JVM_FLAGS=%JVM_FLAGS% -XX:MaxGCPauseMillis=200
set JVM_FLAGS=%JVM_FLAGS% -XX:+UnlockExperimentalVMOptions
set JVM_FLAGS=%JVM_FLAGS% -XX:+DisableExplicitGC
set JVM_FLAGS=%JVM_FLAGS% -XX:+AlwaysPreTouch
set JVM_FLAGS=%JVM_FLAGS% -XX:G1NewSizePercent=30
set JVM_FLAGS=%JVM_FLAGS% -XX:G1MaxNewSizePercent=40
set JVM_FLAGS=%JVM_FLAGS% -XX:G1HeapRegionSize=8M
set JVM_FLAGS=%JVM_FLAGS% -XX:G1ReservePercent=20
set JVM_FLAGS=%JVM_FLAGS% -XX:G1HeapWastePercent=5
set JVM_FLAGS=%JVM_FLAGS% -XX:G1MixedGCCountTarget=4
set JVM_FLAGS=%JVM_FLAGS% -XX:InitiatingHeapOccupancyPercent=15
set JVM_FLAGS=%JVM_FLAGS% -XX:G1MixedGCLiveThresholdPercent=90
set JVM_FLAGS=%JVM_FLAGS% -XX:G1RSetUpdatingPauseTimePercent=5
set JVM_FLAGS=%JVM_FLAGS% -XX:SurvivorRatio=32
set JVM_FLAGS=%JVM_FLAGS% -XX:+PerfDisableSharedMem
set JVM_FLAGS=%JVM_FLAGS% -XX:MaxTenuringThreshold=1
set JVM_FLAGS=%JVM_FLAGS% -Dusing.aikars.flags=https://mcflags.emc.gs
set JVM_FLAGS=%JVM_FLAGS% -Daikars.new.flags=true

REM Flags para desenvolvimento e debugging
set JVM_FLAGS=%JVM_FLAGS% -Dfile.encoding=UTF-8
set JVM_FLAGS=%JVM_FLAGS% -Djava.awt.headless=true
set JVM_FLAGS=%JVM_FLAGS% -Dcom.sun.management.jmxremote
set JVM_FLAGS=%JVM_FLAGS% -Dcom.sun.management.jmxremote.port=25575
set JVM_FLAGS=%JVM_FLAGS% -Dcom.sun.management.jmxremote.authenticate=false
set JVM_FLAGS=%JVM_FLAGS% -Dcom.sun.management.jmxremote.ssl=false

REM Flags de logging para desenvolvimento (Java 8)
REM Java 8 nao suporta -Xlog, usando flags compatíveis
set JVM_FLAGS=%JVM_FLAGS% -Xloggc:logs/gc.log -XX:+PrintGCDetails -XX:+PrintGCDateStamps

echo [INFO] Configuracao de memoria: %MIN_MEM% inicial, %MAX_MEM% maximo
echo [INFO] Iniciando servidor...
echo.

REM Inicia o servidor
if "%JAVA_CMD%"=="" (
    echo [ERRO] JAVA_CMD nao foi definido!
    pause
    exit /b 1
)
"%JAVA_CMD%" %JVM_FLAGS% -jar paper-1.8.8-445.jar nogui

REM Verifica se houve erro na execucao
if %errorlevel% neq 0 (
    echo.
    echo [ERRO] O servidor foi encerrado com erro (codigo: %errorlevel%)
    echo [INFO] Verifique os logs em logs/latest.log
    pause
    exit /b %errorlevel%
)

echo.
echo [INFO] Servidor encerrado normalmente.
pause

