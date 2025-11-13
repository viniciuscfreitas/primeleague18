# Arquitetura Primeleague - Guia de Desenvolvimento

## 📋 Índice

1. [Visão Geral](#visão-geral)
2. [Arquitetura Escolhida](#arquitetura-escolhida)
3. [Por Que Esta Arquitetura?](#por-que-esta-arquitetura)
4. [Estrutura do Core](#estrutura-do-core)
5. [Como Criar Novos Plugins](#como-criar-novos-plugins)
6. [Padrões e Boas Práticas](#padrões-e-boas-práticas)
7. [Exemplos Práticos](#exemplos-práticos)

---

## 🎯 Visão Geral

A arquitetura Primeleague segue o princípio **Grug Brain**: simplicidade, direto ao ponto, sem overengineering. Todos os plugins compartilham uma base comum (Core) que gerencia o banco de dados PostgreSQL através de um pool de conexões thread-safe (HikariCP).

### Princípios Fundamentais

- **Core = Dados + CRUD básico** (sem lógica de negócio)
- **Plugins = Lógica de negócio + Comandos + Listeners**
- **Comunicação via CoreAPI estática** (sem dependências complexas)
- **Pool único de conexões** (HikariCP compartilhado)

---

## 🏗️ Arquitetura Escolhida

### Diagrama de Dependências

```
┌─────────────────────────────────────────┐
│         Core Plugin                     │
│  ┌───────────────────────────────────┐ │
│  │  DatabaseManager (HikariCP Pool)  │ │
│  │  - Pool único compartilhado       │ │
│  │  - Thread-safe por padrão         │ │
│  │  - Config: max 10, min 2          │ │
│  └───────────────────────────────────┘ │
│  ┌───────────────────────────────────┐ │
│  │  CoreAPI (Classe Estática)        │ │
│  │  - getPlayer(uuid)                 │ │
│  │  - getPlayerByName(name)           │ │
│  │  - savePlayer(data)                │ │
│  │  - getDatabase()                   │ │
│  └───────────────────────────────────┘ │
│  ┌───────────────────────────────────┐ │
│  │  PlayerData (Modelo)               │ │
│  │  - POJO simples                    │ │
│  │  - Getters/Setters                 │ │
│  └───────────────────────────────────┘ │
└─────────────────────────────────────────┘
           ▲           ▲           ▲
           │           │           │
    ┌──────┴───┐ ┌────┴────┐ ┌────┴────┐
    │   Auth   │ │ Discord │ │ Payment │
    │  Plugin  │ │  Plugin │ │  Plugin │
    └──────────┘ └─────────┘ └─────────┘
           │           │           │
           └───────────┴───────────┘
                  │
         Todos usam CoreAPI
```

### Componentes Principais

#### 1. Core Plugin
- **Responsabilidade**: Gerenciar banco de dados e fornecer API básica
- **Não faz**: Lógica de negócio, comandos, listeners de gameplay
- **Faz**: CRUD simples, pool de conexões, modelos de dados

#### 2. CoreAPI (Classe Estática)
- **Acesso**: `CoreAPI.getPlayer(uuid)`, `CoreAPI.savePlayer(data)`
- **Thread-safe**: Sim (HikariCP gerencia)
- **Uso**: Todos os plugins acessam via CoreAPI

#### 3. DatabaseManager
- **Pool**: HikariCP (uma única instância)
- **Configuração**: Via `config.yml` do Core
- **Thread-safe**: Sim (HikariCP é thread-safe por padrão)

---

## 🤔 Por Que Esta Arquitetura?

### 1. Simplicidade (Grug Brain)

**Problema**: Como múltiplos plugins acessam o mesmo banco sem conflitos?

**Solução Escolhida**: CoreAPI estática + HikariCP pool único

**Por quê?**
- ✅ Acesso direto: `CoreAPI.getPlayer(uuid)` (sem instâncias)
- ✅ Pool único: todas as conexões gerenciadas em um lugar
- ✅ Thread-safe: HikariCP cuida disso automaticamente
- ✅ Sem overengineering: sem DAO/Repository/Service layers

**Alternativa Rejeitada**: Cada plugin ter seu próprio pool
- ❌ Múltiplas conexões desnecessárias
- ❌ Complexidade desnecessária
- ❌ Risco de esgotar conexões do banco

### 2. Separação de Responsabilidades

**Core = Infraestrutura**
- Banco de dados
- Pool de conexões
- API básica (CRUD)
- Modelos de dados

**Plugins = Lógica de Negócio**
- Cálculos (ELO, stats)
- Comandos (`/elo`, `/stats`)
- Listeners (eventos de jogo)
- Regras de gameplay

**Por quê?**
- ✅ Core limpo e focado
- ✅ Plugins independentes
- ✅ Fácil testar isoladamente
- ✅ Fácil adicionar novos plugins

### 3. Thread Safety

**Problema**: Múltiplos plugins acessando banco simultaneamente

**Solução**: HikariCP pool thread-safe

**Por quê?**
- ✅ HikariCP gerencia thread safety automaticamente
- ✅ Try-with-resources fecha conexões automaticamente
- ✅ Pool compartilhado = eficiente
- ✅ Sem race conditions

### 4. Compatibilidade Paper 1.8.8

**Eventos Síncronos** (PlayerLoginEvent)
- Query síncrona OK (login é raro, query rápida)
- HikariCP é rápido o suficiente

**Eventos Assíncronos** (PlayerJoinEvent)
- Query async recomendada (não bloqueia thread principal)
- Usar `BukkitRunnable.runTaskAsynchronously()`

**Por quê?**
- ✅ Performance adequada
- ✅ Sem bloqueios desnecessários
- ✅ Compatível com Paper 1.8.8

---

## 📦 Estrutura do Core

### CoreAPI.java

```java
public class CoreAPI {
    // Métodos estáticos simples
    public static PlayerData getPlayer(UUID uuid) { ... }
    public static PlayerData getPlayerByName(String name) { ... }
    public static void savePlayer(PlayerData data) { ... }
    public static DatabaseManager getDatabase() { ... }
}
```

**Características:**
- ✅ Métodos estáticos (sem instâncias)
- ✅ Try-with-resources (fecha conexões automaticamente)
- ✅ Queries diretas (sem abstrações)
- ✅ Thread-safe (HikariCP)

### DatabaseManager.java

```java
public class DatabaseManager {
    private HikariDataSource dataSource; // Pool único

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection(); // Thread-safe
    }
}
```

**Características:**
- ✅ Pool único (HikariCP)
- ✅ Thread-safe por padrão
- ✅ Configurável via config.yml

### PlayerData.java

```java
public class PlayerData {
    private UUID uuid;
    private String name;
    private int elo;
    private long money;
    // ... getters/setters
}
```

**Características:**
- ✅ POJO simples
- ✅ Sem lógica de negócio
- ✅ Apenas dados

---

## 🛠️ Como Criar Novos Plugins

### Passo 1: Estrutura Básica

```
primeleague-meuplugin/
├── pom.xml
├── src/
│   └── main/
│       ├── java/
│       │   └── com/primeleague/meuplugin/
│       │       ├── MeuPlugin.java
│       │       ├── listeners/
│       │       │   └── MeuListener.java
│       │       └── commands/
│       │           └── MeuCommand.java
│       └── resources/
│           ├── plugin.yml
│           └── config.yml
```

### Passo 2: pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.primeleague</groupId>
    <artifactId>primeleague-meuplugin</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <properties>
        <java.version>1.8</java.version>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.8.1</version>
                <configuration>
                    <source>1.8</source>
                    <target>1.8</target>
                </configuration>
            </plugin>
        </plugins>
    </build>

    <dependencies>
        <!-- Paper API 1.8.8 -->
        <dependency>
            <groupId>org.spigotmc</groupId>
            <artifactId>spigot-api</artifactId>
            <version>1.8.8-R0.1-SNAPSHOT</version>
            <scope>provided</scope>
        </dependency>

        <!-- Core (dependência de compilação) -->
        <dependency>
            <groupId>com.primeleague</groupId>
            <artifactId>primeleague-core</artifactId>
            <version>1.0.0</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>
</project>
```

### Passo 3: plugin.yml

```yaml
name: PrimeleagueMeuPlugin
version: 1.0.0
main: com.primeleague.meuplugin.MeuPlugin
depend: [PrimeleagueCore]  # Carrega DEPOIS do Core
api-version: 1.8

commands:
  meucomando:
    description: Meu comando
    usage: /meucomando
    permission: meuplugin.use
```

### Passo 4: Plugin Principal

```java
package com.primeleague.meuplugin;

import com.primeleague.core.CoreAPI;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Meu Plugin - Descrição
 * Grug Brain: Plugin simples, depende do Core
 */
public class MeuPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        // Verificar se Core está habilitado
        if (!CoreAPI.isEnabled()) {
            getLogger().severe("PrimeleagueCore não encontrado! Desabilitando plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Salvar config padrão
        saveDefaultConfig();

        // Registrar listeners
        getServer().getPluginManager().registerEvents(new MeuListener(this), this);

        // Registrar comandos
        if (getCommand("meucomando") != null) {
            getCommand("meucomando").setExecutor(new MeuCommand(this));
        }

        getLogger().info("MeuPlugin habilitado");
    }

    @Override
    public void onDisable() {
        getLogger().info("MeuPlugin desabilitado");
    }
}
```

### Passo 5: Usar CoreAPI

```java
package com.primeleague.meuplugin.listeners;

import com.primeleague.core.CoreAPI;
import com.primeleague.core.models.PlayerData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * Listener de eventos
 * Grug Brain: Lógica inline, sem abstrações
 */
public class MeuListener implements Listener {

    private final MeuPlugin plugin;

    public MeuListener(MeuPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        // PlayerDeathEvent é assíncrono em 1.8.8
        // Mas query rápida é OK mesmo síncrona (HikariCP é rápido)

        Player killer = event.getEntity().getKiller();
        if (killer == null) return;

        // 1. Buscar dados do banco via CoreAPI
        PlayerData killerData = CoreAPI.getPlayer(killer.getUniqueId());
        PlayerData victimData = CoreAPI.getPlayer(event.getEntity().getUniqueId());

        if (killerData == null || victimData == null) {
            plugin.getLogger().warning("Player não encontrado no banco");
            return;
        }

        // 2. Fazer sua lógica de negócio aqui
        // Exemplo: atualizar ELO
        int newElo = killerData.getElo() + 10;
        killerData.setElo(newElo);

        // 3. Salvar no banco via CoreAPI
        CoreAPI.savePlayer(killerData);
    }
}
```

### Passo 6: Queries Customizadas (se necessário)

```java
// Para queries que CoreAPI não cobre
import com.primeleague.core.CoreAPI;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

try (Connection conn = CoreAPI.getDatabase().getConnection()) {
    PreparedStatement stmt = conn.prepareStatement(
        "SELECT * FROM minha_tabela WHERE player_uuid = ?");
    stmt.setObject(1, uuid);

    ResultSet rs = stmt.executeQuery();
    while (rs.next()) {
        // Processar resultados
    }
} catch (SQLException e) {
    plugin.getLogger().severe("Erro na query: " + e.getMessage());
}
```

---

## 📐 Padrões e Boas Práticas

### 1. Thread Safety

**✅ CORRETO:**
```java
// HikariCP é thread-safe, pode usar de qualquer thread
PlayerData data = CoreAPI.getPlayer(uuid);
```

**❌ ERRADO:**
```java
// Não precisa sincronizar manualmente
synchronized (lock) {
    PlayerData data = CoreAPI.getPlayer(uuid);
}
```

### 2. Try-With-Resources

**✅ CORRETO:**
```java
try (Connection conn = CoreAPI.getDatabase().getConnection()) {
    // Usar conexão
} // Fecha automaticamente
```

**❌ ERRADO:**
```java
Connection conn = CoreAPI.getDatabase().getConnection();
// Usar conexão
conn.close(); // Pode esquecer de fechar
```

### 3. Eventos Assíncronos

**✅ CORRETO:**
```java
@EventHandler
public void onPlayerJoin(PlayerJoinEvent event) {
    // PlayerJoinEvent é assíncrono
    new BukkitRunnable() {
        @Override
        public void run() {
            PlayerData data = CoreAPI.getPlayer(event.getPlayer().getUniqueId());
            // Processar
        }
    }.runTaskAsynchronously(plugin);
}
```

**⚠️ ACEITÁVEL (mas não ideal):**
```java
@EventHandler
public void onPlayerLogin(PlayerLoginEvent event) {
    // PlayerLoginEvent é síncrono
    // Query rápida é OK (login é raro)
    PlayerData data = CoreAPI.getPlayerByName(event.getPlayer().getName());
}
```

### 4. Separação de Responsabilidades

**✅ CORRETO:**
```java
// Plugin faz lógica de negócio
int newElo = calculateElo(killerElo, victimElo);
killerData.setElo(newElo);
CoreAPI.savePlayer(killerData); // Core só salva
```

**❌ ERRADO:**
```java
// Não colocar lógica de negócio no Core
CoreAPI.calculateAndUpdateElo(killerUuid, victimUuid); // ❌
```

### 5. Tratamento de Erros

**✅ CORRETO:**
```java
PlayerData data = CoreAPI.getPlayer(uuid);
if (data == null) {
    player.sendMessage("§cConta não encontrada.");
    return;
}
```

**❌ ERRADO:**
```java
PlayerData data = CoreAPI.getPlayer(uuid);
data.getElo(); // NullPointerException se data == null
```

---

## 💡 Exemplos Práticos

### Exemplo 1: ELO Plugin

```java
package com.primeleague.elo;

import com.primeleague.core.CoreAPI;
import com.primeleague.core.models.PlayerData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public class EloListener implements Listener {

    private final EloPlugin plugin;

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;

        // Buscar dados
        PlayerData killerData = CoreAPI.getPlayer(killer.getUniqueId());
        PlayerData victimData = CoreAPI.getPlayer(event.getEntity().getUniqueId());

        if (killerData == null || victimData == null) return;

        // Calcular ELO (lógica do plugin)
        int newKillerElo = calculateElo(killerData.getElo(), victimData.getElo(), true);
        int newVictimElo = calculateElo(victimData.getElo(), killerData.getElo(), false);

        // Atualizar
        killerData.setElo(newKillerElo);
        victimData.setElo(newVictimElo);

        // Salvar
        CoreAPI.savePlayer(killerData);
        CoreAPI.savePlayer(victimData);
    }

    private int calculateElo(int playerElo, int opponentElo, boolean won) {
        // Fórmula ELO (lógica do plugin)
        int k = 32; // Fator K
        double expected = 1.0 / (1.0 + Math.pow(10, (opponentElo - playerElo) / 400.0));
        int change = (int) (k * ((won ? 1.0 : 0.0) - expected));
        return Math.max(0, playerElo + change);
    }
}
```

### Exemplo 2: Stats Plugin

```java
package com.primeleague.stats;

import com.primeleague.core.CoreAPI;
import com.primeleague.core.models.PlayerData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public class StatsListener implements Listener {

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        Player victim = event.getEntity();

        // Buscar dados
        PlayerData victimData = CoreAPI.getPlayer(victim.getUniqueId());
        if (victimData == null) return;

        // Incrementar deaths (assumindo que kills/deaths estão no banco)
        // Se não estiver, adicionar colunas na tabela users via migração
        victimData.setDeaths(victimData.getDeaths() + 1);

        if (killer != null) {
            PlayerData killerData = CoreAPI.getPlayer(killer.getUniqueId());
            if (killerData != null) {
                killerData.setKills(killerData.getKills() + 1);
                CoreAPI.savePlayer(killerData);
            }
        }

        CoreAPI.savePlayer(victimData);
    }
}
```

### Exemplo 3: Comando com CoreAPI

```java
package com.primeleague.elo.commands;

import com.primeleague.core.CoreAPI;
import com.primeleague.core.models.PlayerData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class EloCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cEste comando só pode ser usado por jogadores.");
            return true;
        }

        Player player = (Player) sender;

        // Buscar dados do banco
        PlayerData data = CoreAPI.getPlayer(player.getUniqueId());
        if (data == null) {
            player.sendMessage("§cConta não encontrada.");
            return true;
        }

        // Mostrar ELO
        player.sendMessage("§bSeu ELO: §e" + data.getElo());

        return true;
    }
}
```

---

## ✅ Checklist para Novos Plugins

- [ ] Plugin depende apenas do Core (não de outros plugins)
- [ ] Usa `CoreAPI` para acessar banco (não acessa diretamente)
- [ ] Try-with-resources em queries customizadas
- [ ] Eventos assíncronos usam `runTaskAsynchronously()`
- [ ] Verifica se Core está habilitado no `onEnable()`
- [ ] Trata `null` retornado por `CoreAPI.getPlayer()`
- [ ] Lógica de negócio no plugin (não no Core)
- [ ] Comentários Grug Brain explicando decisões

---

## 🎯 Resumo

### Arquitetura Escolhida
- **Core**: Banco de dados + API básica (CRUD)
- **Plugins**: Lógica de negócio + comandos + listeners
- **Comunicação**: CoreAPI estática
- **Pool**: HikariCP único compartilhado

### Por Que?
- ✅ Simplicidade (Grug Brain)
- ✅ Thread-safe (HikariCP)
- ✅ Escalável (pool compartilhado)
- ✅ Testável (plugins independentes)
- ✅ Compatível (Paper 1.8.8)

### Como Fazer Novos Plugins?
1. Depender do Core no `pom.xml`
2. Verificar Core no `onEnable()`
3. Usar `CoreAPI` para acessar banco
4. Lógica de negócio no plugin
5. Try-with-resources em queries customizadas

---

**Última atualização**: 2025-01-XX
**Versão**: 1.0.0
**Autor**: Grug Brain Architecture Team

