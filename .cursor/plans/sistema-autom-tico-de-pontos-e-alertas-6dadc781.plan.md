<!-- 6dadc781-8a99-4b86-a3c8-e9d21588796c d4e59461-fe33-41b0-927e-f3d46b140a1f -->
# Plano: Sistema Automático de Pontos e Alertas - Primeleague Clans

  ## Objetivo

  Implementar sistema 100% automático e autônomo para:

1. **Pontos de Eventos**: Listener que detecta vitórias via evento customizado `ClanWinEvent` + comandos manuais
2. **Alertas Automáticos**: Listener que detecta punições e gera alertas automaticamente + comandos manuais
3. **Sistema de Acúmulo de Alertas**: Penalidades automáticas baseadas em quantidade de alertas (remoção de pontos, bloqueio de eventos)
4. **Histórico de Eventos**: Exibir nas informações do clan quais eventos ganhou e quantas vezes

## Análise e Correções Aplicadas

### Correções Críticas

1. **Eventos Customizados Bukkit 1.8.8**: HandlerList estático obrigatório e métodos `getHandlers()`/`getHandlerList()`
2. **PostgreSQL ALTER TABLE**: Verificação via metadata antes de ALTER (PostgreSQL não suporta IF NOT EXISTS em ALTER TABLE)
3. **PlayerKickEvent**: Substituído por evento customizado `ClanPunishmentEvent` (mais flexível)
4. **Discord API JDA 4.4.0**: Uso correto de `sendMessage(embed.build())` e `EmbedBuilder`
5. **Alertas Permanentes**: Campo `removed` substitui `resolved` (alertas são permanentes, só removidos se punição revisada)
6. **Sistema de Penalidades**: Acúmulo configurável de alertas com remoção de pontos e bloqueio de eventos

## Fase 1: Sistema de Pontos de Eventos (Automático + Manual)

  ### 1.1 Estrutura de Dados PostgreSQL

  **Modificar tabela `clans` (em `ClansPlugin.createTables()`):**

  ```sql
-- PostgreSQL não suporta IF NOT EXISTS em ALTER TABLE
-- Usar verificação via conn.getMetaData().getColumns() antes de ALTER (seguindo padrão existente)
ALTER TABLE clans ADD COLUMN points INTEGER DEFAULT 0;
ALTER TABLE clans ADD COLUMN event_wins_count INTEGER DEFAULT 0;
ALTER TABLE clans ADD COLUMN blocked_from_events BOOLEAN DEFAULT FALSE;  -- Bloqueado de participar de eventos
  ```

  **Criar tabela de histórico (em `ClansPlugin.createTables()`):**

  ```sql
  CREATE TABLE IF NOT EXISTS clan_event_wins (
      id SERIAL PRIMARY KEY,
      clan_id INTEGER NOT NULL,
      event_name VARCHAR(100) NOT NULL,
    points_awarded INTEGER DEFAULT 10,  -- Pode ser negativo (remoção manual)
    awarded_by UUID,  -- NULL se automático
      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
      FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE
  );
  CREATE INDEX IF NOT EXISTS idx_clan_event_wins_clan ON clan_event_wins(clan_id);
  CREATE INDEX IF NOT EXISTS idx_clan_event_wins_time ON clan_event_wins(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_clan_event_wins_event ON clan_event_wins(event_name);
  ```

**Implementação:** Usar verificação via `conn.getMetaData().getColumns()` antes de ALTER TABLE (seguindo padrão existente em `createTables()` para `home_world`, `home_x`, etc).

### 1.2 Evento Customizado (Bukkit 1.8.8)

  **Criar `plugins/primeleague-clans/src/main/java/com/primeleague/clans/events/ClanWinEvent.java`:**

```java
package com.primeleague.clans.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import java.util.UUID;

public class ClanWinEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final int clanId;
    private final String eventName;
    private final int points;
    private final UUID awardedBy;

    public ClanWinEvent(int clanId, String eventName, int points, UUID awardedBy) {
        this.clanId = clanId;
        this.eventName = eventName;
        this.points = points;
        this.awardedBy = awardedBy;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    // Getters: getClanId(), getEventName(), getPoints(), getAwardedBy()
}
```

**Nota:** HandlerList estático obrigatório para Bukkit 1.8.8.

  ### 1.3 Métodos no ClansManager

  **Adicionar em `ClansManager.java`:**

  ```java
  public boolean addEventWin(int clanId, String eventName, int points, UUID awardedBy) {
    // 1. Validar clan existe e não está bloqueado de eventos
    ClanData clan = getClan(clanId);
    if (clan == null) return false;
    if (isClanBlockedFromEvents(clanId)) {
        plugin.getLogger().warning("Clan " + clan.getName() + " está bloqueado de eventos!");
        return false;
    }

      // 2. Inserir em clan_event_wins (histórico)
    // 3. Atualizar points e event_wins_count na tabela clans (atomic via UPDATE)
    //    UPDATE clans SET points = points + ?, event_wins_count = event_wins_count + 1 WHERE id = ?
      // 4. Invalidar cache de ranking "points"
      // 5. Notificar Discord async (se disponível)
      // 6. Retornar true/false
  }

public boolean addPoints(int clanId, int points, UUID addedBy) {
    // 1. Validar clan existe
    // 2. Atualizar points: UPDATE clans SET points = points + ? WHERE id = ?
    // 3. Inserir registro em clan_event_wins com points_awarded positivo (histórico)
    // 4. Invalidar cache de ranking "points"
    // 5. Retornar true/false
}

public boolean removePoints(int clanId, int points, UUID removedBy) {
    // 1. Validar clan existe
    // 2. Atualizar points (pode ficar negativo): UPDATE clans SET points = points - ? WHERE id = ?
    // 3. Inserir registro em clan_event_wins com points_awarded negativo (histórico)
    // 4. Invalidar cache de ranking "points"
    // 5. Retornar true/false
}

public int getClanPoints(int clanId) {
    // SELECT points FROM clans WHERE id = ?
}

public List<EventWinRecord> getClanEventWins(int clanId, int limit) {
    // SELECT * FROM clan_event_wins WHERE clan_id = ? ORDER BY created_at DESC LIMIT ?
}

public Map<String, Integer> getClanEventWinsByEvent(int clanId) {
    // SELECT event_name, COUNT(*) as count FROM clan_event_wins WHERE clan_id = ? GROUP BY event_name
    // Retorna Map<evento, quantidade>
}

public boolean isClanBlockedFromEvents(int clanId) {
    // SELECT blocked_from_events FROM clans WHERE id = ?
}
```

**Classe auxiliar `EventWinRecord`:** POJO simples (id, clanId, eventName, pointsAwarded, awardedBy, createdAt).

  ### 1.4 Listener Automático

  **Criar `plugins/primeleague-clans/src/main/java/com/primeleague/clans/listeners/ClanEventWinListener.java`:**

```java
@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
public void onClanWin(ClanWinEvent event) {
    plugin.getClansManager().addEventWin(
        event.getClanId(),
        event.getEventName(),
        event.getPoints(),
        event.getAwardedBy()
    );
    // Log no console: [CLAN WIN] Clan {name} ganhou evento {eventName} (+{points} pontos)
}
```

  **Registrar em `ClansPlugin.onEnable()`:**

  ```java
  getServer().getPluginManager().registerEvents(new ClanEventWinListener(this), this);
  ```

  ### 1.5 Configuração de Pontos

  **Adicionar em `config.yml`:**

  ```yaml
  points:
    default-award: 10  # Pontos padrão se não especificado no evento
    events:  # Pontos por tipo de evento
      pvp-weekly: 15
      pvp-daily: 10
      tournament: 50
  ```

  **Método auxiliar em `ClansPlugin`:**

  ```java
  public int getPointsForEvent(String eventName) {
    int points = getConfig().getInt("points.events." + eventName, -1);
    if (points == -1) {
        return getConfig().getInt("points.default-award", 10);
    }
    return points;
  }
  ```

### 1.6 Modificar Ranking e Info

  **Atualizar `ClanCommand.handleTop()`:**

  - Adicionar opção `points`: `/clan top points [página]`
- Query: `SELECT name, points, event_wins_count FROM clans WHERE points IS NOT NULL ORDER BY points DESC, event_wins_count DESC LIMIT ? OFFSET ?`
- Cache similar a ELO/kills (TTL 300s via `getTopCache()`/`setTopCache()`)

**Atualizar `ClanCommand.handleInfo()`:**

- Adicionar seção de eventos ganhos:
  - Exibir: `§eEventos Ganhos: §7{evento1} (x{quantidade}), {evento2} (x{quantidade})...`
  - Usar `getClanEventWinsByEvent()` para obter Map<evento, quantidade>
  - Limitar a 10 eventos mais frequentes ou todos se <= 10
  - Formato: `§eEventos Ganhos: §7pvp-weekly (x5), tournament (x2), pvp-daily (x1)`

  ### 1.7 PlaceholderAPI

  **Adicionar em `ClansPlaceholderExpansion.java`:**

- `%clans_points%` - Pontos do clan (query sync, cache 60s opcional)
- `%clans_event_wins%` - Número de vitórias (query sync)

**Nota:** PlaceholderAPI não suporta async, usar queries sync (padrão existente).

### 1.8 Comandos Admin (Manual)

  **Adicionar em `ClanCommand.handleAdmin()`:**

- `/clan admin addwin <clan> <evento> [pontos]` - Adiciona vitória manualmente
  - Dispara `ClanWinEvent` programaticamente via `Bukkit.getPluginManager().callEvent()`
  - Útil para correções manuais

- `/clan admin addpoints <clan> <pontos>` - Adiciona pontos manualmente
  - Chama `clansManager.addPoints()` diretamente
  - Não dispara evento (evita duplicação)

- `/clan admin removepoints <clan> <pontos>` - Remove pontos manualmente
  - Chama `clansManager.removePoints()` diretamente
  - Pontos podem ficar negativos
  - Não dispara evento (evita duplicação)

## Fase 2: Sistema de Alertas Automáticos (Automático + Manual)

  ### 2.1 Estrutura de Dados PostgreSQL

  **Criar tabela (em `ClansPlugin.createTables()`):**

  ```sql
  CREATE TABLE IF NOT EXISTS clan_alerts (
      id SERIAL PRIMARY KEY,
      clan_id INTEGER NOT NULL,
      player_uuid UUID,  -- NULL se for alerta do clan inteiro
      alert_type VARCHAR(50) NOT NULL,  -- WARNING, PUNISHMENT, BAN, CHEAT, INFO
      punishment_id VARCHAR(100),  -- ID da punição (para integração futura)
      message VARCHAR(500) NOT NULL,
      created_by UUID,  -- NULL se automático
      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    removed BOOLEAN DEFAULT FALSE,  -- TRUE se punição foi revisada e removida
    removed_at TIMESTAMP,
    removed_by UUID,  -- Staff que removeu o alerta
    FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE
  );
  CREATE INDEX IF NOT EXISTS idx_clan_alerts_clan ON clan_alerts(clan_id);
CREATE INDEX IF NOT EXISTS idx_clan_alerts_removed ON clan_alerts(removed, created_at DESC);
  CREATE INDEX IF NOT EXISTS idx_clan_alerts_player ON clan_alerts(player_uuid);
  CREATE INDEX IF NOT EXISTS idx_clan_alerts_punish ON clan_alerts(punishment_id);
  ```

**Nota:**
- Alertas são permanentes (não expiram)
- `removed` indica se punição foi revisada e removida (não conta para penalidades)
- Foreign key para `users(uuid)` removida (player_uuid pode ser NULL)

  ### 2.2 Modelo de Dados

  **Criar `plugins/primeleague-clans/src/main/java/com/primeleague/clans/models/ClanAlert.java`:**

- POJO simples (id, clanId, playerUuid, alertType, message, createdBy, createdAt, removed, removedAt, removedBy, punishmentId)
  - Getters/setters padrão

**Nota:** `removed` substitui `resolved` (alertas são permanentes, só removidos se punição revisada).

  ### 2.3 Métodos no ClansManager

  **Adicionar em `ClansManager.java`:**

  ```java
  public boolean addAlert(int clanId, UUID playerUuid, String alertType, String message, UUID createdBy, String punishmentId) {
      // 1. Validar clan existe
      // 2. Se playerUuid não null, validar que é membro do clan
    // 3. Inserir em clan_alerts (removed = FALSE)
    // 4. Verificar se deve aplicar penalidades (pontos removidos, bloqueio)
    //    Chamar applyAlertPenalties(clanId)
    // 5. Notificar Discord async (se disponível)
    // 6. Notificar membros online do clan (thread principal via scheduler)
    // 7. Retornar true/false
}

public boolean removeAlert(int alertId, UUID removedBy) {
    // UPDATE clan_alerts SET removed = TRUE, removed_at = CURRENT_TIMESTAMP, removed_by = ? WHERE id = ?
    // Recalcular penalidades (pontos podem ser devolvidos, bloqueio pode ser removido)
    // Chamar applyAlertPenalties(clanId) novamente
    // Retornar true/false
  }

public List<ClanAlert> getClanAlerts(int clanId, boolean includeRemoved) {
    // SELECT * FROM clan_alerts WHERE clan_id = ? AND (removed = ? OR ?) ORDER BY removed ASC, created_at DESC
  }

public int getClanAlertCount(int clanId) {
    // SELECT COUNT(*) FROM clan_alerts WHERE clan_id = ? AND removed = FALSE
    // Cache TTL 60s
  }

  public int getClanPunishmentCount(int clanId) {
    // SELECT COUNT(*) FROM clan_alerts WHERE clan_id = ? AND removed = FALSE AND alert_type IN ('PUNISHMENT', 'BAN')
      // Cache TTL 60s
  }

private void applyAlertPenalties(int clanId) {
    // 1. Obter contagem de alertas (removed = FALSE)
    int alertCount = getClanAlertCount(clanId);

    // 2. Verificar config: alerts.points-removal-threshold e alerts.points-removal-amount
    int threshold = plugin.getConfig().getInt("alerts.points-removal-threshold", 3);
    int amount = plugin.getConfig().getInt("alerts.points-removal-amount", 10);

    // 3. Calcular quantos pontos remover (a cada threshold, remove amount)
    //    Exemplo: 3 alertas = -10 pontos, 6 alertas = -20 pontos, 9 alertas = -30 pontos
    int timesThreshold = alertCount / threshold;
    int pointsToRemove = timesThreshold * amount;

    // 4. Calcular pontos atuais e pontos que deveriam ter (baseado em histórico)
    //    SELECT SUM(points_awarded) FROM clan_event_wins WHERE clan_id = ?
    //    Pontos corretos = pontos_histórico - pontos_removidos_por_alertas
    //    Se pontos atuais > pontos corretos, remover diferença (pode ficar negativo)

    // 5. Verificar config: alerts.block-threshold
    int blockThreshold = plugin.getConfig().getInt("alerts.block-threshold", 10);

    // 6. Se alertCount >= blockThreshold, bloquear clan de eventos
    //    UPDATE clans SET blocked_from_events = TRUE WHERE id = ?
    // 7. Se alertCount < blockThreshold, desbloquear
    //    UPDATE clans SET blocked_from_events = FALSE WHERE id = ?
}
```

**Nota:** Penalidades aplicadas automaticamente ao adicionar alerta. Recalcular ao remover alerta.

### 2.4 Evento Customizado e Listener Automático

**Criar `plugins/primeleague-clans/src/main/java/com/primeleague/clans/events/ClanPunishmentEvent.java`:**

- Estende `org.bukkit.event.Event` com HandlerList estático (obrigatório Bukkit 1.8.8)
- Campos: `clanId`, `playerUuid`, `alertType`, `message`, `punishmentId`
- Para integração com plugin de punições próprio

  **Criar `plugins/primeleague-clans/src/main/java/com/primeleague/clans/listeners/ClanPunishmentListener.java`:**

- Escuta `ClanPunishmentEvent` com `@EventHandler(priority = EventPriority.MONITOR)`
- Chama `clansManager.addAlert()` automaticamente
- Penalidades aplicadas automaticamente dentro de `addAlert()`

  **Registrar em `ClansPlugin.onEnable()`:**

  ```java
  getServer().getPluginManager().registerEvents(new ClanPunishmentListener(this), this);
  ```

  **API pública em `ClansPlugin.java`:**

  ```java
  public void addPunishmentAlert(int clanId, UUID playerUuid, String type, String message, String punishmentId) {
      getClansManager().addAlert(clanId, playerUuid, type, message, null, punishmentId);
  }

public boolean isClanBlockedFromEvents(int clanId) {
    return getClansManager().isClanBlockedFromEvents(clanId);
  }
  ```

**Nota:** Outros plugins podem disparar `ClanPunishmentEvent` ou chamar `addPunishmentAlert()` diretamente.

### 2.5 Configuração de Alertas

  **Adicionar em `config.yml`:**

  ```yaml
  alerts:
  points-removal-threshold: 3  # Número de alertas antes de remover pontos
  points-removal-amount: 10  # Quantos pontos remover por threshold atingido
  block-threshold: 10  # Número de alertas antes de bloquear clan de eventos
    auto-notify-online: true  # Notificar membros online quando alerta é criado
  ```

**Nota:**
- Sistema de acúmulo: a cada `points-removal-threshold` alertas, remove `points-removal-amount` pontos
- Exemplo: 3 alertas = -10 pontos, 6 alertas = -20 pontos, 9 alertas = -30 pontos
- Com 10 alertas, clan é bloqueado de participar de eventos
- Alertas são permanentes (não expiram), só removidos se punição revisada

### 2.6 Comandos

  **Adicionar em `ClanCommand.java`:**

  - `/clan alertas [página]` - Lista alertas do clan (Leader/Officer)
  - Não removidos primeiro
          - Formato: `§c[ALERTA] §7{tipo} §7- §e{mensagem} §7(por {staff} em {data})`
  - Mostrar total de alertas e status de bloqueio: `§7Total: §e{count} §7| §cBloqueado: {sim/não}`

  - `/clan admin alertar <clan> [player] <tipo> <mensagem>` - Staff adiciona alerta manual
          - Se `player` não especificado, alerta é do clan inteiro
          - Mensagem pode ter espaços (juntar args)
  - Penalidades aplicadas automaticamente

- `/clan admin removealert <alerta_id>` - Staff remove alerta (se punição revisada)
  - Chama `clansManager.removeAlert()`
  - Recalcula penalidades (pontos podem ser devolvidos, bloqueio pode ser removido)
  - Mensagem: `§aAlerta §e#{id} §aremovido! Penalidades recalculadas.`

**Nota:** Removido comando `/clan admin resolver` (não faz sentido - alertas são permanentes).

### 2.7 Notificações Discord

  **Adicionar em `DiscordIntegration.java`:**

  ```java
  public void notifyDiscordAlert(ClanData clan, String alertType, String message, UUID playerUuid) {
    if (!isDiscordEnabled() || clan.getDiscordChannelId() == null) {
        return;
    }

    // Rate limiting (reutilizar rateLimitCache)
    String rateLimitKey = clan.getId() + "_alert_" + alertType;
    Long lastSent = rateLimitCache.get(rateLimitKey);
    if (lastSent != null && System.currentTimeMillis() - lastSent < 60000) {
        return; // Rate limited
    }

    JDA jda = getJDA();
    if (jda == null) return;

    TextChannel channel = jda.getTextChannelById(clan.getDiscordChannelId());
    if (channel == null) return;

      // Embed com título "⚠️ Novo Alerta"
    net.dv8tion.jda.api.EmbedBuilder embed = new net.dv8tion.jda.api.EmbedBuilder();
    embed.setTitle("⚠️ Novo Alerta");
    embed.addField("Tipo", alertType, true);
    if (playerUuid != null) {
        // Buscar nome do player via CoreAPI
        embed.addField("Membro", playerName, true);
    }
    embed.addField("Mensagem", message, false);
      // Cor: Vermelho para PUNISHMENT/BAN, Amarelo para WARNING
    embed.setColor(alertType.equals("PUNISHMENT") || alertType.equals("BAN") ? 0xFF0000 : 0xFFFF00);
    embed.setFooter(new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm").format(new java.util.Date()));

    // Async via queue() (JDA 4.4.0)
    channel.sendMessage(embed.build()).queue(
        (success) -> rateLimitCache.put(rateLimitKey, System.currentTimeMillis()),
        (error) -> plugin.getLogger().warning("Erro ao enviar notificação Discord: " + error.getMessage())
    );
}

  public void notifyDiscordClanWin(ClanData clan, String eventName, int points) {
    if (!isDiscordEnabled() || clan.getDiscordChannelId() == null) {
        return;
    }

    // Rate limiting
    String rateLimitKey = clan.getId() + "_win_" + eventName;
    Long lastSent = rateLimitCache.get(rateLimitKey);
    if (lastSent != null && System.currentTimeMillis() - lastSent < 60000) {
        return; // Rate limited
    }

    JDA jda = getJDA();
    if (jda == null) return;

    TextChannel channel = jda.getTextChannelById(clan.getDiscordChannelId());
    if (channel == null) return;

      // Embed com título "🏆 Vitória em Evento"
    net.dv8tion.jda.api.EmbedBuilder embed = new net.dv8tion.jda.api.EmbedBuilder();
    embed.setTitle("🏆 Vitória em Evento");
    embed.addField("Evento", eventName, true);
    embed.addField("Pontos ganhos", String.valueOf(points), true);
    embed.addField("Total de pontos", String.valueOf(clan.getPoints()), true);
    embed.setColor(0x00FF00); // Verde
    embed.setFooter(new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm").format(new java.util.Date()));

    // Async via queue() (JDA 4.4.0)
    channel.sendMessage(embed.build()).queue(
        (success) -> rateLimitCache.put(rateLimitKey, System.currentTimeMillis()),
        (error) -> plugin.getLogger().warning("Erro ao enviar notificação Discord: " + error.getMessage())
    );
  }
  ```

**Nota:** JDA 4.4.0 usa `channel.sendMessage(embed.build()).queue()`, não `sendMessageEmbeds()`.

  ## Fase 3: Integrações e Melhorias

  ### 3.1 Cache de Alertas

  **Adicionar em `ClansPlugin.java`:**

- `Map<Integer, AlertCache> alertCache` - Cache de contagem de alertas por clan (TTL 60s)
  - Métodos: `getAlertCache()`, `setAlertCache()`, `invalidateAlertCache()`
- Classe interna `AlertCache` similar a `EloCache`:
  ```java
  public static class AlertCache {
      private final int count;
      private final long timestamp;
      public AlertCache(int count) {
          this.count = count;
          this.timestamp = System.currentTimeMillis();
      }
      public int getCount() { return count; }
      public long getTimestamp() { return timestamp; }
  }
  ```

  ### 3.2 Invalidar Cache

  **Adicionar métodos em `ClansPlugin.java`:**

  - `invalidateTopCache(String type)` - Para invalidar cache de ranking quando pontos mudam
- Chamar em `addEventWin()`, `addPoints()`, `removePoints()` após atualizar pontos

**Nota:** Usar padrão existente: `topCache.remove(cacheKey)`.

  ### 3.3 Validações

  **Em todos os métodos:**

  - Verificar se clan existe antes de operações
  - Verificar se player é membro (se playerUuid não null)
  - Validar tipos de alerta (lista fixa: WARNING, PUNISHMENT, BAN, CHEAT, INFO)
  - Validar permissões (clans.admin para comandos admin)
- Verificar se clan está bloqueado antes de adicionar vitória

  ## Padrões Grug Brain

  ### Thread Safety

  - Todas queries via `CoreAPI.getDatabase().getConnection()` (HikariCP)
  - Try-with-resources sempre
- Async para queries pesadas (listar alertas, rankings) via `runTaskAsynchronously()`
  - `ConcurrentHashMap` para cache

  ### Performance

  - Índices PostgreSQL em todas tabelas
  - Cache TTL 300s para rankings, 60s para alertas
  - Invalidar cache quando dados mudam

  ### Compatibilidade Paper 1.8.8

- Eventos customizados seguem padrão Bukkit 1.8.8 (HandlerList estático obrigatório)
- Sem APIs modernas (Java 8)
  - Usar `String` com códigos `§` para mensagens

  ### Mensagens PT-BR

  - Todas mensagens em português brasileiro
  - Códigos de cor: `§a` sucesso, `§c` alerta/erro, `§e` destaque, `§7` info

  ## Arquivos a Modificar/Criar

  ### Criar:

  - `plugins/primeleague-clans/src/main/java/com/primeleague/clans/events/ClanWinEvent.java`
  - `plugins/primeleague-clans/src/main/java/com/primeleague/clans/events/ClanPunishmentEvent.java`
  - `plugins/primeleague-clans/src/main/java/com/primeleague/clans/listeners/ClanEventWinListener.java`
  - `plugins/primeleague-clans/src/main/java/com/primeleague/clans/listeners/ClanPunishmentListener.java`
  - `plugins/primeleague-clans/src/main/java/com/primeleague/clans/models/ClanAlert.java`
- `plugins/primeleague-clans/src/main/java/com/primeleague/clans/models/EventWinRecord.java` (POJO auxiliar)

  ### Modificar:

  - `plugins/primeleague-clans/src/main/java/com/primeleague/clans/ClansPlugin.java` - Adicionar colunas, registrar listeners, métodos de cache
- `plugins/primeleague-clans/src/main/java/com/primeleague/clans/managers/ClansManager.java` - Métodos addEventWin, addPoints, removePoints, addAlert, removeAlert, getClanAlerts, getClanEventWinsByEvent, applyAlertPenalties, isClanBlockedFromEvents
- `plugins/primeleague-clans/src/main/java/com/primeleague/clans/commands/ClanCommand.java` - Adicionar handleTop points, handleInfo (eventos), handleAlertas, handleAdminAlertar, handleAdminRemovealert, handleAdminAddwin, handleAdminAddpoints, handleAdminRemovepoints
  - `plugins/primeleague-clans/src/main/java/com/primeleague/clans/integrations/ClansPlaceholderExpansion.java` - Adicionar %clans_points%, %clans_event_wins%
  - `plugins/primeleague-clans/src/main/java/com/primeleague/clans/integrations/DiscordIntegration.java` - Métodos notifyDiscordAlert, notifyDiscordClanWin
  - `plugins/primeleague-clans/src/main/resources/config.yml` - Adicionar seções points e alerts

  ## Ordem de Implementação

  1. **Estrutura de Dados** (1h)
   - Adicionar colunas na tabela clans (com verificação)
              - Criar tabelas clan_event_wins e clan_alerts
   - Criar modelos ClanAlert e EventWinRecord

  2. **Sistema de Pontos** (2-3h)
   - Criar ClanWinEvent (com HandlerList estático)
   - Métodos no ClansManager (addEventWin, addPoints, removePoints, getClanEventWinsByEvent)
              - Listener ClanEventWinListener
   - Modificar ranking e info
              - PlaceholderAPI
   - Comandos admin (addwin, addpoints, removepoints)

  3. **Sistema de Alertas** (3-4h)
   - Criar ClanPunishmentEvent (com HandlerList estático)
   - Métodos no ClansManager (addAlert, removeAlert, applyAlertPenalties, isClanBlockedFromEvents)
              - Listener ClanPunishmentListener
   - Comandos (alertas, admin alertar, admin removealert)
              - Notificações Discord

  4. **Integrações** (1h)
              - Cache
              - Validações
              - Testes

  **Total estimado: 7-9h**

## Mudanças em Relação ao Plano Original

1. **Eventos Customizados**: Adicionada estrutura correta com HandlerList estático (obrigatório Bukkit 1.8.8)
2. **PostgreSQL**: Corrigido uso de ALTER TABLE (verificação via metadata antes de adicionar coluna)
3. **PlayerKickEvent**: Removido, substituído por evento customizado `ClanPunishmentEvent` (mais flexível)
4. **Discord API**: Verificado uso correto de JDA 4.4.0 (`sendMessage(embed.build())`)
5. **Cache**: Simplificado para seguir padrão existente (TopCache/EloCache)
6. **Config**: Removido `grave-punishments` (não necessário com evento customizado)
7. **Alertas Permanentes**: Campo `removed` substitui `resolved` (alertas são permanentes)
8. **Sistema de Penalidades**: Acúmulo configurável com remoção de pontos e bloqueio de eventos
9. **Comandos Manuais**: Adicionados comandos para adicionar/remover pontos e alertas manualmente
10. **Histórico de Eventos**: Exibição de eventos ganhos nas informações do clan
11. **Bloqueio de Eventos**: Clan bloqueado não pode ganhar eventos
12. **Remover Alerta**: Substituído `resolver` por `removealert` (recalcula penalidades)
