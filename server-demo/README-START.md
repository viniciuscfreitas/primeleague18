# Arquivos Necessários para start.bat / start.sh

## Arquivos OBRIGATÓRIOS

### Para start.bat (Windows):
- `paper-1.8.8-445.jar` - JAR do servidor Paper (obrigatório)
- `eula.txt` - EULA aceita (obrigatório na primeira execução)
- `jdk8.txt` - Caminho para o JDK 8 (obrigatório para start.bat)

### Para start.sh (Linux/macOS):
- `paper-1.8.8-445.jar` - JAR do servidor Paper (obrigatório)
- `eula.txt` - EULA aceita (obrigatório na primeira execução)
- Java 8 no PATH ou JAVA_HOME configurado

### Scripts de inicialização:
- `start.bat` - Script Windows
- `start.sh` - Script Linux/macOS

## Arquivos OPCIONAIS (mas recomendados)

### Configurações do servidor (criados automaticamente se não existirem):
- `server.properties` - Configurações principais do servidor
- `bukkit.yml` - Configurações do Bukkit
- `spigot.yml` - Configurações do Spigot
- `paper.yml` - Configurações do Paper
- `commands.yml` - Configurações de comandos
- `help.yml` - Configurações de ajuda
- `wepif.yml` - Configurações do WorldEdit (se usar)

### Mundos:
- `world/` - Mundo principal
- `world_nether/` - Mundo do Nether
- `world_the_end/` - Mundo do End

### Plugins:
- `plugins/` - Diretório de plugins

## Arquivos REMOVIDOS (não necessários para start local)

Estes arquivos foram removidos pois não são necessários para executar `start.bat` ou `start.sh` localmente:

- `deploy-git.sh` - Script de deploy via Git (removido)
- `deploy-rsync.ps1` - Script de deploy via rsync (removido)
- `deploy-vps.sh` - Script de deploy para VPS (removido)
- `upload-to-vps.ps1` - Script de upload para VPS (removido)
- `minecraft.service` - Arquivo systemd (removido - apenas para produção Linux)

## Arquivos GERADOS AUTOMATICAMENTE (não devem estar no repo)

Estes arquivos são gerados automaticamente pelo servidor em runtime:
- `usercache.json` - Cache de usuários
- `ops.json` - Lista de operadores
- `banned-players.json` - Lista de jogadores banidos
- `banned-ips.json` - Lista de IPs banidos
- `whitelist.json` - Lista de whitelist
- `logs/` - Diretório de logs
- `cache/` - Diretório de cache

## Arquivos GERADOS AUTOMATICAMENTE (podem ser removidos se não customizados)

Estes arquivos são gerados automaticamente pelo Paper na primeira execução. Se você não os customizou, podem ser removidos e serão recriados:

- `bukkit.yml` - Configurações do Bukkit (gerado automaticamente)
- `spigot.yml` - Configurações do Spigot (gerado automaticamente)
- `paper.yml` - Configurações do Paper (gerado automaticamente)
- `commands.yml` - Configurações de comandos (gerado automaticamente)
- `help.yml` - Configurações de ajuda (gerado automaticamente)
- `wepif.yml` - Configurações do WorldEdit (gerado automaticamente se plugin instalado)
- `permissions.yml` - Permissões (gerado automaticamente - já removido por estar vazio)

**Nota:** Se você customizou algum desses arquivos, mantenha-os no repositório.

